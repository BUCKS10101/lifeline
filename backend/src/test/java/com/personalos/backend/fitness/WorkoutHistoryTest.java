package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Workout history, the fitness summary, and how the user's timezone decides which day a workout belongs to. */
class WorkoutHistoryTest extends FitnessApiTest {

    private static final String SUMMARY = "/api/v1/fitness/summary";

    /** Completes a one-exercise workout that starts at the given instant. */
    private JsonNode completeAt(TestClient client, String instant, UUID exercise, Object... weightRepsPairs) throws Exception {
        clock.set(Instant.parse(instant));
        return completedWorkout(client, exercise, weightRepsPairs);
    }

    // ---- history list ------------------------------------------------------------------------

    @Test
    void listsOnlyCompletedWorkoutsNewestFirstWithTotals() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        UUID squat = builtInExercise("Barbell Back Squat");
        JsonNode oldest = completeAt(client, "2026-09-01T10:00:00Z", bench, "60", 8);
        JsonNode middle = completeAt(client, "2026-09-05T10:00:00Z", squat, "100", 5, "100", 5);
        // The newest has a warm-up, which counts as a set but not toward volume.
        clock.set(Instant.parse("2026-09-10T10:00:00Z"));
        String id = startWorkout(client).get("id").asText();
        String we = addExercise(client, id, bench).get("id").asText();
        logWarmup(client, id, we, "40", 10);
        logSet(client, id, we, "60", 8);
        clock.advance(java.time.Duration.ofMinutes(50));
        finishWorkout(client, id);
        // An active workout must not appear in the default list.
        clock.set(Instant.parse("2026-09-12T10:00:00Z"));
        startWorkout(client);

        JsonNode page = expect(client, client.get(WORKOUTS), 200);

        assertThat(page.get("totalItems").asInt()).isEqualTo(3);
        assertThat(page.get("items").findValuesAsText("id")).containsExactly(id, middle.get("id").asText(), oldest.get("id").asText());
        JsonNode newest = page.get("items").get(0);
        assertThat(newest.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(newest.get("performedOn").asText()).isEqualTo("2026-09-10");
        assertThat(newest.get("exerciseCount").asInt()).isEqualTo(1);
        assertThat(newest.get("setCount").asInt()).isEqualTo(2);
        assertThat(newest.get("volumeKg").decimalValue()).isEqualByComparingTo("480");
        assertThat(newest.get("durationMinutes").asLong()).isEqualTo(50);
        JsonNode squatItem = page.get("items").get(1);
        assertThat(squatItem.get("volumeKg").decimalValue()).isEqualByComparingTo("1000");
        assertThat(squatItem.get("setCount").asInt()).isEqualTo(2);
    }

    @Test
    void canListTheActiveWorkoutByStatusAndRejectsUnknownStatuses() throws Exception {
        TestClient client = newUser("a@example.com");
        completeAt(client, "2026-09-01T10:00:00Z", builtInExercise("Deadlift"), "100", 5);
        String active = startWorkout(client).get("id").asText();

        JsonNode inProgress = expect(client, client.get(WORKOUTS + "?status=IN_PROGRESS"), 200);

        assertThat(inProgress.get("items").findValuesAsText("id")).containsExactly(active);
        assertThat(inProgress.get("items").get(0).get("finishedAt").isNull()).isTrue();
        assertThat(inProgress.get("items").get(0).get("durationMinutes").isNull()).isTrue();
        expect(client, client.get(WORKOUTS + "?status=ABANDONED"), 400);
    }

    @Test
    void filtersByInclusiveDateRange() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID deadlift = builtInExercise("Deadlift");
        completeAt(client, "2026-09-01T10:00:00Z", deadlift, "100", 5);
        completeAt(client, "2026-09-10T10:00:00Z", deadlift, "100", 5);
        completeAt(client, "2026-09-20T10:00:00Z", deadlift, "100", 5);

