package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User B, holding valid ids that belong to user A, must get exactly what an unknown id gets: 404 NOT_FOUND.
 * Every endpoint that takes an id is exercised.
 */
class OwnershipIsolationTest extends FitnessApiTest {

    private TestClient alice;
    private TestClient bob;
    private UUID customExercise;
    private UUID template;
    private String activeWorkout;
    private String activeWorkoutExercise;
    private String activeSet;
    private String doneWorkout;
    private String doneWorkoutExercise;
    private String doneSet;

    private void setUpAlicesData() throws Exception {
        alice = newUser("alice@example.com");
        bob = newUser("bob@example.com");

        customExercise = UUID.fromString(expect(alice, alice.post("/api/v1/exercises",
                Map.of("name", "Alice Special", "primaryMuscleGroup", "BACK")), 201).get("id").asText());
        template = UUID.fromString(expect(alice, alice.post("/api/v1/workout-templates", Map.of("name", "Alice Plan",
                "exercises", List.of(Map.of("exerciseId", customExercise.toString())))), 201).get("id").asText());

        JsonNode done = startWorkout(alice);
        doneWorkout = done.get("id").asText();
        doneWorkoutExercise = addExercise(alice, doneWorkout, customExercise).get("id").asText();
        doneSet = logSet(alice, doneWorkout, doneWorkoutExercise, "50", 8).get("id").asText();
        finishWorkout(alice, doneWorkout);

        activeWorkout = startWorkout(alice).get("id").asText();
        activeWorkoutExercise = addExercise(alice, activeWorkout, builtInExercise("Deadlift")).get("id").asText();
        activeSet = logSet(alice, activeWorkout, activeWorkoutExercise, "100", 5).get("id").asText();
    }

    private void notFound(MvcResultSupplier call) throws Exception {
        expectError(bob, call.get(), 404, "NOT_FOUND");
    }

    @FunctionalInterface
    private interface MvcResultSupplier {
        org.springframework.test.web.servlet.MvcResult get() throws Exception;
    }

