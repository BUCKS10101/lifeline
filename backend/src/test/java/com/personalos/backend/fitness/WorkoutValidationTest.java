package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Set validation: DTO validation on the way in, on create and on edit. */
class WorkoutValidationTest extends FitnessApiTest {

    private TestClient client;
    private String workoutId;
    private String workoutExerciseId;

    private void setUpWorkout() throws Exception {
        client = newUser("a@example.com");
        workoutId = startWorkout(client).get("id").asText();
        workoutExerciseId = addExercise(client, workoutId, builtInExercise("Barbell Bench Press")).get("id").asText();
    }

    private Map<String, Object> body(Object weight, Object reps, Object rpe) {
        Map<String, Object> body = new HashMap<>();
        if (weight != null) body.put("weightKg", weight);
        if (reps != null) body.put("reps", reps);
        if (rpe != null) body.put("rpe", rpe);
        return body;
    }

    private int setCount() {
        return jdbc.queryForObject("select count(*) from workout_sets", Integer.class);
    }

    private JsonNode createExpecting(Map<String, Object> body, int status) throws Exception {
        return expect(client, client.post(setsPath(workoutId, workoutExerciseId), body), status);
    }

    // ---- create ------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-5", "-1000", "1000.01", "5000", "99999", "1.234", "10000"})
    void createRejectsInvalidWeights(String weight) throws Exception {
        setUpWorkout();
        JsonNode error = createExpecting(body(new BigDecimal(weight), 5, null), 400);
        assertThat(error.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.get("violations").get(0).get("field").asText()).isEqualTo("weightKg");
        assertThat(setCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.01", "2.5", "62.25", "100", "1000"})
    void createAcceptsValidWeightsIncludingZero(String weight) throws Exception {
        setUpWorkout();
        createExpecting(body(new BigDecimal(weight), 5, null), 201);
        assertThat(setCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, 101, 1000})
    void createRejectsInvalidReps(int reps) throws Exception {
        setUpWorkout();
        JsonNode error = createExpecting(body(new BigDecimal("60"), reps, null), 400);
        assertThat(error.get("violations").get(0).get("field").asText()).isEqualTo("reps");
        assertThat(setCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 50, 100})
    void createAcceptsValidReps(int reps) throws Exception {
        setUpWorkout();
        createExpecting(body(new BigDecimal("60"), reps, null), 201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.5", "10.5", "11", "7.3", "7.25", "-1", "100"})
    void createRejectsInvalidRpe(String rpe) throws Exception {
        setUpWorkout();
        JsonNode error = createExpecting(body(new BigDecimal("60"), 5, new BigDecimal(rpe)), 400);
        assertThat(error.get("violations").get(0).get("field").asText()).isEqualTo("rpe");
        assertThat(setCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.5", "7", "7.5", "8.0", "9.5", "10"})
    void createAcceptsRpeInHalfSteps(String rpe) throws Exception {
        setUpWorkout();
        createExpecting(body(new BigDecimal("60"), 5, new BigDecimal(rpe)), 201);
    }

    @Test
    void createRequiresWeightAndReps() throws Exception {
        setUpWorkout();
        JsonNode noWeight = createExpecting(body(null, 5, null), 400);
        assertThat(noWeight.get("violations").get(0).get("field").asText()).isEqualTo("weightKg");
        JsonNode noReps = createExpecting(body(new BigDecimal("60"), null, null), 400);
        assertThat(noReps.get("violations").get(0).get("field").asText()).isEqualTo("reps");
        assertThat(createExpecting(Map.of(), 400).get("violations")).hasSize(2);
        assertThat(setCount()).isZero();
    }

    @Test
    void createRejectsNonNumericAndMalformedInput() throws Exception {
        setUpWorkout();
        createExpecting(body("heavy", 5, null), 400);
        createExpecting(body(new BigDecimal("60"), "many", null), 400);
        createExpecting(body(new BigDecimal("60"), 5, "hard"), 400);
        createExpecting(Map.of("weightKg", new BigDecimal("60"), "reps", 5, "id", "not-a-uuid"), 400);
        createExpecting(Map.of("weightKg", new BigDecimal("60"), "reps", 5, "warmup", "yes please"), 400);
        assertThat(setCount()).isZero();
    }

    @Test
    void createRejectsABodyThatIsNotAnObject() throws Exception {
        setUpWorkout();
        // A JSON string where an object is expected.
        var result = client.call(HttpMethod.POST, setsPath(workoutId, workoutExerciseId), "{ not json", true);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(setCount()).isZero();
    }

    @Test
    void validationErrorsUseTheStandardErrorBody() throws Exception {
        setUpWorkout();
        JsonNode error = createExpecting(body(new BigDecimal("-1"), 0, null), 400);
        assertThat(error.get("status").asInt()).isEqualTo(400);
        assertThat(error.get("path").asText()).isEqualTo(setsPath(workoutId, workoutExerciseId));
        assertThat(error.get("violations")).hasSize(2);
        assertThat(error.has("timestamp")).isTrue();
    }

    // ---- edit --------------------------------------------------------------------------------

    private String existingSetPath() throws Exception {
        String setId = logSet(client, workoutId, workoutExerciseId, "60", 8).get("id").asText();
        return setsPath(workoutId, workoutExerciseId) + "/" + setId;
    }

    private JsonNode patch(String path, Map<String, Object> body, int status) throws Exception {
        return expect(client, client.call(HttpMethod.PATCH, path, body, true), status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1000.01", "1.234", "-50"})
    void editRejectsInvalidWeights(String weight) throws Exception {
        setUpWorkout();
        String path = existingSetPath();
        patch(path, Map.of("weightKg", new BigDecimal(weight)), 400);
        assertThat(getWorkout(client, workoutId).get("exercises").get(0).get("sets").get(0).get("weightKg").decimalValue())
                .isEqualByComparingTo("60");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3, 101})
    void editRejectsInvalidReps(int reps) throws Exception {
        setUpWorkout();
        patch(existingSetPath(), Map.of("reps", reps), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "10.5", "7.3", "-2"})
    void editRejectsInvalidRpe(String rpe) throws Exception {
        setUpWorkout();
        patch(existingSetPath(), Map.of("rpe", new BigDecimal(rpe)), 400);
    }

    @Test
    void editAcceptsZeroWeightAndBoundaryValues() throws Exception {
        setUpWorkout();
        String path = existingSetPath();
        patch(path, Map.of("weightKg", new BigDecimal("0")), 200);
        patch(path, Map.of("weightKg", new BigDecimal("1000"), "reps", 100, "rpe", new BigDecimal("10")), 200);
        patch(path, Map.of("weightKg", new BigDecimal("0.01"), "reps", 1, "rpe", new BigDecimal("1")), 200);
    }

    @Test
    void invalidIdsInTheSetPathAreRejected() throws Exception {
        setUpWorkout();
        expect(client, client.post(setsPath(workoutId, "not-a-uuid"), body(new BigDecimal("60"), 5, null)), 400);
        expect(client, client.post(setsPath("not-a-uuid", workoutExerciseId), body(new BigDecimal("60"), 5, null)), 400);
        expect(client, client.call(HttpMethod.PATCH, setsPath(workoutId, workoutExerciseId) + "/not-a-uuid", Map.of("reps", 5), true), 400);
        expect(client, client.delete(setsPath(workoutId, workoutExerciseId) + "/" + "12345"), 400);
    }
}