        assertThat(dates(client, "?from=2026-09-10&to=2026-09-10")).containsExactly("2026-09-10");
        assertThat(dates(client, "?from=2026-09-01&to=2026-09-10")).containsExactly("2026-09-10", "2026-09-01");
        assertThat(dates(client, "?from=2026-09-11")).containsExactly("2026-09-20");
        assertThat(dates(client, "?to=2026-09-09")).containsExactly("2026-09-01");
        assertThat(dates(client, "?from=2026-10-01&to=2026-10-31")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"from=2026-09-10&to=2026-09-01", "from=yesterday", "to=2026-13-45", "from=09/10/2026"})
    void rejectsInvalidDateFilters(String query) throws Exception {
        TestClient client = newUser("a@example.com");
        expect(client, client.get(WORKOUTS + "?" + query), 400);
    }

    @Test
    void sortsNewestFirstByDefaultAndSupportsWhitelistedSorts() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID deadlift = builtInExercise("Deadlift");
        completeAt(client, "2026-09-01T10:00:00Z", deadlift, "100", 5);
        completeAt(client, "2026-09-02T10:00:00Z", deadlift, "100", 5);
        completeAt(client, "2026-09-03T10:00:00Z", deadlift, "100", 5);

        assertThat(dates(client, "")).containsExactly("2026-09-03", "2026-09-02", "2026-09-01");
        assertThat(dates(client, "?sort=performedOn,asc")).containsExactly("2026-09-01", "2026-09-02", "2026-09-03");
        assertThat(dates(client, "?sort=performedOn,desc")).containsExactly("2026-09-03", "2026-09-02", "2026-09-01");
        assertThat(dates(client, "?sort=startedAt,asc")).containsExactly("2026-09-01", "2026-09-02", "2026-09-03");
    }

    @ParameterizedTest
    @ValueSource(strings = {"name,asc", "performedOn,sideways", "performedOn,asc,extra", "password", ",asc"})
    void rejectsSortsThatAreNotWhitelisted(String sort) throws Exception {
        TestClient client = newUser("a@example.com");
        expectError(client, client.get(WORKOUTS + "?sort=" + sort), 400, "INVALID_SORT");
    }

    @Test
    void pagesAndValidatesPageParameters() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID deadlift = builtInExercise("Deadlift");
        for (int day = 1; day <= 5; day++) completeAt(client, "2026-09-0" + day + "T10:00:00Z", deadlift, "100", 5);

        JsonNode page = expect(client, client.get(WORKOUTS + "?size=2&page=1"), 200);

        assertThat(page.get("items").findValuesAsText("performedOn")).containsExactly("2026-09-03", "2026-09-02");
        assertThat(page.get("totalItems").asInt()).isEqualTo(5);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        expect(client, client.get(WORKOUTS + "?size=0"), 400);
        expect(client, client.get(WORKOUTS + "?size=101"), 400);
        expect(client, client.get(WORKOUTS + "?page=-1"), 400);
    }

    @Test
    void historyOnlyShowsTheCallersWorkouts() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        completeAt(alice, "2026-09-01T10:00:00Z", builtInExercise("Deadlift"), "100", 5);

        assertThat(expect(alice, alice.get(WORKOUTS), 200).get("items")).hasSize(1);
        assertThat(expect(bob, bob.get(WORKOUTS), 200).get("items")).isEmpty();
    }

    private List<String> dates(TestClient client, String query) throws Exception {
        return expect(client, client.get(WORKOUTS + query), 200).get("items").findValuesAsText("performedOn");
    }

    // ---- timezone ----------------------------------------------------------------------------

    @Test
    void aWorkoutBelongsToTheDayInTheUsersTimezoneNotTheServers() throws Exception {
        TestClient utc = newUser("utc@example.com", "UTC");
        TestClient kiritimati = newUser("kiri@example.com", "Pacific/Kiritimati");   // UTC+14
        TestClient losAngeles = newUser("la@example.com", "America/Los_Angeles");     // UTC-7 in September

        clock.set(Instant.parse("2026-09-24T20:00:00Z"));
        assertThat(startWorkout(utc).get("performedOn").asText()).isEqualTo("2026-09-24");
        assertThat(startWorkout(kiritimati).get("performedOn").asText()).isEqualTo("2026-09-25");
        assertThat(startWorkout(losAngeles).get("performedOn").asText()).isEqualTo("2026-09-24");

        expect(losAngeles, losAngeles.delete(WORKOUTS + "/" + expect(losAngeles, losAngeles.get(WORKOUTS + "/current"), 200).get("id").asText()), 204);
        clock.set(Instant.parse("2026-09-24T03:00:00Z"));
        assertThat(startWorkout(losAngeles).get("performedOn").asText()).isEqualTo("2026-09-23");
    }

    @Test
    void changingTimezoneLaterDoesNotMoveExistingWorkouts() throws Exception {
        TestClient client = newUser("a@example.com", "UTC");
        clock.set(Instant.parse("2026-09-24T20:00:00Z"));
        JsonNode workout = completedWorkout(client, builtInExercise("Deadlift"), "100", 5);
        assertThat(workout.get("performedOn").asText()).isEqualTo("2026-09-24");

        expect(client, client.call(HttpMethod.PATCH, "/api/v1/profile", Map.of("timezone", "Pacific/Kiritimati"), true), 200);

        assertThat(getWorkout(client, workout.get("id").asText()).get("performedOn").asText()).isEqualTo("2026-09-24");
        assertThat(dates(client, "?from=2026-09-24&to=2026-09-24")).containsExactly("2026-09-24");
        // New workouts use the new timezone.
        clock.set(Instant.parse("2026-09-25T20:00:00Z"));
        assertThat(startWorkout(client).get("performedOn").asText()).isEqualTo("2026-09-26");
    }

    @Test
    void dateFiltersUseTheWorkoutsLocalDate() throws Exception {
        TestClient losAngeles = newUser("la@example.com", "America/Los_Angeles");
        // 03:00 UTC on the 24th is still the evening of the 23rd in Los Angeles.
        completeAt(losAngeles, "2026-09-24T03:00:00Z", builtInExercise("Deadlift"), "100", 5);

        assertThat(dates(losAngeles, "?from=2026-09-23&to=2026-09-23")).containsExactly("2026-09-23");
        assertThat(dates(losAngeles, "?from=2026-09-24&to=2026-09-24")).isEmpty();
    }

    // ---- summary -----------------------------------------------------------------------------

    private void fixture(UUID user, String date, UUID exercise, SetSpec... sets) {
        LocalDate day = LocalDate.parse(date);
        addExerciseWithSets(insertCompletedWorkout(user, day, day.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC)), exercise, 1, sets);
    }

    @Test
    void summarizesTheCurrentMondayToSundayWeekByDefault() throws Exception {
        TestClient client = newUser("a@example.com"); // clock: Thursday 2026-09-24
        UUID user = userId(client);
        UUID bench = builtInExercise("Barbell Bench Press"); // CHEST
        UUID row = builtInExercise("Barbell Row");            // BACK
        fixture(user, "2026-09-20", bench, s("100", 10));                        // Sunday before: outside
        fixture(user, "2026-09-21", bench, w("40", 10), s("60", 8), s("60", 6)); // Monday: inside
        fixture(user, "2026-09-27", row, s("80", 5));                            // Sunday: inside
        fixture(user, "2026-09-28", bench, s("100", 10));                        // Monday after: outside
        insertInProgressWorkout(user, LocalDate.of(2026, 9, 24), Instant.parse("2026-09-24T09:00:00Z"));

        JsonNode summary = expect(client, client.get(SUMMARY), 200);

        assertThat(summary.get("from").asText()).isEqualTo("2026-09-21");
        assertThat(summary.get("to").asText()).isEqualTo("2026-09-27");
        assertThat(summary.get("workoutCount").asInt()).isEqualTo(2);
        assertThat(summary.get("totalSets").asInt()).isEqualTo(3);                       // warm-up not counted
        assertThat(summary.get("totalVolumeKg").decimalValue()).isEqualByComparingTo("1240"); // 480 + 360 + 400
        assertThat(summary.get("totalDurationMinutes").asLong()).isEqualTo(120);
        JsonNode groups = summary.get("volumeByMuscleGroup");
        assertThat(groups.findValuesAsText("muscleGroup")).containsExactly("CHEST", "BACK");
        assertThat(groups.get(0).get("volumeKg").decimalValue()).isEqualByComparingTo("840");
        assertThat(groups.get(0).get("sets").asInt()).isEqualTo(2);
        assertThat(groups.get(1).get("volumeKg").decimalValue()).isEqualByComparingTo("400");
    }

    @Test
    void theDefaultWeekFollowsTheUsersTimezone() throws Exception {
        clock.set(Instant.parse("2026-09-27T12:00:00Z")); // Sunday in UTC, already Monday 02:00 in Kiritimati
        TestClient utc = newUser("utc@example.com", "UTC");
        TestClient kiritimati = newUser("kiri@example.com", "Pacific/Kiritimati");

        JsonNode utcWeek = expect(utc, utc.get(SUMMARY), 200);
        JsonNode kiriWeek = expect(kiritimati, kiritimati.get(SUMMARY), 200);

        assertThat(utcWeek.get("from").asText()).isEqualTo("2026-09-21");
        assertThat(utcWeek.get("to").asText()).isEqualTo("2026-09-27");
        assertThat(kiriWeek.get("from").asText()).isEqualTo("2026-09-28");
        assertThat(kiriWeek.get("to").asText()).isEqualTo("2026-10-04");
    }

    @Test
    void anExplicitRangeIsInclusiveAndAnOpenEndIsSevenDays() throws Exception {
        TestClient client = newUser("a@example.com");
        UUID user = userId(client);
        UUID bench = builtInExercise("Barbell Bench Press");
        fixture(user, "2026-09-01", bench, s("60", 10));
        fixture(user, "2026-09-05", bench, s("60", 10));
        fixture(user, "2026-09-08", bench, s("60", 10));

        assertThat(expect(client, client.get(SUMMARY + "?from=2026-09-01&to=2026-09-05"), 200).get("workoutCount").asInt()).isEqualTo(2);
        JsonNode fromOnly = expect(client, client.get(SUMMARY + "?from=2026-09-02"), 200);
        assertThat(fromOnly.get("to").asText()).isEqualTo("2026-09-08");
        assertThat(fromOnly.get("workoutCount").asInt()).isEqualTo(2);
        JsonNode toOnly = expect(client, client.get(SUMMARY + "?to=2026-09-05"), 200);
        assertThat(toOnly.get("from").asText()).isEqualTo("2026-08-30");
        assertThat(toOnly.get("workoutCount").asInt()).isEqualTo(2);
    }

    @Test
    void anEmptyRangeSummarizesToZeros() throws Exception {
        TestClient client = newUser("a@example.com");
        JsonNode summary = expect(client, client.get(SUMMARY), 200);
        assertThat(summary.get("workoutCount").asInt()).isZero();
        assertThat(summary.get("totalSets").asInt()).isZero();
        assertThat(summary.get("totalVolumeKg").decimalValue()).isEqualByComparingTo("0");
        assertThat(summary.get("totalDurationMinutes").asLong()).isZero();
        assertThat(summary.get("volumeByMuscleGroup")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"from=2026-09-10&to=2026-09-01", "from=2025-01-01&to=2026-12-31", "from=nope", "to=2026-02-30"})
    void rejectsInvalidSummaryRanges(String query) throws Exception {
        TestClient client = newUser("a@example.com");
        expect(client, client.get(SUMMARY + "?" + query), 400);
    }

    @Test
    void aFullYearIsTheLargestAllowedRange() throws Exception {
        TestClient client = newUser("a@example.com");
        expect(client, client.get(SUMMARY + "?from=2026-01-01&to=2026-12-31"), 200);
        expectError(client, client.get(SUMMARY + "?from=2026-01-01&to=2027-01-02"), 400, "INVALID_RANGE");
    }

    @Test
    void theSummaryNeverIncludesAnotherUsersWorkouts() throws Exception {
        TestClient alice = newUser("alice@example.com");
        TestClient bob = newUser("bob@example.com");
        fixture(userId(alice), "2026-09-22", builtInExercise("Barbell Bench Press"), s("60", 10));

        assertThat(expect(alice, alice.get(SUMMARY), 200).get("workoutCount").asInt()).isEqualTo(1);
        assertThat(expect(bob, bob.get(SUMMARY), 200).get("workoutCount").asInt()).isZero();
    }
}
