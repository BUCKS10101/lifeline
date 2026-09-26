package com.personalos.backend.tasks;

import com.personalos.backend.support.ApiTestBase;
import com.personalos.backend.tasks.dto.TaskDtos.CreateTaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.personalos.backend.support.Concurrent.runTogether;
import static org.assertj.core.api.Assertions.assertThat;

/** Parallel requests against the real database, each in its own transaction like a separate HTTP request. */
class TaskConcurrencyTest extends ApiTestBase {

    @Autowired TaskService service;

    private static CreateTaskRequest task(String title, UUID id) {
        return new CreateTaskRequest(title, null, null, null, null, id);
    }

    @Test
    void parallelAddsAllSucceedAndEachCreatesItsOwnTask() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String title = "task " + i;
            tasks.add(() -> service.add(user, task(title, null)));
        }
        List<Object> results = runTogether(tasks);
        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((TaskService.AddResult) r).created()).count()).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from tasks where user_id = ?", Integer.class, user)).isEqualTo(12);
    }

    @Test
    void parallelRetriesOfOneClientIdCreateExactlyOneTaskAndNeverFail() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        UUID id = UUID.randomUUID();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> service.add(user, task("Same", id)));
        List<Object> results = runTogether(tasks);
        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().filter(r -> ((TaskService.AddResult) r).created()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void aClientIdRacingAcrossTwoPeopleGivesExactlyOneOwner() throws Exception {
        UUID a = userId(newUser("a@example.com"));
        UUID b = userId(newUser("b@example.com"));
        UUID id = UUID.randomUUID();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> service.add(a, task("A's", id)));
            tasks.add(() -> service.add(b, task("B's", id)));
        }
        List<Object> results = runTogether(tasks);
        assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id)).isEqualTo(1);
        // Whoever lost got a conflict for every attempt, never a second row and never a leak of the other's task.
        assertThat(results.stream().filter(r -> r instanceof Exception).count()).isEqualTo(4);
    }

    @Test
    void parallelCompletionsOfOneTaskAllSucceedAndKeepASingleCompletionMoment() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        UUID id = service.add(user, task("T", null)).task().id();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> service.complete(user, id));
        List<Object> results = runTogether(tasks);
        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(results.stream().map(r -> ((com.personalos.backend.tasks.dto.TaskDtos.TaskResponse) r).completedAt()).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("select completed_at is not null from tasks where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void completingAndReopeningTogetherAlwaysEndInAConsistentState() throws Exception {
        UUID user = userId(newUser("a@example.com"));
        UUID id = service.add(user, task("T", null)).task().id();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> service.complete(user, id));
            tasks.add(() -> service.reopen(user, id));
        }
        assertThat(runTogether(tasks)).noneMatch(r -> r instanceof Exception);
        assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id)).isEqualTo(1);   // one row, either state, no error
    }
}
