package com.personalos.backend.wellness.sleep;

import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.wellness.sleep.dto.SleepDtos.UpsertSleepEntryRequest;
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
class SleepConcurrencyTest extends ApiTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 20);

    @Autowired SleepService service;

    @Test
    void parallelLogsOfTheSameMorningCreateExactlyOneRowAndNeverFail() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<String> bedtimes = List.of("21:00", "21:30", "22:00", "22:30", "23:00", "23:30", "00:00", "00:30");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (String bed : bedtimes) tasks.add(() -> service.upsert(user, DAY, new UpsertSleepEntryRequest(bed, "07:00")));

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((SleepService.UpsertResult) r).created()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sleep_entries where user_id = ?", Integer.class, user)).isEqualTo(1);
        // Last writer wins: the stored night is one of the submitted ones, never a blend of two.
        int minutes = jdbc.queryForObject("select (extract(epoch from woke_at - bedtime_at) / 60)::int from sleep_entries where user_id = ?", Integer.class, user);
        assertThat(minutes).isIn(600, 570, 540, 510, 480, 450, 420, 390);
    }

    @Test
    void parallelLogsOfDifferentMorningsAllSucceed() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate day = DAY.minusDays(i);
            tasks.add(() -> service.upsert(user, day, new UpsertSleepEntryRequest("23:00", "07:00")));
        }

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((SleepService.UpsertResult) r).created()).count()).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from sleep_entries where user_id = ?", Integer.class, user)).isEqualTo(12);
    }
}
