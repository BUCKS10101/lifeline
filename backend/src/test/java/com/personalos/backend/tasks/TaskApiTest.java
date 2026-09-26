package com.personalos.backend.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it, so today is 2026-09-24 in UTC. */
class TaskApiTest extends ApiTestBase {

    private static final String TASKS = "/api/v1/tasks";

    private MvcResult post(TestClient c, Map<String, Object> body) throws Exception {
        return c.post(TASKS, body);
    }

    private static Map<String, Object> body(String title, String due, String priority) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        if (due != null) m.put("dueDate", due);
        if (priority != null) m.put("priority", priority);
        return m;
    }

    /** Adds a task a minute after the previous one, so "when it was added" is an unambiguous order. */
    private JsonNode add(TestClient c, String title, String due, String priority) throws Exception {
        clock.advance(Duration.ofMinutes(1));
        return expect(c, post(c, body(title, due, priority)), 201);
    }

    private UUID goal(UUID user, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into goals (id, user_id, title) values (?, ?, ?)", id, user, title);
        return id;
    }

    private List<String> titles(TestClient c, String query) throws Exception {
        List<String> out = new ArrayList<>();
        expect(c, c.get(TASKS + query), 200).get("items").forEach(t -> out.add(t.get("title").asText()));
        return out;
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from tasks where user_id = ?", Integer.class, user);
    }

    // ---- creating ----------------------------------------------------------------------------

    @Test
    void aTaskWithOnlyATitleGetsSensibleDefaults() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode t = expect(c, post(c, Map.of("title", "  Buy milk  ")), 201);
        assertThat(t.get("id").asText()).isNotBlank();
        assertThat(t.get("title").asText()).isEqualTo("Buy milk");
        assertThat(t.get("notes").isNull()).isTrue();
        assertThat(t.get("dueDate").isNull()).isTrue();
        assertThat(t.get("priority").asText()).isEqualTo("NORMAL");
        assertThat(t.get("completedAt").isNull()).isTrue();
        assertThat(t.get("overdue").asBoolean()).isFalse();
        assertThat(t.get("goal").isNull()).isTrue();
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void aTaskCanCarryNotesADateAPriorityAndAGoal() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID goal = goal(userId(c), "Get fit");
        Map<String, Object> b = body("Book a class", "2026-10-01", "HIGH");
        b.put("notes", "  Try the Tuesday one  ");
        b.put("goalId", goal.toString());
        JsonNode t = expect(c, post(c, b), 201);
        assertThat(t.get("notes").asText()).isEqualTo("Try the Tuesday one");
        assertThat(t.get("dueDate").asText()).isEqualTo("2026-10-01");
        assertThat(t.get("priority").asText()).isEqualTo("HIGH");
        assertThat(t.get("goal").get("id").asText()).isEqualTo(goal.toString());
        assertThat(t.get("goal").get("title").asText()).isEqualTo("Get fit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankTitleIsRejected(String title) throws Exception {
        TestClient c = newUser("a@example.com");
        MvcResult r = post(c, Map.of("title", title));
        expectError(c, r, 400, "VALIDATION_FAILED");
        assertThat(c.json(r).get("violations").toString()).contains("title");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void lengthsAndEnumsAreValidated() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, Map.of()), 400, "VALIDATION_FAILED");
        expectError(c, post(c, Map.of("title", "x".repeat(201))), 400, "VALIDATION_FAILED");
        expect(c, post(c, Map.of("title", "x".repeat(200))), 201);
        Map<String, Object> longNotes = body("t", null, null);
        longNotes.put("notes", "n".repeat(2001));
        expectError(c, post(c, longNotes), 400, "VALIDATION_FAILED");
        expect(c, post(c, body("t", null, "URGENT")), 400);
        expect(c, post(c, body("t", "not-a-date", null)), 400);
        expect(c, c.post(TASKS, "not json"), 400);
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void notesThatAreBlankAreStoredAsNone() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("t", null, null);
        b.put("notes", "   ");
        assertThat(expect(c, post(c, b), 201).get("notes").isNull()).isTrue();
    }

    @Test
    void dueDatesAreBoundedTo2000Through2100() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body("t", "1999-12-31", null)), 400, "DATE_TOO_EARLY");
        expectError(c, post(c, body("t", "2101-01-01", null)), 400, "DATE_TOO_LATE");
        expect(c, post(c, body("t", "2000-01-01", null)), 201);
        expect(c, post(c, body("t", "2100-12-31", null)), 201);
        assertThat(rows(userId(c))).isEqualTo(2);
    }

    @Test
    void aGoalThatIsUnknownOrSomeoneElsesIsNotFoundAndNothingIsCreated() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID theirs = goal(userId(b), "Secret");
        for (UUID goal : new UUID[]{theirs, UUID.randomUUID()}) {
            Map<String, Object> m = body("t", null, null);
            m.put("goalId", goal.toString());
            expectError(a, post(a, m), 404, "NOT_FOUND");
        }
        assertThat(rows(userId(a))).isZero();
    }

    // ---- client ids: retries cannot double-add -----------------------------------------------

    @Test
    void repeatingTheSameClientIdReturnsTheExistingTaskAndAddsItOnce() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID id = UUID.randomUUID();
        Map<String, Object> first = body("Original", null, "HIGH");
        first.put("id", id.toString());
        assertThat(post(c, first).getResponse().getStatus()).isEqualTo(201);

        Map<String, Object> retry = body("Different", null, "LOW");
        retry.put("id", id.toString());
        MvcResult again = post(c, retry);
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(c.json(again).get("title").asText()).isEqualTo("Original");   // the first one won
        assertThat(c.json(again).get("priority").asText()).isEqualTo("HIGH");
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void aClientIdBelongingToSomeoneElseIsRefusedWithoutTouchingTheirTask() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID id = UUID.randomUUID();
        Map<String, Object> mine = body("Mine", null, null);
        mine.put("id", id.toString());
        post(a, mine);

        Map<String, Object> theirs = body("Theirs", null, null);
        theirs.put("id", id.toString());
        expectError(b, post(b, theirs), 409, "CONFLICT");
        assertThat(rows(userId(b))).isZero();
        assertThat(jdbc.queryForObject("select title from tasks where id = ?", String.class, id)).isEqualTo("Mine");
        Map<String, Object> bad = body("t", null, null);
        bad.put("id", "not-a-uuid");
        expect(a, post(a, bad), 400);
    }

    // ---- the four views ----------------------------------------------------------------------

    /** Today is 2026-09-24. Creates a spread of tasks around it and returns nothing: the tests read the views. */
    private void seedViews(TestClient c) throws Exception {
        add(c, "A overdue 22nd", "2026-09-22", "NORMAL");
        add(c, "B overdue 23rd high", "2026-09-23", "HIGH");
        add(c, "C overdue 23rd low", "2026-09-23", "LOW");
        add(c, "D overdue 23rd normal", "2026-09-23", "NORMAL");
        add(c, "E due today", "2026-09-24", "NORMAL");
        add(c, "F tomorrow", "2026-09-25", "NORMAL");
        add(c, "G no date", null, "NORMAL");
        add(c, "H no date high", null, "HIGH");
        add(c, "I next week high", "2026-09-30", "HIGH");
        add(c, "J next week", "2026-09-30", "NORMAL");
    }

    @Test
    void todayHoldsOverdueAndDueTodayOrderedByDateThenPriorityThenWhenAdded() throws Exception {
        TestClient c = newUser("a@example.com");
        seedViews(c);
        assertThat(titles(c, "")).containsExactly("A overdue 22nd", "B overdue 23rd high", "D overdue 23rd normal", "C overdue 23rd low", "E due today");
        assertThat(titles(c, "?view=today")).isEqualTo(titles(c, ""));   // today is the default
        JsonNode items = expect(c, c.get(TASKS + "?view=today"), 200).get("items");
        assertThat(items.get(0).get("overdue").asBoolean()).isTrue();
        assertThat(items.get(3).get("overdue").asBoolean()).isTrue();
        assertThat(items.get(4).get("overdue").asBoolean()).isFalse();   // due today is not overdue
    }

    @Test
    void upcomingHoldsOnlyFutureDatesAndAnytimeOnlyUndatedOrderedByPriority() throws Exception {
        TestClient c = newUser("a@example.com");
        seedViews(c);
        assertThat(titles(c, "?view=upcoming")).containsExactly("F tomorrow", "I next week high", "J next week");
        assertThat(titles(c, "?view=anytime")).containsExactly("H no date high", "G no date");
    }

    @Test
    void doneHoldsCompletedTasksNewestFirstAndTheOtherViewsForgetThem() throws Exception {
        TestClient c = newUser("a@example.com");
        seedViews(c);
        String first = expect(c, c.get(TASKS + "?view=anytime"), 200).get("items").get(1).get("id").asText();      // G
        String second = expect(c, c.get(TASKS + "?view=today"), 200).get("items").get(0).get("id").asText();      // A
        expect(c, c.post(TASKS + "/" + first + "/complete", Map.of()), 200);
        clock.advance(Duration.ofMinutes(5));
        expect(c, c.post(TASKS + "/" + second + "/complete", Map.of()), 200);

        assertThat(titles(c, "?view=done")).containsExactly("A overdue 22nd", "G no date");
        assertThat(titles(c, "?view=today")).doesNotContain("A overdue 22nd");
        assertThat(titles(c, "?view=anytime")).doesNotContain("G no date");
    }

    @Test
    void viewsAreCaseInsensitiveAndAnUnknownViewIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        add(c, "T", null, null);
        assertThat(titles(c, "?view=ANYTIME")).containsExactly("T");
        assertThat(titles(c, "?view=Anytime")).containsExactly("T");
        expectError(c, c.get(TASKS + "?view=someday"), 400, "INVALID_VIEW");
    }

    @Test
    void theBoundaryIsTheDateInThePersonsTimezoneNotUtc() throws Exception {
        // At 10:00 UTC on 2026-09-24 it is already 2026-09-25 in Kiritimati (UTC+14).
        TestClient far = newUser("k@example.com", "Pacific/Kiritimati");
        TestClient utc = newUser("u@example.com");
        for (TestClient c : List.of(far, utc)) {
            add(c, "due 24th", "2026-09-24", null);
            add(c, "due 25th", "2026-09-25", null);
            add(c, "due 26th", "2026-09-26", null);
        }
        assertThat(titles(far, "?view=today")).containsExactly("due 24th", "due 25th");
        assertThat(titles(far, "?view=upcoming")).containsExactly("due 26th");
        assertThat(expect(far, far.get(TASKS + "?view=today"), 200).get("items").get(0).get("overdue").asBoolean()).isTrue();
        assertThat(expect(far, far.get(TASKS + "?view=today"), 200).get("items").get(1).get("overdue").asBoolean()).isFalse();
        assertThat(titles(utc, "?view=today")).containsExactly("due 24th");
        assertThat(titles(utc, "?view=upcoming")).containsExactly("due 25th", "due 26th");
    }

    @Test
    void aTaskDueYesterdayBecomesOverdueWhenTheDayTurns() throws Exception {
        TestClient c = newUser("a@example.com");
        add(c, "T", "2026-09-24", null);
        assertThat(expect(c, c.get(TASKS), 200).get("items").get(0).get("overdue").asBoolean()).isFalse();
        clock.advance(Duration.ofDays(1));
        assertThat(expect(c, c.get(TASKS), 200).get("items").get(0).get("overdue").asBoolean()).isTrue();
    }

    @Test
    void aCompletedTaskIsNeverOverdue() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = add(c, "late", "2026-09-01", null).get("id").asText();
        assertThat(expect(c, c.get(TASKS), 200).get("items").get(0).get("overdue").asBoolean()).isTrue();
        assertThat(expect(c, c.post(TASKS + "/" + id + "/complete", Map.of()), 200).get("overdue").asBoolean()).isFalse();
        assertThat(expect(c, c.get(TASKS + "?view=done"), 200).get("items").get(0).get("overdue").asBoolean()).isFalse();
    }

    @Test
    void listsArePagedInAStableOrderAndPaginationIsValidated() throws Exception {
        TestClient c = newUser("a@example.com");
        for (int i = 1; i <= 25; i++) add(c, "task %02d".formatted(i), null, null);

        JsonNode first = expect(c, c.get(TASKS + "?view=anytime&size=10"), 200);
        assertThat(first.get("totalItems").asInt()).isEqualTo(25);
        assertThat(first.get("totalPages").asInt()).isEqualTo(3);
        assertThat(titles(c, "?view=anytime&size=10&page=0")).startsWith("task 01").hasSize(10);
        assertThat(titles(c, "?view=anytime&size=10&page=2")).containsExactly("task 21", "task 22", "task 23", "task 24", "task 25");
        assertThat(titles(c, "?view=anytime&size=10&page=3")).isEmpty();
        for (String bad : new String[]{"size=0", "size=101", "page=-1", "size=x"}) expect(c, c.get(TASKS + "?" + bad), 400);
    }

    @Test
    void theGoalFilterNarrowsAnyViewAndAForeignGoalJustFindsNothing() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID g1 = goal(userId(a), "One");
        UUID g2 = goal(userId(a), "Two");
        for (Object[] row : new Object[][]{{"in one", g1}, {"also one", g1}, {"in two", g2}}) {
            Map<String, Object> m = body((String) row[0], null, null);
            m.put("goalId", row[1].toString());
            clock.advance(Duration.ofMinutes(1));
            expect(a, post(a, m), 201);
        }
        add(a, "no goal", null, null);
        assertThat(titles(a, "?view=anytime&goalId=" + g1)).containsExactly("in one", "also one");
        assertThat(titles(a, "?view=anytime&goalId=" + g2)).containsExactly("in two");
        assertThat(titles(a, "?view=anytime")).hasSize(4);
        assertThat(titles(b, "?view=anytime&goalId=" + g1)).isEmpty();          // b cannot see a's goal's tasks
        assertThat(titles(a, "?view=anytime&goalId=" + UUID.randomUUID())).isEmpty();
        expect(a, a.get(TASKS + "?goalId=not-a-uuid"), 400);
    }

    // ---- editing -----------------------------------------------------------------------------

    private JsonNode patch(TestClient c, String id, Map<String, Object> body) throws Exception {
        return expect(c, c.patch(TASKS + "/" + id, body), 200);
    }

    @Test
    void aPartialEditChangesOnlyWhatIsSent() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID goal = goal(userId(c), "G");
        Map<String, Object> b = body("Original", "2026-10-01", "HIGH");
        b.put("notes", "keep me");
        b.put("goalId", goal.toString());
        String id = expect(c, post(c, b), 201).get("id").asText();

        JsonNode t = patch(c, id, Map.of("title", "  Renamed  "));
        assertThat(t.get("title").asText()).isEqualTo("Renamed");
        assertThat(t.get("notes").asText()).isEqualTo("keep me");
        assertThat(t.get("dueDate").asText()).isEqualTo("2026-10-01");
        assertThat(t.get("priority").asText()).isEqualTo("HIGH");
        assertThat(t.get("goal").get("id").asText()).isEqualTo(goal.toString());

        t = patch(c, id, Map.of("priority", "LOW", "dueDate", "2026-11-11"));
        assertThat(t.get("priority").asText()).isEqualTo("LOW");
        assertThat(t.get("dueDate").asText()).isEqualTo("2026-11-11");
        assertThat(t.get("title").asText()).isEqualTo("Renamed");
    }

    @Test
    void anExplicitNullClearsNotesDateAndGoalButAnOmittedFieldDoesNot() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID goal = goal(userId(c), "G");
        Map<String, Object> b = body("T", "2026-10-01", "HIGH");
        b.put("notes", "n");
        b.put("goalId", goal.toString());
        String id = expect(c, post(c, b), 201).get("id").asText();

        Map<String, Object> clear = new HashMap<>();
        clear.put("dueDate", null);
        JsonNode t = patch(c, id, clear);
        assertThat(t.get("dueDate").isNull()).isTrue();
        assertThat(t.get("notes").asText()).isEqualTo("n");           // untouched
        assertThat(t.get("goal").isNull()).isFalse();                  // untouched

        clear = new HashMap<>();
        clear.put("notes", null);
        clear.put("goalId", null);
        t = patch(c, id, clear);
        assertThat(t.get("notes").isNull()).isTrue();
        assertThat(t.get("goal").isNull()).isTrue();
        assertThat(t.get("priority").asText()).isEqualTo("HIGH");
        assertThat(patch(c, id, Map.of("notes", "   ")).get("notes").isNull()).isTrue();   // blank also clears
    }

    @Test
    void anEditThatMakesADatedTaskUndatedOrFutureMovesItBetweenViews() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = add(c, "Movable", "2026-09-24", null).get("id").asText();
        assertThat(titles(c, "?view=today")).containsExactly("Movable");
        patch(c, id, Map.of("dueDate", "2026-10-05"));
        assertThat(titles(c, "?view=today")).isEmpty();
        assertThat(titles(c, "?view=upcoming")).containsExactly("Movable");
        Map<String, Object> clear = new HashMap<>();
        clear.put("dueDate", null);
        patch(c, id, clear);
        assertThat(titles(c, "?view=anytime")).containsExactly("Movable");
    }

    @Test
    void editValidationRejectsBadValuesAndAnEmptyBodyAndChangesNothing() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = add(c, "Keep", "2026-10-01", "HIGH").get("id").asText();
        expectError(c, c.patch(TASKS + "/" + id, Map.of()), 400, "EMPTY_UPDATE");
        Map<String, Object> nullTitle = new HashMap<>();
        nullTitle.put("title", null);
        expectError(c, c.patch(TASKS + "/" + id, nullTitle), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(TASKS + "/" + id, Map.of("title", "   ")), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(TASKS + "/" + id, Map.of("title", "x".repeat(201))), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(TASKS + "/" + id, Map.of("notes", "n".repeat(2001))), 400, "VALIDATION_FAILED");
        Map<String, Object> nullPriority = new HashMap<>();
        nullPriority.put("priority", null);
        expectError(c, c.patch(TASKS + "/" + id, nullPriority), 400, "VALIDATION_FAILED");
        expect(c, c.patch(TASKS + "/" + id, Map.of("priority", "URGENT")), 400);
        expectError(c, c.patch(TASKS + "/" + id, Map.of("dueDate", "1999-12-31")), 400, "DATE_TOO_EARLY");
        expectError(c, c.patch(TASKS + "/" + id, Map.of("dueDate", "2101-01-01")), 400, "DATE_TOO_LATE");
        expectError(c, c.patch(TASKS + "/" + id, Map.of("goalId", UUID.randomUUID().toString())), 404, "NOT_FOUND");

        JsonNode unchanged = expect(c, c.get(TASKS + "?view=upcoming"), 200).get("items").get(0);
        assertThat(unchanged.get("title").asText()).isEqualTo("Keep");
        assertThat(unchanged.get("dueDate").asText()).isEqualTo("2026-10-01");
        assertThat(unchanged.get("priority").asText()).isEqualTo("HIGH");
    }

    @Test
    void aGoalThatIsSomeoneElsesCannotBeLinkedOnEdit() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID theirs = goal(userId(b), "Secret");
        String id = add(a, "Mine", null, null).get("id").asText();
        expectError(a, a.patch(TASKS + "/" + id, Map.of("goalId", theirs.toString())), 404, "NOT_FOUND");
        assertThat(expect(a, a.get(TASKS + "?view=anytime"), 200).get("items").get(0).get("goal").isNull()).isTrue();
    }

    // ---- complete and reopen -----------------------------------------------------------------

    @Test
    void completingRecordsTheMomentAndIsIdempotentKeepingTheFirstOne() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = add(c, "T", null, null).get("id").asText();
        String at = "2026-09-24T10:01:00Z";

        JsonNode done = expect(c, c.post(TASKS + "/" + id + "/complete", Map.of()), 200);
        assertThat(done.get("completedAt").asText()).isEqualTo(at);
        clock.advance(Duration.ofHours(3));
        JsonNode again = expect(c, c.post(TASKS + "/" + id + "/complete", Map.of()), 200);
        assertThat(again.get("completedAt").asText()).isEqualTo(at);   // the original moment is kept
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void reopeningClearsTheMomentAndIsIdempotent() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = add(c, "T", null, null).get("id").asText();
        expect(c, c.post(TASKS + "/" + id + "/complete", Map.of()), 200);
        assertThat(expect(c, c.post(TASKS + "/" + id + "/reopen", Map.of()), 200).get("completedAt").isNull()).isTrue();
        assertThat(expect(c, c.post(TASKS + "/" + id + "/reopen", Map.of()), 200).get("completedAt").isNull()).isTrue();
        assertThat(titles(c, "?view=anytime")).containsExactly("T");
        assertThat(titles(c, "?view=done")).isEmpty();
    }

    @Test
    void completingOrReopeningATaskThatIsNotYoursIsNotFound() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = add(a, "Mine", null, null).get("id").asText();
        expectError(b, b.post(TASKS + "/" + id + "/complete", Map.of()), 404, "NOT_FOUND");
        expectError(b, b.post(TASKS + "/" + id + "/reopen", Map.of()), 404, "NOT_FOUND");
        expectError(a, a.post(TASKS + "/" + UUID.randomUUID() + "/complete", Map.of()), 404, "NOT_FOUND");
        expect(a, a.post(TASKS + "/not-a-uuid/complete", Map.of()), 400);
        assertThat(jdbc.queryForObject("select completed_at is null from tasks where id = ?::uuid", Boolean.class, id)).isTrue();
    }

    // ---- delete ------------------------------------------------------------------------------

    @Test
    void deleteRemovesTheTaskAndDoesNotTouchItsGoal() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID goal = goal(userId(c), "G");
        Map<String, Object> b = body("T", null, null);
        b.put("goalId", goal.toString());
        String id = expect(c, post(c, b), 201).get("id").asText();
        expect(c, c.delete(TASKS + "/" + id), 204);
        expectError(c, c.delete(TASKS + "/" + id), 404, "NOT_FOUND");
        assertThat(rows(userId(c))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from goals where id = ?", Integer.class, goal)).isEqualTo(1);
    }

    @Test
    void anotherUsersTaskCannotBeEditedCompletedDeletedOrSeen() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = add(a, "Mine", "2026-09-24", null).get("id").asText();
        expectError(b, b.patch(TASKS + "/" + id, Map.of("title", "Hijack")), 404, "NOT_FOUND");
        expectError(b, b.delete(TASKS + "/" + id), 404, "NOT_FOUND");
        assertThat(titles(b, "")).isEmpty();
        assertThat(titles(b, "?view=done")).isEmpty();
        assertThat(titles(a, "")).containsExactly("Mine");
        assertThat(jdbc.queryForObject("select title from tasks where id = ?::uuid", String.class, id)).isEqualTo("Mine");
    }

    @Test
    void deletingAGoalLeavesItsTasksUnlinked() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID goal = goal(userId(c), "G");
        Map<String, Object> b = body("T", null, null);
        b.put("goalId", goal.toString());
        expect(c, post(c, b), 201);
        jdbc.update("delete from goals where id = ?", goal);
        JsonNode t = expect(c, c.get(TASKS + "?view=anytime"), 200).get("items").get(0);
        assertThat(t.get("title").asText()).isEqualTo("T");
        assertThat(t.get("goal").isNull()).isTrue();
    }

    // ---- summary -----------------------------------------------------------------------------

    @Test
    void theSummaryCountsOpenTasksDueTodayOverdueAndInTotal() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode empty = expect(c, c.get(TASKS + "/summary"), 200);
        assertThat(empty.get("dueToday").asInt()).isZero();
        assertThat(empty.get("overdue").asInt()).isZero();
        assertThat(empty.get("open").asInt()).isZero();

        seedViews(c);   // 4 overdue, 1 due today, 5 later or undated
        String done = expect(c, c.get(TASKS + "?view=today"), 200).get("items").get(0).get("id").asText();
        expect(c, c.post(TASKS + "/" + done + "/complete", Map.of()), 200);   // an overdue one is completed
        JsonNode s = expect(c, c.get(TASKS + "/summary"), 200);
        assertThat(s.get("overdue").asInt()).isEqualTo(3);
        assertThat(s.get("dueToday").asInt()).isEqualTo(1);
        assertThat(s.get("open").asInt()).isEqualTo(9);
    }

    @Test
    void theSummaryFollowsTheTimezoneAndOnlyCountsThePersonsOwnTasks() throws Exception {
        TestClient far = newUser("k@example.com", "Pacific/Kiritimati");
        TestClient utc = newUser("u@example.com");
        add(far, "due 24th", "2026-09-24", null);
        add(far, "due 25th", "2026-09-25", null);
        add(utc, "due 24th", "2026-09-24", null);
        JsonNode f = expect(far, far.get(TASKS + "/summary"), 200);
        assertThat(f.get("overdue").asInt()).isEqualTo(1);
        assertThat(f.get("dueToday").asInt()).isEqualTo(1);
        JsonNode u = expect(utc, utc.get(TASKS + "/summary"), 200);
        assertThat(u.get("overdue").asInt()).isZero();
        assertThat(u.get("dueToday").asInt()).isEqualTo(1);
        assertThat(u.get("open").asInt()).isEqualTo(1);
    }

    // ---- access ------------------------------------------------------------------------------

    @Test
    void everyEndpointRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        String id = UUID.randomUUID().toString();
        assertThat(anon.get(TASKS).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.get(TASKS + "/summary").getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(TASKS, Map.of("title", "t")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.patch(TASKS + "/" + id, Map.of("title", "t")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(TASKS + "/" + id + "/complete", Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(TASKS + "/" + id + "/reopen", Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(TASKS + "/" + id).getResponse().getStatus()).isEqualTo(401);

        TestClient c = newUser("a@example.com");
        String mine = add(c, "T", null, null).get("id").asText();
        assertThat(c.call(HttpMethod.POST, TASKS, Map.of("title", "t"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.PATCH, TASKS + "/" + mine, Map.of("title", "x"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.POST, TASKS + "/" + mine + "/complete", Map.of(), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.DELETE, TASKS + "/" + mine, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(c))).isEqualTo(1);
    }
}
