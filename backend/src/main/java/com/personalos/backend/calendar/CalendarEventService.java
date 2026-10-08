package com.personalos.backend.calendar;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.calendar.CalendarEventRepository.Row;
import com.personalos.backend.calendar.dto.CalendarEventDtos;
import com.personalos.backend.calendar.dto.CalendarEventDtos.*;
import com.personalos.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lightweight calendar events: a title, an optional description, a start (and optional end) moment, and whether it
 * is an all-day marker. No recurrence. An event's day range is its start day through its end day (defaulting to the
 * start day), both read in the zone the event was created in; this one rule decides which day(s) a timed event spans
 * (even across midnight) and which day(s) an all-day event covers.
 */
@Service
public class CalendarEventService {

    /** Matches the CHECK constraint on calendar_events via its date inputs. */
    static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    static final LocalDate LATEST = LocalDate.of(2100, 12, 31);
    private static final int MAX_TITLE = 200;
    private static final int MAX_DESCRIPTION = 2000;
    /** A generous cap on one list request's span, so a mistaken or hostile range cannot force a huge scan. */
    private static final int MAX_RANGE_DAYS = 370;
    private static final Pattern CLOCK = Pattern.compile(CalendarEventDtos.CLOCK);

    private final CalendarEventRepository events;
    private final ProfileService profiles;
    private final Clock clock;

    public CalendarEventService(CalendarEventRepository events, ProfileService profiles, Clock clock) {
        this.events = events;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> list(UUID userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "The range can be at most " + MAX_RANGE_DAYS + " days");
        }
        // Padded by a day on each side: enough to cover any real UTC offset (-12 to +14) around the requested window.
        Instant paddedFrom = from.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant paddedTo = to.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        return events.listBetween(userId, paddedFrom, paddedTo).stream()
                .map(this::toResponse)
                .filter(e -> !(e.endDate() != null ? e.endDate() : e.startDate()).isBefore(from) && !e.startDate().isAfter(to))
                .toList();
    }

    @Transactional
    public CalendarEventResponse create(UUID userId, CreateEventRequest request) {
        String title = requireTitle(request.title());
        String description = blankToNull(request.description());
        if (description != null && description.length() > MAX_DESCRIPTION) throw tooLong("description", MAX_DESCRIPTION);
        requireDate(request.startDate(), "startDate");

        ZoneId zone = profiles.timezoneOf(userId);
        Instants instants = buildInstants(request.startDate(), request.startTime(), request.endDate(), request.endTime(), request.allDay(), zone);

        UUID id = events.insert(userId, title, description, instants.startAt(), instants.endAt(), zone.getId(), request.allDay(), Instant.now(clock));
        return events.find(userId, id).map(this::toResponse).orElseThrow();
    }

    @Transactional
    public CalendarEventResponse update(UUID userId, UUID id, UpdateEventRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Provide at least one field to change");
        }
        CalendarEventResponse current = events.find(userId, id).map(this::toResponse).orElseThrow(CalendarEventService::notFound);

        String title = current.title();
        if (request.title().present()) {
            String value = request.title().value();
            if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "title cannot be cleared");
            title = requireTitle(value);
        }
        String description = current.description();
        if (request.description().present()) {
            description = blankToNull(request.description().value());
            if (description != null && description.length() > MAX_DESCRIPTION) throw tooLong("description", MAX_DESCRIPTION);
        }
        LocalDate startDate = request.startDate().present() ? request.startDate().value() : current.startDate();
        if (startDate == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "startDate cannot be cleared");
        requireDate(startDate, "startDate");
        String startTime = request.startTime().present() ? request.startTime().value() : current.startTime();
        LocalDate endDate = request.endDate().present() ? request.endDate().value() : current.endDate();
        String endTime = request.endTime().present() ? request.endTime().value() : current.endTime();
        boolean allDay = request.allDay().present() ? requireNonNull(request.allDay().value(), "allDay") : current.allDay();

        ZoneId zone = ZoneId.of(current.timeZone()); // the zone it was created in; never changes on edit
        Instants instants = buildInstants(startDate, startTime, endDate, endTime, allDay, zone);

        events.update(id, userId, title, description, instants.startAt(), instants.endAt(), allDay, Instant.now(clock));
        return events.find(userId, id).map(this::toResponse).orElseThrow();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (events.delete(id, userId) == 0) throw notFound();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Instants(Instant startAt, Instant endAt) {}

    /** Combines the local fields into real moments and checks they describe a sensible event. */
    private static Instants buildInstants(LocalDate startDate, String startTimeText, LocalDate endDate, String endTimeText, boolean allDay, ZoneId zone) {
        if (allDay) {
            Instant startAt = startDate.atStartOfDay(zone).toInstant();
            Instant endAt = null;
            if (endDate != null) {
                requireDate(endDate, "endDate");
                if (endDate.isBefore(startDate)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "endDate must not be before startDate");
                endAt = endDate.atStartOfDay(zone).toInstant();
            }
            return new Instants(startAt, endAt);
        }
        LocalTime startTime = requireTime(startTimeText, "startTime is required for a timed event");
        Instant startAt = startDate.atTime(startTime).atZone(zone).toInstant();
        boolean hasEndDate = endDate != null;
        boolean hasEndTime = endTimeText != null;
        if (hasEndDate != hasEndTime) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "endDate and endTime must be given together");
        }
        Instant endAt = null;
        if (hasEndDate) {
            requireDate(endDate, "endDate");
            LocalTime endTime = requireTime(endTimeText, "endTime is not valid");
            endAt = endDate.atTime(endTime).atZone(zone).toInstant();
            if (endAt.isBefore(startAt)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "end must not be before the start");
        }
        return new Instants(startAt, endAt);
    }

    private CalendarEventResponse toResponse(Row row) {
        ZoneId zone = ZoneId.of(row.zoneId());
        ZonedDateTime start = row.startAt().atZone(zone);
        ZonedDateTime end = row.endAt() == null ? null : row.endAt().atZone(zone);
        return new CalendarEventResponse(row.id(), row.title(), row.description(), start.toLocalDate(),
                row.allDay() ? null : formatTime(start.toLocalTime()), end == null ? null : end.toLocalDate(),
                (row.allDay() || end == null) ? null : formatTime(end.toLocalTime()), row.allDay(), row.zoneId(),
                row.startAt(), row.endAt());
    }

    private static String formatTime(LocalTime time) {
        return "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }

    private static LocalTime requireTime(String text, String message) {
        if (text == null || !CLOCK.matcher(text).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
        }
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
        }
    }

    private static void requireDate(LocalDate date, String field) {
        if (date.isBefore(EARLIEST)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
        if (date.isAfter(LATEST)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_LATE", "Dates after 2100-12-31 are not supported");
        }
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " cannot be cleared");
        return value;
    }

    private static String requireTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "title must not be blank");
        if (trimmed.length() > MAX_TITLE) throw tooLong("title", MAX_TITLE);
        return trimmed;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Event not found");
    }

    private static ApiException tooLong(String field, int max) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " must be at most " + max + " characters");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
