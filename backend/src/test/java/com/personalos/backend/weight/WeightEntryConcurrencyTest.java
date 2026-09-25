package com.personalos.backend.weight;

import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.weight.dto.WeightDtos.UpsertWeightEntryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.personalos.backend.support.Concurrent.runTogether;
import static org.assertj.core.api.Assertions.assertThat;

/** Parallel requests against the real database, each in its own transaction like a separate HTTP request. */
class WeightEntryConcurrencyTest extends ApiTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 20);

    @Autowired WeightEntryService service;

    private static UpsertWeightEntryRequest reading(String weight) {
        return new UpsertWeightEntryRequest(new BigDecimal(weight), null);
    }

    @Test
    void parallelLogsOfTheSameDayCreateExactlyOneRowAndNeverFail() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<Callable<Object>> tasks = new ArrayList<>();
        List<String> weights = List.of("70.1", "70.2", "70.3", "70.4", "70.5", "70.6", "70.7", "70.8");
        for (String weight : weights) tasks.add(() -> service.upsert(user, DAY, reading(weight)));

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((WeightEntryService.UpsertResult) r).created()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from weight_entries where user_id = ?", Integer.class, user)).isEqualTo(1);
        // Last writer wins: the stored value is one of the submitted readings, never a blend or a lost row.
        BigDecimal stored = jdbc.queryForObject("select weight_kg from weight_entries where user_id = ?", BigDecimal.class, user);
        assertThat(weights.stream().map(BigDecimal::new)).anyMatch(w -> w.compareTo(stored) == 0);
    }

    @Test
    void parallelLogsOfDifferentDaysAllSucceed() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate day = DAY.minusDays(i);
            tasks.add(() -> service.upsert(user, day, reading("70")));
        }

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((WeightEntryService.UpsertResult) r).created()).count()).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from weight_entries where user_id = ?", Integer.class, user)).isEqualTo(12);
    }

    @Test
    void differentUsersLoggingTheSameDayNeverInterfere() throws Exception {
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) users.add(userId(newUser("user" + i + "@example.com")));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            UUID user = users.get(i);
            String weight = String.valueOf(60 + i);
            tasks.add(() -> service.upsert(user, DAY, reading(weight)));
        }

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(jdbc.queryForObject("select count(*) from weight_entries", Integer.class)).isEqualTo(6);
        for (int i = 0; i < users.size(); i++) {
            assertThat(jdbc.queryForObject("select weight_kg from weight_entries where user_id = ?", BigDecimal.class, users.get(i)))
                    .isEqualByComparingTo(String.valueOf(60 + i));
        }
    }

    @Test
    void loggingAndDeletingTheSameDayAtOnceLeavesAConsistentState() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        service.upsert(user, DAY, reading("70"));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String weight = "71." + i;
            tasks.add(() -> service.upsert(user, DAY, reading(weight)));
            tasks.add(() -> { service.delete(user, DAY); return "deleted"; });
        }

        List<Object> results = runTogether(tasks);

        // A delete that finds nothing is a normal 404 outcome; anything else would be a real failure.
        assertThat(results.stream().filter(r -> r instanceof Exception && !(r instanceof ApiException e && e.getCode().equals("NOT_FOUND")))).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from weight_entries where user_id = ?", Integer.class, user)).isIn(0, 1);
    }
}
