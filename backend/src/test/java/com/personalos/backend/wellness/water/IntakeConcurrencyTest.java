package com.personalos.backend.wellness.water;

import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.wellness.protein.ProteinService;
import com.personalos.backend.wellness.protein.dto.ProteinDtos.AddProteinRequest;
import com.personalos.backend.wellness.water.dto.WaterDtos.AddWaterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.personalos.backend.support.Concurrent.runTogether;
import static org.assertj.core.api.Assertions.assertThat;

/** Parallel requests against the real database, each in its own transaction like a separate HTTP request. */
class IntakeConcurrencyTest extends ApiTestBase {

    @Autowired WaterService water;
    @Autowired ProteinService protein;

    @Test
    void parallelDrinksAllCountAndTheTotalIsExact() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) tasks.add(() -> water.add(user, new AddWaterRequest(250, null, null)));

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((WaterService.AddResult) r).created()).count()).isEqualTo(12);
        assertThat(jdbc.queryForObject("select sum(amount_ml) from water_entries where user_id = ?", Integer.class, user)).isEqualTo(3000);
    }

    @Test
    void parallelRetriesOfOneClientIdCreateExactlyOneDrinkAndNeverFail() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        UUID id = UUID.randomUUID();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> water.add(user, new AddWaterRequest(250, null, id)));

        List<Object> results = runTogether(tasks);

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((WaterService.AddResult) r).created()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from water_entries where user_id = ?", Integer.class, user)).isEqualTo(1);
    }

    @Test
    void parallelProteinAddsAndRetriesBehaveTheSameWay() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<Callable<Object>> adds = new ArrayList<>();
        for (int i = 0; i < 10; i++) adds.add(() -> protein.add(user, new AddProteinRequest(20, "Whey shake", null, null)));
        assertThat(runTogether(adds)).noneMatch(r -> r instanceof Exception);
        assertThat(jdbc.queryForObject("select sum(grams) from protein_entries where user_id = ?", Integer.class, user)).isEqualTo(200);

        UUID id = UUID.randomUUID();
        List<Callable<Object>> retries = new ArrayList<>();
        for (int i = 0; i < 8; i++) retries.add(() -> protein.add(user, new AddProteinRequest(30, null, null, id)));
        List<Object> results = runTogether(retries);
        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((ProteinService.AddResult) r).created()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from protein_entries where id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void aClientIdRacingAcrossTwoPeopleGivesExactlyOneOwner() throws Exception {
        UUID a = userId(newUser("a@example.com"));
        UUID b = userId(newUser("b@example.com"));
        UUID id = UUID.randomUUID();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> water.add(a, new AddWaterRequest(250, null, id)));
            tasks.add(() -> water.add(b, new AddWaterRequest(250, null, id)));
        }

        List<Object> results = runTogether(tasks);

        assertThat(jdbc.queryForObject("select count(*) from water_entries where id = ?", Integer.class, id)).isEqualTo(1);
        // Whoever lost got a conflict, never a second row and never a leak of the other's entry.
        assertThat(results.stream().filter(r -> r instanceof Exception).count()).isEqualTo(4);
    }
}
