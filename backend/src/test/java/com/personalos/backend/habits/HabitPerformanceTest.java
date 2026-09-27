package com.personalos.backend.habits;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import com.personalos.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A smoke test on about three years of habit data: 10 daily habits, each started three years ago and ticked on every
 * day except a deterministic one day in ten (about 1,000 completions per habit, roughly 10,000 rows). It checks that
 * every habit read answers correctly and quickly on a realistic personal history, not that it is the fastest possible.
 * The limit is deliberately generous so it does not flake on a slow CI machine; a query that scanned per habit, or
 * that recomputed a streak day by day per request in an unreasonable way, would still blow well past it.
 */
class HabitPerformanceTest extends ApiTestBase {

    private static final long LIMIT_MS = 3000;
    private static final int HABITS = 10;
    private static final int DAYS = 1096; // three years, inclusive of today

    private JsonNode timed(TestClient c, String label, String path) throws Exception {
        long start = System.nanoTime();
        JsonNode body = expect(c, c.get(path), 200);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("habit perf: %-32s %4d ms%n", label, ms);
        assertThat(ms).as(label + " took " + ms + " ms").isLessThan(LIMIT_MS);
        return body;
    }

    @Test
    void everyHabitReadIsCorrectAndFastOnThreeYearsOfData() throws Exception {
        TestClient c = newUser("perf@example.com");
        UUID user = userId(c);

        // 10 daily habits, all started 2026-09-24 minus 1095 days (three years ago, inclusive of today = 1096 days).
        jdbc.update("""
                insert into habits (id, user_id, name, days_of_week, started_on, created_at, updated_at)
                select gen_random_uuid(), ?, 'Habit ' || n, 127, date '2026-09-24' - (%d - 1), now(), now()
                from generate_series(1, %d) as n
                """.formatted(DAYS, HABITS), user);
        // Ticked every day except every 10th (day % 10 = 0): about 90%% of 1096 days, ~987 completions per habit.
        jdbc.update("""
                insert into habit_completions (habit_id, completed_on, created_at)
                select h.id, date '2026-09-24' - d, now()
                from habits h, generate_series(0, %d - 1) as d
                where h.user_id = ? and d %% 10 <> 0
                """.formatted(DAYS), user);
        assertThat(jdbc.queryForObject("select count(*) from habits where user_id = ?", Integer.class, user)).isEqualTo(HABITS);
        long totalCompletions = jdbc.queryForObject("select count(*) from habit_completions hc join habits h on h.id = hc.habit_id where h.user_id = ?", Long.class, user);
        assertThat(totalCompletions).isBetween((long) HABITS * 900, (long) HABITS * 1000);

        JsonNode list = timed(c, "list, 10 habits x 3 years", "/api/v1/habits");
        assertThat(list).hasSize(HABITS);
        for (JsonNode h : list) {
            // Today (day offset 0) is itself one of the deliberate gaps (0 is a multiple of 10), so it is not done, and
            // being still open does not extend or reset the streak: the streak is the nine days 1-9 before it.
            assertThat(h.get("currentStreak").asInt()).isEqualTo(9);
            assertThat(h.get("longestStreak").asInt()).isEqualTo(9);
            assertThat(h.get("doneToday").asBoolean()).isFalse();
            assertThat(h.get("scheduledToday").asBoolean()).isTrue();
        }
        assertThat(timed(c, "today, 10 habits", "/api/v1/habits/today")).hasSize(HABITS);

        UUID first = UUID.fromString(list.get(0).get("id").asText());
        JsonNode history = timed(c, "history, 365-day window", "/api/v1/habits/" + first + "/history?from=2025-09-25&to=2026-09-24");
        assertThat(history.get("points")).hasSize(365); // 2025-09-25 to 2026-09-24 inclusive, a non-leap year, is 365 days
        long doneInWindow = history.get("points").findValues("done").stream().filter(JsonNode::asBoolean).count();
        assertThat(doneInWindow).isBetween(320L, 340L); // ~90% of 366
        // The streaks in the history response cover the whole three years, same rule as the list endpoint's.
        assertThat(history.get("currentStreak").asInt()).isEqualTo(list.get(0).get("currentStreak").asInt());
        assertThat(history.get("longestStreak").asInt()).isEqualTo(list.get(0).get("longestStreak").asInt());

        for (JsonNode h : list) {
            timed(c, "history, default window (habit " + h.get("name").asText() + ")", "/api/v1/habits/" + h.get("id").asText() + "/history");
        }
    }
}
