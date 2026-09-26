package com.personalos.backend.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalos.backend.support.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock is fixed at Thursday 2026-09-24 10:00 UTC. ISO weeks start on Monday (2026-09-21 is a Monday). */
class FitnessAnalyticsTest extends FitnessApiTest {

    private static final String VOLUME = "/api/v1/fitness/analytics/volume";
    private static final String GLOBAL_PRS = "/api/v1/fitness/personal-records";

    // ---- fixtures -----------------------------------------------------------------------------

    private UUID exercise(UUID owner, String name, String muscle) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exercises (id, owner_id, name, primary_muscle_group) values (?, ?, ?, ?)", id, owner, name, muscle);
        return id;
    }

    /** A completed workout on the date, started at the given hour, containing one exercise. */
    private UUID session(UUID user, String date, int hour, UUID exercise, SetSpec... sets) {
        LocalDate d = LocalDate.parse(date);
        UUID workout = insertCompletedWorkout(user, d, d.atTime(hour, 0).toInstant(java.time.ZoneOffset.UTC));
        addExerciseWithSets(workout, exercise, 1, sets);
        return workout;
    }

    private JsonNode get(TestClient c, String path) throws Exception {
        return expect(c, c.get(path), 200);
    }

    private static JsonNode group(JsonNode point, String name) {
        for (JsonNode g : point.get("byMovementGroup")) {
            if (g.get("movementGroup").asText().equals(name)) return g;
        }
        throw new AssertionError("no group " + name);
    }

    private static List<String> starts(JsonNode points) {
        List<String> out = new ArrayList<>();
        points.forEach(p -> out.add(p.get("periodStart").asText()));
        return out;
    }

    // ---- volume -------------------------------------------------------------------------------

    @Test
    void everyMuscleGroupMapsToOneOfTheFourMovementGroups() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID workout = insertCompletedWorkout(u, LocalDate.parse("2026-09-22"), Instant.parse("2026-09-22T09:00:00Z"));
        int position = 1;
        for (String muscle : List.of("CHEST", "SHOULDERS", "TRICEPS", "BACK", "BICEPS", "FOREARMS", "QUADS", "HAMSTRINGS",
                "GLUTES", "CALVES", "CORE", "FULL_BODY")) {
            addExerciseWithSets(workout, exercise(u, "Ex " + muscle, muscle), position++, s("10", 10));
        }

        JsonNode point = get(c, VOLUME + "?from=2026-09-21&to=2026-09-27").get("points").get(0);
        assertThat(point.get("workouts").asInt()).isEqualTo(1);
        assertThat(point.get("workingSets").asInt()).isEqualTo(12);
        assertThat(point.get("volumeKg").decimalValue()).isEqualByComparingTo("1200");
        assertThat(point.get("byMovementGroup")).hasSize(4);
        assertThat(group(point, "PUSH").get("volumeKg").decimalValue()).isEqualByComparingTo("300");
        assertThat(group(point, "PUSH").get("sets").asInt()).isEqualTo(3);
        assertThat(group(point, "PULL").get("volumeKg").decimalValue()).isEqualByComparingTo("300");
        assertThat(group(point, "LEGS").get("volumeKg").decimalValue()).isEqualByComparingTo("400");
        assertThat(group(point, "LEGS").get("sets").asInt()).isEqualTo(4);
        assertThat(group(point, "CORE_FULL_BODY").get("volumeKg").decimalValue()).isEqualByComparingTo("200");
    }

    @Test
    void weeklyBucketsStartOnMondayAndRespectTheRangeBoundaries() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID chest = exercise(u, "Chest ex", "CHEST");
        UUID back = exercise(u, "Back ex", "BACK");

        session(u, "2026-09-06", 9, chest, s("100", 10));           // Sunday before the range: excluded
        session(u, "2026-09-14", 9, chest, w("20", 10), s("100", 10)); // Monday: starts the week of 09-14; warm-up ignored
        session(u, "2026-09-20", 9, back, s("50", 10));             // Sunday: still the week of 09-14
        session(u, "2026-09-20", 18, chest, s("60", 5));            // a second workout the same day
        session(u, "2026-09-21", 9, chest, s("100", 1));            // Monday: the next week
        session(u, "2026-09-24", 9, chest, s("10", 10));            // the last day of the range: included
        session(u, "2026-09-25", 9, chest, s("999", 10));           // the day after the range: excluded
        UUID live = insertInProgressWorkout(u, LocalDate.parse("2026-09-22"), Instant.parse("2026-09-22T09:00:00Z"));
        addExerciseWithSets(live, chest, 1, s("500", 10));          // in progress: excluded

        JsonNode series = get(c, VOLUME + "?granularity=weekly&from=2026-09-07&to=2026-09-24");
        assertThat(series.get("granularity").asText()).isEqualTo("weekly");
        JsonNode p = series.get("points");
        assertThat(starts(p)).containsExactly("2026-09-07", "2026-09-14", "2026-09-21");

        assertThat(p.get(0).get("workouts").asInt()).isZero();     // an empty week is still listed
        assertThat(p.get(0).get("volumeKg").decimalValue()).isEqualByComparingTo("0");
        assertThat(group(p.get(0), "PUSH").get("sets").asInt()).isZero();

        assertThat(p.get(1).get("workouts").asInt()).isEqualTo(3);
        assertThat(p.get(1).get("workingSets").asInt()).isEqualTo(3);
        assertThat(p.get(1).get("volumeKg").decimalValue()).isEqualByComparingTo("1800");
        assertThat(group(p.get(1), "PUSH").get("volumeKg").decimalValue()).isEqualByComparingTo("1300");
        assertThat(group(p.get(1), "PUSH").get("sets").asInt()).isEqualTo(2);
        assertThat(group(p.get(1), "PULL").get("volumeKg").decimalValue()).isEqualByComparingTo("500");

        assertThat(p.get(2).get("workouts").asInt()).isEqualTo(2);
        assertThat(p.get(2).get("volumeKg").decimalValue()).isEqualByComparingTo("200");
    }

    @Test
    void theFirstBucketMayStartBeforeFromButOnlyCountsWorkoutsInsideTheRange() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID chest = exercise(u, "Chest ex", "CHEST");
        session(u, "2026-09-15", 9, chest, s("100", 10)); // same week as `from`, but before it
        session(u, "2026-09-17", 9, chest, s("10", 10));

        JsonNode p = get(c, VOLUME + "?from=2026-09-16&to=2026-09-18").get("points");
        assertThat(starts(p)).containsExactly("2026-09-14");
        assertThat(p.get(0).get("workouts").asInt()).isEqualTo(1);
        assertThat(p.get(0).get("volumeKg").decimalValue()).isEqualByComparingTo("100");
    }

    @Test
    void monthlyBucketsAreCalendarMonthsAndEmptyMonthsAreFilled() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID chest = exercise(u, "Chest ex", "CHEST");
        UUID back = exercise(u, "Back ex", "BACK");
        session(u, "2026-07-10", 9, chest, s("999", 10)); // before `from`, in July's bucket: excluded
        session(u, "2026-08-31", 9, chest, s("100", 10));
        session(u, "2026-09-01", 9, back, s("50", 10));
        session(u, "2026-09-23", 9, back, s("50", 10));

        JsonNode s = get(c, VOLUME + "?granularity=MONTHLY&from=2026-07-15&to=2026-09-24");
        assertThat(s.get("granularity").asText()).isEqualTo("monthly");
        JsonNode p = s.get("points");
        assertThat(starts(p)).containsExactly("2026-07-01", "2026-08-01", "2026-09-01");
        assertThat(p.get(0).get("workouts").asInt()).isZero();
        assertThat(p.get(1).get("volumeKg").decimalValue()).isEqualByComparingTo("1000");
        assertThat(group(p.get(1), "PUSH").get("volumeKg").decimalValue()).isEqualByComparingTo("1000");
        assertThat(p.get(2).get("workouts").asInt()).isEqualTo(2);
        assertThat(group(p.get(2), "PULL").get("volumeKg").decimalValue()).isEqualByComparingTo("1000");
        assertThat(group(p.get(2), "PULL").get("sets").asInt()).isEqualTo(2);
    }

    @Test
    void volumeDefaultsToTheLastTwelveWeeksOrMonths() throws Exception {
        TestClient c = newUser("a@example.com");
        JsonNode weekly = get(c, VOLUME);
        assertThat(weekly.get("granularity").asText()).isEqualTo("weekly");
        assertThat(weekly.get("to").asText()).isEqualTo("2026-09-24");
        assertThat(weekly.get("from").asText()).isEqualTo("2026-07-03");
        assertThat(weekly.get("points")).hasSize(13); // Monday 06-29 through Monday 09-21
        assertThat(weekly.get("points").get(0).get("workouts").asInt()).isZero();

        JsonNode monthly = get(c, VOLUME + "?granularity=monthly");
        assertThat(monthly.get("from").asText()).isEqualTo("2025-09-25");
        assertThat(monthly.get("points")).hasSize(13); // 2025-09 through 2026-09
    }

    @Test
    void aWorkoutWithOnlyWarmUpsCountsAsAWorkoutButAddsNoVolume() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        session(u, "2026-09-22", 9, exercise(u, "Chest ex", "CHEST"), w("40", 10));

        JsonNode point = get(c, VOLUME + "?from=2026-09-21&to=2026-09-27").get("points").get(0);
        assertThat(point.get("workouts").asInt()).isEqualTo(1);
        assertThat(point.get("workingSets").asInt()).isZero();
        assertThat(point.get("volumeKg").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void volumeOnlyCountsTheCallersOwnWorkouts() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID shared = builtInExercise("Barbell Bench Press");
        session(userId(a), "2026-09-22", 9, shared, s("100", 10));
        session(userId(b), "2026-09-22", 9, shared, s("50", 10));

        JsonNode p = get(a, VOLUME + "?from=2026-09-21&to=2026-09-27").get("points").get(0);
        assertThat(p.get("workouts").asInt()).isEqualTo(1);
        assertThat(p.get("volumeKg").decimalValue()).isEqualByComparingTo("1000");
    }

    @Test
    void volumeRejectsBadInput() throws Exception {
        TestClient c = newUser("a@example.com");
        expectError(c, c.get(VOLUME + "?from=2026-09-10&to=2026-09-01"), 400, "INVALID_RANGE");
        expectError(c, c.get(VOLUME + "?granularity=daily"), 400, "INVALID_GRANULARITY");
        expectError(c, c.get(VOLUME + "?granularity="), 400, "INVALID_GRANULARITY");
        expectError(c, c.get(VOLUME + "?from=2020-01-01&to=2026-09-01"), 400, "INVALID_RANGE");
        expect(c, c.get(VOLUME + "?from=not-a-date"), 400);
    }

    // ---- progression --------------------------------------------------------------------------

    private UUID progressionSetup(UUID u) {
        UUID bench = exercise(u, "Bench", "CHEST");
        session(u, "2026-09-01", 9, bench, w("100", 5), s("60", 8));
        session(u, "2026-09-08", 9, bench, s("60", 8), s("62.5", 5));
        session(u, "2026-09-15", 9, bench, s("60", 10), s("60", 10));
        return bench;
    }

    @Test
    void progressionHasOnePointPerSessionOldestFirstAndIgnoresWarmUps() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID bench = progressionSetup(userId(c));

        JsonNode p = get(c, "/api/v1/exercises/" + bench + "/progression").get("points");
        assertThat(p).hasSize(3);
        assertThat(p.get(0).get("performedOn").asText()).isEqualTo("2026-09-01");
        assertThat(p.get(0).get("topSet").get("weightKg").decimalValue()).isEqualByComparingTo("60"); // not the 100 kg warm-up
        assertThat(p.get(0).get("bestEstimated1rmKg").decimalValue()).isEqualByComparingTo("76.00");
        assertThat(p.get(0).get("volumeKg").decimalValue()).isEqualByComparingTo("480");
        assertThat(p.get(0).get("workingSets").asInt()).isEqualTo(1);

        assertThat(p.get(1).get("topSet").get("weightKg").decimalValue()).isEqualByComparingTo("62.5"); // heavier, fewer reps
        assertThat(p.get(1).get("topSet").get("reps").asInt()).isEqualTo(5);
        assertThat(p.get(1).get("bestEstimated1rmKg").decimalValue()).isEqualByComparingTo("76.00");    // 60x8 beats 62.5x5 (72.92)
        assertThat(p.get(1).get("volumeKg").decimalValue()).isEqualByComparingTo("792.5");
        assertThat(p.get(1).get("workingSets").asInt()).isEqualTo(2);

        assertThat(p.get(2).get("bestEstimated1rmKg").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(p.get(2).get("volumeKg").decimalValue()).isEqualByComparingTo("1200");
        assertThat(p.get(2).get("workingSets").asInt()).isEqualTo(2);
    }

    @Test
    void progressionFiltersByDateRangeAndDefaultsToTheLastYear() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID bench = progressionSetup(u);
        session(u, "2025-09-24", 9, bench, s("40", 5)); // outside the default 365 days (2025-09-25 .. 2026-09-24)

        String base = "/api/v1/exercises/" + bench + "/progression";
        JsonNode dflt = get(c, base);
        assertThat(dflt.get("from").asText()).isEqualTo("2025-09-25");
        assertThat(dflt.get("to").asText()).isEqualTo("2026-09-24");
        assertThat(dflt.get("points")).hasSize(3);

        JsonNode ranged = get(c, base + "?from=2026-09-08&to=2026-09-08").get("points");
        assertThat(ranged).hasSize(1);
        assertThat(ranged.get(0).get("performedOn").asText()).isEqualTo("2026-09-08");
        assertThat(get(c, base + "?from=2025-09-24&to=2025-09-24").get("points")).hasSize(1);
        expectError(c, c.get(base + "?from=2026-09-10&to=2026-09-01"), 400, "INVALID_RANGE");
    }

    @Test
    void progressionTopSetBreaksWeightTiesByRepsAndSameDaySessionsStayInOrder() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID ex = exercise(u, "Row", "BACK");
        session(u, "2026-09-10", 18, ex, s("60", 5), s("60", 8), s("60", 6));
        session(u, "2026-09-10", 7, ex, s("50", 10));   // same date, earlier in the day
        session(u, "2026-09-11", 9, ex, s("100", 1));   // a single rep: the estimate is the weight itself

        JsonNode p = get(c, "/api/v1/exercises/" + ex + "/progression").get("points");
        assertThat(p).hasSize(3);
        assertThat(p.get(0).get("topSet").get("weightKg").decimalValue()).isEqualByComparingTo("50"); // 07:00 first
        assertThat(p.get(1).get("topSet").get("reps").asInt()).isEqualTo(8);
        assertThat(p.get(2).get("bestEstimated1rmKg").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void progressionHasNoPointsWithoutQualifyingWork() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID ex = exercise(u, "Curl", "BICEPS");
        assertThat(get(c, "/api/v1/exercises/" + ex + "/progression").get("points")).isEmpty();

        session(u, "2026-09-10", 9, ex, w("10", 10)); // warm-ups only: no session
        UUID live = insertInProgressWorkout(u, LocalDate.parse("2026-09-22"), Instant.parse("2026-09-22T09:00:00Z"));
        addExerciseWithSets(live, ex, 1, s("20", 10)); // in progress: not counted
        assertThat(get(c, "/api/v1/exercises/" + ex + "/progression").get("points")).isEmpty();
    }

    @Test
    void progressionForAnUnknownOrForeignExerciseIsNotFoundAndBuiltInsAreVisible() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID theirs = exercise(userId(b), "Secret", "CHEST");
        session(userId(b), "2026-09-10", 9, theirs, s("60", 8));

        expectError(a, a.get("/api/v1/exercises/" + theirs + "/progression"), 404, "NOT_FOUND");
        expectError(a, a.get("/api/v1/exercises/" + UUID.randomUUID() + "/progression"), 404, "NOT_FOUND");
        expectError(a, a.get("/api/v1/exercises/" + theirs + "/personal-records"), 404, "NOT_FOUND");
        expect(a, a.get("/api/v1/exercises/not-a-uuid/progression"), 400);

        UUID builtIn = builtInExercise("Barbell Bench Press");
        session(userId(a), "2026-09-10", 9, builtIn, s("60", 8));
        session(userId(b), "2026-09-10", 9, builtIn, s("90", 8)); // never leaks into a's view
        JsonNode p = get(a, "/api/v1/exercises/" + builtIn + "/progression").get("points");
        assertThat(p).hasSize(1);
        assertThat(p.get(0).get("topSet").get("weightKg").decimalValue()).isEqualByComparingTo("60");
    }

    // ---- personal records ---------------------------------------------------------------------

    @Test
    void personalRecordsAreDerivedFromSetsTiesAndWarmUpsAreNotRecords() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID bench = progressionSetup(userId(c));

        JsonNode page = get(c, "/api/v1/exercises/" + bench + "/personal-records");
        JsonNode events = page.get("items");
        // 09-01 is the baseline; 09-08's repeat of 60x8 ties (no record) and 62.5x5 is only a weight record;
        // 09-15's 60x10 is both a better estimate and more reps at that weight; the second 60x10 ties.
        assertThat(page.get("totalItems").asInt()).isEqualTo(3);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).get("performedOn").asText()).isEqualTo("2026-09-15");
        assertThat(events.get(0).get("type").asText()).isEqualTo("ESTIMATED_1RM");
        assertThat(events.get(0).get("estimated1rmKg").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(events.get(1).get("type").asText()).isEqualTo("REPS_AT_WEIGHT");
        assertThat(events.get(1).get("setId").asText()).isEqualTo(events.get(0).get("setId").asText()); // one set, two records
        assertThat(events.get(2).get("performedOn").asText()).isEqualTo("2026-09-08");
        assertThat(events.get(2).get("type").asText()).isEqualTo("WEIGHT");
        assertThat(events.get(2).get("weightKg").decimalValue()).isEqualByComparingTo("62.5");
        assertThat(events.get(2).get("estimated1rmKg").decimalValue()).isEqualByComparingTo("72.92");
    }

    @Test
    void equalWeightsAndRepeatedPerformancesNeverCreateRecords() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID ex = exercise(u, "Press", "SHOULDERS");
        session(u, "2026-09-01", 9, ex, s("50", 8));
        session(u, "2026-09-08", 9, ex, s("50", 8), s("50", 8));
        session(u, "2026-09-15", 9, ex, s("50", 7), s("45", 8)); // fewer reps, or lighter with no more reps

        assertThat(get(c, "/api/v1/exercises/" + ex + "/personal-records").get("items")).isEmpty();
    }

    @Test
    void decreasingLoadsNeverCreateRecordsAndIncreasingLoadsDo() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID down = exercise(u, "Down", "CHEST");
        UUID up = exercise(u, "Up", "CHEST");
        session(u, "2026-09-01", 9, down, s("100", 5));
        session(u, "2026-09-08", 9, down, s("90", 5));
        session(u, "2026-09-15", 9, down, s("80", 5));
        session(u, "2026-09-01", 10, up, s("60", 5));
        session(u, "2026-09-08", 10, up, s("70", 5));
        session(u, "2026-09-15", 10, up, s("80", 5));

        assertThat(get(c, "/api/v1/exercises/" + down + "/personal-records").get("items")).isEmpty();
        JsonNode ups = get(c, "/api/v1/exercises/" + up + "/personal-records").get("items");
        assertThat(ups.findValuesAsText("type")).containsOnly("WEIGHT", "ESTIMATED_1RM");
        assertThat(ups.findValuesAsText("performedOn")).containsOnly("2026-09-15", "2026-09-08");
    }

    @Test
    void exerciseRecordsArePaginatedWithoutOverlapOrGaps() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID ex = exercise(u, "Squat", "QUADS");
        for (int i = 0; i < 6; i++) { // strictly heavier every week: a WEIGHT and an ESTIMATED_1RM record after the first
            session(u, LocalDate.parse("2026-08-03").plusWeeks(i).toString(), 9, ex, s(String.valueOf(100 + i * 5), 5));
        }
        String base = "/api/v1/exercises/" + ex + "/personal-records";
        JsonNode first = get(c, base + "?size=4&page=0");
        assertThat(first.get("totalItems").asInt()).isEqualTo(10);
        assertThat(first.get("totalPages").asInt()).isEqualTo(3);

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            for (JsonNode e : get(c, base + "?size=4&page=" + page).get("items")) {
                seen.add(e.get("setId").asText() + "/" + e.get("type").asText());
            }
        }
        assertThat(seen).hasSize(10).doesNotHaveDuplicates();
        assertThat(get(c, base + "?size=4&page=3").get("items")).isEmpty();
        List<String> dates = get(c, base + "?size=100").get("items").findValuesAsText("performedOn");
        assertThat(dates).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "page=-1", "size=x"})
    void exerciseRecordsRejectBadPagination(String query) throws Exception {
        TestClient c = newUser("a@example.com");
        UUID ex = exercise(userId(c), "Squat", "QUADS");
        expect(c, c.get("/api/v1/exercises/" + ex + "/personal-records?" + query), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit=0", "limit=51", "limit=-1", "limit=x"})
    void globalRecordsRejectBadLimit(String query) throws Exception {
        TestClient c = newUser("a@example.com");
        expect(c, c.get(GLOBAL_PRS + "?" + query), 400);
    }

    @Test
    void globalRecordsSpanExercisesNewestFirstAndRespectTheLimit() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID squat = exercise(u, "Squat", "QUADS");
        UUID bench = exercise(u, "Bench", "CHEST");
        session(u, "2026-09-01", 9, squat, s("100", 5));
        session(u, "2026-09-01", 10, bench, s("60", 5));
        session(u, "2026-09-08", 9, squat, s("110", 5));   // squat WEIGHT + ESTIMATED_1RM
        session(u, "2026-09-15", 9, bench, s("65", 5));    // bench WEIGHT + ESTIMATED_1RM (newest)

        JsonNode all = get(c, GLOBAL_PRS);
        assertThat(all).hasSize(4);
        assertThat(all.get(0).get("exercise").get("name").asText()).isEqualTo("Bench");
        assertThat(all.get(0).get("record").get("performedOn").asText()).isEqualTo("2026-09-15");
        assertThat(all.get(0).get("exercise").get("id").asText()).isEqualTo(bench.toString());
        assertThat(all.get(2).get("exercise").get("name").asText()).isEqualTo("Squat");
        assertThat(all.findValuesAsText("performedOn")).isSortedAccordingTo(java.util.Comparator.reverseOrder());

        JsonNode limited = get(c, GLOBAL_PRS + "?limit=3");
        assertThat(limited).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(limited.get(i)).isEqualTo(all.get(i)); // the limit is a prefix of the same ordering
        }
        assertThat(get(c, GLOBAL_PRS)).isEqualTo(all);        // deterministic across calls
    }

    @Test
    void recordsInTheSameWorkoutOrderByExerciseNameSoTheOrderIsTotal() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID zebra = exercise(u, "Zebra press", "CHEST");
        UUID apple = exercise(u, "Apple press", "CHEST");
        session(u, "2026-09-01", 9, zebra, s("50", 5));
        session(u, "2026-09-01", 10, apple, s("50", 5));
        UUID second = insertCompletedWorkout(u, LocalDate.parse("2026-09-08"), Instant.parse("2026-09-08T09:00:00Z"));
        addExerciseWithSets(second, zebra, 1, s("60", 5));
        addExerciseWithSets(second, apple, 2, s("60", 5));

        List<String> names = get(c, GLOBAL_PRS).findValues("name").stream().map(JsonNode::asText).toList();
        assertThat(names).containsExactly("Apple press", "Apple press", "Zebra press", "Zebra press");
    }

    @Test
    void recordsOnlyUseTheCallersHistoryAndOnlyCompletedWorkouts() throws Exception {
        TestClient a = newUser("a@example.com");
        TestClient b = newUser("b@example.com");
        UUID bench = builtInExercise("Barbell Bench Press");
        session(userId(a), "2026-09-01", 9, bench, s("60", 5));
        // b lifted far heavier earlier; it must not turn a's later set into a non-record or show up for a
        session(userId(b), "2026-08-01", 9, bench, s("200", 5));
        session(userId(a), "2026-09-08", 9, bench, s("65", 5));
        UUID live = insertInProgressWorkout(userId(a), LocalDate.parse("2026-09-22"), Instant.parse("2026-09-22T09:00:00Z"));
        addExerciseWithSets(live, bench, 1, s("300", 5));

        JsonNode events = get(a, "/api/v1/exercises/" + bench + "/personal-records").get("items");
        assertThat(events).hasSize(2);
        assertThat(events.findValuesAsText("performedOn")).containsOnly("2026-09-08");
        assertThat(get(a, GLOBAL_PRS)).hasSize(2);
    }

    @Test
    void noHistoryMeansEmptyResultsNotErrors() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID ex = exercise(userId(c), "Squat", "QUADS");
        JsonNode page = get(c, "/api/v1/exercises/" + ex + "/personal-records");
        assertThat(page.get("items")).isEmpty();
        assertThat(page.get("totalItems").asInt()).isZero();
        assertThat(get(c, GLOBAL_PRS)).isEmpty();
    }

    @Test
    void recordEventsMatchTheFlagsShownInWorkoutDetail() throws Exception {
        TestClient c = newUser("a@example.com");
        UUID u = userId(c);
        UUID bench = progressionSetup(u);
        JsonNode events = get(c, "/api/v1/exercises/" + bench + "/personal-records").get("items");
        List<String> fromEvents = new ArrayList<>();
        events.forEach(e -> fromEvents.add(e.get("setId").asText() + "/" + e.get("type").asText()));

        List<String> fromWorkouts = new ArrayList<>();
        for (UUID workout : jdbc.queryForList("select id from workouts where user_id = ?", UUID.class, u)) {
            JsonNode detail = get(c, "/api/v1/workouts/" + workout);
            for (JsonNode ex : detail.get("exercises")) {
                for (JsonNode set : ex.get("sets")) {
                    for (JsonNode pr : set.get("personalRecords")) {
                        fromWorkouts.add(set.get("id").asText() + "/" + pr.asText());
                    }
                }
            }
        }
        assertThat(fromEvents).containsExactlyInAnyOrderElementsOf(fromWorkouts);
    }

    // ---- authentication -----------------------------------------------------------------------

    @Test
    void everyAnalyticsEndpointRequiresAuthentication() throws Exception {
        TestClient anon = new TestClient(mvc, mapper);
        String id = UUID.randomUUID().toString();
        for (String path : new String[]{VOLUME, GLOBAL_PRS, "/api/v1/exercises/" + id + "/progression",
                "/api/v1/exercises/" + id + "/personal-records"}) {
            assertThat(anon.get(path).getResponse().getStatus()).as(path).isEqualTo(401);
        }
    }
}
