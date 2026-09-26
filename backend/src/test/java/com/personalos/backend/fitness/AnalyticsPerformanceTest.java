package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A smoke test on about two years of data: 730 daily weights, and 312 finished workouts of five exercises and five
 * sets each (about 7,800 sets). It checks that every analytics endpoint answers correctly and quickly on a realistic
 * personal history, not that it is the fastest possible. The time limit is deliberately generous so it does not flake
 * on a slow CI machine; a query that scans or loops badly would still blow well past it.
 */
class AnalyticsPerformanceTest extends FitnessApiTest {

    private static final long LIMIT_MS = 3000;
    private static final int WORKOUTS = 312;

    private UUID builtIn(String muscle) {
        return jdbc.queryForObject("select id from exercises where owner_id is null and primary_muscle_group = ? order by name limit 1", UUID.class, muscle);
    }

    private JsonNode timed(TestClient c, String label, String path) throws Exception {
        long start = System.nanoTime();
        JsonNode body = expect(c, c.get(path), 200);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("analytics perf: %-34s %4d ms%n", label, ms);
        assertThat(ms).as(label + " took " + ms + " ms").isLessThan(LIMIT_MS);
        return body;
    }

    @Test
    void everyAnalyticsEndpointIsCorrectAndFastOnTwoYearsOfData() throws Exception {
        TestClient c = newUser("perf@example.com");
        UUID u = userId(c);
        List<UUID> exercises = List.of(builtIn("CHEST"), builtIn("BACK"), builtIn("QUADS"), builtIn("SHOULDERS"), builtIn("CORE"));
        UUID bench = exercises.get(0);

        // 730 days of weights, ending today (2026-09-24).
        jdbc.update("""
                insert into weight_entries (id, user_id, entry_date, weight_kg)
                select gen_random_uuid(), ?, date '2026-09-24' - d, 90 - d * 0.01 + (d % 5) * 0.1 from generate_series(0, 729) as d
                """, u);

        // 312 workouts, one every 2.33 days (about three a week) over two years, each with five exercises.
        jdbc.update("""
                insert into workouts (id, user_id, name, status, performed_on, started_at, finished_at)
                select gen_random_uuid(), ?, 'Seed ' || i, 'COMPLETED', date '2026-09-24' - ((%d - 1 - i) * 7 / 3),
                       (date '2026-09-24' - ((%d - 1 - i) * 7 / 3))::timestamp + interval '9 hours',
                       (date '2026-09-24' - ((%d - 1 - i) * 7 / 3))::timestamp + interval '10 hours'
                from generate_series(0, %d - 1) as i
                """.formatted(WORKOUTS, WORKOUTS, WORKOUTS, WORKOUTS), u);
        for (int position = 0; position < exercises.size(); position++) {
            jdbc.update("insert into workout_exercises (id, workout_id, exercise_id, position) "
                    + "select gen_random_uuid(), id, ?, ? from workouts where user_id = ?", exercises.get(position), position + 1, u);
        }
        // Set 1 is a warm-up; sets 2 to 5 are working sets whose weight creeps up with the workout number.
        jdbc.update("""
                insert into workout_sets (id, workout_exercise_id, set_number, weight_kg, reps, is_warmup)
                select gen_random_uuid(), we.id, s, case when s = 1 then 20 else 40 + (row_number() over (order by w.started_at) %% 60) * 0.5 end,
                       5 + (s %% 3), s = 1
                from workout_exercises we
                join workouts w on w.id = we.workout_id and w.user_id = ?
                cross join generate_series(1, 5) as s
                """.replace("%%", "%"), u);
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isEqualTo(WORKOUTS * 5 * 5);

        // Weight.
        JsonNode daily = timed(c, "weight series, 730 days daily", "/api/v1/weight/series?from=2024-09-25&to=2026-09-24&granularity=DAILY");
        assertThat(daily.get("points")).hasSize(730);
        assertThat(timed(c, "weight series, weekly", "/api/v1/weight/series?from=2024-09-25&to=2026-09-24&granularity=WEEKLY").get("points").size()).isBetween(104, 106);
        assertThat(timed(c, "weight series, monthly", "/api/v1/weight/series?from=2024-09-01&to=2026-09-24&granularity=MONTHLY").get("points")).hasSize(25);
        assertThat(timed(c, "weight summary", "/api/v1/weight/summary").get("entryCount").asInt()).isEqualTo(730);

        // Fitness volume: every workout is counted exactly once, and only working sets add volume.
        JsonNode weekly = timed(c, "volume, weekly over 2 years", "/api/v1/fitness/analytics/volume?from=2024-09-25&to=2026-09-24&granularity=weekly");
        int workouts = 0;
        for (JsonNode p : weekly.get("points")) workouts += p.get("workouts").asInt();
        assertThat(workouts).isEqualTo(WORKOUTS);
        assertThat(weekly.get("points").get(0).get("byMovementGroup")).hasSize(4);
        Integer workingSets = 0;
        for (JsonNode p : weekly.get("points")) workingSets += p.get("workingSets").asInt();
        assertThat(workingSets).isEqualTo(WORKOUTS * 5 * 4); // four working sets per exercise, the warm-up excluded
        JsonNode monthly = timed(c, "volume, monthly over 2 years", "/api/v1/fitness/analytics/volume?from=2024-09-01&to=2026-09-24&granularity=monthly");
        assertThat(monthly.get("points")).hasSize(25);

        // Exercise progression and personal records.
        JsonNode progression = timed(c, "progression, 312 sessions", "/api/v1/exercises/" + bench + "/progression?from=2024-09-25&to=2026-09-24");
        assertThat(progression.get("points")).hasSize(WORKOUTS);
        JsonNode records = timed(c, "exercise personal records", "/api/v1/exercises/" + bench + "/personal-records?size=20");
        assertThat(records.get("totalItems").asInt()).isGreaterThan(0);
        assertThat(records.get("items").size()).isLessThanOrEqualTo(20);
        JsonNode global = timed(c, "global personal records, limit 50", "/api/v1/fitness/personal-records?limit=50");
        assertThat(global.size()).isBetween(1, 50);
    }
}
