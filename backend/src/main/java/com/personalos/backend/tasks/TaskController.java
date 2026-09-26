package com.personalos.backend.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.tasks.dto.TaskDtos.CreateTaskRequest;
import com.personalos.backend.tasks.dto.TaskDtos.TaskResponse;
import com.personalos.backend.tasks.dto.TaskDtos.TaskSummary;
import com.personalos.backend.tasks.dto.TaskDtos.UpdateTaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Tasks always belong to the logged-in user: there is no user id in any path or body. */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;
    private final ObjectMapper mapper;

    public TaskController(TaskService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** One view of the person's tasks: today (default, includes overdue), upcoming, anytime or done. */
    @GetMapping
    public PagedResponse<TaskResponse> list(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) UUID goalId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(userId, TaskView.parse(view), goalId, page, size);
    }

    /** 201 when the task was added, 200 when a retry with the same client-generated id found the existing one. */
    @PostMapping
    public ResponseEntity<TaskResponse> add(@AuthenticationPrincipal(expression = "id") UUID userId,
                                            @Valid @RequestBody CreateTaskRequest request) {
        TaskService.AddResult result = service.add(userId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.task());
    }

    /** A partial edit: a field left out is unchanged, and an explicit null clears notes, dueDate or goalId. */
    @PatchMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                               @RequestBody JsonNode body) {
        return service.update(userId, id, UpdateTaskRequest.from(body, mapper));
    }

    /** Idempotent: completing a done task returns it unchanged. */
    @PostMapping("/{id}/complete")
    public TaskResponse complete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.complete(userId, id);
    }

    /** Idempotent: reopening an open task returns it unchanged. */
    @PostMapping("/{id}/reopen")
    public TaskResponse reopen(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.reopen(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    /** The counts the dashboard shows. */
    @GetMapping("/summary")
    public TaskSummary summary(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return service.summary(userId);
    }
}
