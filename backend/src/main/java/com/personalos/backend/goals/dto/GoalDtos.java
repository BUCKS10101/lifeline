package com.personalos.backend.goals.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.goals.GoalStatus;
import com.personalos.backend.tasks.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GoalDtos {

    private GoalDtos() {
    }

    /** A new goal. Only the title is required; it always starts {@code ACTIVE}. */
    public record CreateGoalRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 1000) String description,
            LocalDate targetDate
    ) {}

    /** One field of a partial edit: whether the request mentioned it, and its value (null when it was mentioned as null). */
    public record Patch<T>(boolean present, T value) {
        public static <T> Patch<T> absent() {
            return new Patch<>(false, null);
        }
    }

    /**
     * A partial edit. A field left out is unchanged; an explicit {@code null} clears {@code description} or
     * {@code targetDate} ({@code title} and {@code status} cannot be cleared). At least one field is required. JSON
     * cannot tell "left out" from "null" once bound to a plain record, so the body is read as a JSON object and each
     * field is looked up (the same trick as the tasks and habits modules' partial edits).
     */
    public record UpdateGoalRequest(Patch<String> title, Patch<String> description, Patch<LocalDate> targetDate, Patch<GoalStatus> status) {

        public boolean isEmpty() {
            return !title.present() && !description.present() && !targetDate.present() && !status.present();
        }

        public static UpdateGoalRequest from(JsonNode body, ObjectMapper mapper) {
            if (body == null || !body.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Send a JSON object");
            }
            return new UpdateGoalRequest(read(body, "title", String.class, mapper), read(body, "description", String.class, mapper),
                    read(body, "targetDate", LocalDate.class, mapper), read(body, "status", GoalStatus.class, mapper));
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

    /** {@code progressPercent} is null when the goal has no linked tasks; habits never affect it. */
    public record GoalResponse(UUID id, String title, String description, LocalDate targetDate, GoalStatus status,
                               Instant achievedAt, int taskCount, int doneCount, Integer progressPercent) {}

    /** A task linked to a goal, enough to show on the goal's own page. */
    public record GoalTaskItem(UUID id, String title, LocalDate dueDate, Priority priority, Instant completedAt, boolean overdue) {}

    /** A habit linked to a goal, as supporting work: its streaks, never its progress. */
    public record GoalHabitItem(UUID id, String name, boolean archived, int currentStreak, int longestStreak) {}

    /** The goal plus its linked tasks (open first) and habits. */
    public record GoalDetail(GoalResponse goal, List<GoalTaskItem> tasks, List<GoalHabitItem> habits) {}
}
