package com.personalos.backend.reminders;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.reminders.ReminderRepository.Row;
import com.personalos.backend.reminders.dto.ReminderDtos;
import com.personalos.backend.reminders.dto.ReminderDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Lightweight reminders: a title and a moment to go off at. No delivery infrastructure; LifeLine only surfaces what is due or upcoming. */
@Service
public class ReminderService {

    static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    static final LocalDate LATEST = LocalDate.of(2100, 12, 31);
    private static final int MAX_TITLE = 200;
    private static final Pattern CLOCK = Pattern.compile(ReminderDtos.CLOCK);

    private final ReminderRepository reminders;
    private final ProfileService profiles;
    private final Clock clock;

    public ReminderService(ReminderRepository reminders, ProfileService profiles, Clock clock) {
        this.reminders = reminders;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> list(UUID userId, boolean includeCompleted) {
        Instant now = Instant.now(clock);
        return reminders.list(userId, includeCompleted).stream().map(r -> toResponse(r, now)).toList();
    }

    @Transactional
    public ReminderResponse create(UUID userId, CreateReminderRequest request) {
        String title = requireTitle(request.title());
        requireDate(request.date());
        LocalTime time = requireTime(request.time());
        ZoneId zone = profiles.timezoneOf(userId);
        Instant remindAt = request.date().atTime(time).atZone(zone).toInstant();

        UUID id = reminders.insert(userId, title, remindAt, zone.getId(), Instant.now(clock));
        return toResponse(reminders.find(userId, id).orElseThrow(), Instant.now(clock));
    }

    @Transactional
    public ReminderResponse update(UUID userId, UUID id, UpdateReminderRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Provide at least one field to change");
        }
        Row current = require(userId, id);
        ZoneId zone = ZoneId.of(current.zoneId());
        var currentZoned = current.remindAt().atZone(zone);

        String title = current.title();
        if (request.title().present()) {
            String value = request.title().value();
            if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "title cannot be cleared");
            title = requireTitle(value);
        }
        LocalDate date = request.date().present() ? request.date().value() : currentZoned.toLocalDate();
        if (date == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "date cannot be cleared");
        requireDate(date);
        LocalTime time;
        if (request.time().present()) {
            if (request.time().value() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "time cannot be cleared");
            time = requireTime(request.time().value());
        } else {
            time = currentZoned.toLocalTime().withSecond(0).withNano(0);
        }
        Instant remindAt = date.atTime(time).atZone(zone).toInstant();

        reminders.update(id, userId, title, remindAt, Instant.now(clock));
        return toResponse(reminders.find(userId, id).orElseThrow(), Instant.now(clock));
    }

    @Transactional
    public ReminderResponse complete(UUID userId, UUID id) {
        if (reminders.complete(id, userId, Instant.now(clock)) == 0) throw notFound();
        return toResponse(reminders.find(userId, id).orElseThrow(), Instant.now(clock));
    }

    @Transactional
    public ReminderResponse reopen(UUID userId, UUID id) {
        if (reminders.reopen(id, userId, Instant.now(clock)) == 0) throw notFound();
        return toResponse(reminders.find(userId, id).orElseThrow(), Instant.now(clock));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (reminders.delete(id, userId) == 0) throw notFound();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Row require(UUID userId, UUID id) {
        return reminders.find(userId, id).orElseThrow(ReminderService::notFound);
    }

    private static ReminderResponse toResponse(Row row, Instant now) {
        ZoneId zone = ZoneId.of(row.zoneId());
        var zoned = row.remindAt().atZone(zone);
        boolean due = row.completedAt() == null && !row.remindAt().isAfter(now);
        return new ReminderResponse(row.id(), row.title(), zoned.toLocalDate(), formatTime(zoned.toLocalTime()),
                row.zoneId(), row.remindAt(), row.completedAt(), due);
    }

    private static String formatTime(LocalTime time) {
        return "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }

    private static LocalTime requireTime(String text) {
        if (text == null || !CLOCK.matcher(text).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "time must be a time like 09:00");
        }
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "time must be a time like 09:00");
        }
    }

    private static void requireDate(LocalDate date) {
        if (date == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "date is required");
        if (date.isBefore(EARLIEST)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
        if (date.isAfter(LATEST)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_LATE", "Dates after 2100-12-31 are not supported");
        }
    }

    private static String requireTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "title must not be blank");
        if (trimmed.length() > MAX_TITLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "title must be at most " + MAX_TITLE + " characters");
        }
        return trimmed;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Reminder not found");
    }
}
