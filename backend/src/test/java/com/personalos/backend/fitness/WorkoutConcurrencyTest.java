package com.personalos.backend.fitness;

import com.personalos.backend.common.error.ApiException;
import static com.personalos.backend.support.Concurrent.runTogether;
import com.personalos.backend.fitness.dto.WorkoutDtos.CreateSetRequest;
import com.personalos.backend.fitness.dto.WorkoutDtos.StartWorkoutRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real parallel requests against the real database. The services are called directly from a thread pool,
 * so each call runs in its own transaction exactly as a separate HTTP request would.
 */
class WorkoutConcurrencyTest extends FitnessApiTest {

    @Autowired WorkoutService workoutService;

    private static long count(List<Object> results, java.util.function.Predicate<Object> test) {
        return results.stream().filter(test).count();
    }

    private static boolean failedWith(Object result, String code) {
        return result instanceof ApiException e && e.getCode().equals(code);
    }

    private int queryInt(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---- starting ----------------------------------------------------------------------------

    @Test
    void parallelStartsCreateExactlyOneWorkout() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> workoutService.start(user, new StartWorkoutRequest(null, null)));

        List<Object> results = runTogether(tasks);

        assertThat(count(results, r -> !(r instanceof Exception))).isEqualTo(1);
        assertThat(count(results, r -> failedWith(r, "WORKOUT_IN_PROGRESS"))).isEqualTo(7);
        assertThat(queryInt("select count(*) from workouts where user_id = ?", user)).isEqualTo(1);
        assertThat(queryInt("select count(*) from workouts where user_id = ? and status = 'IN_PROGRESS'", user)).isEqualTo(1);
    }

    @Test
    void parallelStartsFromATemplateLeaveNoPartialRowsBehind() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        UUID push = builtInTemplate("Push");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> workoutService.start(user, new StartWorkoutRequest(push, null)));

        List<Object> results = runTogether(tasks);

        assertThat(count(results, r -> !(r instanceof Exception))).isEqualTo(1);
        assertThat(queryInt("select count(*) from workouts where user_id = ?", user)).isEqualTo(1);
        // Only the winner's six exercises exist; the losers rolled back completely.
        assertThat(queryInt("select count(*) from workout_exercises")).isEqualTo(6);
    }

    @Test
    void differentUsersCanStartAtTheSameTime() throws Exception {
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) users.add(userId(newUser("user" + i + "@example.com")));
        List<Callable<Object>> tasks = users.stream()
                .map(u -> (Callable<Object>) () -> workoutService.start(u, new StartWorkoutRequest(null, null))).toList();

        List<Object> results = runTogether(tasks);

        assertThat(count(results, r -> r instanceof Exception)).isZero();
        assertThat(queryInt("select count(*) from workouts where status = 'IN_PROGRESS'")).isEqualTo(6);
    }

    // ---- sets --------------------------------------------------------------------------------

    private record Setup(UUID user, UUID workout, UUID workoutExercise) {}

    private Setup workoutWithBench(String email) throws Exception {
        var client = newUser(email);
        String workout = startWorkout(client).get("id").asText();
        String we = addExercise(client, workout, builtInExercise("Barbell Bench Press")).get("id").asText();
        return new Setup(userId(client), UUID.fromString(workout), UUID.fromString(we));
    }

    private static CreateSetRequest setRequest(UUID id) {
        return new CreateSetRequest(id, new BigDecimal("60"), 8, null, null);
    }

    @Test
    void parallelRetriesOfTheSameSetCreateOneSet() throws Exception {
        Setup setup = workoutWithBench("a@example.com");
        UUID clientId = UUID.randomUUID();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> workoutService.addSet(setup.user(), setup.workout(), setup.workoutExercise(), setRequest(clientId)));

        List<Object> results = runTogether(tasks);

        assertThat(count(results, r -> r instanceof Exception)).isZero();
        assertThat(count(results, r -> r instanceof WorkoutService.SetResult s && s.created())).isEqualTo(1);
        assertThat(count(results, r -> r instanceof WorkoutService.SetResult s && !s.created())).isEqualTo(7);
        assertThat(queryInt("select count(*) from workout_sets")).isEqualTo(1);
        assertThat(queryInt("select set_number from workout_sets")).isEqualTo(1);
    }

    @Test
    void parallelSetsGetDistinctContiguousNumbers() throws Exception {
        Setup setup = workoutWithBench("a@example.com");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) tasks.add(() -> workoutService.addSet(setup.user(), setup.workout(), setup.workoutExercise(), setRequest(null)));

        List<Object> results = runTogether(tasks);

        assertThat(count(results, r -> r instanceof Exception)).isZero();
        List<Integer> numbers = jdbc.queryForList("select set_number from workout_sets order by set_number", Integer.class);
        assertThat(numbers).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());
    }

    @Test
    void parallelExerciseAddsGetDistinctContiguousPositions() throws Exception {
        var client = newUser("a@example.com");
        UUID user = userId(client);
        UUID workout = UUID.fromString(startWorkout(client).get("id").asText());
        List<UUID> exercises = jdbc.queryForList("select id from exercises where owner_id is null limit 10", UUID.class);
        List<Callable<Object>> tasks = exercises.stream()
                .map(e -> (Callable<Object>) () -> workoutService.addExercise(user, workout, e)).toList();

        List<Object> results = runTogether(tasks);

        assertThat(count(results, r -> r instanceof Exception)).isZero();
        List<Integer> positions = jdbc.queryForList("select position from workout_exercises order by position", Integer.class);
        assertThat(positions).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 10).boxed().toList());
    }

    // ---- finishing ---------------------------------------------------------------------------

    @Test
    void parallelFinishesAgreeOnOneFinishTime() throws Exception {
        Setup setup = workoutWithBench("a@example.com");
        workoutService.addSet(setup.user(), setup.workout(), setup.workoutExercise(), setRequest(null));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) tasks.add(() -> workoutService.finish(setup.user(), setup.workout()));

        List<Object> results = runTogether(tasks);

        assertThat(count(results, r -> r instanceof Exception)).isZero();
        assertThat(results.stream().map(r -> ((com.personalos.backend.fitness.dto.WorkoutDtos.WorkoutDetail) r).finishedAt()).distinct())
                .hasSize(1);
        assertThat(jdbc.queryForObject("select status from workouts", String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void finishRacingSetsLeavesAConsistentWorkout() throws Exception {
        for (int round = 1; round <= 5; round++) {
            jdbc.execute("delete from users");
            Setup setup = workoutWithBench("round" + round + "@example.com");
            workoutService.addSet(setup.user(), setup.workout(), setup.workoutExercise(), setRequest(null));

            List<Callable<Object>> tasks = new ArrayList<>();
            tasks.add(() -> workoutService.finish(setup.user(), setup.workout()));
            for (int i = 0; i < 10; i++) tasks.add(() -> workoutService.addSet(setup.user(), setup.workout(), setup.workoutExercise(), setRequest(null)));
            List<Object> results = runTogether(tasks);

            // Each set either made it in before the finish, or was refused because the workout was already finished.
            long added = count(results, r -> r instanceof WorkoutService.SetResult);
            long refused = count(results, r -> failedWith(r, "WORKOUT_NOT_IN_PROGRESS"));
            assertThat(added + refused).as("round " + round).isEqualTo(10);
            assertThat(results.stream().filter(r -> r instanceof Exception && !failedWith(r, "WORKOUT_NOT_IN_PROGRESS"))).isEmpty();

            assertThat(queryInt("select count(*) from workout_sets")).as("round " + round).isEqualTo((int) (1 + added));
            List<Integer> numbers = jdbc.queryForList("select set_number from workout_sets order by set_number", Integer.class);
            assertThat(numbers).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, numbers.size()).boxed().toList());
            assertThat(jdbc.queryForObject("select status from workouts", String.class)).isEqualTo("COMPLETED");
            assertThat(queryInt("select count(*) from workouts where finished_at is not null")).isEqualTo(1);
        }
    }
}