    private void assertAlicesDataIntact() throws Exception {
        JsonNode active = getWorkout(alice, activeWorkout);
        assertThat(active.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(active.get("exercises").get(0).get("sets")).hasSize(1);
        assertThat(active.get("exercises").get(0).get("sets").get(0).get("reps").asInt()).isEqualTo(5);
        assertThat(getWorkout(alice, doneWorkout).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select name from exercises where id = ?", String.class, customExercise)).isEqualTo("Alice Special");
        assertThat(jdbc.queryForObject("select name from workout_templates where id = ?", String.class, template)).isEqualTo("Alice Plan");
    }

    // ---- workouts ----------------------------------------------------------------------------

    @Test
    void bobCannotReadOrChangeAlicesWorkouts() throws Exception {
        setUpAlicesData();
        for (String id : List.of(activeWorkout, doneWorkout)) {
            notFound(() -> bob.get(WORKOUTS + "/" + id));
            notFound(() -> bob.call(HttpMethod.PATCH, WORKOUTS + "/" + id, Map.of("name", "Mine"), true));
            notFound(() -> bob.post(WORKOUTS + "/" + id + "/finish", Map.of()));
            notFound(() -> bob.delete(WORKOUTS + "/" + id));
        }
        assertAlicesDataIntact();
    }

    @Test
    void bobCannotTouchTheExercisesInAlicesWorkout() throws Exception {
        setUpAlicesData();
        String base = WORKOUTS + "/" + activeWorkout;

        notFound(() -> bob.post(base + "/exercises", Map.of("exerciseId", builtInExercise("Barbell Row").toString())));
        notFound(() -> bob.delete(base + "/exercises/" + activeWorkoutExercise));
        notFound(() -> bob.put(base + "/exercises/order", Map.of("workoutExerciseIds", List.of(activeWorkoutExercise))));
        notFound(() -> bob.call(HttpMethod.PATCH, base + "/exercises/" + activeWorkoutExercise, Map.of("notes", "x"), true));
        assertAlicesDataIntact();
        assertThat(getWorkout(alice, activeWorkout).get("exercises")).hasSize(1);
    }

    @Test
    void bobCannotLogEditOrDeleteSetsInAlicesWorkout() throws Exception {
        setUpAlicesData();
        String sets = setsPath(activeWorkout, activeWorkoutExercise);

        notFound(() -> bob.post(sets, Map.of("weightKg", new BigDecimal("1"), "reps", 1)));
        notFound(() -> bob.call(HttpMethod.PATCH, sets + "/" + activeSet, Map.of("reps", 99), true));
        notFound(() -> bob.delete(sets + "/" + activeSet));
        // Same for a completed workout: it must look absent, not "read-only".
        String doneSets = setsPath(doneWorkout, doneWorkoutExercise);
        notFound(() -> bob.post(doneSets, Map.of("weightKg", new BigDecimal("1"), "reps", 1)));
        notFound(() -> bob.call(HttpMethod.PATCH, doneSets + "/" + doneSet, Map.of("reps", 99), true));
        notFound(() -> bob.delete(doneSets + "/" + doneSet));
        assertAlicesDataIntact();
    }

    @Test
    void bobsOwnWorkoutCannotBeUsedToReachAlicesExerciseRows() throws Exception {
        setUpAlicesData();
        String bobWorkout = startWorkout(bob).get("id").asText();

        // Bob's workout id with Alice's workout-exercise / set ids.
        notFound(() -> bob.delete(WORKOUTS + "/" + bobWorkout + "/exercises/" + activeWorkoutExercise));
        notFound(() -> bob.post(setsPath(bobWorkout, activeWorkoutExercise), Map.of("weightKg", new BigDecimal("1"), "reps", 1)));
        notFound(() -> bob.call(HttpMethod.PATCH, setsPath(bobWorkout, activeWorkoutExercise) + "/" + activeSet, Map.of("reps", 1), true));
        notFound(() -> bob.delete(setsPath(bobWorkout, activeWorkoutExercise) + "/" + activeSet));
        assertAlicesDataIntact();
    }

    @Test
    void currentHistoryAndSummaryOnlyShowTheCallersData() throws Exception {
        setUpAlicesData();

        assertThat(bob.get(WORKOUTS + "/current").getResponse().getStatus()).isEqualTo(204);
        assertThat(expect(alice, alice.get(WORKOUTS + "/current"), 200).get("id").asText()).isEqualTo(activeWorkout);
        assertThat(expect(bob, bob.get(WORKOUTS), 200).get("items")).isEmpty();
        assertThat(expect(bob, bob.get(WORKOUTS + "?status=IN_PROGRESS"), 200).get("items")).isEmpty();
        assertThat(expect(alice, alice.get(WORKOUTS), 200).get("items")).hasSize(1);
        assertThat(expect(bob, bob.get("/api/v1/fitness/summary?from=2026-01-01&to=2026-12-31"), 200).get("workoutCount").asInt()).isZero();
        assertThat(expect(alice, alice.get("/api/v1/fitness/summary?from=2026-01-01&to=2026-12-31"), 200).get("workoutCount").asInt()).isEqualTo(1);
    }

    @Test
    void bobCanStartHisOwnWorkoutWhileAliceHasOneActive() throws Exception {
        setUpAlicesData();
        JsonNode bobs = startWorkout(bob);
        assertThat(bobs.get("id").asText()).isNotEqualTo(activeWorkout);
        assertAlicesDataIntact();
    }

    // ---- exercises and templates -------------------------------------------------------------

    @Test
    void bobCannotSeeOrUseAlicesCustomExercise() throws Exception {
        setUpAlicesData();
        String bobWorkout = startWorkout(bob).get("id").asText();

        notFound(() -> bob.call(HttpMethod.PATCH, "/api/v1/exercises/" + customExercise, Map.of("name", "Mine"), true));
        notFound(() -> bob.delete("/api/v1/exercises/" + customExercise));
        notFound(() -> bob.get("/api/v1/exercises/" + customExercise + "/history"));
        notFound(() -> bob.get("/api/v1/exercises/" + customExercise + "/records"));
        notFound(() -> bob.post(WORKOUTS + "/" + bobWorkout + "/exercises", Map.of("exerciseId", customExercise.toString())));
        notFound(() -> bob.post("/api/v1/workout-templates", Map.of("name", "Sneaky",
                "exercises", List.of(Map.of("exerciseId", customExercise.toString())))));
        assertThat(expect(bob, bob.get("/api/v1/exercises?q=special&size=100"), 200).get("items")).isEmpty();
        assertThat(expect(alice, alice.get("/api/v1/exercises?q=special&size=100"), 200).get("items")).hasSize(1);
        assertThat(getWorkout(bob, bobWorkout).get("exercises")).isEmpty();
        assertAlicesDataIntact();
    }

    @Test
    void bobCannotSeeOrUseAlicesTemplate() throws Exception {
        setUpAlicesData();

        notFound(() -> bob.get("/api/v1/workout-templates/" + template));
        notFound(() -> bob.put("/api/v1/workout-templates/" + template, Map.of("name", "Mine",
                "exercises", List.of(Map.of("exerciseId", builtInExercise("Deadlift").toString())))));
        notFound(() -> bob.delete("/api/v1/workout-templates/" + template));
        notFound(() -> bob.post("/api/v1/workout-templates/" + template + "/duplicate", Map.of()));
        notFound(() -> bob.post(WORKOUTS, Map.of("templateId", template.toString())));
        List<String> bobsTemplates = expect(bob, bob.get("/api/v1/workout-templates?size=100"), 200).get("items").findValuesAsText("id");
        assertThat(bobsTemplates).doesNotContain(template.toString());
        assertThat(jdbc.queryForObject("select count(*) from workouts where user_id = ?", Integer.class, userId(bob))).isZero();
        assertAlicesDataIntact();
    }

    @Test
    void bothUsersShareBuiltInsButNeverEachOthersData() throws Exception {
        setUpAlicesData();
        UUID bench = builtInExercise("Barbell Bench Press");

        assertThat(expect(alice, alice.get("/api/v1/exercises/" + bench + "/records"), 200).get("exercise").get("builtIn").asBoolean()).isTrue();
        assertThat(expect(bob, bob.get("/api/v1/exercises/" + bench + "/records"), 200).get("exercise").get("builtIn").asBoolean()).isTrue();
        assertThat(expect(bob, bob.get("/api/v1/workout-templates/" + builtInTemplate("Push")), 200).get("builtIn").asBoolean()).isTrue();
        // Alice's completed Deadlift history is invisible to Bob's exercise history.
        assertThat(expect(bob, bob.get("/api/v1/exercises/" + builtInExercise("Deadlift") + "/history"), 200).get("items")).isEmpty();
    }
}
