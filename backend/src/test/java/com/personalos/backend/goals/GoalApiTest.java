package com.personalos.backend.goals;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it, so today is 2026-09-24 in UTC. */
class GoalApiTest extends ApiTestBase {

    private static final String GOALS = "/api/v1/goals";

    private MvcResult post(TestClient c, Map<String, Object> body) throws Exception {
        return c.post(GOALS, body);
    }

    private static Map<String, Object> body(String title) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        return m;
    }

    private List<String> titles(TestClient c, String query) throws Exception {
        List<String> out = new ArrayList<>();
        expect(c, c.get(GOALS + query), 200).forEach(g -> out.add(g.get("title").asText()));
        return out;
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from goals where user_id = ?", Integer.class, user);
    }

    // ---- fixtures: tasks and habits attached directly by SQL, bypassing their own APIs ----------

    private UUID insertTask(UUID user, UUID goalId, String title, boolean done) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tasks (id, user_id, goal_id, title, priority, completed_at, created_at, updated_at) "
                        + "values (?, ?, ?, ?, 'NORMAL', ?, now(), now())",
                id, user, goalId, title, done ? java.sql.Timestamp.from(clock.instant()) : null);
        return id;
    }

    private UUID insertHabit(UUID user, UUID goalId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into habits (id, user_id, goal_id, name, days_of_week, started_on, created_at, updated_at) "
                        + "values (?, ?, ?, ?, 127, ?, now(), now())",
                id, user, goalId, name, java.sql.Date.valueOf("2026-09-01"));
        return id;
    }

    private void tick(UUID habitId, String date) {
        jdbc.update("insert into habit_completions (habit_id, completed_on, created_at) values (?, ?::date, now())", habitId, date);
    }

    // ---- creating ------------------------------------------------------------------------------

    @Test
    void aGoalWithOnlyATitleGetsSensibleDefaults() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode g = expect(c, post(c, body("  Run a marathon  ")), 201);
        assertThat(g.get("id").asText()).isNotBlank();
        assertThat(g.get("title").asText()).isEqualTo("Run a marathon");
        assertThat(g.get("description").isNull()).isTrue();
        assertThat(g.get("targetDate").isNull()).isTrue();
        assertThat(g.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(g.get("achievedAt").isNull()).isTrue();
        assertThat(g.get("taskCount").asInt()).isZero();
        assertThat(g.get("doneCount").asInt()).isZero();
        assertThat(g.get("progressPercent").isNull()).isTrue(); // no tasks yet: null, not zero
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void aGoalCanCarryADescriptionAndATargetDate() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("Run a marathon");
        b.put("description", "  Sub-4 hours  ");
        b.put("targetDate", "2027-04-01");
        JsonNode g = expect(c, post(c, b), 201);
        assertThat(g.get("description").asText()).isEqualTo("Sub-4 hours");
        assertThat(g.get("targetDate").asText()).isEqualTo("2027-04-01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankTitleIsRejected(String title) throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, body(title)), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void lengthsAreValidated() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, post(c, Map.of()), 400, "VALIDATION_FAILED");
        expectError(c, post(c, body("t".repeat(121))), 400, "VALIDATION_FAILED");
        expect(c, post(c, body("t".repeat(120))), 201);
        Map<String, Object> longDescription = body("t");
        longDescription.put("description", "n".repeat(1001));
        expectError(c, post(c, longDescription), 400, "VALIDATION_FAILED");
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void descriptionThatIsBlankIsStoredAsNone() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("t");
        b.put("description", "   ");
        assertThat(expect(c, post(c, b), 201).get("description").isNull()).isTrue();
    }

    @Test
    void targetDatesAreBoundedTo2000Through2100() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> early = body("t");
        early.put("targetDate", "1999-12-31");
        expectError(c, post(c, early), 400, "DATE_TOO_EARLY");
        Map<String, Object> late = body("t");
        late.put("targetDate", "2101-01-01");
        expectError(c, post(c, late), 400, "DATE_TOO_LATE");
        assertThat(rows(userId(c))).isZero();
    }

    // ---- listing and status ----------------------------------------------------------------------

    @Test
    void listDefaultsToActiveOrderedBySoonestTargetDateThenCreation() throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, post(c, body("No date")), 201);
        Map<String, Object> later = body("Later");
        later.put("targetDate", "2027-06-01");
        expect(c, post(c, later), 201);
        Map<String, Object> sooner = body("Sooner");
        sooner.put("targetDate", "2027-01-01");
        expect(c, post(c, sooner), 201);
        assertThat(titles(c, "")).containsExactly("Sooner", "Later", "No date");
        assertThat(titles(c, "?status=active")).isEqualTo(titles(c, ""));   // active is the default
        assertThat(titles(c, "?status=ACTIVE")).isEqualTo(titles(c, ""));  // case-insensitive
    }

    @Test
    void anUnknownStatusIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, c.get(GOALS + "?status=someday"), 400, "INVALID_STATUS");
    }

    @Test
    void achievedAndArchivedGoalsAreListedSeparatelyFromActiveOnes() throws Exception {
        TestClient c = newUser("a@example.com");
        String activeId = expect(c, post(c, body("Active")), 201).get("id").asText();
        String achievedId = expect(c, post(c, body("Achieved")), 201).get("id").asText();
        String archivedId = expect(c, post(c, body("Archived")), 201).get("id").asText();
        patch(c, achievedId, Map.of("status", "ACHIEVED"));
        patch(c, archivedId, Map.of("status", "ARCHIVED"));

        assertThat(titles(c, "")).containsExactly("Active");
        assertThat(titles(c, "?status=achieved")).containsExactly("Achieved");
        assertThat(titles(c, "?status=archived")).containsExactly("Archived");
        assertThat(activeId).isNotBlank();
    }

    // ---- progress: derived from tasks only, never habits ------------------------------------------

    @Test
    void progressIsNullWithNoLinkedTasksEvenWithLinkedHabits() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID user = userId(c);
        String id = expect(c, post(c, body("G")), 201).get("id").asText();
        insertHabit(user, UUID.fromString(id), "Run daily");

        JsonNode g = expect(c, c.get(GOALS + "/" + id), 200).get("goal");
        assertThat(g.get("taskCount").asInt()).isZero();
        assertThat(g.get("progressPercent").isNull()).isTrue();
    }

    @Test
    void progressReflectsOnlyDoneVsOpenTasksLinkedToTheGoal() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID user = userId(c);
        String id = expect(c, post(c, body("G")), 201).get("id").asText();
        UUID goalId = UUID.fromString(id);
        insertTask(user, goalId, "done 1", true);
        insertTask(user, goalId, "done 2", true);
        insertTask(user, goalId, "open 1", false);
        insertTask(user, null, "unlinked", false); // must not count toward this goal

        JsonNode g = expect(c, c.get(GOALS + "/" + id), 200).get("goal");
        assertThat(g.get("taskCount").asInt()).isEqualTo(3);
        assertThat(g.get("doneCount").asInt()).isEqualTo(2);
        assertThat(g.get("progressPercent").asInt()).isEqualTo(67); // 2/3 rounded
    }

    @Test
    void aFullyCompletedGoalIsAHundredPercentAndAnEmptyOneIsZero() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID user = userId(c);
        String allDone = expect(c, post(c, body("All done")), 201).get("id").asText();
        insertTask(user, UUID.fromString(allDone), "t1", true);
        insertTask(user, UUID.fromString(allDone), "t2", true);
        assertThat(expect(c, c.get(GOALS + "/" + allDone), 200).get("goal").get("progressPercent").asInt()).isEqualTo(100);

        String allOpen = expect(c, post(c, body("All open")), 201).get("id").asText();
        insertTask(user, UUID.fromString(allOpen), "t1", false);
        assertThat(expect(c, c.get(GOALS + "/" + allOpen), 200).get("goal").get("progressPercent").asInt()).isZero();
    }

    @Test
    void habitsLinkedToAGoalNeverChangeItsProgressPercent() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID user = userId(c);
        String id = expect(c, post(c, body("G")), 201).get("id").asText();
        UUID goalId = UUID.fromString(id);
        insertTask(user, goalId, "t1", true);
        insertTask(user, goalId, "t2", false);
        int before = expect(c, c.get(GOALS + "/" + id), 200).get("goal").get("progressPercent").asInt();

        UUID habit = insertHabit(user, goalId, "Run daily");
        tick(habit, "2026-09-24");
        tick(habit, "2026-09-23");
        tick(habit, "2026-09-22");

        JsonNode g = expect(c, c.get(GOALS + "/" + id), 200).get("goal");
        assertThat(g.get("progressPercent").asInt()).isEqualTo(before); // unchanged: 50, from tasks alone
        assertThat(g.get("progressPercent").asInt()).isEqualTo(50);
        assertThat(g.get("taskCount").asInt()).isEqualTo(2); // the habit is not counted as a task
    }

    // ---- detail: the goal plus its linked tasks (open first) and habits ---------------------------

    @Test
    void detailListsLinkedTasksOpenFirstThenDoneAndLinkedHabitsWithTheirStreaks() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID user = userId(c);
        String id = expect(c, post(c, body("G")), 201).get("id").asText();
        UUID goalId = UUID.fromString(id);
        insertTask(user, goalId, "Done task", true);
        insertTask(user, goalId, "Open task", false);
        UUID habit = insertHabit(user, goalId, "Run daily");
        tick(habit, "2026-09-24");
        tick(habit, "2026-09-23");

        JsonNode detail = expect(c, c.get(GOALS + "/" + id), 200);
        assertThat(detail.get("goal").get("id").asText()).isEqualTo(id);
        JsonNode tasks = detail.get("tasks");
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).get("title").asText()).isEqualTo("Open task"); // open first
        assertThat(tasks.get(0).get("completedAt").isNull()).isTrue();
        assertThat(tasks.get(1).get("title").asText()).isEqualTo("Done task");
        assertThat(tasks.get(1).get("completedAt").isNull()).isFalse();

        JsonNode habits = detail.get("habits");
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).get("name").asText()).isEqualTo("Run daily");
        assertThat(habits.get(0).get("currentStreak").asInt()).isEqualTo(2);
        assertThat(habits.get(0).get("longestStreak").asInt()).isEqualTo(2);
        assertThat(habits.get(0).get("archived").asBoolean()).isFalse();
    }

    @Test
    void aGoalThatIsMissingOrSomeoneElsesIsNotFound() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String theirs = expect(b, post(b, body("Secret")), 201).get("id").asText();
        expectError(a, a.get(GOALS + "/" + theirs), 404, "NOT_FOUND");
        expectError(a, a.get(GOALS + "/" + UUID.randomUUID()), 404, "NOT_FOUND");
        expect(a, a.get(GOALS + "/not-a-uuid"), 400);
    }

    // ---- editing: partial updates and status transitions ------------------------------------------

    private JsonNode patch(TestClient c, String id, Map<String, Object> body) throws Exception {
        return expect(c, c.patch(GOALS + "/" + id, body), 200);
    }

    @Test
    void aPartialEditChangesOnlyWhatIsSent() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("Original");
        b.put("description", "keep me");
        b.put("targetDate", "2027-01-01");
        String id = expect(c, post(c, b), 201).get("id").asText();

        JsonNode g = patch(c, id, Map.of("title", "  Renamed  "));
        assertThat(g.get("title").asText()).isEqualTo("Renamed");
        assertThat(g.get("description").asText()).isEqualTo("keep me");
        assertThat(g.get("targetDate").asText()).isEqualTo("2027-01-01");

        g = patch(c, id, Map.of("targetDate", "2027-06-01"));
        assertThat(g.get("targetDate").asText()).isEqualTo("2027-06-01");
        assertThat(g.get("title").asText()).isEqualTo("Renamed");
    }

    @Test
    void anExplicitNullClearsDescriptionAndTargetDateButAnOmittedFieldDoesNot() throws Exception {
        TestClient c = newUser("a@example.com");
        Map<String, Object> b = body("T");
        b.put("description", "d");
        b.put("targetDate", "2027-01-01");
        String id = expect(c, post(c, b), 201).get("id").asText();

        Map<String, Object> clear = new HashMap<>();
        clear.put("targetDate", null);
        JsonNode g = patch(c, id, clear);
        assertThat(g.get("targetDate").isNull()).isTrue();
        assertThat(g.get("description").asText()).isEqualTo("d"); // untouched

        clear = new HashMap<>();
        clear.put("description", null);
        g = patch(c, id, clear);
        assertThat(g.get("description").isNull()).isTrue();
        assertThat(patch(c, id, Map.of("description", "   ")).get("description").isNull()).isTrue(); // blank also clears
    }

    @Test
    void titleAndStatusCannotBeClearedAndAnEmptyBodyIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T")), 201).get("id").asText();
        expectError(c, c.patch(GOALS + "/" + id, Map.of()), 400, "EMPTY_UPDATE");
        Map<String, Object> nullTitle = new HashMap<>();
        nullTitle.put("title", null);
        expectError(c, c.patch(GOALS + "/" + id, nullTitle), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(GOALS + "/" + id, Map.of("title", "   ")), 400, "VALIDATION_FAILED");
        expectError(c, c.patch(GOALS + "/" + id, Map.of("title", "t".repeat(121))), 400, "VALIDATION_FAILED");
        Map<String, Object> nullStatus = new HashMap<>();
        nullStatus.put("status", null);
        expectError(c, c.patch(GOALS + "/" + id, nullStatus), 400, "VALIDATION_FAILED");
        expect(c, c.patch(GOALS + "/" + id, Map.of("status", "SOMEDAY")), 400);
        expectError(c, c.patch(GOALS + "/" + id, Map.of("targetDate", "1999-12-31")), 400, "DATE_TOO_EARLY");
        expectError(c, c.patch(GOALS + "/" + id, Map.of("targetDate", "2101-01-01")), 400, "DATE_TOO_LATE");
    }

    @Test
    void achievingSetsAchievedAtAndReopeningClearsIt() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T")), 201).get("id").asText();

        JsonNode achieved = patch(c, id, Map.of("status", "ACHIEVED"));
        assertThat(achieved.get("status").asText()).isEqualTo("ACHIEVED");
        assertThat(achieved.get("achievedAt").asText()).isEqualTo("2026-09-24T10:00:00Z");

        JsonNode reopened = patch(c, id, Map.of("status", "ACTIVE"));
        assertThat(reopened.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(reopened.get("achievedAt").isNull()).isTrue();
    }

    @Test
    void reAchievingWithoutReopeningKeepsTheOriginalAchievedMoment() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T")), 201).get("id").asText();
        patch(c, id, Map.of("status", "ACHIEVED"));
        String firstMoment = expect(c, c.get(GOALS + "/" + id), 200).get("goal").get("achievedAt").asText();

        clock.advance(java.time.Duration.ofHours(5));
        JsonNode again = patch(c, id, Map.of("status", "ACHIEVED", "description", "still true"));
        assertThat(again.get("achievedAt").asText()).isEqualTo(firstMoment); // not bumped
    }

    @Test
    void archivingClearsAnyAchievedMoment() throws Exception {
        TestClient c = newUser("a@example.com");
        String id = expect(c, post(c, body("T")), 201).get("id").asText();
        patch(c, id, Map.of("status", "ACHIEVED"));
        JsonNode archived = patch(c, id, Map.of("status", "ARCHIVED"));
        assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(archived.get("achievedAt").isNull()).isTrue();
    }

    @Test
    void statusIsIndependentOfProgressAGoalCanBeAchievedAtAnyPercentage() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID user = userId(c);
        String id = expect(c, post(c, body("G")), 201).get("id").asText();
        insertTask(user, UUID.fromString(id), "still open", false);
        JsonNode achieved = patch(c, id, Map.of("status", "ACHIEVED"));
        assertThat(achieved.get("progressPercent").asInt()).isZero(); // 0% but achieved anyway: a person's own call
        assertThat(achieved.get("status").asText()).isEqualTo("ACHIEVED");
    }

    @Test
    void editingSomeoneElsesGoalIsNotFoundAndChangesNothing() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String theirs = expect(b, post(b, body("Theirs")), 201).get("id").asText();
        expectError(a, a.patch(GOALS + "/" + theirs, Map.of("title", "Hijack")), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select title from goals where id = ?::uuid", String.class, theirs)).isEqualTo("Theirs");
    }

    // ---- delete: unlinks tasks and habits, never deletes them --------------------------------------

    @Test
    void deletingAGoalUnlinksItsTasksAndHabitsWithoutDeletingThem() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID user = userId(c);
        String id = expect(c, post(c, body("G")), 201).get("id").asText();
        UUID goalId = UUID.fromString(id);
        UUID task = insertTask(user, goalId, "T", false);
        UUID habit = insertHabit(user, goalId, "H");

        expect(c, c.delete(GOALS + "/" + id), 204);
        expectError(c, c.get(GOALS + "/" + id), 404, "NOT_FOUND");

        assertThat(jdbc.queryForObject("select goal_id is null from tasks where id = ?", Boolean.class, task)).isTrue();
        assertThat(jdbc.queryForObject("select title from tasks where id = ?", String.class, task)).isEqualTo("T");
        assertThat(jdbc.queryForObject("select goal_id is null from habits where id = ?", Boolean.class, habit)).isTrue();
        assertThat(jdbc.queryForObject("select name from habits where id = ?", String.class, habit)).isEqualTo("H");
    }

    @Test
    void deletingAMissingOrForeignGoalIsNotFound() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String theirs = expect(b, post(b, body("Theirs")), 201).get("id").asText();
        expectError(a, a.delete(GOALS + "/" + theirs), 404, "NOT_FOUND");
        expectError(a, a.delete(GOALS + "/" + UUID.randomUUID()), 404, "NOT_FOUND");
        expectError(a, a.delete(GOALS + "/" + theirs), 404, "NOT_FOUND"); // still theirs, not deleted
        assertThat(jdbc.queryForObject("select count(*) from goals where id = ?::uuid", Integer.class, theirs)).isEqualTo(1);
    }

    // ---- access --------------------------------------------------------------------------------

    @Test
    void anotherUsersGoalCannotBeSeenEditedOrDeleted() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = expect(a, post(a, body("Mine")), 201).get("id").asText();
        expectError(b, b.get(GOALS + "/" + id), 404, "NOT_FOUND");
        expectError(b, b.patch(GOALS + "/" + id, Map.of("title", "Hijack")), 404, "NOT_FOUND");
        expectError(b, b.delete(GOALS + "/" + id), 404, "NOT_FOUND");
        assertThat(titles(b, "")).isEmpty();
        assertThat(titles(a, "")).containsExactly("Mine");
    }

    @Test
    void everyEndpointRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        String id = UUID.randomUUID().toString();
        assertThat(anon.get(GOALS).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.get(GOALS + "/" + id).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(GOALS, Map.of("title", "t")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.patch(GOALS + "/" + id, Map.of("title", "t")).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(GOALS + "/" + id).getResponse().getStatus()).isEqualTo(401);

        TestClient c = newUser("a@example.com");
        String mine = expect(c, post(c, body("T")), 201).get("id").asText();
        assertThat(c.call(HttpMethod.POST, GOALS, Map.of("title", "t"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.PATCH, GOALS + "/" + mine, Map.of("title", "x"), false).getResponse().getStatus()).isEqualTo(403);
        assertThat(c.call(HttpMethod.DELETE, GOALS + "/" + mine, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(c))).isEqualTo(1);
    }
}
