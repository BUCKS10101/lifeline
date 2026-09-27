package com.personalos.backend.habits;

import com.personalos.backend.habits.dto.HabitDtos.CreateHabitRequest;
import com.personalos.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.personalos.backend.support.Concurrent.runTogether;
import static org.assertj.core.api.Assertions.assertThat;

/** Parallel requests against the real database, each in its own transaction like a separate HTTP request. */
class HabitConcurrencyTest extends ApiTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 20);
    private static final List<Integer> DAILY = List.of(1, 2, 3, 4, 5, 6, 7);

    @Autowired HabitService service;

    /** Started well before DAY, so ticking DAY (and the days before it) is always within range. */
    private UUID createHabit(UUID user, String name) {
        return service.create(user, new CreateHabitRequest(name, DAILY, LocalDate.of(2026, 8, 1), null)).id();
    }

    @Test
    void parallelTicksOfTheSameDayCreateExactlyOneCompletionAndNeverFail() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        UUID habit = createHabit(user, "Read");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> service.tick(user, habit, DAY));

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((HabitService.TickResult) r).created()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?", Integer.class, habit)).isEqualTo(1);
    }

    @Test
    void parallelTicksOfDifferentDaysAllSucceed() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        UUID habit = createHabit(user, "Read");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate day = DAY.minusDays(i);
            tasks.add(() -> service.tick(user, habit, day));
        }

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((HabitService.TickResult) r).created()).count()).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?", Integer.class, habit)).isEqualTo(12);
    }

    @Test
    void parallelTickAndUntickOfOneDayAlwaysEndInAConsistentState() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        UUID habit = createHabit(user, "Read");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> service.tick(user, habit, DAY));
            tasks.add(() -> { try { service.untick(user, habit, DAY); } catch (Exception ignored) { /* it may already be gone */ } return null; });
        }
        assertThat(runTogether(tasks)).noneMatch(r -> r instanceof Exception);
        // Either 0 or 1 rows for that day, never more, and no leftover for any other date.
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ? and completed_on = ?", Integer.class, habit, DAY)).isIn(0, 1);
        assertThat(jdbc.queryForObject("select count(*) from habit_completions where habit_id = ?", Integer.class, habit)).isLessThanOrEqualTo(1);
    }

    @Test
    void parallelCreatesWithTheSameNameLetExactlyOneWin() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> service.create(user, new CreateHabitRequest("Read", DAILY, null, null)));

        List<Object> results = runTogether(tasks);

        assertThat(results.stream().filter(r -> !(r instanceof Exception)).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r instanceof Exception).count()).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from habits where user_id = ?", Integer.class, user)).isEqualTo(1);
    }
}
