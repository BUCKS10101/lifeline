package com.personalos.backend.goals;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A smoke test on a realistic personal scale, matching the phase's other performance tests: 30 goals, about 5,000
 * tasks spread across them (some linked, most not, mirroring real use), and a handful of linked habits with three
 * years of history. It checks that the goals list (which counts every goal's tasks) and a goal's own detail (which
 * also computes its linked habits' streaks) stay correct and fast, not that they are the fastest possible. The limit
 * is deliberately generous so it does not flake on a slow CI machine.
 */
class GoalPerformanceTest extends ApiTestBase {

    private static final long LIMIT_MS = 3000;
    private static final int GOALS = 30;
    private static final int TASKS = 5000;
    private static final int HABITS = 5;
    private static final int HABIT_DAYS = 1096; // three years, inclusive of today

    private JsonNode timed(TestClient c, String label, String path) throws Exception {
        long start = System.nanoTime();
        JsonNode body = expect(c, c.get(path), 200);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("goal perf: %-32s %4d ms%n", label, ms);
        assertThat(ms).as(label + " took " + ms + " ms").isLessThan(LIMIT_MS);
        return body;
    }

    @Test
    void everyGoalReadIsCorrectAndFastOnAThreeYearScaleOfData() throws Exception {
        TestClient c = newUser("perf@example.com");
        UUID user = userId(c);

        // 30 goals, oldest-first target dates so the list's ordering is exercised too.
        jdbc.update("""
                insert into goals (id, user_id, title, target_date, status, created_at, updated_at)
                select gen_random_uuid(), ?, 'Goal ' || n, date '2027-01-01' + n, 'ACTIVE', now(), now()
                from generate_series(1, %d) as n
                """.formatted(GOALS), user);
        java.util.List<UUID> goalIdList = jdbc.queryForList("select id from goals where user_id = ? order by title", UUID.class, user);
        assertThat(goalIdList).hasSize(GOALS);
        UUID firstGoal = goalIdList.get(0);

        // ~5,000 tasks, round-robin one third linked to a goal (about 1,667 linked, spread across all 30 goals),
        // completed_at set on every third task so counts are a genuine mix of done and open. The goal to link to is
        // picked by row number modulo the goal count, so no array parameter binding is needed.
        jdbc.update("""
                with numbered_goals as (
                    select id, row_number() over (order by title) - 1 as rn from goals where user_id = ?
                )
                insert into tasks (id, user_id, goal_id, title, priority, completed_at, created_at, updated_at)
                select gen_random_uuid(), ?, case when n %% 3 = 0 then g.id else null end,
                       'Task ' || n, 'NORMAL', case when n %% 3 = 0 then now() else null end, now(), now()
                from generate_series(1, %d) as n
                left join numbered_goals g on g.rn = n %% %d
                """.formatted(TASKS, GOALS), user, user);
        long totalTasks = jdbc.queryForObject("select count(*) from tasks where user_id = ?", Long.class, user);
        assertThat(totalTasks).isEqualTo(TASKS);

        // A handful of daily habits, three years old, linked to the first goal, ticked every day but every 10th.
        jdbc.update("""
                insert into habits (id, user_id, goal_id, name, days_of_week, started_on, created_at, updated_at)
                select gen_random_uuid(), ?, ?, 'Habit ' || n, 127, date '2026-09-24' - (%d - 1), now(), now()
                from generate_series(1, %d) as n
                """.formatted(HABIT_DAYS, HABITS), user, firstGoal);
        jdbc.update("""
                insert into habit_completions (habit_id, completed_on, created_at)
                select h.id, date '2026-09-24' - d, now()
                from habits h, generate_series(0, %d - 1) as d
                where h.user_id = ? and h.goal_id = ? and d %% 10 <> 0
                """.formatted(HABIT_DAYS), user, firstGoal);

        JsonNode list = timed(c, "list, 30 goals x ~5,000 tasks", "/api/v1/goals");
        assertThat(list).hasSize(GOALS);
        int totalCounted = 0;
        for (JsonNode g : list) totalCounted += g.get("taskCount").asInt();
        assertThat(totalCounted).isBetween(TASKS / 3 - GOALS, TASKS / 3 + GOALS); // ~1,667 linked tasks total

        JsonNode detail = timed(c, "detail, goal with linked tasks and 5 habits", "/api/v1/goals/" + firstGoal);
        assertThat(detail.get("goal").get("id").asText()).isEqualTo(firstGoal.toString());
        assertThat(detail.get("habits")).hasSize(HABITS);
        for (JsonNode h : detail.get("habits")) {
            assertThat(h.get("currentStreak").asInt()).isEqualTo(9); // same shape as the habit performance test
            assertThat(h.get("longestStreak").asInt()).isEqualTo(9);
        }
        JsonNode tasks = detail.get("tasks");
        assertThat(tasks).isNotEmpty();
        boolean seenOpenBeforeDone = false;
        boolean pastFirstDone = false;
        for (JsonNode t : tasks) {
            boolean done = !t.get("completedAt").isNull();
            if (done) pastFirstDone = true;
            else if (pastFirstDone) seenOpenBeforeDone = true; // an open task appeared after a done one: ordering broke
        }
        assertThat(seenOpenBeforeDone).isFalse();
    }
}
