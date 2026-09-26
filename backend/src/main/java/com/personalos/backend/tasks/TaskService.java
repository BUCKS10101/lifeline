package com.personalos.backend.tasks;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.tasks.dto.TaskDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    /** Match the CHECK constraint on tasks.due_date. */
    static final LocalDate EARLIEST_DUE = LocalDate.of(2000, 1, 1);
    static final LocalDate LATEST_DUE = LocalDate.of(2100, 12, 31);
    private static final int MAX_TITLE = 200;
    private static final int MAX_NOTES = 2000;

    private final TaskRepository tasks;
    private final ProfileService profiles;
    private final Clock clock;

    public TaskService(TaskRepository tasks, ProfileService profiles, Clock clock) {
        this.tasks = tasks;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** The task, and whether this call added it (false when a retry with the same client id found the existing one). */
    public record AddResult(TaskResponse task, boolean created) {}

    @Transactional
    public AddResult add(UUID userId, CreateTaskRequest request) {
        LocalDate today = today(userId);
        requireDueDate(request.dueDate());
        requireOwnGoal(userId, request.goalId());
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        Priority priority = request.priority() != null ? request.priority() : Priority.NORMAL;

        boolean created = tasks.insertIfAbsent(id, userId, request.goalId(), request.title().trim(), blankToNull(request.notes()),
                request.dueDate(), priority, Instant.now(clock)) == 1;
        if (!created && !userId.equals(tasks.ownerOf(id).orElse(null))) {
            // Somebody else's task already has this id. Say nothing about it beyond the id being taken.
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "This task id is already in use");
        }
        // A retry returns what already exists, even if the retried request differs: the first one won.
        return new AddResult(tasks.find(userId, id, today).orElseThrow(), created);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> list(UUID userId, TaskView view, UUID goalId, int page, int size) {
        LocalDate today = today(userId);
        long total = tasks.count(userId, view, goalId, today);
        List<TaskResponse> items = tasks.list(userId, view, goalId, today, size, (long) page * size);
        return new PagedResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional
    public TaskResponse update(UUID userId, UUID id, UpdateTaskRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Provide at least one field to change");
        }
        LocalDate today = today(userId);
        TaskResponse current = tasks.find(userId, id, today).orElseThrow(TaskService::notFound);

        String title = current.title();
        if (request.title().present()) {
            String value = request.title().value();
            title = value == null ? "" : value.trim();
            if (title.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "title must not be blank");
            if (title.length() > MAX_TITLE) throw tooLong("title", MAX_TITLE);
        }
        String notes = current.notes();
        if (request.notes().present()) {
            notes = blankToNull(request.notes().value());
            if (notes != null && notes.length() > MAX_NOTES) throw tooLong("notes", MAX_NOTES);
        }
        LocalDate dueDate = current.dueDate();
        if (request.dueDate().present()) {
            dueDate = request.dueDate().value();
            requireDueDate(dueDate);
        }
        Priority priority = current.priority();
        if (request.priority().present()) {
            if (request.priority().value() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "priority cannot be cleared");
            }
            priority = request.priority().value();
        }
        UUID goalId = current.goal() == null ? null : current.goal().id();
        if (request.goalId().present()) {
            goalId = request.goalId().value();
            requireOwnGoal(userId, goalId);
        }

        tasks.update(id, userId, goalId, title, notes, dueDate, priority, Instant.now(clock));
        return tasks.find(userId, id, today).orElseThrow();
    }

    @Transactional
    public TaskResponse complete(UUID userId, UUID id) {
        if (tasks.complete(id, userId, Instant.now(clock)) == 0) throw notFound();
        return tasks.find(userId, id, today(userId)).orElseThrow();
    }

    @Transactional
    public TaskResponse reopen(UUID userId, UUID id) {
        if (tasks.reopen(id, userId, Instant.now(clock)) == 0) throw notFound();
        return tasks.find(userId, id, today(userId)).orElseThrow();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (tasks.delete(id, userId) == 0) throw notFound();
    }

    @Transactional(readOnly = true)
    public TaskSummary summary(UUID userId) {
        return tasks.summary(userId, today(userId));
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** Today in the person's own timezone. */
    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
    }

    private static void requireDueDate(LocalDate date) {
        if (date == null) return;
        if (date.isBefore(EARLIEST_DUE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
        if (date.isAfter(LATEST_DUE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_LATE", "Dates after 2100-12-31 are not supported");
        }
    }

    /** A goal that is not the caller's is reported as not found, never as belonging to someone else. */
    private void requireOwnGoal(UUID userId, UUID goalId) {
        if (goalId != null && !tasks.ownsGoal(userId, goalId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Goal not found");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Task not found");
    }

    private static ApiException tooLong(String field, int max) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " must be at most " + max + " characters");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
