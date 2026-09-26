package com.personalos.backend.tasks.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.tasks.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {
    }

    /**
     * A new task. Only the title is required. {@code id} is an optional client-generated UUID: sending the same id again
     * returns the task that already exists instead of adding a second one, which makes retries safe.
     */
    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String notes,
            LocalDate dueDate,
            Priority priority,
            UUID goalId,
            UUID id
    ) {}

    /** One field of a partial edit: whether the request mentioned it, and its value (null when it was mentioned as null). */
    public record Patch<T>(boolean present, T value) {
        public static <T> Patch<T> absent() {
            return new Patch<>(false, null);
        }
    }

    /**
     * A partial edit. A field left out is unchanged; an explicit {@code null} clears {@code notes}, {@code dueDate} or
     * {@code goalId} (the title and priority cannot be cleared). At least one field is required. JSON cannot tell "left
     * out" from "null" once bound to a plain record, so the body is read as a JSON object and each field is looked up.
     */
    public record UpdateTaskRequest(Patch<String> title, Patch<String> notes, Patch<LocalDate> dueDate, Patch<Priority> priority, Patch<UUID> goalId) {

        public boolean isEmpty() {
            return !title.present() && !notes.present() && !dueDate.present() && !priority.present() && !goalId.present();
        }

        public static UpdateTaskRequest from(JsonNode body, ObjectMapper mapper) {
            if (body == null || !body.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Send a JSON object");
            }
            return new UpdateTaskRequest(read(body, "title", String.class, mapper), read(body, "notes", String.class, mapper),
                    read(body, "dueDate", LocalDate.class, mapper), read(body, "priority", Priority.class, mapper),
                    read(body, "goalId", UUID.class, mapper));
        }

        private static <T> Patch<T> read(JsonNode body, String field, Class<T> type, ObjectMapper mapper) {
            if (!body.has(field)) return Patch.absent();
            JsonNode node = body.get(field);
            if (node.isNull()) return new Patch<>(true, null);
            try {
                return new Patch<>(true, mapper.treeToValue(node, type));
            } catch (Exception e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " is not valid");
            }
        }
    }

    public record GoalRef(UUID id, String title) {}

    /** {@code overdue} is derived: open and due before today in the person's timezone. */
    public record TaskResponse(UUID id, String title, String notes, LocalDate dueDate, Priority priority, Instant completedAt,
                               boolean overdue, GoalRef goal) {}

    /** The counts the dashboard shows. {@code dueToday} and {@code overdue} are open tasks only. */
    public record TaskSummary(int dueToday, int overdue, int open) {}
}
