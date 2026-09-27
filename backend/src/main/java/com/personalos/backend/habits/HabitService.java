package com.personalos.backend.habits;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.habits.HabitRepository.HabitRow;
import com.personalos.backend.habits.dto.HabitDtos.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class HabitService {

    /** Matches the CHECK constraint on habits.started_on (and, for history, habit_completions.completed_on). */
    static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    private static final int MAX_NAME = 100;
    private static final int DEFAULT_HISTORY_DAYS = 56; // 8 weeks
    private static final int MAX_HISTORY_DAYS = 366;
    /** Postgres reports the unique index by this name; used to tell a name clash from any other constraint failure. */
    private static final String DUPLICATE_NAME_INDEX = "uq_habits_user_active_name";

    private final HabitRepository habits;
    private final ProfileService profiles;
    private final Clock clock;

    public HabitService(HabitRepository habits, ProfileService profiles, Clock clock) {
        this.habits = habits;
        this.profiles = profiles;
        this.clock = clock;
    }

    // ---- reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<HabitResponse> list(UUID userId, boolean includeArchived) {
        LocalDate today = today(userId);
        var rows = habits.list(userId, includeArchived);
        var completions = habits.completionsByHabit(userId);
        return rows.stream().map(row -> toResponse(row, today, new HashSet<>(completions.getOrDefault(row.id(), List.of())))).toList();
    }

    @Transactional(readOnly = true)
    public List<HabitResponse> todayList(UUID userId) {
        return list(userId, false).stream().filter(HabitResponse::scheduledToday).toList();
    }

    @Transactional(readOnly = true)
    public HabitHistory history(UUID userId, UUID id, LocalDate from, LocalDate to) {
        HabitRow row = require(userId, id);
        LocalDate today = today(userId);
        LocalDate end = to != null && to.isBefore(today) ? to : today; // a future 'to' is clamped: nothing can have happened yet
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_HISTORY_DAYS - 1);
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_HISTORY_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "The range can be at most " + MAX_HISTORY_DAYS + " days");
        }

        Set<LocalDate> completed = new HashSet<>(habits.completions(id));
        List<HistoryPoint> points = new java.util.ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            boolean scheduled = !day.isBefore(row.startedOn()) && WeekdayMask.isScheduled(row.daysOfWeek(), day);
            points.add(new HistoryPoint(day, scheduled, completed.contains(day)));
        }
        var streaks = HabitStreaks.compute(row.daysOfWeek(), row.startedOn(), today, completed);
        return new HabitHistory(start, end, points, streaks.current(), streaks.longest());
    }

    // ---- writing -------------------------------------------------------------------------------

    @Transactional
    public HabitResponse create(UUID userId, CreateHabitRequest request) {
        String name = requireName(request.name());
        int mask = requireSchedule(request.daysOfWeek());
        LocalDate today = today(userId);
        LocalDate startedOn = request.startedOn() != null ? request.startedOn() : today;
        requireStartedOn(startedOn, today);
        requireOwnGoal(userId, request.goalId());

        UUID id;
        try {
            id = habits.insert(userId, request.goalId(), name, mask, startedOn, Instant.now(clock));
        } catch (DataIntegrityViolationException e) {
            throw duplicateNameOr(e);
        }
        return detail(userId, id, today);
    }

    @Transactional
    public HabitResponse update(UUID userId, UUID id, UpdateHabitRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Provide at least one field to change");
        }
        HabitRow current = require(userId, id);
        LocalDate today = today(userId);

        String name = current.name();
        if (request.name().present()) {
            name = requireName(request.name().value());
        }
        int mask = current.daysOfWeek();
        if (request.daysOfWeek().present()) {
            mask = requireSchedule(request.daysOfWeek().value());
        }
        LocalDate startedOn = current.startedOn();
        if (request.startedOn().present()) {
            startedOn = request.startedOn().value();
            if (startedOn == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "startedOn cannot be cleared");
            }
            requireStartedOn(startedOn, today);
            LocalDate earliestTick = habits.earliestCompletion(id).orElse(null);
            if (earliestTick != null && startedOn.isAfter(earliestTick)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_START_DATE",
                        "The start date cannot be later than the earliest tick (" + earliestTick + ")");
            }
        }
        UUID goalId = current.goal() == null ? null : current.goal().id();
        if (request.goalId().present()) {
            goalId = request.goalId().value();
            requireOwnGoal(userId, goalId);
        }

        try {
            habits.update(id, userId, goalId, name, mask, startedOn, Instant.now(clock));
        } catch (DataIntegrityViolationException e) {
            throw duplicateNameOr(e);
        }
        return detail(userId, id, today);
    }

    /** Idempotent: archiving an already-archived habit changes nothing. */
    @Transactional
    public HabitResponse archive(UUID userId, UUID id) {
        HabitRow current = require(userId, id);
        if (!current.archived()) {
            habits.setArchived(id, userId, true, Instant.now(clock));
        }
        return detail(userId, id, today(userId));
    }

    /** Idempotent: unarchiving an already-active habit changes nothing. A name now taken by another active habit is 409. */
    @Transactional
    public HabitResponse unarchive(UUID userId, UUID id) {
        HabitRow current = require(userId, id);
        if (current.archived()) {
            try {
                habits.setArchived(id, userId, false, Instant.now(clock));
            } catch (DataIntegrityViolationException e) {
                throw duplicateNameOr(e);
            }
        }
        return detail(userId, id, today(userId));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (habits.delete(id, userId) == 0) throw notFound();
        // habit_completions cascades on delete (ON DELETE CASCADE); nothing else to clean up.
    }

    /** The habit's fresh state, and whether this call newly ticked the day (false when it was already ticked). */
    public record TickResult(HabitResponse habit, boolean created) {}

    /** Ticks the given day. Idempotent: ticking an already-ticked day changes nothing. */
    @Transactional
    public TickResult tick(UUID userId, UUID id, LocalDate date) {
        HabitRow row = require(userId, id);
        LocalDate today = today(userId);
        requireTickableDate(date, row.startedOn(), today);
        boolean created = habits.tick(id, date, Instant.now(clock)) == 1;
        return new TickResult(detail(userId, id, today), created);
    }

    @Transactional
    public void untick(UUID userId, UUID id, LocalDate date) {
        require(userId, id);
        if (habits.untick(id, date) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "That day was not ticked");
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private HabitResponse detail(UUID userId, UUID id, LocalDate today) {
        HabitRow row = habits.find(userId, id).orElseThrow(HabitService::notFound);
        Set<LocalDate> completed = new HashSet<>(habits.completions(id));
        return toResponse(row, today, completed);
    }

    private static HabitResponse toResponse(HabitRow row, LocalDate today, Set<LocalDate> completed) {
        boolean scheduledToday = !today.isBefore(row.startedOn()) && WeekdayMask.isScheduled(row.daysOfWeek(), today);
        var streaks = HabitStreaks.compute(row.daysOfWeek(), row.startedOn(), today, completed);
        return new HabitResponse(row.id(), row.name(), WeekdayMask.toWeekdays(row.daysOfWeek()), row.startedOn(), row.archived(),
                row.goal(), scheduledToday, completed.contains(today), streaks.current(), streaks.longest());
    }

    private HabitRow require(UUID userId, UUID id) {
        return habits.find(userId, id).orElseThrow(HabitService::notFound);
    }

    private void requireOwnGoal(UUID userId, UUID goalId) {
        if (goalId != null && !habits.ownsGoal(userId, goalId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Goal not found");
        }
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "name must not be blank");
        if (trimmed.length() > MAX_NAME) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "name must be at most " + MAX_NAME + " characters");
        }
        return trimmed;
    }

    private static int requireSchedule(List<Integer> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "daysOfWeek must have at least one day");
        }
        if (WeekdayMask.hasDuplicates(daysOfWeek)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "daysOfWeek must not repeat a day");
        }
        try {
            return WeekdayMask.fromWeekdays(daysOfWeek);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "daysOfWeek must each be 1 (Monday) to 7 (Sunday)");
        }
    }

    private static void requireStartedOn(LocalDate startedOn, LocalDate today) {
        if (startedOn.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_IN_FUTURE", "A habit cannot start in the future");
        }
        if (startedOn.isBefore(EARLIEST)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
    }

    private static void requireTickableDate(LocalDate date, LocalDate startedOn, LocalDate today) {
        if (date.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_IN_FUTURE", "You cannot tick a future date");
        }
        if (date.isBefore(startedOn)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_BEFORE_START", "That is before this habit started");
        }
    }

    private static ApiException duplicateNameOr(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains(DUPLICATE_NAME_INDEX)) {
            return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_HABIT", "An active habit with this name already exists");
        }
        throw e;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Habit not found");
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
    }
}
