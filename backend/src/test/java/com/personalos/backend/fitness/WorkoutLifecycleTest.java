package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutLifecycleTest extends FitnessApiTest {

    private static final String TEMPLATES = "/api/v1/workout-templates";

    // ---- starting and resuming ---------------------------------------------------------------

    @Test
    void startsAnEmptyWorkoutWithDefaults() throws Exception {
        TestClient client = newUser("a@example.com");

        JsonNode workout = startWorkout(client);

        assertThat(workout.get("name").asText()).isEqualTo("Workout");
        assertThat(workout.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(workout.get("performedOn").asText()).isEqualTo("2026-09-24");
        assertThat(workout.get("startedAt").asText()).startsWith("2026-09-24T10:00:00");
        assertThat(workout.get("finishedAt").isNull()).isTrue();
        assertThat(workout.get("durationMinutes").isNull()).isTrue();
        assertThat(workout.get("templateId").isNull()).isTrue();
        assertThat(workout.get("exercises")).isEmpty();
        assertThat(workout.get("totals").get("sets").asInt()).isZero();
        assertThat(workout.get("totals").get("volumeKg").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void startsWithACustomNameTrimmed() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode workout = expect(client, client.post(WORKOUTS, Map.of("name", "  Leg Day! ")), 201);
        assertThat(workout.get("name").asText()).isEqualTo("Leg Day!");
    }

    @Test
    void startsFromABuiltInTemplateCopyingItsExercisesInOrderWithoutSets() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID push = builtInTemplate("Push");

        JsonNode workout = startFromTemplate(client, push);

        assertThat(workout.get("name").asText()).isEqualTo("Push");
        assertThat(workout.get("templateId").asText()).isEqualTo(push.toString());
        assertThat(names(workout)).containsExactly("Barbell Bench Press", "Incline Dumbbell Press", "Overhead Press",
                "Lateral Raise", "Triceps Pushdown", "Overhead Triceps Extension");
        assertThat(workout.get("exercises").findValues("position").stream().map(JsonNode::asInt)).containsExactly(1, 2, 3, 4, 5, 6);
        workout.get("exercises").forEach(e -> assertThat(e.get("sets")).isEmpty());
        assertThat(workout.get("totals").get("exercises").asInt()).isEqualTo(6);
        // The template itself is untouched.
        assertThat(jdbc.queryForObject("select count(*) from workout_template_exercises where template_id = ?", Integer.class, push)).isEqualTo(6);
    }

    @Test
    void startsFromPullAndLegsToo() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode pull = startFromTemplate(client, builtInTemplate("Pull"));
        assertThat(pull.get("exercises")).hasSize(7);
        expect(client, client.delete(WORKOUTS + "/" + pull.get("id").asText()), 204);
        JsonNode legs = startFromTemplate(client, builtInTemplate("Legs"));
        assertThat(legs.get("exercises")).hasSize(6);
        assertThat(names(legs).get(0)).isEqualTo("Barbell Back Squat");
    }

    @Test
    void aTemplateStartCanOverrideTheNameAndSkipsExercisesArchivedSinceTheTemplateWasMade() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID custom = insertExercise(user, "Custom Lift");
        UUID template = UUID.fromString(expect(client, client.post(TEMPLATES, Map.of("name", "Mine", "exercises", List.of(
                Map.of("exerciseId", builtInExercise("Deadlift").toString()), Map.of("exerciseId", custom.toString())))), 201)
                .get("id").asText());
        jdbc.update("update exercises set archived_at = now() where id = ?", custom);

        JsonNode workout = expect(client, client.post(WORKOUTS, Map.of("templateId", template.toString(), "name", "Renamed")), 201);

        assertThat(workout.get("name").asText()).isEqualTo("Renamed");
        assertThat(names(workout)).containsExactly("Deadlift");
    }

    @Test
    void onlyOneWorkoutCanBeActiveAtATime() throws Exception {
        TestClient client = newUser("a@example.com");
        TestClient other = newUser("b@example.com");
        String first = startWorkout(client).get("id").asText();

        expectError(client, client.post(WORKOUTS, Map.of()), 409, "WORKOUT_IN_PROGRESS");
        expectError(client, client.post(WORKOUTS, Map.of("templateId", builtInTemplate("Push").toString())), 409, "WORKOUT_IN_PROGRESS");
        assertThat(jdbc.queryForObject("select count(*) from workouts where user_id = ?", Integer.class, userId(client))).isEqualTo(1);

        startWorkout(other); // other users are independent

        // Finishing (or discarding) frees the slot again.
        String workoutExercise = addExercise(client, first, builtInExercise("Deadlift")).get("id").asText();
        logSet(client, first, workoutExercise, "100", 5);
        finishWorkout(client, first);
        String second = startWorkout(client).get("id").asText();
        expect(client, client.delete(WORKOUTS + "/" + second), 204);
        startWorkout(client);
    }

    @Test
    void currentReturnsTheActiveWorkoutOrNoContent() throws Exception {
        TestClient client = newUser("a@example.com");
        assertThat(client.get(WORKOUTS + "/current").getResponse().getStatus()).isEqualTo(204);

        String id = startWorkout(client).get("id").asText();
        JsonNode current = expect(client, client.get(WORKOUTS + "/current"), 200);
        assertThat(current.get("id").asText()).isEqualTo(id);
        assertThat(current.get("status").asText()).isEqualTo("IN_PROGRESS");

        String weId = addExercise(client, id, builtInExercise("Deadlift")).get("id").asText();
        logSet(client, id, weId, "100", 5);
        finishWorkout(client, id);
        assertThat(client.get(WORKOUTS + "/current").getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void rejectsUnknownAndMalformedTemplates() throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, client.post(WORKOUTS, Map.of("templateId", UUID.randomUUID().toString())), 404, "NOT_FOUND");
        expect(client, client.post(WORKOUTS, Map.of("templateId", "nope")), 400);
        expectError(client, client.post(WORKOUTS, Map.of("name", "   ")), 400, "VALIDATION_FAILED");
        expectError(client, client.post(WORKOUTS, Map.of("name", "x".repeat(101))), 400, "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from workouts", Integer.class)).isZero();
    }

    // ---- exercises in a workout --------------------------------------------------------------

    @Test
    void addsExercisesAppendingThemInOrder() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();

        JsonNode first = addExercise(client, id, builtInExercise("Barbell Bench Press"));
        JsonNode second = addExercise(client, id, builtInExercise("Deadlift"));

        assertThat(first.get("position").asInt()).isEqualTo(1);
        assertThat(second.get("position").asInt()).isEqualTo(2);
        assertThat(first.get("exercise").get("name").asText()).isEqualTo("Barbell Bench Press");
        assertThat(first.get("sets")).isEmpty();
        assertThat(first.get("lastSession").isNull()).isTrue();
        assertThat(names(getWorkout(client, id))).containsExactly("Barbell Bench Press", "Deadlift");
    }

    @Test
    void anExerciseCanAppearOnlyOnceAndMustBeUsable() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        String id = startWorkout(alice).get("id").asText();
        UUID bench = builtInExercise("Barbell Bench Press");
        addExercise(alice, id, bench);

        expectError(alice, alice.post(WORKOUTS + "/" + id + "/exercises", Map.of("exerciseId", bench.toString())),
                409, "DUPLICATE_WORKOUT_EXERCISE");
        expectError(alice, alice.post(WORKOUTS + "/" + id + "/exercises", Map.of("exerciseId", UUID.randomUUID().toString())),
                404, "NOT_FOUND");
        UUID bobsExercise = insertExercise(userId(bob), "Bob Only");
        expectError(alice, alice.post(WORKOUTS + "/" + id + "/exercises", Map.of("exerciseId", bobsExercise.toString())),
                404, "NOT_FOUND");
        UUID archived = insertExercise(userId(alice), "Retired");
        jdbc.update("update exercises set archived_at = now() where id = ?", archived);
        expectError(alice, alice.post(WORKOUTS + "/" + id + "/exercises", Map.of("exerciseId", archived.toString())),
                409, "EXERCISE_ARCHIVED");
        expect(alice, alice.post(WORKOUTS + "/" + id + "/exercises", Map.of()), 400);

        assertThat(getWorkout(alice, id).get("exercises")).hasSize(1);
    }

    @Test
    void aWorkoutHoldsAtMostThirtyExercises() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        List<UUID> ids = jdbc.queryForList("select id from exercises where owner_id is null limit 31", UUID.class);
        for (int i = 0; i < 30; i++) addExercise(client, id, ids.get(i));

        expectError(client, client.post(WORKOUTS + "/" + id + "/exercises", Map.of("exerciseId", ids.get(30).toString())),
                409, "LIMIT_EXCEEDED");
    }

    @Test
    void removesAnExerciseWithItsSetsAndKeepsPositionsContiguous() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String a = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String b = addExercise(client, id, builtInExercise("Deadlift")).get("id").asText();
        String c = addExercise(client, id, builtInExercise("Barbell Row")).get("id").asText();
        logSet(client, id, b, "100", 5);

        expect(client, client.delete(WORKOUTS + "/" + id + "/exercises/" + b), 204);

        JsonNode workout = getWorkout(client, id);
        assertThat(names(workout)).containsExactly("Barbell Bench Press", "Barbell Row");
        assertThat(workout.get("exercises").findValues("position").stream().map(JsonNode::asInt)).containsExactly(1, 2);
        assertThat(workout.get("exercises").get(1).get("id").asText()).isEqualTo(c);
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isZero();
        expectError(client, client.delete(WORKOUTS + "/" + id + "/exercises/" + b), 404, "NOT_FOUND");
        assertThat(a).isNotEmpty();
    }

    @Test
    void reordersExercisesAndKeepsEachOnesSets() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String a = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String b = addExercise(client, id, builtInExercise("Deadlift")).get("id").asText();
        String c = addExercise(client, id, builtInExercise("Barbell Row")).get("id").asText();
        logSet(client, id, a, "80", 8);

        JsonNode reordered = expect(client, client.put(WORKOUTS + "/" + id + "/exercises/order",
                Map.of("workoutExerciseIds", List.of(c, a, b))), 200);

        assertThat(names(reordered)).containsExactly("Barbell Row", "Barbell Bench Press", "Deadlift");
        assertThat(reordered.get("exercises").findValues("position").stream().map(JsonNode::asInt)).containsExactly(1, 2, 3);
        assertThat(reordered.get("exercises").get(1).get("sets")).hasSize(1);
        assertThat(names(getWorkout(client, id))).containsExactly("Barbell Row", "Barbell Bench Press", "Deadlift");
    }

    @Test
    void reorderRequiresExactlyTheWorkoutsExercises() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String a = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String b = addExercise(client, id, builtInExercise("Deadlift")).get("id").asText();
        String path = WORKOUTS + "/" + id + "/exercises/order";

        expectError(client, client.put(path, Map.of("workoutExerciseIds", List.of(a))), 400, "INVALID_ORDER");
        expectError(client, client.put(path, Map.of("workoutExerciseIds", List.of(a, a))), 400, "INVALID_ORDER");
        expectError(client, client.put(path, Map.of("workoutExerciseIds", List.of(a, b, UUID.randomUUID().toString()))), 400, "INVALID_ORDER");
        expectError(client, client.put(path, Map.of("workoutExerciseIds", List.of(a, UUID.randomUUID().toString()))), 400, "INVALID_ORDER");
        expect(client, client.put(path, Map.of()), 400);
        assertThat(names(getWorkout(client, id))).containsExactly("Barbell Bench Press", "Deadlift");
    }

    @Test
    void setsAndClearsExerciseNotes() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String path = WORKOUTS + "/" + id + "/exercises/" + we;

        JsonNode noted = expect(client, client.call(HttpMethod.PATCH, path, Map.of("notes", "  felt strong "), true), 200);
        assertThat(noted.get("notes").asText()).isEqualTo("felt strong");
        JsonNode cleared = expect(client, client.call(HttpMethod.PATCH, path, Map.of("notes", "  "), true), 200);
        assertThat(cleared.get("notes").isNull()).isTrue();
        expectError(client, client.call(HttpMethod.PATCH, path, Map.of("notes", "n".repeat(501)), true), 400, "VALIDATION_FAILED");
        expect(client, client.call(HttpMethod.PATCH, path, Map.of(), true), 400);
    }

    // ---- sets --------------------------------------------------------------------------------

    @Test
    void logsSetsNumberedInOrderWithRpeAndWarmupFlags() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();

        JsonNode warmup = logWarmup(client, id, we, "40", 10);
        JsonNode first = logSet(client, id, we, "60", 8);
        JsonNode withRpe = expect(client, client.post(setsPath(id, we),
                Map.of("weightKg", new BigDecimal("62.5"), "reps", 6, "rpe", new BigDecimal("8.5"))), 201);

        assertThat(warmup.get("setNumber").asInt()).isEqualTo(1);
        assertThat(warmup.get("warmup").asBoolean()).isTrue();
        assertThat(first.get("setNumber").asInt()).isEqualTo(2);
        assertThat(first.get("warmup").asBoolean()).isFalse();
        assertThat(first.get("rpe").isNull()).isTrue();
        assertThat(withRpe.get("setNumber").asInt()).isEqualTo(3);
        assertThat(withRpe.get("rpe").decimalValue()).isEqualByComparingTo("8.5");
        assertThat(withRpe.get("id").asText()).isNotEmpty();

        JsonNode workout = getWorkout(client, id);
        JsonNode totals = workout.get("totals");
        assertThat(totals.get("sets").asInt()).isEqualTo(3);
        assertThat(totals.get("workingSets").asInt()).isEqualTo(2);
        assertThat(totals.get("volumeKg").decimalValue()).isEqualByComparingTo("855"); // 60*8 + 62.5*6, warm-up excluded
    }

    @Test
    void weightOfZeroIsAllowedForBodyweightExercises() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Pull-Up")).get("id").asText();

        JsonNode set = logSet(client, id, we, "0", 10);

        assertThat(set.get("weightKg").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void editsASetPartiallyAndCanClearRpe() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String setId = expect(client, client.post(setsPath(id, we), Map.of("weightKg", new BigDecimal("60"), "reps", 8,
                "rpe", new BigDecimal("7"))), 201).get("id").asText();
        String path = setsPath(id, we) + "/" + setId;

        JsonNode heavier = expect(client, client.call(HttpMethod.PATCH, path, Map.of("weightKg", new BigDecimal("62.5")), true), 200);
        assertThat(heavier.get("weightKg").decimalValue()).isEqualByComparingTo("62.5");
        assertThat(heavier.get("reps").asInt()).isEqualTo(8);
        assertThat(heavier.get("rpe").decimalValue()).isEqualByComparingTo("7");

        JsonNode moreReps = expect(client, client.call(HttpMethod.PATCH, path, Map.of("reps", 9, "warmup", true), true), 200);
        assertThat(moreReps.get("reps").asInt()).isEqualTo(9);
        assertThat(moreReps.get("warmup").asBoolean()).isTrue();

        JsonNode retagged = expect(client, client.call(HttpMethod.PATCH, path, Map.of("rpe", new BigDecimal("9.5")), true), 200);
        assertThat(retagged.get("rpe").decimalValue()).isEqualByComparingTo("9.5");

        JsonNode cleared = expect(client, client.call(HttpMethod.PATCH, path, Map.of("clearRpe", true), true), 200);
        assertThat(cleared.get("rpe").isNull()).isTrue();
        assertThat(cleared.get("weightKg").decimalValue()).isEqualByComparingTo("62.5");
    }

    @Test
    void rejectsEmptyOrContradictorySetEdits() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String setId = logSet(client, id, we, "60", 8).get("id").asText();
        String path = setsPath(id, we) + "/" + setId;

        expectError(client, client.call(HttpMethod.PATCH, path, Map.of(), true), 400, "EMPTY_UPDATE");
        expectError(client, client.call(HttpMethod.PATCH, path, Map.of("rpe", new BigDecimal("8"), "clearRpe", true), true),
                400, "INVALID_REQUEST");
        expectError(client, client.call(HttpMethod.PATCH, setsPath(id, we) + "/" + UUID.randomUUID(),
                Map.of("reps", 5), true), 404, "NOT_FOUND");
    }

    @Test
    void deletingASetRenumbersTheRest() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String s1 = logSet(client, id, we, "60", 8).get("id").asText();
        String s2 = logSet(client, id, we, "65", 6).get("id").asText();
        String s3 = logSet(client, id, we, "70", 4).get("id").asText();

        expect(client, client.delete(setsPath(id, we) + "/" + s2), 204);

        JsonNode sets = getWorkout(client, id).get("exercises").get(0).get("sets");
        assertThat(sets.findValuesAsText("id")).containsExactly(s1, s3);
        assertThat(sets.findValues("setNumber").stream().map(JsonNode::asInt)).containsExactly(1, 2);
        expectError(client, client.delete(setsPath(id, we) + "/" + s2), 404, "NOT_FOUND");
        // A new set continues after the last one.
        assertThat(logSet(client, id, we, "72.5", 3).get("setNumber").asInt()).isEqualTo(3);
    }

    @Test
    void aRetriedSetWithTheSameClientIdReturnsTheExistingSetInsteadOfADuplicate() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        UUID clientId = UUID.randomUUID();
        Map<String, Object> body = Map.of("id", clientId.toString(), "weightKg", new BigDecimal("60"), "reps", 8);

        JsonNode created = expect(client, client.post(setsPath(id, we), body), 201);
        JsonNode replay = expect(client, client.post(setsPath(id, we), body), 200);
        // Even if the retry carries different numbers, the original set wins: it is a retry, not an edit.
        JsonNode replayChanged = expect(client, client.post(setsPath(id, we),
                Map.of("id", clientId.toString(), "weightKg", new BigDecimal("99"), "reps", 1)), 200);

        assertThat(created.get("id").asText()).isEqualTo(clientId.toString());
        assertThat(replay.get("id").asText()).isEqualTo(clientId.toString());
        assertThat(replay.get("setNumber").asInt()).isEqualTo(1);
        assertThat(replayChanged.get("weightKg").decimalValue()).isEqualByComparingTo("60");
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isEqualTo(1);
    }

    @Test
    void aClientIdAlreadyUsedElsewhereIsAConflictThatRevealsNothing() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        String aliceWorkout = startWorkout(alice).get("id").asText();
        String aliceWe = addExercise(alice, aliceWorkout, builtInExercise("Barbell Bench Press")).get("id").asText();
        String aliceSet = logSet(alice, aliceWorkout, aliceWe, "60", 8).get("id").asText();

        String bobWorkout = startWorkout(bob).get("id").asText();
        String bobWe = addExercise(bob, bobWorkout, builtInExercise("Barbell Bench Press")).get("id").asText();
        JsonNode error = expect(bob, bob.post(setsPath(bobWorkout, bobWe),
                Map.of("id", aliceSet, "weightKg", new BigDecimal("50"), "reps", 5)), 409);

        assertThat(error.get("code").asText()).isEqualTo("CONFLICT");
        assertThat(error.toString()).doesNotContain("60");
        assertThat(getWorkout(bob, bobWorkout).get("exercises").get(0).get("sets")).isEmpty();
        assertThat(getWorkout(alice, aliceWorkout).get("exercises").get(0).get("sets")).hasSize(1);

        // The same id under a different exercise of the same workout is also a conflict.
        String otherWe = addExercise(alice, aliceWorkout, builtInExercise("Deadlift")).get("id").asText();
        expectError(alice, alice.post(setsPath(aliceWorkout, otherWe),
                Map.of("id", aliceSet, "weightKg", new BigDecimal("50"), "reps", 5)), 409, "CONFLICT");
    }

    @Test
    void anExerciseHoldsAtMostFiftySets() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        for (int i = 1; i <= 50; i++) {
            jdbc.update("insert into workout_sets (id, workout_exercise_id, set_number, weight_kg, reps) values (?, ?, ?, 20, 5)",
                    UUID.randomUUID(), UUID.fromString(we), i);
        }

        expectError(client, client.post(setsPath(id, we), Map.of("weightKg", new BigDecimal("20"), "reps", 5)),
                409, "LIMIT_EXCEEDED");
    }

    @Test
    void aSetCannotBeReachedThroughTheWrongWorkoutOrExercise() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String otherWe = addExercise(client, id, builtInExercise("Deadlift")).get("id").asText();
        String setId = logSet(client, id, we, "60", 8).get("id").asText();

        expectError(client, client.call(HttpMethod.PATCH, setsPath(id, otherWe) + "/" + setId, Map.of("reps", 5), true), 404, "NOT_FOUND");
        expectError(client, client.delete(setsPath(id, otherWe) + "/" + setId), 404, "NOT_FOUND");
        expectError(client, client.post(setsPath(id, UUID.randomUUID().toString()),
                Map.of("weightKg", new BigDecimal("20"), "reps", 5)), 404, "NOT_FOUND");
        expectError(client, client.post(setsPath(UUID.randomUUID().toString(), we),
                Map.of("weightKg", new BigDecimal("20"), "reps", 5)), 404, "NOT_FOUND");
    }

    // ---- finishing ---------------------------------------------------------------------------

    @Test
    void finishingRequiresAtLeastOneSetAndChangesNothingWhenItFails() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode started = startFromTemplate(client, builtInTemplate("Push"));
        String id = started.get("id").asText();

        expectError(client, client.post(WORKOUTS + "/" + id + "/finish", Map.of()), 409, "WORKOUT_EMPTY");

        JsonNode after = getWorkout(client, id);
        assertThat(after.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(after.get("exercises")).hasSize(6); // nothing was dropped by the failed attempt
        expectError(client, client.post(WORKOUTS + "/" + UUID.randomUUID() + "/finish", Map.of()), 404, "NOT_FOUND");
    }

    @Test
    void finishingDropsExercisesWithoutSetsAndRenumbersTheRest() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode started = startFromTemplate(client, builtInTemplate("Push"));
        String id = started.get("id").asText();
        String bench = started.get("exercises").get(0).get("id").asText();
        String overhead = started.get("exercises").get(2).get("id").asText();
        logSet(client, id, bench, "80", 5);
        logSet(client, id, overhead, "50", 8);

        JsonNode finished = finishWorkout(client, id);

        assertThat(finished.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(names(finished)).containsExactly("Barbell Bench Press", "Overhead Press");
        assertThat(finished.get("exercises").findValues("position").stream().map(JsonNode::asInt)).containsExactly(1, 2);
        assertThat(finished.get("totals").get("exercises").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from workout_exercises where workout_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void finishRecordsTheTimesAndDuration() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        logSet(client, id, we, "60", 8);
        clock.advance(Duration.ofMinutes(47).plusSeconds(30));

        JsonNode finished = finishWorkout(client, id);

        assertThat(finished.get("finishedAt").asText()).startsWith("2026-09-24T10:47:30");
        assertThat(finished.get("durationMinutes").asLong()).isEqualTo(47);
    }

    @Test
    void finishingTwiceIsIdempotent() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        logSet(client, id, we, "60", 8);
        JsonNode first = finishWorkout(client, id);
        clock.advance(Duration.ofHours(2));

        JsonNode second = finishWorkout(client, id);

        assertThat(second.get("finishedAt").asText()).isEqualTo(first.get("finishedAt").asText());
        assertThat(second.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(second.get("totals").get("sets").asInt()).isEqualTo(1);
    }

    // ---- completed workouts are read-only ----------------------------------------------------

    @Test
    void aCompletedWorkoutRejectsEveryStructuralChange() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String setId = logSet(client, id, we, "60", 8).get("id").asText();
        finishWorkout(client, id);
        String base = WORKOUTS + "/" + id;

        expectError(client, client.post(base + "/exercises", Map.of("exerciseId", builtInExercise("Deadlift").toString())), 409, "WORKOUT_NOT_IN_PROGRESS");
        expectError(client, client.delete(base + "/exercises/" + we), 409, "WORKOUT_NOT_IN_PROGRESS");
        expectError(client, client.put(base + "/exercises/order", Map.of("workoutExerciseIds", List.of(we))), 409, "WORKOUT_NOT_IN_PROGRESS");
        expectError(client, client.call(HttpMethod.PATCH, base + "/exercises/" + we, Map.of("notes", "x"), true), 409, "WORKOUT_NOT_IN_PROGRESS");
        expectError(client, client.post(setsPath(id, we), Map.of("weightKg", new BigDecimal("60"), "reps", 8)), 409, "WORKOUT_NOT_IN_PROGRESS");
        expectError(client, client.call(HttpMethod.PATCH, setsPath(id, we) + "/" + setId, Map.of("reps", 9), true), 409, "WORKOUT_NOT_IN_PROGRESS");
        expectError(client, client.delete(setsPath(id, we) + "/" + setId), 409, "WORKOUT_NOT_IN_PROGRESS");

        JsonNode unchanged = getWorkout(client, id);
        assertThat(unchanged.get("exercises").get(0).get("sets")).hasSize(1);
        assertThat(unchanged.get("exercises").get(0).get("sets").get(0).get("reps").asInt()).isEqualTo(8);
    }

    @Test
    void nameAndNotesStayEditableAfterCompletion() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        logSet(client, id, we, "60", 8);
        finishWorkout(client, id);

        JsonNode renamed = expect(client, client.call(HttpMethod.PATCH, WORKOUTS + "/" + id,
                Map.of("name", "  Chest day ", "notes", "  Good session "), true), 200);
        assertThat(renamed.get("name").asText()).isEqualTo("Chest day");
        assertThat(renamed.get("notes").asText()).isEqualTo("Good session");
        assertThat(renamed.get("status").asText()).isEqualTo("COMPLETED");

        JsonNode cleared = expect(client, client.call(HttpMethod.PATCH, WORKOUTS + "/" + id, Map.of("notes", ""), true), 200);
        assertThat(cleared.get("notes").isNull()).isTrue();
        assertThat(cleared.get("name").asText()).isEqualTo("Chest day");
    }

    @Test
    void workoutEditsValidateTheirInput() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String path = WORKOUTS + "/" + id;
        expectError(client, client.call(HttpMethod.PATCH, path, Map.of(), true), 400, "EMPTY_UPDATE");
        expectError(client, client.call(HttpMethod.PATCH, path, Map.of("name", "   "), true), 400, "VALIDATION_FAILED");
        expectError(client, client.call(HttpMethod.PATCH, path, Map.of("notes", "n".repeat(1001)), true), 400, "VALIDATION_FAILED");
        assertThat(getWorkout(client, id).get("name").asText()).isEqualTo("Workout");
    }

    // ---- discarding and deleting -------------------------------------------------------------

    @Test
    void discardingAnActiveWorkoutRemovesItAndItsData() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        logSet(client, id, we, "60", 8);

        expect(client, client.delete(WORKOUTS + "/" + id), 204);

        expectError(client, client.get(WORKOUTS + "/" + id), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from workout_exercises", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isZero();
        assertThat(client.get(WORKOUTS + "/current").getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void deletingACompletedWorkoutRemovesItFromHistory() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode done = completedWorkout(client, builtInExercise("Deadlift"), "100", 5);

        expect(client, client.delete(WORKOUTS + "/" + done.get("id").asText()), 204);

        assertThat(expect(client, client.get(WORKOUTS), 200).get("items")).isEmpty();
        assertThat(expect(client, client.get("/api/v1/exercises/" + builtInExercise("Deadlift") + "/history"), 200).get("items")).isEmpty();
    }

    // ---- last session hint -------------------------------------------------------------------

    @Test
    void showsWhatYouDidLastTimeForEachExerciseIgnoringWarmUps() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");

        String first = startWorkout(client).get("id").asText();
        String firstWe = addExercise(client, first, bench).get("id").asText();
        logWarmup(client, first, firstWe, "40", 10);
        logSet(client, first, firstWe, "60", 8);
        logSet(client, first, firstWe, "62.5", 6);
        finishWorkout(client, first);
        clock.advance(Duration.ofDays(3));

        String second = startWorkout(client).get("id").asText();
        JsonNode entry = addExercise(client, second, bench);

        JsonNode last = entry.get("lastSession");
        assertThat(last.get("performedOn").asText()).isEqualTo("2026-09-24");
        assertThat(last.get("sets")).hasSize(2);
        assertThat(last.get("sets").get(0).get("weightKg").decimalValue()).isEqualByComparingTo("60");
        assertThat(last.get("sets").get(1).get("reps").asInt()).isEqualTo(6);
        assertThat(getWorkout(client, second).get("exercises").get(0).get("lastSession").get("sets")).hasSize(2);
    }

    @Test
    void theLastSessionHintUsesOnlyEarlierCompletedWorkouts() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        JsonNode older = completedWorkout(client, bench, "60", 8);
        clock.advance(Duration.ofDays(1));
        JsonNode newer = completedWorkout(client, bench, "70", 5);

        // Looking back at the older workout, the newer one is not "last time".
        assertThat(getWorkout(client, older.get("id").asText()).get("exercises").get(0).get("lastSession").isNull()).isTrue();
        JsonNode newerLast = getWorkout(client, newer.get("id").asText()).get("exercises").get(0).get("lastSession");
        assertThat(newerLast.get("sets").get(0).get("weightKg").decimalValue()).isEqualByComparingTo("60");
    }
}
