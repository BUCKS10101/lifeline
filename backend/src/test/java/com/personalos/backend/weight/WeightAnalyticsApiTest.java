package com.personalos.backend.weight;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC. The ISO week is Monday 2026-09-21 to Sunday 2026-09-27. */
class WeightAnalyticsApiTest extends ApiTestBase {

    private static final String SERIES = "/api/v1/weight/series";
    private static final String SUMMARY = "/api/v1/weight/summary";
    private static final String TARGET = "/api/v1/weight/target";

    private void entry(UUID user, String date, String kg) {
        jdbc.update("insert into weight_entries (id, user_id, entry_date, weight_kg) values (?, ?, ?::date, ?::numeric)",
                UUID.randomUUID(), user, date, kg);
    }

    private JsonNode get(TestClient c, String path) throws Exception {
        return expect(c, c.get(path), 200);
    }

    private JsonNode setTarget(TestClient c, String kg) throws Exception {
        return expect(c, c.put(TARGET, Map.of("targetWeightKg", new BigDecimal(kg))), 200);
    }

    // ---- series -------------------------------------------------------------------------------

    @Test
    void dailySeriesIsOrderedAndTheTrendAveragesTheSevenCalendarDaysEndingThatDay() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        entry(u, "2026-09-10", "80.00");
        entry(u, "2026-09-16", "82.00"); // 6 days after the 10th: the 10th is still in its window
        entry(u, "2026-09-17", "84.00"); // 7 days after the 10th: the 10th has dropped out
        entry(u, "2026-09-20", "83.00");

        JsonNode s = get(c, SERIES + "?from=2026-09-01&to=2026-09-24");
        assertThat(s.get("granularity").asText()).isEqualTo("DAILY");
        JsonNode p = s.get("points");
        assertThat(p).hasSize(4);
        assertThat(p.get(0).get("date").asText()).isEqualTo("2026-09-10");
        assertThat(p.get(0).get("trendKg").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(p.get(1).get("trendKg").decimalValue()).isEqualByComparingTo("81.00");  // (80+82)/2
        assertThat(p.get(2).get("trendKg").decimalValue()).isEqualByComparingTo("83.00");  // (82+84)/2, the 10th excluded
        assertThat(p.get(3).get("trendKg").decimalValue()).isEqualByComparingTo("83.00");  // (82+84+83)/3
        assertThat(p.get(3).get("weightKg").decimalValue()).isEqualByComparingTo("83.00");
    }

    @Test
    void theTrendAtTheFirstPointInRangeStillSeesEntriesBeforeTheRange() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        entry(u, "2026-09-10", "80.00");
        entry(u, "2026-09-12", "82.00");

