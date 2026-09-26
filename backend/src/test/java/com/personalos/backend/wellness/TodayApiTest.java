package com.personalos.backend.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it. */
class TodayApiTest extends ApiTestBase {

    private static final String TODAY = "/api/v1/wellness/today";
    private static final String PREFS = "/api/v1/wellness/preferences";

    private void prefs(TestClient c, boolean sleep, boolean water, boolean protein, Integer waterGoal, Integer proteinGoal, Integer sleepGoal) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("sleepEnabled", sleep, "waterEnabled", water, "proteinEnabled", protein));
        if (waterGoal != null) body.put("waterGoalMl", waterGoal);
        if (proteinGoal != null) body.put("proteinGoalG", proteinGoal);
        if (sleepGoal != null) body.put("sleepGoalMinutes", sleepGoal);
        expect(c, c.put(PREFS, body), 200);
    }

    private JsonNode today(TestClient c) throws Exception {
        return expect(c, c.get(TODAY), 200);
    }

    // ---- nothing logged ----------------------------------------------------------------------

    @Test
    void aNewUserSeesTodayWithEverythingVisibleAndNothingLogged() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode t = today(c);
        assertThat(t.get("date").asText()).isEqualTo("2026-09-24");
        assertThat(t.get("preferences").get("sleepEnabled").asBoolean()).isTrue();
        assertThat(t.get("preferences").get("waterGoalMl").isNull()).isTrue();

        assertThat(t.get("sleep").get("entry").isNull()).isTrue();
        assertThat(t.get("sleep").get("goalMinutes").isNull()).isTrue();
        assertThat(t.get("sleep").get("progressPercent").isNull()).isTrue();
        assertThat(t.get("water").get("totalMl").asInt()).isZero();
        assertThat(t.get("water").get("entryCount").asInt()).isZero();
        assertThat(t.get("water").get("goalReached").asBoolean()).isFalse();
        assertThat(t.get("protein").get("totalG").asInt()).isZero();
        assertThat(t.get("protein").get("progressPercent").isNull()).isTrue();
    }

    // ---- what counts as today ----------------------------------------------------------------

    @Test
    void todayShowsTheNightThatEndedTodayAndOnlyTodaysDrinksAndProtein() throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, c.put("/api/v1/sleep-entries/2026-09-24", Map.of("bedtime", "23:30", "wakeTime", "07:42")), 201);
        expect(c, c.put("/api/v1/sleep-entries/2026-09-23", Map.of("bedtime", "22:00", "wakeTime", "07:00")), 201);
        expect(c, c.post("/api/v1/water-entries", Map.of("amountMl", 250)), 201);
        expect(c, c.post("/api/v1/water-entries", Map.of("amountMl", 1000)), 201);
        expect(c, c.post("/api/v1/water-entries", Map.of("amountMl", 700, "date", "2026-09-23")), 201);
        expect(c, c.post("/api/v1/protein-entries", Map.of("grams", 25, "label", "Whey shake")), 201);
        expect(c, c.post("/api/v1/protein-entries", Map.of("grams", 57)), 201);
        expect(c, c.post("/api/v1/protein-entries", Map.of("grams", 90, "date", "2026-09-23")), 201);

        JsonNode t = today(c);
        assertThat(t.get("sleep").get("entry").get("date").asText()).isEqualTo("2026-09-24");
        assertThat(t.get("sleep").get("entry").get("durationMinutes").asInt()).isEqualTo(8 * 60 + 12);
        assertThat(t.get("sleep").get("entry").get("bedtime").asText()).isEqualTo("23:30");
        assertThat(t.get("water").get("totalMl").asInt()).isEqualTo(1250);
        assertThat(t.get("water").get("entryCount").asInt()).isEqualTo(2);
        assertThat(t.get("protein").get("totalG").asInt()).isEqualTo(82);
        assertThat(t.get("protein").get("entryCount").asInt()).isEqualTo(2);
    }

    @Test
    void yesterdaysNightIsNotShownAsTodays() throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, c.put("/api/v1/sleep-entries/2026-09-23", Map.of("bedtime", "22:00", "wakeTime", "07:00")), 201);
        assertThat(today(c).get("sleep").get("entry").isNull()).isTrue();
    }

    @Test
    void todayFollowsThePersonsTimezoneAndEarlierEntriesKeepTheirDate() throws Exception {
        // 10:00 UTC on 2026-09-24 is already 2026-09-25 in Kiritimati (UTC+14).
        TestClient c = newUser("k@example.com", "Pacific/Kiritimati");
        expect(c, c.post("/api/v1/water-entries", Map.of("amountMl", 250)), 201);     // logged as 2026-09-25
        assertThat(today(c).get("date").asText()).isEqualTo("2026-09-25");
        assertThat(today(c).get("water").get("totalMl").asInt()).isEqualTo(250);

        // Moving to UTC makes "today" 2026-09-24, so the earlier entry is no longer today's, but it is not lost or re-dated.
        expect(c, c.patch("/api/v1/profile", Map.of("timezone", "UTC")), 200);
        JsonNode t = today(c);
        assertThat(t.get("date").asText()).isEqualTo("2026-09-24");
        assertThat(t.get("water").get("totalMl").asInt()).isZero();
        assertThat(jdbc.queryForObject("select log_date::text from water_entries", String.class)).isEqualTo("2026-09-25");
    }

    // ---- preferences: visibility and goals ---------------------------------------------------

    @Test
    void hiddenMetricsAreAbsentButTheirDataIsKept() throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, c.post("/api/v1/water-entries", Map.of("amountMl", 500)), 201);
        expect(c, c.post("/api/v1/protein-entries", Map.of("grams", 30)), 201);
        expect(c, c.put("/api/v1/sleep-entries/2026-09-24", Map.of("bedtime", "23:00", "wakeTime", "07:00")), 201);

        prefs(c, false, false, true, null, null, null);
        JsonNode t = today(c);
        assertThat(t.get("sleep").isNull()).isTrue();
        assertThat(t.get("water").isNull()).isTrue();
        assertThat(t.get("protein").get("totalG").asInt()).isEqualTo(30);
        assertThat(t.get("preferences").get("sleepEnabled").asBoolean()).isFalse();   // the switches are always reported

        prefs(c, true, true, true, null, null, null);
        t = today(c);
        assertThat(t.get("sleep").get("entry").get("durationMinutes").asInt()).isEqualTo(480);
        assertThat(t.get("water").get("totalMl").asInt()).isEqualTo(500);
    }

    @Test
    void everythingHiddenStillReturnsTheDateAndPreferences() throws Exception {
        TestClient c = newUser("a@example.com");
        prefs(c, false, false, false, null, null, null);
        JsonNode t = today(c);
        assertThat(t.get("date").asText()).isEqualTo("2026-09-24");
        assertThat(t.get("sleep").isNull() && t.get("water").isNull() && t.get("protein").isNull()).isTrue();
    }

    @Test
    void goalsGiveProgressReachedAndAClampAtOneHundred() throws Exception {
        TestClient c = newUser("a@example.com");
        prefs(c, true, true, true, 2000, 140, 480);
        expect(c, c.put("/api/v1/sleep-entries/2026-09-24", Map.of("bedtime", "23:15", "wakeTime", "07:00")), 201);   // 465
        expect(c, c.post("/api/v1/water-entries", Map.of("amountMl", 1250)), 201);
        expect(c, c.post("/api/v1/protein-entries", Map.of("grams", 82)), 201);

        JsonNode t = today(c);
        assertThat(t.get("sleep").get("goalMinutes").asInt()).isEqualTo(480);
        assertThat(t.get("sleep").get("progressPercent").asInt()).isEqualTo(96);
        assertThat(t.get("sleep").get("goalReached").asBoolean()).isFalse();
        assertThat(t.get("water").get("goalMl").asInt()).isEqualTo(2000);
        assertThat(t.get("water").get("progressPercent").asInt()).isEqualTo(62);
        assertThat(t.get("protein").get("goalG").asInt()).isEqualTo(140);
        assertThat(t.get("protein").get("progressPercent").asInt()).isEqualTo(58);

        expect(c, c.post("/api/v1/water-entries", Map.of("amountMl", 1500)), 201);
        expect(c, c.post("/api/v1/protein-entries", Map.of("grams", 100)), 201);
        t = today(c);
        assertThat(t.get("water").get("totalMl").asInt()).isEqualTo(2750);
        assertThat(t.get("water").get("progressPercent").asInt()).isEqualTo(100);
        assertThat(t.get("water").get("goalReached").asBoolean()).isTrue();
        assertThat(t.get("protein").get("progressPercent").asInt()).isEqualTo(100);
        assertThat(t.get("protein").get("goalReached").asBoolean()).isTrue();
    }

    @Test
    void aSleepGoalWithNoNightLoggedHasNoProgress() throws Exception {
        TestClient c = newUser("a@example.com");
        prefs(c, true, true, true, null, null, 480);
        JsonNode sleep = today(c).get("sleep");
        assertThat(sleep.get("goalMinutes").asInt()).isEqualTo(480);
        assertThat(sleep.get("progressPercent").isNull()).isTrue();
        assertThat(sleep.get("goalReached").asBoolean()).isFalse();
    }

    // ---- ownership and access ----------------------------------------------------------------

    @Test
    void todayOnlyShowsThePersonsOwnData() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        expect(a, a.post("/api/v1/water-entries", Map.of("amountMl", 900)), 201);
        expect(a, a.post("/api/v1/protein-entries", Map.of("grams", 70)), 201);
        expect(a, a.put("/api/v1/sleep-entries/2026-09-24", Map.of("bedtime", "23:00", "wakeTime", "07:00")), 201);
        prefs(a, true, true, false, 3000, null, null);

        JsonNode seenByB = today(b);
        assertThat(seenByB.get("water").get("totalMl").asInt()).isZero();
        assertThat(seenByB.get("water").get("goalMl").isNull()).isTrue();
        assertThat(seenByB.get("protein").get("totalG").asInt()).isZero();
        assertThat(seenByB.get("sleep").get("entry").isNull()).isTrue();
        assertThat(seenByB.get("preferences").get("proteinEnabled").asBoolean()).isTrue();
    }

    @Test
    void todayRequiresAuthentication() throws Exception {
        assertThat(new TestClient(mvc, mapper).get(TODAY).getResponse().getStatus()).isEqualTo(401);
    }
}
