package com.personalos.backend.tasks;

import com.personalos.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the database itself enforces the rules of goals, tasks, habits and completions, independent of the application code. */
@SpringBootTest
class TasksGoalsHabitsSchemaTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    UUID userA;
    UUID userB;

    @BeforeEach
    void reset() {
        jdbc.execute("delete from users");
        userA = insertUser("a@example.com");
        userB = insertUser("b@example.com");
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, email_verified) values (?, ?, 'x', true)", id, email);
        return id;
    }

    private UUID goal(UUID user, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into goals (id, user_id, title) values (?, ?, ?)", id, user, title);
        return id;
    }

    private UUID task(UUID user, String title, String priority, String due, UUID goal) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tasks (id, user_id, goal_id, title, priority, due_date) values (?, ?, ?, ?, ?, ?::date)", id, user, goal, title, priority, due);
        return id;
    }

    private UUID habit(UUID user, String name, int days, String startedOn, boolean archived) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into habits (id, user_id, name, days_of_week, started_on, archived_at) values (?, ?, ?, ?, ?::date, ?)",
                id, user, name, days, startedOn, archived ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
        return id;
    }

    private void completion(UUID habit, String date) {
        jdbc.update("insert into habit_completions (habit_id, completed_on) values (?, ?::date)", habit, date);
    }

    private static void assertRejected(Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    // ---- goals -------------------------------------------------------------------------------

    @Test
    void goalTitlesMustBeOneToOneHundredTwentyCharacters() {
        goal(userA, "x".repeat(120));
        assertRejected(() -> goal(userA, ""));
        assertRejected(() -> goal(userA, "   "));
        assertRejected(() -> goal(userA, "x".repeat(121)));
        assertThat(count("goals")).isEqualTo(1);
    }

    @Test
    void goalStatusMustBeKnownAndAchievedAtMustMatchIt() {
        UUID g = goal(userA, "Run a 10k");
        assertRejected(() -> jdbc.update("update goals set status = 'DONE' where id = ?", g));
        assertRejected(() -> jdbc.update("update goals set status = 'ACHIEVED' where id = ?", g));                       // achieved needs a moment
        assertRejected(() -> jdbc.update("update goals set achieved_at = now() where id = ?", g));                       // a moment needs achieved
        jdbc.update("update goals set status = 'ACHIEVED', achieved_at = now() where id = ?", g);
        jdbc.update("update goals set status = 'ARCHIVED', achieved_at = null where id = ?", g);
        jdbc.update("update goals set status = 'ACTIVE' where id = ?", g);
        assertThat(jdbc.queryForObject("select status from goals where id = ?", String.class, g)).isEqualTo("ACTIVE");
    }

    @Test
    void goalDescriptionAndTargetDateAreBounded() {
        jdbc.update("insert into goals (id, user_id, title, description, target_date) values (?, ?, 'A', ?, '2000-01-01')", UUID.randomUUID(), userA, "d".repeat(1000));
        jdbc.update("insert into goals (id, user_id, title, target_date) values (?, ?, 'B', '2100-12-31')", UUID.randomUUID(), userA);
        assertRejected(() -> jdbc.update("insert into goals (id, user_id, title, description) values (?, ?, 'C', ?)", UUID.randomUUID(), userA, "d".repeat(1001)));
        assertRejected(() -> jdbc.update("insert into goals (id, user_id, title, target_date) values (?, ?, 'D', '1999-12-31')", UUID.randomUUID(), userA));
        assertRejected(() -> jdbc.update("insert into goals (id, user_id, title, target_date) values (?, ?, 'E', '2101-01-01')", UUID.randomUUID(), userA));
        assertThat(count("goals")).isEqualTo(2);
    }

    // ---- tasks -------------------------------------------------------------------------------

    @Test
    void aTaskDefaultsToNormalPriorityAndOpenAndTitlesAreBounded() {
        UUID t = task(userA, "Buy milk", "NORMAL", null, null);
        UUID plain = UUID.randomUUID();
        jdbc.update("insert into tasks (id, user_id, title) values (?, ?, 'Plain')", plain, userA);
        assertThat(jdbc.queryForObject("select priority from tasks where id = ?", String.class, plain)).isEqualTo("NORMAL");
        assertThat(jdbc.queryForObject("select completed_at is null from tasks where id = ?", Boolean.class, t)).isTrue();
        task(userA, "x".repeat(200), "LOW", null, null);
        assertRejected(() -> task(userA, "", "NORMAL", null, null));
        assertRejected(() -> task(userA, "  ", "NORMAL", null, null));
        assertRejected(() -> task(userA, "x".repeat(201), "NORMAL", null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"URGENT", "high", "", "MEDIUM"})
    void taskPriorityMustBeLowNormalOrHigh(String priority) {
        assertRejected(() -> task(userA, "T", priority, null, null));
        for (String ok : new String[]{"LOW", "NORMAL", "HIGH"}) task(userA, "T", ok, null, null);
        assertThat(count("tasks")).isEqualTo(3);
    }

    @Test
    void taskDueDatesAndNotesAreBounded() {
        task(userA, "early", "NORMAL", "2000-01-01", null);
        task(userA, "late", "NORMAL", "2100-12-31", null);
        assertRejected(() -> task(userA, "too early", "NORMAL", "1999-12-31", null));
        assertRejected(() -> task(userA, "too late", "NORMAL", "2101-01-01", null));
        jdbc.update("insert into tasks (id, user_id, title, notes) values (?, ?, 'n', ?)", UUID.randomUUID(), userA, "n".repeat(2000));
        assertRejected(() -> jdbc.update("insert into tasks (id, user_id, title, notes) values (?, ?, 'n', ?)", UUID.randomUUID(), userA, "n".repeat(2001)));
        assertThat(count("tasks")).isEqualTo(3);
    }

    @Test
    void aTaskMustBelongToARealUserAndLinkToARealGoal() {
        assertRejected(() -> task(UUID.randomUUID(), "orphan", "NORMAL", null, null));
        assertRejected(() -> task(userA, "bad link", "NORMAL", null, UUID.randomUUID()));
        assertThat(count("tasks")).isZero();
    }

    @Test
    void deletingAGoalUnlinksItsTasksAndHabitsButKeepsThem() {
        UUID g = goal(userA, "Get fit");
        UUID t = task(userA, "Sign up", "NORMAL", null, g);
        UUID h = habit(userA, "Walk", 127, "2026-09-01", false);
        jdbc.update("update habits set goal_id = ? where id = ?", g, h);

        jdbc.update("delete from goals where id = ?", g);

        assertThat(jdbc.queryForObject("select goal_id from tasks where id = ?", UUID.class, t)).isNull();
        assertThat(jdbc.queryForObject("select goal_id from habits where id = ?", UUID.class, h)).isNull();
        assertThat(count("tasks")).isEqualTo(1);
        assertThat(count("habits")).isEqualTo(1);
    }

    // ---- habits ------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 128, 255, 1000})
    void habitDaysMustBeABitmaskOfAtLeastOneDay(int days) {
        assertRejected(() -> habit(userA, "H", days, "2026-09-01", false));
        for (int ok : new int[]{1, 21, 64, 127}) habit(userA, "H" + ok, ok, "2026-09-01", false);
        assertThat(count("habits")).isEqualTo(4);
    }

    @Test
    void habitNamesAndStartDatesAreBounded() {
        habit(userA, "x".repeat(100), 127, "2000-01-01", false);
        assertRejected(() -> habit(userA, "", 127, "2026-09-01", false));
        assertRejected(() -> habit(userA, "   ", 127, "2026-09-01", false));
        assertRejected(() -> habit(userA, "x".repeat(101), 127, "2026-09-01", false));
        assertRejected(() -> habit(userA, "Old", 127, "1999-12-31", false));
        assertThat(count("habits")).isEqualTo(1);
    }

    @Test
    void activeHabitNamesAreUniquePerPersonIgnoringCaseAndArchivingFreesTheName() {
        UUID first = habit(userA, "Read", 127, "2026-09-01", false);
        assertRejected(() -> habit(userA, "read", 127, "2026-09-01", false));     // same name, another case
        habit(userB, "Read", 127, "2026-09-01", false);                          // another person may use it
        jdbc.update("update habits set archived_at = now() where id = ?", first);
        habit(userA, "Read", 127, "2026-09-01", false);                          // archived: the name is free again
        habit(userA, "Read", 127, "2026-09-01", true);                           // any number of archived duplicates
        UUID archived = jdbc.queryForObject("select id from habits where user_id = ? and archived_at is not null limit 1", UUID.class, userA);
        assertRejected(() -> jdbc.update("update habits set archived_at = null where id = ?", archived));   // restoring would clash
    }

    // ---- completions -------------------------------------------------------------------------

    @Test
    void aHabitCanBeDoneOncePerDayAndOnlyForARealHabit() {
        UUID h = habit(userA, "Read", 127, "2026-09-01", false);
        completion(h, "2026-09-10");
        completion(h, "2026-09-11");
        assertRejected(() -> completion(h, "2026-09-10"));                       // the same day twice
        assertRejected(() -> completion(UUID.randomUUID(), "2026-09-10"));       // no such habit
        assertRejected(() -> completion(h, "1999-12-31"));
        assertThat(count("habit_completions")).isEqualTo(2);
    }

    @Test
    void deletingAHabitRemovesItsCompletionsOnly() {
        UUID a = habit(userA, "Read", 127, "2026-09-01", false);
        UUID b = habit(userB, "Read", 127, "2026-09-01", false);
        completion(a, "2026-09-10"); completion(a, "2026-09-11"); completion(b, "2026-09-10");

        jdbc.update("delete from habits where id = ?", a);

        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?", Integer.class, a)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?", Integer.class, b)).isEqualTo(1);
    }

    // ---- ownership ---------------------------------------------------------------------------

    @Test
    void deletingAUserRemovesTheirDataAcrossAllFourTablesOnly() {
        for (UUID user : new UUID[]{userA, userB}) {
            UUID g = goal(user, "G");
            task(user, "T", "NORMAL", "2026-09-24", g);
            UUID h = habit(user, "H", 127, "2026-09-01", false);
            completion(h, "2026-09-10");
        }

        jdbc.update("delete from users where id = ?", userA);

        for (String table : new String[]{"goals", "tasks", "habits"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userA)).as(table).isZero();
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userB)).as(table).isEqualTo(1);
        }
        assertThat(count("habit_completions")).isEqualTo(1);   // only B's remains
    }
}
