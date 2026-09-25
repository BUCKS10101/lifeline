package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.support.TestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fitness-specific helpers on top of {@link ApiTestBase}: seeded ids, SQL fixtures for history, workout API helpers. */
public abstract class FitnessApiTest extends ApiTestBase {

    // ---- ids of seeded data ------------------------------------------------------------------

    protected UUID builtInExercise(String name) {
        return jdbc.queryForObject("select id from exercises where owner_id is null and name = ?", UUID.class, name);
    }

    protected UUID builtInTemplate(String name) {
        return jdbc.queryForObject("select id from workout_templates where owner_id is null and name = ?", UUID.class, name);
    }

    // ---- SQL fixtures (history data without going through the workout API) -------------------

    protected UUID insertExercise(UUID owner, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exercises (id, owner_id, name, primary_muscle_group) values (?, ?, ?, 'CHEST')",
                id, owner, name);
        return id;
    }

    /** A logged set: {@code s("60", 8)} is a working set, {@code w("40", 10)} a warm-up. */
    protected record SetSpec(String weight, int reps, boolean warmup) {}

    protected static SetSpec s(String weight, int reps) { return new SetSpec(weight, reps, false); }

    protected static SetSpec w(String weight, int reps) { return new SetSpec(weight, reps, true); }

    protected UUID insertCompletedWorkout(UUID user, LocalDate performedOn, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at, finished_at) "
                        + "values (?, ?, 'Fixture', 'COMPLETED', ?, ?, ?)",
                id, user, performedOn, java.sql.Timestamp.from(startedAt), java.sql.Timestamp.from(startedAt.plusSeconds(3600)));
        return id;
    }

    protected UUID insertInProgressWorkout(UUID user, LocalDate performedOn, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into workouts (id, user_id, name, status, performed_on, started_at) "
                        + "values (?, ?, 'Fixture', 'IN_PROGRESS', ?, ?)",
                id, user, performedOn, java.sql.Timestamp.from(startedAt));
        return id;
    }

    protected UUID addExerciseWithSets(UUID workout, UUID exercise, int position, SetSpec... sets) {
        UUID we = UUID.randomUUID();
        jdbc.update("insert into workout_exercises (id, workout_id, exercise_id, position) values (?, ?, ?, ?)",
                we, workout, exercise, position);
        int number = 1;
        for (SetSpec set : sets) {
            jdbc.update("insert into workout_sets (id, workout_exercise_id, set_number, weight_kg, reps, is_warmup) "
                            + "values (?, ?, ?, ?::numeric, ?, ?)",
                    UUID.randomUUID(), we, number++, set.weight(), set.reps(), set.warmup());
        }
        return we;
    }

    // ---- workout API helpers -----------------------------------------------------------------

    protected static final String WORKOUTS = "/api/v1/workouts";

    protected JsonNode startWorkout(TestClient client) throws Exception {
        return expect(client, client.post(WORKOUTS, Map.of()), 201);
    }

    protected JsonNode startFromTemplate(TestClient client, UUID templateId) throws Exception {
        return expect(client, client.post(WORKOUTS, Map.of("templateId", templateId.toString())), 201);
    }

    protected JsonNode getWorkout(TestClient client, String workoutId) throws Exception {
        return expect(client, client.get(WORKOUTS + "/" + workoutId), 200);
    }

    protected JsonNode finishWorkout(TestClient client, String workoutId) throws Exception {
        return expect(client, client.post(WORKOUTS + "/" + workoutId + "/finish", Map.of()), 200);
    }

    /** Adds an exercise to a workout and returns the new workout-exercise entry. */
    protected JsonNode addExercise(TestClient client, String workoutId, UUID exerciseId) throws Exception {
        return expect(client, client.post(WORKOUTS + "/" + workoutId + "/exercises",
                Map.of("exerciseId", exerciseId.toString())), 201);
    }

    protected JsonNode logSet(TestClient client, String workoutId, String workoutExerciseId, String weight, int reps)
            throws Exception {
        return expect(client, client.post(setsPath(workoutId, workoutExerciseId),
                Map.of("weightKg", new BigDecimal(weight), "reps", reps)), 201);
    }

    protected JsonNode logWarmup(TestClient client, String workoutId, String workoutExerciseId, String weight, int reps)
            throws Exception {
        return expect(client, client.post(setsPath(workoutId, workoutExerciseId),
                Map.of("weightKg", new BigDecimal(weight), "reps", reps, "warmup", true)), 201);
    }

    protected static String setsPath(String workoutId, String workoutExerciseId) {
        return WORKOUTS + "/" + workoutId + "/exercises/" + workoutExerciseId + "/sets";
    }

    /** Starts an empty workout, logs the given working sets of one exercise, finishes it, and returns the detail. */
    protected JsonNode completedWorkout(TestClient client, UUID exercise, Object... weightRepsPairs) throws Exception {
        String workoutId = startWorkout(client).get("id").asText();
        String weId = addExercise(client, workoutId, exercise).get("id").asText();
        for (int i = 0; i < weightRepsPairs.length; i += 2) {
            logSet(client, workoutId, weId, weightRepsPairs[i].toString(), (Integer) weightRepsPairs[i + 1]);
        }
        return finishWorkout(client, workoutId);
    }

    protected List<String> names(JsonNode workoutDetail) {
        return workoutDetail.get("exercises").findValues("exercise").stream().map(e -> e.get("name").asText()).toList();
    }
}
