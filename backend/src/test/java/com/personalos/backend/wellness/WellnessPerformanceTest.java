package com.personalos.backend.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A smoke test on two years of wellness data: 730 nights of sleep and three water and three protein entries every day
 * (about 4,400 rows). It checks that every wellness read answers correctly and quickly on a realistic personal history,
 * not that it is the fastest possible. The limit is deliberately generous so it does not flake on a slow CI machine; a
 * query that scanned or looped badly would still blow well past it.
 */
class WellnessPerformanceTest extends ApiTestBase {

    private static final long LIMIT_MS = 3000;

    private JsonNode timed(TestClient c, String label, String path) throws Exception {
        long start = System.nanoTime();
        JsonNode body = expect(c, c.get(path), 200);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("wellness perf: %-36s %4d ms%n", label, ms);
        assertThat(ms).as(label + " took " + ms + " ms").isLessThan(LIMIT_MS);
        return body;
    }

    @Test
    void everyWellnessReadIsCorrectAndFastOnTwoYearsOfData() throws Exception {
        TestClient c = newUser("perf@example.com");
        UUID user = userId(c);
        expect(c, c.put("/api/v1/wellness/preferences", java.util.Map.of("sleepEnabled", true, "waterEnabled", true, "proteinEnabled", true,
                "waterGoalMl", 2500, "proteinGoalG", 140, "sleepGoalMinutes", 480)), 200);

        // 730 nights ending today (2026-09-24), each 8 to 9 hours: bed at 23:00 minus 0..60 minutes, up at 07:00.
        jdbc.update("""
                insert into sleep_entries (id, user_id, sleep_date, bedtime_at, woke_at, zone_id)
                select gen_random_uuid(), ?, date '2026-09-24' - d,
                       (date '2026-09-24' - d - 1)::timestamp at time zone 'UTC' + interval '23 hours' - (d %% 5) * interval '15 minutes',
                       (date '2026-09-24' - d)::timestamp at time zone 'UTC' + interval '7 hours', 'UTC'
                from generate_series(0, 729) as d
                """.replace("%%", "%"), user);
        // Three drinks a day (250 + 500 + 330 = 1080 ml) and three protein entries a day (20 + 30 + 40 = 90 g) with rotating labels.
        jdbc.update("""
                insert into water_entries (id, user_id, log_date, amount_ml, logged_at)
                select gen_random_uuid(), ?, date '2026-09-24' - d, (array[250, 500, 330])[k],
                       (date '2026-09-24' - d)::timestamp at time zone 'UTC' + (k * interval '4 hours')
                from generate_series(0, 729) as d, generate_series(1, 3) as k
                """, user);
        jdbc.update("""
                insert into protein_entries (id, user_id, log_date, grams, label, logged_at)
                select gen_random_uuid(), ?, date '2026-09-24' - d, (array[20, 30, 40])[k], 'Food ' || ((d + k) %% 8),
                       (date '2026-09-24' - d)::timestamp at time zone 'UTC' + (k * interval '4 hours')
                from generate_series(0, 729) as d, generate_series(1, 3) as k
                """.replace("%%", "%"), user);
        assertThat(jdbc.queryForObject("select count(*) from sleep_entries", Integer.class)).isEqualTo(730);
        assertThat(jdbc.queryForObject("select count(*) from water_entries", Integer.class)).isEqualTo(2190);
        assertThat(jdbc.queryForObject("select count(*) from protein_entries", Integer.class)).isEqualTo(2190);

        // Sleep: a year of nights, the default month, and a deep page of the history.
        JsonNode year = timed(c, "sleep series, 365 days", "/api/v1/sleep/series?from=2025-09-25&to=2026-09-24");
        assertThat(year.get("points")).hasSize(365);
        assertThat(year.get("average").get("entries").asInt()).isEqualTo(365);
        assertThat(year.get("previousAverage").get("entries").asInt()).isEqualTo(365);   // the year before is fully logged too
        assertThat(year.get("average").get("durationMinutes").asInt()).isBetween(480, 540);
        assertThat(timed(c, "sleep series, default 30 days", "/api/v1/sleep/series").get("points")).hasSize(30);
        JsonNode deep = timed(c, "sleep history, page 72 of 730", "/api/v1/sleep-entries?page=72&size=10");
        assertThat(deep.get("totalItems").asInt()).isEqualTo(730);
        assertThat(deep.get("items")).hasSize(10);

        // Water and protein: a year of zero-filled daily totals with averages and the goal.
        JsonNode water = timed(c, "water series, 365 days", "/api/v1/water/series?from=2025-09-25&to=2026-09-24");
        assertThat(water.get("points")).hasSize(365);
        assertThat(water.get("points").findValues("totalMl").stream().allMatch(n -> n.asInt() == 1080)).isTrue();
        assertThat(water.get("average").get("amount").asInt()).isEqualTo(1080);
        assertThat(water.get("average").get("days").asInt()).isEqualTo(365);
        assertThat(water.get("previousAverage").get("days").asInt()).isEqualTo(365);
        assertThat(water.get("goalMl").asInt()).isEqualTo(2500);
        JsonNode protein = timed(c, "protein series, 365 days", "/api/v1/protein/series?from=2025-09-25&to=2026-09-24");
        assertThat(protein.get("points")).hasSize(365);
        assertThat(protein.get("points").findValues("totalG").stream().allMatch(n -> n.asInt() == 90)).isTrue();
        assertThat(protein.get("average").get("amount").asInt()).isEqualTo(90);
        assertThat(protein.get("goalG").asInt()).isEqualTo(140);

        // Today, one day's entries, and the personal label suggestions over the recent window.
        JsonNode today = timed(c, "wellness today", "/api/v1/wellness/today");
        assertThat(today.get("water").get("totalMl").asInt()).isEqualTo(1080);
        assertThat(today.get("water").get("progressPercent").asInt()).isEqualTo(43);
        assertThat(today.get("protein").get("totalG").asInt()).isEqualTo(90);
        assertThat(today.get("sleep").get("entry").get("date").asText()).isEqualTo("2026-09-24");
        assertThat(timed(c, "water day", "/api/v1/water-entries").get("entries")).hasSize(3);
        assertThat(timed(c, "protein day", "/api/v1/protein-entries").get("entries")).hasSize(3);
        JsonNode suggestions = timed(c, "protein suggestions", "/api/v1/protein/suggestions");
        assertThat(suggestions).hasSize(6);
        int previous = Integer.MAX_VALUE;
        for (JsonNode s : suggestions) {
            assertThat(s.get("uses").asInt()).isLessThanOrEqualTo(previous);   // most used first
            previous = s.get("uses").asInt();
        }
    }
}
