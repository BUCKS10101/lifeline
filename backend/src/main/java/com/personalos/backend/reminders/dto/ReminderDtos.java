package com.personalos.backend.reminders.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.common.error.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ReminderDtos {

    private ReminderDtos() {
    }

    public static final String CLOCK = "^([01]\\d|2[0-3]):[0-5]\\d$";

    /** The timezone is always the person's own profile timezone at the moment the reminder is created. */
    public record CreateReminderRequest(
            @NotBlank @Size(max = 200) String title,
            LocalDate date,
            @Pattern(regexp = CLOCK, message = "must be a time like 09:00") String time
    ) {}

    /** One field of a partial edit: whether the request mentioned it, and its value (null when it was mentioned as null). */
    public record Patch<T>(boolean present, T value) {
        public static <T> Patch<T> absent() {
            return new Patch<>(false, null);
        }
    }

    /** A partial edit. Nothing here can be cleared (title, date and time are all required); a field left out is unchanged. */
    public record UpdateReminderRequest(Patch<String> title, Patch<LocalDate> date, Patch<String> time) {

        public boolean isEmpty() {
            return !title.present() && !date.present() && !time.present();
        }

        public static UpdateReminderRequest from(JsonNode body, ObjectMapper mapper) {
            if (body == null || !body.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Send a JSON object");
            }
            return new UpdateReminderRequest(read(body, "title", String.class, mapper), read(body, "date", LocalDate.class, mapper),
                    read(body, "time", String.class, mapper));
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

    /** {@code due} is derived: pending (not completed) and its moment has arrived. {@code timeZone} never changes after creation. */
    public record ReminderResponse(UUID id, String title, LocalDate date, String time, String timeZone,
                                   Instant remindAt, Instant completedAt, boolean due) {}
}
