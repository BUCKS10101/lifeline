package com.personalos.backend.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The daily-totals series of water and protein. Both behave the same way, so each test runs for both. The clock is fixed
 * at Thursday 2026-09-24 10:00 UTC.
 */
class IntakeSeriesApiTest extends ApiTestBase {

    /** One metric: where to add, where to read the series, and what its fields are called. */
    private record Metric(String name, String add, String series, String amountField, String totalField, String goalField, int[] amounts, int big) {}

    private static final List<Metric> METRICS = List.of(
            new Metric("water", "/api/v1/water-entries", "/api/v1/water/series", "amountMl", "totalMl", "goalMl", new int[]{300, 400, 500, 250, 251, 900}, 5000),
            new Metric("protein", "/api/v1/protein-entries", "/api/v1/protein/series", "grams", "totalG", "goalG", new int[]{30, 40, 50, 20, 21, 90}, 500));
    private static final String PREFS = "/api/v1/wellness/preferences";

    private void add(TestClient c, Metric m, int amount, String date) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of(m.amountField(), amount));
        if (date != null) body.put("date", date);
        expect(c, c.post(m.add(), body), 201);
    }

    private JsonNode series(TestClient c, Metric m, String query) throws Exception {
        return expect(c, c.get(m.series() + query), 200);
    }

    private static int total(JsonNode point, Metric m) {
        return point.get(m.totalField()).asInt();
    }

    @Test
    void theDefaultIsTheLastFourteenDaysEndingTodayEveryDayListedOldestFirst() throws Exception {
        for (Metric m : METRICS) {
            TestClient c = newUser(m.name() + "-a@example.com");
            JsonNode s = series(c, m, "");
            assertThat(s.get("from").asText()).as(m.name()).isEqualTo("2026-09-11");
            assertThat(s.get("to").asText()).isEqualTo("2026-09-24");
            assertThat(s.get("points")).hasSize(14);
            assertThat(s.get("points").get(0).get("date").asText()).isEqualTo("2026-09-11");
            assertThat(s.get("points").get(13).get("date").asText()).isEqualTo("2026-09-24");
            assertThat(s.get("points").findValues(m.totalField()).stream().mapToInt(JsonNode::asInt).sum()).isZero();
            assertThat(s.get(m.goalField()).isNull()).isTrue();
            assertThat(s.get("average").isNull()).isTrue();
            assertThat(s.get("previousAverage").isNull()).isTrue();
        }
    }

    @Test
    void eachDaysTotalIsTheSumOfItsEntriesAndEmptyDaysAreZero() throws Exception {
        for (Metric m : METRICS) {
            TestClient c = newUser(m.name() + "-b@example.com");
            add(c, m, m.amounts()[0], "2026-09-22");
            add(c, m, m.amounts()[1], "2026-09-22");
            add(c, m, m.amounts()[2], null);
            JsonNode points = series(c, m, "?from=2026-09-21&to=2026-09-24").get("points");
            assertThat(points).hasSize(4);
            assertThat(total(points.get(0), m)).as(m.name()).isZero();                                  // 21st: nothing
            assertThat(total(points.get(1), m)).isEqualTo(m.amounts()[0] + m.amounts()[1]);              // 22nd: two entries
            assertThat(total(points.get(2), m)).isZero();
            assertThat(total(points.get(3), m)).isEqualTo(m.amounts()[2]);                               // today
        }
    }

    @Test
    void theRangeIsInclusiveOnBothEndsAndEntriesOutsideItAreLeftOut() throws Exception {
        for (Metric m : METRICS) {
            TestClient c = newUser(m.name() + "-c@example.com");
            add(c, m, m.amounts()[0], "2026-09-19");   // one day before
            add(c, m, m.amounts()[1], "2026-09-20");   // first day
            add(c, m, m.amounts()[2], "2026-09-22");   // last day
            add(c, m, m.amounts()[3], "2026-09-23");   // one day after
            JsonNode points = series(c, m, "?from=2026-09-20&to=2026-09-22").get("points");
            assertThat(points).hasSize(3);
            assertThat(points.findValues(m.totalField()).stream().mapToInt(JsonNode::asInt).boxed().toList())
                    .as(m.name()).containsExactly(m.amounts()[1], 0, m.amounts()[2]);
        }
    }

    @Test
    void theAverageIsPerDayWithAnEntryAndThePreviousPeriodIsTheEquallyLongPeriodJustBefore() throws Exception {
        for (Metric m : METRICS) {
            TestClient c = newUser(m.name() + "-d@example.com");
            int[] a = m.amounts();
            add(c, m, a[0], "2026-09-20"); add(c, m, a[1], "2026-09-20");   // 20th: a0 + a1
            add(c, m, a[2], "2026-09-22");                                    // 22nd: a2; the 21st has nothing
            add(c, m, a[3], "2026-09-18");                                    // previous period (17th to 19th)
            add(c, m, a[5], "2026-09-19");
            add(c, m, m.big(), "2026-09-16");                                 // one day before the previous period: left out
            JsonNode s = series(c, m, "?from=2026-09-20&to=2026-09-22");
            // Two days have entries; the empty 21st is not counted as a zero day.
            assertThat(s.get("average").get("days").asInt()).as(m.name()).isEqualTo(2);
            assertThat(s.get("average").get("amount").asInt()).isEqualTo(Math.round((a[0] + a[1] + a[2]) / 2.0f));
            assertThat(s.get("previousAverage").get("days").asInt()).isEqualTo(2);
            assertThat(s.get("previousAverage").get("amount").asInt()).isEqualTo(Math.round((a[3] + a[5]) / 2.0f));
        }
    }

    @Test
    void theAverageRoundsHalfUpToWholeUnits() throws Exception {
        for (Metric m : METRICS) {
            TestClient c = newUser(m.name() + "-e@example.com");
            add(c, m, m.amounts()[3], "2026-09-22");   // 250 or 20
            add(c, m, m.amounts()[4], "2026-09-23");   // 251 or 21
            int expected = (int) Math.round((m.amounts()[3] + m.amounts()[4]) / 2.0 + 1e-9);
            assertThat(series(c, m, "?from=2026-09-22&to=2026-09-23").get("average").get("amount").asInt()).as(m.name()).isEqualTo(expected);
        }
    }

    @Test
    void theGoalIsEchoedAndAClearedGoalIsNull() throws Exception {
        TestClient c = newUser("goal@example.com");
        expect(c, c.put(PREFS, Map.of("sleepEnabled", true, "waterEnabled", true, "proteinEnabled", true, "waterGoalMl", 2500, "proteinGoalG", 140)), 200);
        assertThat(series(c, METRICS.get(0), "").get("goalMl").asInt()).isEqualTo(2500);
        assertThat(series(c, METRICS.get(1), "").get("goalG").asInt()).isEqualTo(140);
        expect(c, c.put(PREFS, Map.of("sleepEnabled", true, "waterEnabled", true, "proteinEnabled", true)), 200);
        assertThat(series(c, METRICS.get(0), "").get("goalMl").isNull()).isTrue();
    }

    @Test
    void badRangesAreRejectedAndTheLongestAllowedIs366Days() throws Exception {
        for (Metric m : METRICS) {
            TestClient c = newUser(m.name() + "-f@example.com");
            expectError(c, c.get(m.series() + "?from=2026-09-10&to=2026-09-01"), 400, "INVALID_RANGE");
            expectError(c, c.get(m.series() + "?from=2025-01-01&to=2026-09-24"), 400, "INVALID_RANGE");
            assertThat(series(c, m, "?from=2025-09-24&to=2026-09-24").get("points")).hasSize(366);
            expectError(c, c.get(m.series() + "?from=2025-09-23&to=2026-09-24"), 400, "INVALID_RANGE");   // 367 days: one too many
            expect(c, c.get(m.series() + "?from=nope"), 400);
        }
    }

    @Test
    void todayFollowsThePersonsTimezone() throws Exception {
        for (Metric m : METRICS) {
            // 10:00 UTC on 2026-09-24 is already 2026-09-25 in Kiritimati (UTC+14).
            TestClient c = newUser(m.name() + "-k@example.com", "Pacific/Kiritimati");
            add(c, m, m.amounts()[0], null);
            JsonNode s = series(c, m, "");
            assertThat(s.get("to").asText()).as(m.name()).isEqualTo("2026-09-25");
            assertThat(total(s.get("points").get(13), m)).isEqualTo(m.amounts()[0]);
        }
    }

    @Test
    void onlyThePersonsOwnEntriesCountAndEveryEndpointNeedsASession() throws Exception {
        for (Metric m : METRICS) {
            TestClient a = newUser(m.name() + "-x@example.com");
            TestClient b = newUser(m.name() + "-y@example.com");
            add(a, m, m.amounts()[0], null);
            JsonNode seenByB = series(b, m, "");
            assertThat(seenByB.get("points").findValues(m.totalField()).stream().mapToInt(JsonNode::asInt).sum()).as(m.name()).isZero();
            assertThat(seenByB.get("average").isNull()).isTrue();
            assertThat(new TestClient(mvc, mapper).get(m.series()).getResponse().getStatus()).isEqualTo(401);
        }
    }
}
