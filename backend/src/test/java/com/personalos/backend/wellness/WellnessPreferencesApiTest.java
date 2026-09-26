package com.personalos.backend.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WellnessPreferencesApiTest extends ApiTestBase {

    private static final String PREFS = "/api/v1/wellness/preferences";

    private static Map<String, Object> body(boolean sleep, boolean water, boolean protein, Integer waterGoal, Integer proteinGoal, Integer sleepGoal) {
        Map<String, Object> m = new HashMap<>();
        m.put("sleepEnabled", sleep);
        m.put("waterEnabled", water);
        m.put("proteinEnabled", protein);
        if (waterGoal != null) m.put("waterGoalMl", waterGoal);
        if (proteinGoal != null) m.put("proteinGoalG", proteinGoal);
        if (sleepGoal != null) m.put("sleepGoalMinutes", sleepGoal);
        return m;
    }

    @Test
    void aPersonWhoNeverSavedHasEverythingShownAndNoGoals() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode prefs = expect(client, client.get(PREFS), 200);
        assertThat(prefs.get("sleepEnabled").asBoolean()).isTrue();
        assertThat(prefs.get("waterEnabled").asBoolean()).isTrue();
        assertThat(prefs.get("proteinEnabled").asBoolean()).isTrue();
        assertThat(prefs.get("waterGoalMl").isNull()).isTrue();
        assertThat(prefs.get("proteinGoalG").isNull()).isTrue();
        assertThat(prefs.get("sleepGoalMinutes").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from wellness_preferences", Integer.class)).isZero(); // reading creates nothing
    }

    @Test
    void savingReplacesTheWholeThingAndAnOmittedGoalIsCleared() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode saved = expect(client, client.put(PREFS, body(true, false, true, 2500, 140, 480)), 200);
        assertThat(saved.get("waterEnabled").asBoolean()).isFalse();
        assertThat(saved.get("waterGoalMl").asInt()).isEqualTo(2500);
        assertThat(saved.get("proteinGoalG").asInt()).isEqualTo(140);
        assertThat(saved.get("sleepGoalMinutes").asInt()).isEqualTo(480);
        assertThat(expect(client, client.get(PREFS), 200)).isEqualTo(saved);

        JsonNode cleared = expect(client, client.put(PREFS, body(false, true, true, null, 150, null)), 200);
        assertThat(cleared.get("sleepEnabled").asBoolean()).isFalse();
        assertThat(cleared.get("waterGoalMl").isNull()).isTrue();
        assertThat(cleared.get("sleepGoalMinutes").isNull()).isTrue();
        assertThat(cleared.get("proteinGoalG").asInt()).isEqualTo(150);
        assertThat(jdbc.queryForObject("select count(*) from wellness_preferences", Integer.class)).isEqualTo(1);
    }

    @Test
    void savingTheSameThingTwiceIsIdempotent() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode first = expect(client, client.put(PREFS, body(true, true, true, 2000, null, null)), 200);
        JsonNode second = expect(client, client.put(PREFS, body(true, true, true, 2000, null, null)), 200);
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from wellness_preferences", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"250, 10, 240", "10000, 500, 960", "2500, 140, 480"})
    void goalsAtTheirBoundsAreAccepted(int water, int protein, int sleep) throws Exception {
        TestClient client = newUser("a@example.com");
        expect(client, client.put(PREFS, body(true, true, true, water, protein, sleep)), 200);
    }

    @ParameterizedTest
    @CsvSource({"249, 140, 480, waterGoalMl", "10001, 140, 480, waterGoalMl", "2500, 9, 480, proteinGoalG",
            "2500, 501, 480, proteinGoalG", "2500, 140, 239, sleepGoalMinutes", "2500, 140, 961, sleepGoalMinutes"})
    void goalsOutsideTheirRangesAreRejectedAndNothingIsSaved(int water, int protein, int sleep, String field) throws Exception {
        TestClient client = newUser("a@example.com");
        var result = client.put(PREFS, body(true, true, true, water, protein, sleep));
        expectError(client, result, 400, "VALIDATION_FAILED");
        assertThat(client.json(result).get("violations").toString()).contains(field);
        assertThat(jdbc.queryForObject("select count(*) from wellness_preferences", Integer.class)).isZero();
    }

    @Test
    void theThreeSwitchesAreRequired() throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, client.put(PREFS, Map.of("waterGoalMl", 2000)), 400, "VALIDATION_FAILED");
        Map<String, Object> missingOne = body(true, true, true, null, null, null);
        missingOne.remove("proteinEnabled");
        expectError(client, client.put(PREFS, missingOne), 400, "VALIDATION_FAILED");
    }

    @Test
    void preferencesArePerUser() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        expect(a, a.put(PREFS, body(false, false, false, 3000, 200, 540)), 200);
        JsonNode seenByB = expect(b, b.get(PREFS), 200);
        assertThat(seenByB.get("sleepEnabled").asBoolean()).isTrue();
        assertThat(seenByB.get("waterGoalMl").isNull()).isTrue();
        expect(b, b.put(PREFS, body(true, true, false, null, null, null)), 200);
        assertThat(expect(a, a.get(PREFS), 200).get("waterGoalMl").asInt()).isEqualTo(3000);
    }

    @Test
    void authenticationAndCsrfAreRequired() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        assertThat(anon.get(PREFS).getResponse().getStatus()).isEqualTo(401);
        assertThat(anon.put(PREFS, body(true, true, true, null, null, null)).getResponse().getStatus()).isEqualTo(401);

        TestClient client = newUser("a@example.com");
        assertThat(client.call(HttpMethod.PUT, PREFS, body(true, true, true, null, null, null), false).getResponse().getStatus()).isEqualTo(403);
    }
}