        JsonNode p = get(c, SERIES + "?from=2026-09-12&to=2026-09-24").get("points");
        assertThat(p).hasSize(1);
        assertThat(p.get(0).get("trendKg").decimalValue()).isEqualByComparingTo("81.00");
    }

    @Test
    void defaultsToTheLastNinetyDaysAsDailyAndEchoesTheRange() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        entry(u, "2026-06-27", "80.00"); // day 90 counting today: included
        entry(u, "2026-06-26", "79.00"); // one day earlier: excluded

        JsonNode s = get(c, SERIES);
        assertThat(s.get("granularity").asText()).isEqualTo("DAILY");
        assertThat(s.get("to").asText()).isEqualTo("2026-09-24");
        assertThat(s.get("from").asText()).isEqualTo("2026-06-27");
        assertThat(s.get("points")).hasSize(1);
    }

    @Test
    void weeklySeriesBucketsByIsoWeekStartingMonday() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        entry(u, "2026-09-13", "90.00"); // Sunday: belongs to the week of 09-07
        entry(u, "2026-09-14", "88.00"); // Monday: starts the week of 09-14
        entry(u, "2026-09-16", "86.00");
        entry(u, "2026-09-20", "87.00"); // Sunday: still the week of 09-14

        JsonNode p = get(c, SERIES + "?granularity=WEEKLY&from=2026-09-01&to=2026-09-24").get("points");
        assertThat(p).hasSize(2);
        assertThat(p.get(0).get("periodStart").asText()).isEqualTo("2026-09-07");
        assertThat(p.get(0).get("entries").asInt()).isEqualTo(1);
        assertThat(p.get(1).get("periodStart").asText()).isEqualTo("2026-09-14");
        assertThat(p.get(1).get("entries").asInt()).isEqualTo(3);
        assertThat(p.get(1).get("averageKg").decimalValue()).isEqualByComparingTo("87.00");
        assertThat(p.get(1).get("minKg").decimalValue()).isEqualByComparingTo("86.00");
        assertThat(p.get(1).get("maxKg").decimalValue()).isEqualByComparingTo("88.00");
    }

    @Test
    void monthlySeriesBucketsByCalendarMonth() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        entry(u, "2026-08-31", "90.00");
        entry(u, "2026-09-01", "88.00");
        entry(u, "2026-09-15", "86.00");

        JsonNode p = get(c, SERIES + "?granularity=MONTHLY&from=2026-08-01&to=2026-09-24").get("points");
        assertThat(p).hasSize(2);
        assertThat(p.get(0).get("periodStart").asText()).isEqualTo("2026-08-01");
        assertThat(p.get(1).get("periodStart").asText()).isEqualTo("2026-09-01");
        assertThat(p.get(1).get("averageKg").decimalValue()).isEqualByComparingTo("87.00");
        assertThat(p.get(1).get("entries").asInt()).isEqualTo(2);
    }

    @Test
    void seriesRejectsABackwardsRangeAndAnUnknownGranularityAndIsEmptyWithNoData() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, c.get(SERIES + "?from=2026-09-10&to=2026-09-01"), 400, "INVALID_RANGE");
        expect(c, c.get(SERIES + "?granularity=HOURLY"), 400);
        assertThat(get(c, SERIES).get("points")).isEmpty();
    }

    @Test
    void seriesOnlyIncludesTheCallersOwnEntries() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        entry(userId(a), "2026-09-20", "80.00");
        entry(userId(b), "2026-09-20", "99.00");

        JsonNode p = get(a, SERIES).get("points");
        assertThat(p).hasSize(1);
        assertThat(p.get(0).get("weightKg").decimalValue()).isEqualByComparingTo("80.00");
    }

    // ---- target -------------------------------------------------------------------------------

    @Test
    void targetLifecycle() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, c.get(TARGET), 404, "NOT_FOUND");
        expectError(c, c.delete(TARGET), 404, "NOT_FOUND");

        JsonNode set = setTarget(c, "75.5");
        assertThat(set.get("targetWeightKg").decimalValue()).isEqualByComparingTo("75.50");
        assertThat(set.get("startedOn").asText()).isEqualTo("2026-09-24");
        assertThat(get(c, TARGET).get("targetWeightKg").decimalValue()).isEqualByComparingTo("75.50");

        expect(c, c.delete(TARGET), 204);
        expectError(c, c.get(TARGET), 404, "NOT_FOUND");
    }

    @Test
    void repeatingTheSameTargetKeepsTheStartDateButANewValueRestartsTheGoal() throws Exception {
        TestClient c = newUser("a@example.com");
        setTarget(c, "75");

        clock.advance(java.time.Duration.ofDays(5));
        assertThat(setTarget(c, "75").get("startedOn").asText()).isEqualTo("2026-09-24");
        assertThat(setTarget(c, "70").get("startedOn").asText()).isEqualTo("2026-09-29");
        assertThat(jdbc.queryForObject("select count(*) from weight_targets", Integer.class)).isEqualTo(1);
    }

    @Test
    void targetValidation() throws Exception {
        TestClient c = newUser("a@example.com");
        for (Object bad : new Object[]{19.99, 500.01, 70.123}) {
            expectError(c, c.put(TARGET, Map.of("targetWeightKg", bad)), 400, "VALIDATION_FAILED");
        }
        expectError(c, c.put(TARGET, Map.of()), 400, "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from weight_targets", Integer.class)).isZero();
    }

    @Test
    void targetsArePerUser() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        setTarget(a, "70");
        expectError(b, b.get(TARGET), 404, "NOT_FOUND");
        expectError(b, b.delete(TARGET), 404, "NOT_FOUND");
        assertThat(get(a, TARGET).get("targetWeightKg").decimalValue()).isEqualByComparingTo("70");
    }

    // ---- summary ------------------------------------------------------------------------------

    @Test
    void emptySummaryHasNullPartsAndZeroCount() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode s = get(c, SUMMARY);
        for (String part : new String[]{"current", "starting", "target", "progress", "change"}) {
            assertThat(s.get(part).isNull()).as(part).isTrue();
        }
        assertThat(s.get("weekAverage").get("thisWeek").isNull()).isTrue();
        assertThat(s.get("weekAverage").get("lastWeek").isNull()).isTrue();
        assertThat(s.get("entryCount").asInt()).isZero();
    }

    @Test
    void summaryReportsCurrentChangeWindowsAndWeekAverages() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        entry(u, "2026-08-20", "92.00");
        entry(u, "2026-09-10", "90.00"); // latest is 09-24, so the 7-day baseline is on/before 09-17 -> this one
        entry(u, "2026-09-15", "89.00"); // in last week (09-14..09-20)
        entry(u, "2026-09-17", "88.00"); // last week too; baseline for 7 days
        entry(u, "2026-09-22", "87.00"); // this week
        entry(u, "2026-09-24", "86.00");

        JsonNode s = get(c, SUMMARY);
        assertThat(s.get("current").get("weightKg").decimalValue()).isEqualByComparingTo("86.00");
        assertThat(s.get("current").get("date").asText()).isEqualTo("2026-09-24");
        assertThat(s.get("entryCount").asInt()).isEqualTo(6);

        JsonNode d7 = s.get("change").get("last7Days");
        assertThat(d7.get("baselineDate").asText()).isEqualTo("2026-09-17");
        assertThat(d7.get("changeKg").decimalValue()).isEqualByComparingTo("-2.00");
        JsonNode d30 = s.get("change").get("last30Days");
        assertThat(d30.get("baselineDate").asText()).isEqualTo("2026-08-20"); // on/before 2026-08-25
        assertThat(d30.get("changeKg").decimalValue()).isEqualByComparingTo("-6.00");

        JsonNode weeks = s.get("weekAverage");
        assertThat(weeks.get("thisWeek").get("periodStart").asText()).isEqualTo("2026-09-21");
        assertThat(weeks.get("thisWeek").get("averageKg").decimalValue()).isEqualByComparingTo("86.50");
        assertThat(weeks.get("thisWeek").get("entries").asInt()).isEqualTo(2);
        assertThat(weeks.get("lastWeek").get("periodStart").asText()).isEqualTo("2026-09-14");
        assertThat(weeks.get("lastWeek").get("averageKg").decimalValue()).isEqualByComparingTo("88.50");
    }

    @Test
    void aChangeWindowIsNullWhenNoEntryIsOldEnough() throws Exception {
        TestClient c = newUser("a@example.com");
        entry(userId(c), "2026-09-22", "80.00");
        entry(userId(c), "2026-09-24", "79.00");
        JsonNode change = get(c, SUMMARY).get("change");
        assertThat(change.get("last7Days").isNull()).isTrue();
        assertThat(change.get("last30Days").isNull()).isTrue();
    }

    @Test
    void summaryProgressStartsFromTheReadingInForceWhenTheGoalBegan() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        entry(u, "2026-09-01", "95.00");
        entry(u, "2026-09-20", "90.00"); // in force on 09-24, when the goal is set
        setTarget(c, "80");
        clock.advance(java.time.Duration.ofDays(3));
        entry(u, "2026-09-27", "85.00");

        JsonNode s = get(c, SUMMARY);
        assertThat(s.get("starting").get("weightKg").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(s.get("starting").get("date").asText()).isEqualTo("2026-09-20");
        assertThat(s.get("target").get("targetWeightKg").decimalValue()).isEqualByComparingTo("80.00");
        JsonNode p = s.get("progress");
        assertThat(p.get("direction").asText()).isEqualTo("LOSE");
        assertThat(p.get("percent").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(p.get("remainingKg").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(p.get("reached").asBoolean()).isFalse();
    }

    @Test
    void withNoReadingBeforeTheGoalTheFirstReadingAfterItIsTheStart() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        setTarget(c, "70");
        entry(u, "2026-09-24", "80.00");
        assertThat(get(c, SUMMARY).get("starting").get("weightKg").decimalValue()).isEqualByComparingTo("80.00");
    }

    @Test
    void aTargetWithoutEntriesOrEntriesWithoutATargetHaveNoProgress() throws Exception {
        TestClient c = newUser("a@example.com");
        setTarget(c, "70");
        JsonNode s = get(c, SUMMARY);
        assertThat(s.get("target").get("targetWeightKg").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(s.get("progress").isNull()).isTrue();
        assertThat(s.get("starting").isNull()).isTrue();

        expect(c, c.delete(TARGET), 204);
        entry(userId(c), "2026-09-24", "80.00");
        s = get(c, SUMMARY);
        assertThat(s.get("target").isNull()).isTrue();
        assertThat(s.get("progress").isNull()).isTrue();
        assertThat(s.get("current").get("weightKg").decimalValue()).isEqualByComparingTo("80.00");
    }

    @Test
    void maintainingHasANullPercentage() throws Exception {
        TestClient c = newUser("a@example.com");
        entry(userId(c), "2026-09-24", "75.00");
        setTarget(c, "75");
        JsonNode p = get(c, SUMMARY).get("progress");
        assertThat(p.get("direction").asText()).isEqualTo("MAINTAIN");
        assertThat(p.get("percent").isNull()).isTrue();
        assertThat(p.get("reached").asBoolean()).isTrue();
    }

    @Test
    void editingAnOldEntryCanFlipTheDirectionOfTheGoal() throws Exception {
        TestClient c = newUser("a@example.com");
        String old = "/api/v1/weight-entries/2026-09-10";
        expect(c, c.put(old, Map.of("weightKg", new BigDecimal("90"))), 201);
        setTarget(c, "80");                       // the goal begins on 09-24, when the reading in force is 90
        clock.advance(java.time.Duration.ofDays(3));
        String latest = "/api/v1/weight-entries/2026-09-27";
        expect(c, c.put(latest, Map.of("weightKg", new BigDecimal("85"))), 201);

        JsonNode before = get(c, SUMMARY).get("progress");
        assertThat(before.get("direction").asText()).isEqualTo("LOSE");
        assertThat(before.get("percent").decimalValue()).isEqualByComparingTo("50.0");

        // The starting reading was mistyped: it was really 70, so 80 is now a gain and 85 is already past it.
        expect(c, c.put(old, Map.of("weightKg", new BigDecimal("70"))), 200);
        JsonNode after = get(c, SUMMARY);
        assertThat(after.get("starting").get("weightKg").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(after.get("progress").get("direction").asText()).isEqualTo("GAIN");
        assertThat(after.get("progress").get("reached").asBoolean()).isTrue();
        assertThat(after.get("progress").get("percent").decimalValue()).isEqualByComparingTo("100.0");

        // And deleting the latest entry moves "current" back to the previous reading.
        expect(c, c.delete(latest), 204);
        JsonNode last = get(c, SUMMARY).get("progress");
        assertThat(last.get("direction").asText()).isEqualTo("GAIN");
        assertThat(last.get("reached").asBoolean()).isFalse();
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        for (String path : new String[]{SERIES, SUMMARY, TARGET}) {
            assertThat(anon.get(path).getResponse().getStatus()).as(path).isEqualTo(401);
        }
    }
}
