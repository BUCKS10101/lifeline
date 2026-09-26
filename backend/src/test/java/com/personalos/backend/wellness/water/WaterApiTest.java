package com.personalos.backend.wellness.water;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC unless a test moves it. */
class WaterApiTest extends ApiTestBase {

    private static final String WATER = "/api/v1/water-entries";
    private static final String PREFS = "/api/v1/wellness/preferences";
    private static final String TODAY = "2026-09-24";

    private MvcResult add(TestClient c, Object amount, String date, UUID id) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (amount != null) body.put("amountMl", amount);
        if (date != null) body.put("date", date);
        if (id != null) body.put("id", id.toString());
        return c.post(WATER, body);
    }

    private JsonNode added(TestClient c, int ml) throws Exception {
        return expect(c, add(c, ml, null, null), 201);
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from water_entries where user_id = ?", Integer.class, user);
    }

    private void goal(TestClient c, Integer ml) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("sleepEnabled", true, "waterEnabled", true, "proteinEnabled", true));
        if (ml != null) body.put("waterGoalMl", ml);
        expect(c, c.put(PREFS, body), 200);
    }

    // ---- adding ------------------------------------------------------------------------------

    @Test
    void quickAddsAndACustomAmountAreIndividualEntriesWithARunningTotal() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID user = userId(c);

        JsonNode first = added(c, 250);
        assertThat(first.get("entry").get("amountMl").asInt()).isEqualTo(250);
        assertThat(first.get("entry").get("date").asText()).isEqualTo(TODAY);
        assertThat(first.get("entry").get("id").asText()).isNotBlank();
        assertThat(first.get("entry").get("loggedAt").asText()).isEqualTo("2026-09-24T10:00:00Z");
        assertThat(first.get("totalMl").asInt()).isEqualTo(250);

        clock.advance(Duration.ofMinutes(1));
        assertThat(added(c, 500).get("totalMl").asInt()).isEqualTo(750);
        clock.advance(Duration.ofMinutes(1));
        assertThat(added(c, 330).get("totalMl").asInt()).isEqualTo(1080);
        assertThat(rows(user)).isEqualTo(3);

        JsonNode day = expect(c, c.get(WATER), 200);
        assertThat(day.get("date").asText()).isEqualTo(TODAY);
        assertThat(day.get("totalMl").asInt()).isEqualTo(1080);
        assertThat(day.get("entries")).hasSize(3);
        assertThat(day.get("entries").get(0).get("amountMl").asInt()).isEqualTo(330);   // newest first
        assertThat(day.get("entries").get(1).get("amountMl").asInt()).isEqualTo(500);
        assertThat(day.get("entries").get(2).get("amountMl").asInt()).isEqualTo(250);
    }

    @Test
    void theSameAmountTwiceIsTwoDrinksWhenNoClientIdIsSent() throws Exception {
        TestClient c = newUser("a@example.com");
        added(c, 250);
        assertThat(added(c, 250).get("totalMl").asInt()).isEqualTo(500);
        assertThat(rows(userId(c))).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 250, 500, 5000})
    void amountsInsideTheRangeAreAccepted(int ml) throws Exception {
        TestClient c = newUser("a@example.com");
        assertThat(added(c, ml).get("totalMl").asInt()).isEqualTo(ml);
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 0, -250, 5001, 100000})
    void amountsOutsideTheRangeAreRejectedAndNothingIsSaved(int ml) throws Exception {
        TestClient c = newUser("a@example.com");
        MvcResult result = add(c, ml, null, null);
        expectError(c, result, 400, "VALIDATION_FAILED");
        assertThat(c.json(result).get("violations").toString()).contains("amountMl");
        assertThat(rows(userId(c))).isZero();
    }

    @Test
    void anAmountIsRequiredAndMustBeANumber() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, add(c, null, null, null), 400, "VALIDATION_FAILED");
        expect(c, add(c, "lots", null, null), 400);
        expect(c, c.post(WATER, "not json"), 400);
        assertThat(rows(userId(c))).isZero();
    }

    // ---- dates -------------------------------------------------------------------------------

    @Test
    void todayIsTheDateInThePersonsTimezoneAndBackDatingIsAllowed() throws Exception {
        // At 10:00 UTC it is already 2026-09-25 in Kiritimati (UTC+14).
        TestClient kiritimati = newUser("k@example.com", "Pacific/Kiritimati");
        assertThat(added(kiritimati, 250).get("entry").get("date").asText()).isEqualTo("2026-09-25");

        TestClient c = newUser("a@example.com");
        JsonNode yesterday = expect(c, add(c, 400, "2026-09-23", null), 201);
        assertThat(yesterday.get("entry").get("date").asText()).isEqualTo("2026-09-23");
        assertThat(yesterday.get("totalMl").asInt()).isEqualTo(400);           // that day's total
        assertThat(expect(c, c.get(WATER), 200).get("totalMl").asInt()).isZero(); // today is untouched
        assertThat(expect(c, c.get(WATER + "?date=2026-09-23"), 200).get("totalMl").asInt()).isEqualTo(400);
    }

    @Test
    void futureDatesAndDatesBefore2000AreRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, add(c, 250, "2026-09-25", null), 400, "DATE_IN_FUTURE");
        expectError(c, add(c, 250, "1999-12-31", null), 400, "DATE_TOO_EARLY");
        expect(c, add(c, 250, "2000-01-01", null), 201);
        expect(c, add(c, 250, "not-a-date", null), 400);
        expectError(c, c.get(WATER + "?date=2026-09-25"), 400, "DATE_IN_FUTURE");
        expect(c, c.get(WATER + "?date=nope"), 400);
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    // ---- client ids: retries cannot double-count ---------------------------------------------

    @Test
    void repeatingTheSameClientIdReturnsTheExistingDrinkAndCountsItOnce() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID id = UUID.randomUUID();

        MvcResult first = add(c, 250, null, id);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        MvcResult retry = add(c, 250, null, id);
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(c.json(retry).get("entry").get("id").asText()).isEqualTo(id.toString());
        assertThat(c.json(retry).get("totalMl").asInt()).isEqualTo(250);
        assertThat(rows(userId(c))).isEqualTo(1);
    }

    @Test
    void aRetryReturnsTheFirstResultEvenIfTheRetriedRequestDiffers() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID id = UUID.randomUUID();
        add(c, 250, null, id);
        JsonNode retry = expect(c, add(c, 500, null, id), 200);
        assertThat(retry.get("entry").get("amountMl").asInt()).isEqualTo(250);
        assertThat(retry.get("totalMl").asInt()).isEqualTo(250);
    }

    @Test
    void aClientIdBelongingToSomeoneElseIsRefusedWithoutTouchingTheirEntry() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID id = UUID.randomUUID();
        add(a, 250, null, id);

        expectError(b, add(b, 999, null, id), 409, "CONFLICT");
        assertThat(rows(userId(b))).isZero();
        assertThat(jdbc.queryForObject("select amount_ml from water_entries where id = ?", Integer.class, id)).isEqualTo(250);
        assertThat(jdbc.queryForObject("select user_id from water_entries where id = ?", UUID.class, id)).isEqualTo(userId(a));
    }

    @Test
    void aMalformedClientIdIsRejected() throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, c.post(WATER, Map.of("amountMl", 250, "id", "not-a-uuid")), 400);
    }

    // ---- undo and delete ---------------------------------------------------------------------

    @Test
    void deleteRemovesTheDrinkAndTheTotalFollows() throws Exception {
        TestClient c = newUser("a@example.com");
        added(c, 250);
        clock.advance(Duration.ofMinutes(1));
        JsonNode second = added(c, 500);
        String id = second.get("entry").get("id").asText();

        expect(c, c.delete(WATER + "/" + id), 204);
        JsonNode day = expect(c, c.get(WATER), 200);
        assertThat(day.get("totalMl").asInt()).isEqualTo(250);
        assertThat(day.get("entries")).hasSize(1);
        expectError(c, c.delete(WATER + "/" + id), 404, "NOT_FOUND");
    }

    @Test
    void anotherUsersDrinkCannotBeDeletedOrSeen() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        String id = added(a, 250).get("entry").get("id").asText();

        expectError(b, b.delete(WATER + "/" + id), 404, "NOT_FOUND");
        assertThat(rows(userId(a))).isEqualTo(1);
        assertThat(expect(b, b.get(WATER), 200).get("entries")).isEmpty();
        expectError(b, b.delete(WATER + "/" + UUID.randomUUID()), 404, "NOT_FOUND");
        expect(b, b.delete(WATER + "/not-a-uuid"), 400);
    }

    // ---- day view, goal and progress ---------------------------------------------------------

    @Test
    void anEmptyDayHasZeroTotalNoEntriesAndNoProgress() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode day = expect(c, c.get(WATER), 200);
        assertThat(day.get("totalMl").asInt()).isZero();
        assertThat(day.get("entries")).isEmpty();
        assertThat(day.get("goalMl").isNull()).isTrue();
        assertThat(day.get("progressPercent").isNull()).isTrue();
        assertThat(day.get("goalReached").asBoolean()).isFalse();
    }

    @Test
    void withAGoalTheDayShowsProgressReachedAndStopsAtOneHundred() throws Exception {
        TestClient c = newUser("a@example.com");
        goal(c, 2000);
        added(c, 250); added(c, 1000);
        JsonNode day = expect(c, c.get(WATER), 200);
        assertThat(day.get("goalMl").asInt()).isEqualTo(2000);
        assertThat(day.get("progressPercent").asInt()).isEqualTo(62);
        assertThat(day.get("goalReached").asBoolean()).isFalse();

        added(c, 750);
        day = expect(c, c.get(WATER), 200);
        assertThat(day.get("totalMl").asInt()).isEqualTo(2000);
        assertThat(day.get("progressPercent").asInt()).isEqualTo(100);
        assertThat(day.get("goalReached").asBoolean()).isTrue();

        added(c, 500);
        day = expect(c, c.get(WATER), 200);
        assertThat(day.get("progressPercent").asInt()).isEqualTo(100);   // never above 100
        assertThat(day.get("totalMl").asInt()).isEqualTo(2500);          // the real total is still shown

        goal(c, null);
        assertThat(expect(c, c.get(WATER), 200).get("progressPercent").isNull()).isTrue();
    }

    @Test
    void aDaysTotalOnlyCountsThatDayAndThatPerson() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        added(a, 250);
        expect(a, add(a, 700, "2026-09-23", null), 201);
        added(b, 1000);

        assertThat(expect(a, a.get(WATER), 200).get("totalMl").asInt()).isEqualTo(250);
        assertThat(expect(a, a.get(WATER + "?date=2026-09-23"), 200).get("totalMl").asInt()).isEqualTo(700);
        assertThat(expect(b, b.get(WATER), 200).get("totalMl").asInt()).isEqualTo(1000);
    }

    @Test
    void drinksAtTheSameInstantKeepAStableOrder() throws Exception {
        TestClient c = newUser("a@example.com");
        for (int i = 0; i < 4; i++) added(c, 250 + i * 10);   // the clock does not move
        JsonNode first = expect(c, c.get(WATER), 200).get("entries");
        JsonNode second = expect(c, c.get(WATER), 200).get("entries");
        assertThat(second).isEqualTo(first);
    }

    // ---- access ------------------------------------------------------------------------------

    @Test
    void everyEndpointRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        assertThat(anon.get(WATER).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.post(WATER, Map.of("amountMl", 250)).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.delete(WATER + "/" + UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);

        TestClient c = newUser("a@example.com");
        assertThat(c.call(HttpMethod.POST, WATER, Map.of("amountMl", 250), false).getResponse().getStatus()).isEqualTo(403);
        String id = added(c, 250).get("entry").get("id").asText();
        assertThat(c.call(HttpMethod.DELETE, WATER + "/" + id, null, false).getResponse().getStatus()).isEqualTo(403);
        assertThat(rows(userId(c))).isEqualTo(1);
    }
}
