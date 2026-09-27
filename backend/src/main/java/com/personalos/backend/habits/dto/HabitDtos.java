package com.personalos.backend.habits.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.common.error.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HabitDtos {

    private HabitDtos() {
    }

    public record GoalRef(UUID id, String title) {
    }

    /** {@code startedOn} defaults to today (the person's timezone) when omitted. */
    public record CreateHabitRequest(
            @NotBlank @Size(max = 100) String name,
            @NotEmpty List<Integer> daysOfWeek,
            LocalDate startedOn,
            UUID goalId
    ) {
    }

    /** One field of a partial edit: whether the request mentioned it, and its value (null when it was mentioned as null). */
    public record Patch<T>(boolean present, T value) {
        public static <T> Patch<T> absent() {
            return new Patch<>(false, null);
        }
    }

    /**
     * A partial edit. A field left out is unchanged. Only {@code goalId} can be cleared (an explicit {@code null});
     * {@code name}, {@code daysOfWeek} and {@code startedOn} cannot be blanked out, only replaced. At least one field is
     * required. JSON cannot tell "left out" from "null" once bound to a plain record, so the body is read as a JSON object
     * and each field is looked up (the same trick as the tasks module's partial edit).
     */
    public record UpdateHabitRequest(Patch<String> name, Patch<List<Integer>> daysOfWeek, Patch<LocalDate> startedOn, Patch<UUID> goalId) {

        public boolean isEmpty() {
            return !name.present() && !daysOfWeek.present() && !startedOn.present() && !goalId.present();
        }

        public static UpdateHabitRequest from(JsonNode body, ObjectMapper mapper) {
            if (body == null || !body.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Send a JSON object");
            }
            return new UpdateHabitRequest(readString(body), readDays(body), readDate(body, "startedOn"), readGoalId(body));
        }

        private static Patch<String> readString(JsonNode body) {
            if (!body.has("name")) return Patch.absent();
            JsonNode node = body.get("name");
            return new Patch<>(true, node.isNull() ? null : node.asText());
        }

        private static Patch<List<Integer>> readDays(JsonNode body) {
            if (!body.has("daysOfWeek")) return Patch.absent();
            JsonNode node = body.get("daysOfWeek");
            if (node.isNull() || !node.isArray()) return new Patch<>(true, null);
            List<Integer> days = new java.util.ArrayList<>();
            node.forEach(n -> days.add(n.asInt()));
            return new Patch<>(true, days);
        }

        private static Patch<LocalDate> readDate(JsonNode body, String field) {
            if (!body.has(field)) return Patch.absent();
            JsonNode node = body.get(field);
            if (node.isNull()) return new Patch<>(true, null);
            try {
                return new Patch<>(true, LocalDate.parse(node.asText()));
            } catch (Exception e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " is not a valid date");
            }
        }

        private static Patch<UUID> readGoalId(JsonNode body) {
            if (!body.has("goalId")) return Patch.absent();
            JsonNode node = body.get("goalId");
            if (node.isNull()) return new Patch<>(true, null);
            try {
                return new Patch<>(true, UUID.fromString(node.asText()));
            } catch (Exception e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "goalId is not a valid id");
            }
        }
    }

    /** {@code scheduledToday} and {@code doneToday} follow the caller's today; the streaks cover the whole history. */
    public record HabitResponse(UUID id, String name, List<Integer> daysOfWeek, LocalDate startedOn, boolean archived,
                                GoalRef goal, boolean scheduledToday, boolean doneToday, int currentStreak, int longestStreak) {
    }

    public record HistoryPoint(LocalDate date, boolean scheduled, boolean done) {
    }

    /** The streaks here cover the whole history, not just the window in {@code points}. */
    public record HabitHistory(LocalDate from, LocalDate to, List<HistoryPoint> points, int currentStreak, int longestStreak) {
    }
}
