package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-step writes are all or nothing. Each test installs a temporary database trigger that makes one chosen
 * step fail for real, lets the earlier steps run, and then checks that those earlier steps were rolled back.
 */
class TransactionBehaviorTest extends FitnessApiTest {

    private static final String TEMPLATES = "/api/v1/workout-templates";

    /** Makes an INSERT/UPDATE on {@code table} fail when {@code condition} matches the new row. */
    private void failWhen(String name, String timing, String table, String condition) {
        jdbc.execute("create or replace function test_fail() returns trigger language plpgsql as "
                + "$$ begin raise exception 'injected failure' using errcode = 'XX000'; end $$");
        jdbc.execute("create trigger " + name + " before " + timing + " on " + table
                + " for each row when (" + condition + ") execute function test_fail()");
    }

    @AfterEach
    void removeInjectedFailures() {
        jdbc.execute("drop trigger if exists fail_we_insert on workout_exercises");
        jdbc.execute("drop trigger if exists fail_tpl_insert on workout_template_exercises");
        jdbc.execute("drop trigger if exists fail_workout_complete on workouts");
        jdbc.execute("drop trigger if exists fail_set_insert on workout_sets");
        jdbc.execute("drop function if exists test_fail()");
    }

    private void removeFailure(String trigger, String table) {
        jdbc.execute("drop trigger if exists " + trigger + " on " + table);
    }

    @Test
    void aFailureWhileCopyingTemplateExercisesLeavesNoWorkoutBehind() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        failWhen("fail_we_insert", "insert", "workout_exercises", "new.position = 3");   // the third copy fails

        JsonNode error = expect(client, client.post(WORKOUTS, Map.of("templateId", builtInTemplate("Push").toString())), 500);

        assertThat(error.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.toString()).doesNotContain("injected"); // internals are not leaked
        assertThat(jdbc.queryForObject("select count(*) from workouts where user_id = ?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from workout_exercises", Integer.class)).isZero();

        // Nothing is stuck: once the fault is gone the user can start normally.
        removeFailure("fail_we_insert", "workout_exercises");
        assertThat(startFromTemplate(client, builtInTemplate("Push")).get("exercises")).hasSize(6);
    }

    @Test
    void aFailedTemplateReplaceRestoresTheOriginalExercises() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        UUID squat = builtInExercise("Barbell Back Squat");
        UUID deadlift = builtInExercise("Deadlift");
        UUID row = builtInExercise("Barbell Row");
        UUID template = UUID.fromString(expect(client, client.post(TEMPLATES, Map.of("name", "Original", "exercises", List.of(
                Map.of("exerciseId", bench.toString()), Map.of("exerciseId", squat.toString())))), 201).get("id").asText());

        // The old rows are deleted first, then the new ones are inserted; the second insert fails.
        failWhen("fail_tpl_insert", "insert", "workout_template_exercises", "new.position = 2");
        expect(client, client.put(TEMPLATES + "/" + template, Map.of("name", "Changed", "exercises", List.of(
                Map.of("exerciseId", deadlift.toString()), Map.of("exerciseId", row.toString()),
                Map.of("exerciseId", squat.toString())))), 500);

        assertThat(jdbc.queryForObject("select name from workout_templates where id = ?", String.class, template)).isEqualTo("Original");
        List<String> rows = jdbc.queryForList("select e.name from workout_template_exercises x join exercises e on e.id = x.exercise_id "
                + "where x.template_id = ? order by x.position", String.class, template);
        assertThat(rows).containsExactly("Barbell Bench Press", "Barbell Back Squat");

        // The template still works and can be replaced once the fault is gone.
        removeFailure("fail_tpl_insert", "workout_template_exercises");
        JsonNode replaced = expect(client, client.put(TEMPLATES + "/" + template, Map.of("name", "Changed", "exercises", List.of(
                Map.of("exerciseId", deadlift.toString())))), 200);
        assertThat(replaced.get("exercises")).hasSize(1);
    }

    @Test
    void aFailureAfterDroppingEmptyExercisesRollsTheDropBackAndKeepsTheWorkoutActive() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode started = startFromTemplate(client, builtInTemplate("Push"));
        UUID id = UUID.fromString(started.get("id").asText());
        logSet(client, id.toString(), started.get("exercises").get(0).get("id").asText(), "80", 5);

        // finish() deletes the five empty exercises, renumbers, and only then marks the workout completed.
        failWhen("fail_workout_complete", "update", "workouts", "new.status = 'COMPLETED'");
        expect(client, client.post(WORKOUTS + "/" + id + "/finish", Map.of()), 500);

        assertThat(jdbc.queryForObject("select status from workouts where id = ?", String.class, id)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select finished_at is null from workouts where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from workout_exercises where workout_id = ?", Integer.class, id)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isEqualTo(1);

        // Retrying after the fault is gone finishes correctly.
        removeFailure("fail_workout_complete", "workouts");
        JsonNode finished = finishWorkout(client, id.toString());
        assertThat(finished.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.get("exercises")).hasSize(1);
    }

    @Test
    void aFailedExerciseInsertChangesNothing() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        logSet(client, id, we, "60", 8);

        failWhen("fail_we_insert", "insert", "workout_exercises", "new.position = 2");
        expect(client, client.post(WORKOUTS + "/" + id + "/exercises", Map.of("exerciseId", builtInExercise("Deadlift").toString())), 500);

        removeFailure("fail_we_insert", "workout_exercises");
        JsonNode workout = getWorkout(client, id);
        assertThat(names(workout)).containsExactly("Barbell Bench Press");
        assertThat(workout.get("exercises").get(0).get("sets")).hasSize(1);
        // The next exercise still gets position 2: the failed attempt left no gap.
        assertThat(addExercise(client, id, builtInExercise("Deadlift")).get("position").asInt()).isEqualTo(2);
    }

    @Test
    void aFailedSetInsertLeavesTheEarlierSetsAndNumberingIntact() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        logSet(client, id, we, "60", 8);

        failWhen("fail_set_insert", "insert", "workout_sets", "new.set_number = 2");
        UUID retryId = UUID.randomUUID();
        expect(client, client.post(setsPath(id, we), Map.of("id", retryId.toString(),
                "weightKg", new java.math.BigDecimal("62.5"), "reps", 6)), 500);
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isEqualTo(1);

        // A retry with the same client id, after the fault clears, succeeds as a normal create (201), not a replay.
        removeFailure("fail_set_insert", "workout_sets");
        JsonNode retried = expect(client, client.post(setsPath(id, we), Map.of("id", retryId.toString(),
                "weightKg", new java.math.BigDecimal("62.5"), "reps", 6)), 201);
        assertThat(retried.get("setNumber").asInt()).isEqualTo(2);
        assertThat(getWorkout(client, id).get("exercises").get(0).get("sets")).hasSize(2);
    }
}
