package com.personalos.backend.habits;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it, so today is 2026-09-24 (a Thursday, ISO
 * weekday 4) in UTC.
 */
class HabitApiTest extends ApiTestBase {

    private static final String HABITS = "/api/v1/habits";
    private static final String TODAY = "2026-09-24";

    private static Map<String, Object> body(String name, List<Integer> days, String startedOn, UUID goalId) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        if (days != null) m.put("daysOfWeek", days);
        if (startedOn != null) m.put("startedOn", startedOn);
        if (goalId != null) m.put("goalId", goalId.toString());
        return m;
    }

    private JsonNode create(TestClient c, String name, List<Integer> days, String startedOn) throws Exception {
        return expect(c, c.post(HABITS, body(name, days, startedOn, null)), 201);
    }

    private JsonNode createDaily(TestClient c, String name) throws Exception {
        return create(c, name, List.of(1, 2, 3, 4, 5, 6, 7), null);
    }

    private UUID goal(UUID user, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into goals (id, user_id, title) values (?, ?, ?)", id, user, title);
        return id;
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from habits where user_id = ?", Integer.class, user);
    }

    private JsonNode list(TestClient c, String query) throws Exception {
        return expect(c, c.get(HABITS + query), 200);
    }

    // ---- creating ------------------------------------------------------------------------------

    @Test
    void aDailyHabitDefaultsToStartingTodayAndHasNoStreakYet() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode h = createDaily(c, "Read");
        assertThat(h.get("id").asText()).isNotBlank();
        assertThat(h.get("name").asText()).isEqualTo("Read");
        assertThat(h.get("daysOfWeek")).hasSize(7);
        assertThat(h.get("startedOn").asText()).isEqualTo(TODAY);
        assertThat(h.get("archived").asBoolean()).isFalse();
        assertThat(h.get("goal").isNull()).isTrue();
        assertThat(h.get("scheduledToday").asBoolean()).isTrue();
        assertThat(h.get("doneToday").asBoolean()).isFalse();
        assertThat(h.get("currentStreak").asInt()).isZero();
        assertThat(h.get("longestStreak").asInt()).isZero();
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void nameIsTrimmedAndDaysOfWeekComeBackSortedRegardlessOfInputOrder() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode h = create(c, "  Stretch  ", List.of(5, 1, 3), null);
        assertThat(h.get("name").asText()).isEqualTo("Stretch");
        assertThat(h.get("daysOfWeek")).extracting(JsonNode::asInt).containsExactly(1, 3, 5);
    }

    @Test
    void aStartDateInThePastIsAcceptedForBackfillingAnExistingRoutine() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode h = create(c, "Meditate", List.of(1, 2, 3, 4, 5, 6, 7), "2026-01-01");
        assertThat(h.get("startedOn").asText()).isEqualTo("2026-01-01");
    }

    @Test
    void aHabitCanBeLinkedToAGoalOnCreation() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID g = goal(userId(c), "Get fit");
        JsonNode h = expect(c, c.post(HABITS, body("Walk", List.of(1, 2, 3, 4, 5, 6, 7), null, g)), 201);
        assertThat(h.get("goal").get("id").asText()).isEqualTo(g.toString());
        assertThat(h.get("goal").get("title").asText()).isEqualTo("Get fit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankNameIsRejected(String name) throws Exception {
        TestClient c = newUser("a@example.com");
        MvcResult r = c.post(HABITS, body(name, List.of(1), null, null));
        expectError(c, r, 400, "VALIDATION_FAILED");
        assertThat(c.json(r).get("violations").toString()).contains("name");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void namesAndDaysOfWeekAreValidated() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, c.post(HABITS, body("x".repeat(101), List.of(1), null, null)), 400, "VALIDATION_FAILED");
        expect(c, c.post(HABITS, body("ok", List.of(1), null, null)), 201);
        expectError(c, c.post(HABITS, body("no days", List.of(), null, null)), 400, "VALIDATION_FAILED");
        expectError(c, c.post(HABITS, Map.of("name", "no days field")), 400, "VALIDATION_FAILED");
        expectError(c, c.post(HABITS, body("bad day", List.of(0), null, null)), 400, "VALIDATION_FAILED");
        expectError(c, c.post(HABITS, body("bad day", List.of(8), null, null)), 400, "VALIDATION_FAILED");
        expectError(c, c.post(HABITS, body("dup day", List.of(1, 1), null, null)), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void startDatesAreBoundedToTodayAndToThe2000Floor() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, c.post(HABITS, body("future", List.of(1), "2026-09-25", null)), 400, "DATE_IN_FUTURE");
        expectError(c, c.post(HABITS, body("ancient", List.of(1), "1999-12-31", null)), 400, "DATE_TOO_EARLY");
        expect(c, c.post(HABITS, body("today ok", List.of(1), TODAY, null)), 201);
        expect(c, c.post(HABITS, body("floor ok", List.of(1), "2000-01-01", null)), 201);
        assertThat(rows(userId(c))).isEqualTo(2);
    }

    @Test
    void aGoalThatIsUnknownOrSomeoneElsesIsNotFoundAndNothingIsCreated() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID theirs = goal(userId(b), "Secret");
        for (UUID g : new UUID[]{theirs, UUID.randomUUID()}) {
            expectError(a, a.post(HABITS, body("h", List.of(1), null, g)), 404, "NOT_FOUND");
        }
        assertThat(rows(userId(a))).isZero();
    }

    @Test
    void activeNamesAreUniquePerPersonCaseInsensitivelyButNotAcrossPeople() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        createDaily(a, "Read");
        expectError(a, a.post(HABITS, body("read", List.of(1), null, null)), 409, "DUPLICATE_HABIT");
        expectError(a, a.post(HABITS, body("READ", List.of(1), null, null)), 409, "DUPLICATE_HABIT");
        expect(b, b.post(HABITS, body("Read", List.of(1), null, null)), 201);
        assertThat(rows(userId(a))).isEqualTo(1);
    }

    // ---- listing -------------------------------------------------------------------------------

    @Test
    void listOrdersActiveFirstThenArchivedAlphabeticallyAndExcludesArchivedByDefault() throws Exception {
        TestClient c = newUser("a@example.com");
        createDaily(c, "Zebra");
        createDaily(c, "apple");
        String bId = createDaily(c, "Banana").get("id").asText();
        expect(c, c.post(HABITS + "/" + bId + "/archive", Map.of()), 200);

        assertThat(names(list(c, ""))).containsExactly("apple", "Zebra");
        assertThat(names(list(c, "?includeArchived=true"))).containsExactly("apple", "Zebra", "Banana");
    }

    private static List<String> names(JsonNode list) {
        return java.util.stream.StreamSupport.stream(list.spliterator(), false).map(n -> n.get("name").asText()).toList();
    }

    @Test
    void scheduledTodayFollowsTheWeekdayAndTheStartDateAndThePersonsTimezone() throws Exception {
        // 2026-09-24 is a Thursday (ISO 4). At 10:00 UTC it is already 2026-09-25 (Friday, ISO 5) in Kiritimati (UTC+14).
        TestClient c = newUser("a@example.com");
        JsonNode thursdayOnly = create(c, "Thu", List.of(4), null);
        JsonNode fridayOnly = create(c, "Fri", List.of(5), null);
        JsonNode future = create(c, "Future", List.of(4, 5), "2026-09-24"); // starts today, fine
        jdbc.update("update habits set started_on = '2026-09-25' where id = ?::uuid", future.get("id").asText());

        JsonNode l = list(c, "");
        assertThat(scheduledToday(l, "Thu")).isTrue();
        assertThat(scheduledToday(l, "Fri")).isFalse();
        // "Future" matches today's weekday (and yesterday's) but its start date was moved to tomorrow: not scheduled yet.
        assertThat(scheduledToday(l, "Future")).isFalse();

        TestClient far = newUser("k@example.com", "Pacific/Kiritimati");
        create(far, "Thu", List.of(4), null);
        create(far, "Fri", List.of(5), null);
        JsonNode lf = list(far, "");
        assertThat(scheduledToday(lf, "Thu")).isFalse();
        assertThat(scheduledToday(lf, "Fri")).isTrue();
    }

    private static boolean scheduledToday(JsonNode list, String name) {
        for (JsonNode h : list) if (h.get("name").asText().equals(name)) return h.get("scheduledToday").asBoolean();
        throw new AssertionError("no habit named " + name);
    }

    @Test
    void todayListsOnlyWhatIsScheduledTodayAndNeverArchivedHabits() throws Exception {
        TestClient c = newUser("a@example.com");
        createDaily(c, "Daily");
        String thu = create(c, "Thu only", List.of(4), null).get("id").asText();
        create(c, "Fri only", List.of(5), null);
        String archivedDaily = createDaily(c, "Archived daily").get("id").asText();
        expect(c, c.post(HABITS + "/" + archivedDaily + "/archive", Map.of()), 200);

        assertThat(names(expect(c, c.get(HABITS + "/today"), 200))).containsExactlyInAnyOrder("Daily", "Thu only");
    }

    // ---- editing -------------------------------------------------------------------------------

    private JsonNode patch(TestClient c, String id, Map<String, Object> body) throws Exception {
        return expect(c, c.patch(HABITS + "/" + id, body), 200);
    }

    @Test
    void aPartialEditChangesOnlyWhatIsSent() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID g = goal(userId(c), "G");
        String id = expect(c, c.post(HABITS, body("Original", List.of(1, 2), null, g)), 201).get("id").asText();

        JsonNode h = patch(c, id, Map.of("name", "  Renamed  "));
        assertThat(h.get("name").asText()).isEqualTo("Renamed");
        assertThat(h.get("daysOfWeek")).extracting(JsonNode::asInt).containsExactly(1, 2);
        assertThat(h.get("goal").get("id").asText()).isEqualTo(g.toString());

        h = patch(c, id, Map.of("daysOfWeek", List.of(3, 4, 5)));
        assertThat(h.get("name").asText()).isEqualTo("Renamed");
        assertThat(h.get("daysOfWeek")).extracting(JsonNode::asInt).containsExactly(3, 4, 5);
    }

    @Test
    void anExplicitNullClearsTheGoalButAnOmittedFieldDoesNot() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID g = goal(userId(c), "G");
        String id = expect(c, c.post(HABITS, body("T", List.of(1), null, g)), 201).get("id").asText();
        Map<String, Object> clear = new HashMap<>();
        clear.put("goalId", null);
        JsonNode h = patch(c, id, clear);
        assertThat(h.get("goal").isNull()).isTrue();
        assertThat(h.get("name").asText()).isEqualTo("T"); // untouched
    }

    @Test
    void editValidationRejectsBadValuesAndAnEmptyBodyAndChangesNothing() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = createDaily(c, "Keep").get("id").asText();
        expectError(c, c.patch(HABITS + "/" + id, Map.of()), 400, "EMPTY_UPDATE");
        expectError(c, c.patch(HABITS + "/" + id, Map.of("name", "   ")), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(HABITS + "/" + id, Map.of("daysOfWeek", List.of())), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(HABITS + "/" + id, Map.of("daysOfWeek", List.of(9))), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(HABITS + "/" + id, Map.of("startedOn", "2026-09-25")), 400, "DATE_IN_FUTURE");
        expectError(c, c.patch(HABITS + "/" + id, Map.of("goalId", UUID.randomUUID().toString())), 404, "NOT_FOUND");
        assertThat(patch(c, id, Map.of("name", "Keep")).get("name").asText()).isEqualTo("Keep"); // sanity: no-op edit still 200s
    }

    @Test
    void renamingToAnActiveNameThatIsAlreadyTakenIsRefused() throws Exception {
        TestClient c = newUser("a@example.com");
        createDaily(c, "Taken");
        String id = createDaily(c, "Original").get("id").asText();
        expectError(c, c.patch(HABITS + "/" + id, Map.of("name", "taken")), 409, "DUPLICATE_HABIT");
        assertThat(expect(c, c.get(HABITS + "/today"), 200)).isNotNull(); // sanity: the endpoint still works
        assertThat(names(list(c, ""))).containsExactlyInAnyOrder("Taken", "Original"); // it truly did not change
    }

    @Test
    void movingTheStartDateLaterThanAnExistingTickIsRefused() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = create(c, "H", List.of(1, 2, 3, 4, 5, 6, 7), "2026-09-01").get("id").asText();
        expect(c, c.put(HABITS + "/" + id + "/completions/2026-09-20", Map.of()), 201);
        expectError(c, c.patch(HABITS + "/" + id, Map.of("startedOn", "2026-09-21")), 400, "INVALID_START_DATE");
        expect(c, c.patch(HABITS + "/" + id, Map.of("startedOn", "2026-09-20")), 200); // exactly the earliest tick is fine
        expect(c, c.patch(HABITS + "/" + id, Map.of("startedOn", "2026-09-01")), 200); // earlier still is fine
    }

    @Test
    void anotherUsersHabitCannotBeEditedOrSeen() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = createDaily(a, "Mine").get("id").asText();
        expectError(b, b.patch(HABITS + "/" + id, Map.of("name", "Hijack")), 404, "NOT_FOUND");
        assertThat(names(list(b, ""))).isEmpty();
    }

    // ---- archive and unarchive -------------------------------------------------------------------

    @Test
    void archivingIsIdempotentAndHidesItFromTheDefaultListButKeepsItsData() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = createDaily(c, "H").get("id").asText();
        expect(c, c.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of()), 201);

        JsonNode first = expect(c, c.post(HABITS + "/" + id + "/archive", Map.of()), 200);
        assertThat(first.get("archived").asBoolean()).isTrue();
        JsonNode second = expect(c, c.post(HABITS + "/" + id + "/archive", Map.of()), 200);
        assertThat(second.get("archived").asBoolean()).isTrue();
        assertThat(names(list(c, ""))).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?::uuid", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void unarchivingIsIdempotentAndRestoresItToTheDefaultList() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = createDaily(c, "H").get("id").asText();
        expect(c, c.post(HABITS + "/" + id + "/archive", Map.of()), 200);
        JsonNode first = expect(c, c.post(HABITS + "/" + id + "/unarchive", Map.of()), 200);
        assertThat(first.get("archived").asBoolean()).isFalse();
        expect(c, c.post(HABITS + "/" + id + "/unarchive", Map.of()), 200);
        assertThat(names(list(c, ""))).containsExactly("H");
    }

    @Test
    void unarchivingIntoATakenNameIsRefusedAndTheHabitStaysArchived() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = createDaily(c, "Read").get("id").asText();
        expect(c, c.post(HABITS + "/" + id + "/archive", Map.of()), 200);
        createDaily(c, "Read"); // a new active habit takes the freed name
        expectError(c, c.post(HABITS + "/" + id + "/unarchive", Map.of()), 409, "DUPLICATE_HABIT");
        assertThat(names(list(c, "?includeArchived=true"))).contains("Read");
        assertThat(names(list(c, ""))).containsExactly("Read"); // only the new one is active
    }

    @Test
    void archivingOrUnarchivingSomeoneElsesHabitIsNotFound() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = createDaily(a, "Mine").get("id").asText();
        expectError(b, b.post(HABITS + "/" + id + "/archive", Map.of()), 404, "NOT_FOUND");
        expectError(b, b.post(HABITS + "/" + id + "/unarchive", Map.of()), 404, "NOT_FOUND");
    }

    // ---- delete --------------------------------------------------------------------------------

    @Test
    void deleteRemovesTheHabitAndItsCompletionsAndFreesItsName() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = createDaily(c, "Read").get("id").asText();
        expect(c, c.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of()), 201);
        expect(c, c.delete(HABITS + "/" + id), 204);
        expectError(c, c.delete(HABITS + "/" + id), 404, "NOT_FOUND");
        assertThat(rows(userId(c))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from habit_completions", Integer.class)).isZero();
        expect(c, c.post(HABITS, body("Read", List.of(1), null, null)), 201); // the name is free again
    }

    @Test
    void anotherUsersHabitCannotBeDeleted() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = createDaily(a, "Mine").get("id").asText();
        expectError(b, b.delete(HABITS + "/" + id), 404, "NOT_FOUND");
        assertThat(rows(userId(a))).isEqualTo(1);
    }

    // ---- ticking and unticking -------------------------------------------------------------------

    @Test
    void tickingIsIdempotentAndReturnsTheHabitsFreshState() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = createDaily(c, "H").get("id").asText();
        MvcResult first = c.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of());
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(c.json(first).get("doneToday").asBoolean()).isTrue();
        assertThat(c.json(first).get("currentStreak").asInt()).isEqualTo(1);
        MvcResult again = c.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of());
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(c.json(again).get("currentStreak").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?::uuid", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void untickingRemovesTheDayAndAMissingOneIsNotFound() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = createDaily(c, "H").get("id").asText();
        expect(c, c.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of()), 201);
        expect(c, c.delete(HABITS + "/" + id + "/completions/" + TODAY), 204);
        expectError(c, c.delete(HABITS + "/" + id + "/completions/" + TODAY), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?::uuid", Integer.class, id)).isZero();
    }

    @Test
    void tickingIsBoundedToTheHabitsStartDateAndNeverAFutureDate() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = create(c, "H", List.of(1, 2, 3, 4, 5, 6, 7), "2026-09-20").get("id").asText();
        expectError(c, c.put(HABITS + "/" + id + "/completions/2026-09-19", Map.of()), 400, "DATE_BEFORE_START");
        expectError(c, c.put(HABITS + "/" + id + "/completions/2026-09-25", Map.of()), 400, "DATE_IN_FUTURE");
        expect(c, c.put(HABITS + "/" + id + "/completions/2026-09-20", Map.of()), 201); // exactly the start date
        expect(c, c.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of()), 201);   // exactly today
        expect(c, c.put(HABITS + "/" + id + "/completions/not-a-date", Map.of()), 400);
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?::uuid", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void tickingSomeoneElsesHabitIsNotFoundAndUntouched() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = createDaily(a, "Mine").get("id").asText();
        expectError(b, b.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of()), 404, "NOT_FOUND");
        expectError(b, b.delete(HABITS + "/" + id + "/completions/" + TODAY), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?::uuid", Integer.class, id)).isZero();
    }

    @Test
    void backfillingAPastDayUpdatesTheStreaksImmediately() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = create(c, "H", List.of(1, 2, 3, 4, 5, 6, 7), "2026-09-20").get("id").asText();
        expect(c, c.put(HABITS + "/" + id + "/completions/2026-09-22", Map.of()), 201);
        expect(c, c.put(HABITS + "/" + id + "/completions/2026-09-23", Map.of()), 201);
        expect(c, c.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of()), 201);
        JsonNode before = expect(c, c.get(HABITS), 200).get(0);
        assertThat(before.get("currentStreak").asInt()).isEqualTo(3); // 22, 23, 24
        MvcResult backfilled = c.put(HABITS + "/" + id + "/completions/2026-09-21", Map.of());
        assertThat(backfilled.getResponse().getStatus()).isEqualTo(201);
        assertThat(c.json(backfilled).get("currentStreak").asInt()).isEqualTo(4); // 21-24, joined with the 20th's gap still open
        assertThat(c.json(backfilled).get("longestStreak").asInt()).isEqualTo(4);
    }

    // ---- history ---------------------------------------------------------------------------------

    @Test
    void historyDefaultsToTheLastEightWeeksOldestFirstAndReportsTheWholeHistorysStreaks() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = create(c, "H", List.of(1, 2, 3, 4, 5, 6, 7), "2026-07-01").get("id").asText();
        expect(c, c.put(HABITS + "/" + id + "/completions/2026-09-22", Map.of()), 201);
        expect(c, c.put(HABITS + "/" + id + "/completions/2026-09-23", Map.of()), 201);
        expect(c, c.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of()), 201);

        JsonNode h = expect(c, c.get(HABITS + "/" + id + "/history"), 200);
        assertThat(h.get("to").asText()).isEqualTo(TODAY);
        assertThat(h.get("from").asText()).isEqualTo("2026-07-31"); // 56 days ending 2026-09-24
        assertThat(h.get("points")).hasSize(56);
        assertThat(h.get("points").get(0).get("date").asText()).isEqualTo("2026-07-31");
        assertThat(h.get("points").get(0).get("scheduled").asBoolean()).isTrue();
        assertThat(h.get("points").get(0).get("done").asBoolean()).isFalse();
        JsonNode last = h.get("points").get(55);
        assertThat(last.get("date").asText()).isEqualTo(TODAY);
        assertThat(last.get("done").asBoolean()).isTrue();
        assertThat(h.get("currentStreak").asInt()).isEqualTo(3);
        assertThat(h.get("longestStreak").asInt()).isEqualTo(3);
    }

    @Test
    void daysBeforeTheStartDateAreNeverScheduledEvenOnAMatchingWeekday() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = create(c, "H", List.of(1, 2, 3, 4, 5, 6, 7), "2026-09-22").get("id").asText();
        JsonNode h = expect(c, c.get(HABITS + "/" + id + "/history?from=2026-09-18&to=2026-09-24"), 200);
        var points = h.get("points");
        assertThat(points.get(0).get("date").asText()).isEqualTo("2026-09-18");
        assertThat(points.get(0).get("scheduled").asBoolean()).isFalse(); // before the habit existed
        assertThat(points.get(4).get("date").asText()).isEqualTo("2026-09-22"); // the start date itself
        assertThat(points.get(4).get("scheduled").asBoolean()).isTrue();
    }

    @Test
    void aFutureToIsClampedToTodayAndTheRangeIsValidated() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = createDaily(c, "H").get("id").asText();
        JsonNode h = expect(c, c.get(HABITS + "/" + id + "/history?from=" + TODAY + "&to=2026-10-15"), 200);
        assertThat(h.get("to").asText()).isEqualTo(TODAY);
        assertThat(h.get("points")).hasSize(1);
        expectError(c, c.get(HABITS + "/" + id + "/history?from=2026-09-10&to=2026-09-01"), 400, "INVALID_RANGE");
        expectError(c, c.get(HABITS + "/" + id + "/history?from=2025-09-23&to=" + TODAY), 400, "INVALID_RANGE"); // 367 days
        expect(c, c.get(HABITS + "/" + id + "/history?from=2025-09-24&to=" + TODAY), 200); // exactly 366 days
    }

    @Test
    void historyOfSomeoneElsesHabitIsNotFound() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = createDaily(a, "Mine").get("id").asText();
        expectError(b, b.get(HABITS + "/" + id + "/history"), 404, "NOT_FOUND");
    }

    // ---- access ----------------------------------------------------------------------------------

    @Test
    void everyEndpointRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        String id = UUID.randomUUID().toString();
        assertThat(anon.get(HABITS).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.get(HABITS + "/today").getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(HABITS, Map.of("name", "h", "daysOfWeek", List.of(1))).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.patch(HABITS + "/" + id, Map.of("name", "x")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(HABITS + "/" + id + "/archive", Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(HABITS + "/" + id + "/unarchive", Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(HABITS + "/" + id).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.put(HABITS + "/" + id + "/completions/" + TODAY, Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(HABITS + "/" + id + "/completions/" + TODAY).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.get(HABITS + "/" + id + "/history").getResponse().getStatus()).isEqualTo(401);

        TestClient c = newUser("a@example.com");
        String mine = createDaily(c, "Mine").get("id").asText();
        assertThat(c.call(HttpMethod.POST, HABITS, body("x", List.of(1), null, null), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.PATCH, HABITS + "/" + mine, Map.of("name", "y"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.POST, HABITS + "/" + mine + "/archive", Map.of(), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.PUT, HABITS + "/" + mine + "/completions/" + TODAY, Map.of(), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.DELETE, HABITS + "/" + mine, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(c))).isEqualTo(1);
    }
}
