package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Personal records end to end: derived from history at read time, so they follow every edit and delete. */
class PersonalRecordsIntegrationTest extends FitnessApiTest {

    private static List<String> flags(JsonNode set) {
        List<String> result = new ArrayList<>();
        set.get("personalRecords").forEach(n -> result.add(n.asText()));
        return result;
    }

    /** The record flags of every set of the workout's first exercise, in set order. */
    private List<List<String>> setFlags(TestClient client, String workoutId) throws Exception {
        List<List<String>> result = new ArrayList<>();
        getWorkout(client, workoutId).get("exercises").get(0).get("sets").forEach(s -> result.add(flags(s)));
        return result;
    }

    @Test
    void theFirstSetEverIsABaselineWithNoRecord() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();

        JsonNode first = logSet(client, id, we, "100", 5);

        assertThat(flags(first)).isEmpty();
        assertThat(setFlags(client, id)).containsExactly(List.of());
    }

    @Test
    void newRecordsAreFlaggedLiveWhileTheWorkoutIsInProgress() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        logSet(client, id, we, "100", 5);

        JsonNode heavier = logSet(client, id, we, "105", 3);
        JsonNode tie = logSet(client, id, we, "105", 3);
        JsonNode lighter = logSet(client, id, we, "80", 5);

        // 105 x 3 is heavier than 100 x 5, but its estimated 1RM (115.5) is below 100 x 5 (116.67): WEIGHT only.
        assertThat(flags(heavier)).containsExactly("WEIGHT");
        assertThat(flags(tie)).isEmpty();       // ties are never records
        assertThat(flags(lighter)).isEmpty();
        assertThat(setFlags(client, id)).containsExactly(List.of(), List.of("WEIGHT"), List.of(), List.of());
    }

    @Test
    void repsAtWeightRecordsAppearAcrossWorkouts() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        completedWorkout(client, bench, "100", 5);
        clock.advance(Duration.ofDays(2));

        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, bench).get("id").asText();
        JsonNode moreReps = logSet(client, id, we, "100", 7);

        assertThat(flags(moreReps)).containsExactly("ESTIMATED_1RM", "REPS_AT_WEIGHT");
    }

    @Test
    void warmUpsNeverCountAsRecordsOrAsTheBaseline() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();

        JsonNode heavyWarmup = logWarmup(client, id, we, "200", 10);
        JsonNode firstWorking = logSet(client, id, we, "100", 5);
        JsonNode second = logSet(client, id, we, "102.5", 5);

        assertThat(flags(heavyWarmup)).isEmpty();
        assertThat(flags(firstWorking)).isEmpty();               // still the baseline: the warm-up did not count
        assertThat(flags(second)).contains("WEIGHT");            // beats 100, not the 200 warm-up
    }

    @Test
    void aPastWorkoutKeepsItsFlagsRelativeToWhatCameBefore() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        completedWorkout(client, bench, "100", 5);
        clock.advance(Duration.ofDays(1));
        JsonNode second = completedWorkout(client, bench, "105", 5);
        String secondId = second.get("id").asText();
        assertThat(setFlags(client, secondId).get(0)).contains("WEIGHT");

        clock.advance(Duration.ofDays(1));
        completedWorkout(client, bench, "200", 1); // a much later, much heavier lift

        assertThat(setFlags(client, secondId).get(0)).contains("WEIGHT"); // unchanged by the later workout
    }

    @Test
    void deletingAnEarlierWorkoutRecomputesLaterFlags() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        JsonNode first = completedWorkout(client, bench, "100", 5);
        clock.advance(Duration.ofDays(1));
        JsonNode second = completedWorkout(client, bench, "105", 5);
        String secondId = second.get("id").asText();
        assertThat(setFlags(client, secondId).get(0)).contains("WEIGHT");

        expect(client, client.delete(WORKOUTS + "/" + first.get("id").asText()), 204);

        // With the earlier workout gone, the 105 set is the first ever: a baseline, not a record.
        assertThat(setFlags(client, secondId).get(0)).isEmpty();
    }

    @Test
    void editingASetChangesTheFlagsOfLaterSets() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        String firstSet = logSet(client, id, we, "100", 5).get("id").asText();
        logSet(client, id, we, "105", 5);
        assertThat(setFlags(client, id).get(1)).contains("WEIGHT");
        String path = setsPath(id, we) + "/" + firstSet;

        // Raising the first set above the second removes the second's record.
        expect(client, client.call(HttpMethod.PATCH, path, Map.of("weightKg", new BigDecimal("110")), true), 200);
        assertThat(setFlags(client, id).get(1)).isEmpty();

        // Lowering it again restores it.
        expect(client, client.call(HttpMethod.PATCH, path, Map.of("weightKg", new BigDecimal("100")), true), 200);
        assertThat(setFlags(client, id).get(1)).contains("WEIGHT");

        // Turning the first set into a warm-up makes the second the baseline.
        expect(client, client.call(HttpMethod.PATCH, path, Map.of("warmup", true), true), 200);
        assertThat(setFlags(client, id).get(1)).isEmpty();
    }

    @Test
    void deletingASetChangesTheFlagsOfLaterSets() throws Exception {
        TestClient client = newUser("a@example.com");
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, builtInExercise("Barbell Bench Press")).get("id").asText();
        logSet(client, id, we, "100", 5);
        String big = logSet(client, id, we, "120", 3).get("id").asText();
        logSet(client, id, we, "110", 3);
        assertThat(setFlags(client, id).get(2)).isEmpty(); // 120 came first

        expect(client, client.delete(setsPath(id, we) + "/" + big), 204);

        assertThat(setFlags(client, id).get(1)).contains("WEIGHT"); // now 110 beats 100
    }

    @Test
    void recordsEndpointMatchesTheFlaggedSets() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        completedWorkout(client, bench, "100", 5, "90", 10);
        clock.advance(Duration.ofDays(1));
        completedWorkout(client, bench, "105", 3);

        JsonNode records = expect(client, client.get("/api/v1/exercises/" + bench + "/records"), 200);

        assertThat(records.get("heaviestWeight").get("weightKg").decimalValue()).isEqualByComparingTo("105");
        assertThat(records.get("bestEstimated1rm").get("valueKg").decimalValue()).isEqualByComparingTo("120.00"); // 90 x 10
        assertThat(records.get("bestRepsAtWeight").findValues("weightKg").stream().map(JsonNode::asInt)).containsExactly(105, 100, 90);
    }

    @Test
    void anotherUsersHistoryNeverInfluencesMyRecords() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        completedWorkout(alice, bench, "200", 5); // Alice's heavy lift must not make Bob's sets non-records

        String id = startWorkout(bob).get("id").asText();
        String we = addExercise(bob, id, bench).get("id").asText();
        logSet(bob, id, we, "100", 5);
        JsonNode heavier = logSet(bob, id, we, "105", 5);

        assertThat(flags(heavier)).contains("WEIGHT");
        assertThat(getWorkout(bob, id).get("exercises").get(0).get("lastSession").isNull()).isTrue();
    }

    @Test
    void bodyweightExercisesTrackRepRecordsAtZeroKg() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID pullUp = builtInExercise("Pull-Up");
        completedWorkout(client, pullUp, "0", 8);
        clock.advance(Duration.ofDays(1));

        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, pullUp).get("id").asText();
        JsonNode more = logSet(client, id, we, "0", 10);
        JsonNode fewer = logSet(client, id, we, "0", 9);

        assertThat(flags(more)).containsExactly("REPS_AT_WEIGHT");
        assertThat(flags(fewer)).isEmpty();
    }
}
