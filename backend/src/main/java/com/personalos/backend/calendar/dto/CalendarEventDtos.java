package com.personalos.backend.calendar.dto;

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

public final class CalendarEventDtos {

    private CalendarEventDtos() {
    }

    public static final String CLOCK = "^([01]\\d|2[0-3]):[0-5]\\d$";

    /**
     * {@code startTime} is required unless {@code allDay}; {@code endDate} and {@code endTime} are either both given
     * or both left out (an all-day event ignores {@code endTime} and only needs {@code endDate}). The timezone is
     * always the person's own profile timezone at the moment of creation: it is not a field here.
     */
    public record CreateEventRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            LocalDate startDate,
            @Pattern(regexp = CLOCK, message = "must be a time like 09:00") String startTime,
            LocalDate endDate,
            @Pattern(regexp = CLOCK, message = "must be a time like 17:30") String endTime,
            boolean allDay
    ) {}

    /** One field of a partial edit: whether the request mentioned it, and its value (null when it was mentioned as null). */
    public record Patch<T>(boolean present, T value) {
        public static <T> Patch<T> absent() {
            return new Patch<>(false, null);
        }
    }

    /**
     * A partial edit. {@code title} cannot be cleared; {@code description}, {@code endDate} and {@code endTime} can
     * be (an explicit {@code null}); {@code startDate}, {@code startTime} and {@code allDay} can be replaced but not
     * cleared. The timezone an event was created in never changes. JSON cannot tell "left out" from "null" once bound
     * to a plain record, so the body is read as a JSON object and each field is looked up (the same trick as the
     * tasks, habits and goals modules' partial edits).
     */
    public record UpdateEventRequest(Patch<String> title, Patch<String> description, Patch<LocalDate> startDate,
                                     Patch<String> startTime, Patch<LocalDate> endDate, Patch<String> endTime, Patch<Boolean> allDay) {

        public boolean isEmpty() {
            return !title.present() && !description.present() && !startDate.present() && !startTime.present()
                    && !endDate.present() && !endTime.present() && !allDay.present();
        }

        public static UpdateEventRequest from(JsonNode body, ObjectMapper mapper) {
            if (body == null || !body.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Send a JSON object");
            }
            return new UpdateEventRequest(read(body, "title", String.class, mapper), read(body, "description", String.class, mapper),
                    read(body, "startDate", LocalDate.class, mapper), read(body, "startTime", String.class, mapper),
                    read(body, "endDate", LocalDate.class, mapper), read(body, "endTime", String.class, mapper),
                    read(body, "allDay", Boolean.class, mapper));
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

    /**
     * {@code startTime}/{@code endTime} are null for an all-day event. {@code timeZone} is the zone this event was
     * created in (never the viewer's current profile zone), so history reads the same wall-clock time even if the
     * person's own timezone later changes.
     */
    public record CalendarEventResponse(UUID id, String title, String description, LocalDate startDate, String startTime,
                                        LocalDate endDate, String endTime, boolean allDay, String timeZone,
                                        Instant startAt, Instant endAt) {}
}
