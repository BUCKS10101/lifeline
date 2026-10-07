package com.personalos.backend.goals;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.goals.GoalRepository.LinkedHabitRow;
import com.personalos.backend.goals.dto.GoalDtos.*;
import com.personalos.backend.habits.HabitRepository;
import com.personalos.backend.habits.HabitStreaks;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class GoalService {

    /** Matches the CHECK constraint on goals.target_date. */
    static final LocalDate EARLIEST_TARGET = LocalDate.of(2000, 1, 1);
    static final LocalDate LATEST_TARGET = LocalDate.of(2100, 12, 31);
    private static final int MAX_TITLE = 120;
    private static final int MAX_DESCRIPTION = 1000;

    private final GoalRepository goals;
    private final HabitRepository habits;
    private final ProfileService profiles;
    private final Clock clock;

    public GoalService(GoalRepository goals, HabitRepository habits, ProfileService profiles, Clock clock) {
        this.goals = goals;
        this.habits = habits;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public GoalResponse create(UUID userId, CreateGoalRequest request) {
        String title = requireTitle(request.title());
        String description = blankToNull(request.description());
        if (description != null && description.length() > MAX_DESCRIPTION) throw tooLong("description", MAX_DESCRIPTION);
        requireTargetDate(request.targetDate());

        UUID id = goals.insert(userId, title, description, request.targetDate(), Instant.now(clock));
        return goals.find(userId, id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId, GoalStatus status) {
        return goals.list(userId, status);
    }

    @Transactional(readOnly = true)
    public GoalDetail get(UUID userId, UUID id) {
        GoalResponse goal = goals.find(userId, id).orElseThrow(GoalService::notFound);
        LocalDate today = today(userId);
        var tasks = goals.linkedTasks(userId, id, today);
        var habitItems = goals.linkedHabits(userId, id).stream().map(row -> toHabitItem(row, today)).toList();
        return new GoalDetail(goal, tasks, habitItems);
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID id, UpdateGoalRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Provide at least one field to change");
        }
        GoalResponse current = goals.find(userId, id).orElseThrow(GoalService::notFound);

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
        LocalDate targetDate = current.targetDate();
        if (request.targetDate().present()) {
            targetDate = request.targetDate().value();
            requireTargetDate(targetDate);
        }
        GoalStatus status = current.status();
        if (request.status().present()) {
            if (request.status().value() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "status cannot be cleared");
            }
            status = request.status().value();
        }
        // Achieving sets achieved_at (kept from before if it was already achieved); anything else clears it.
        Instant achievedAt = status == GoalStatus.ACHIEVED ? (current.achievedAt() != null ? current.achievedAt() : Instant.now(clock)) : null;

        goals.update(id, userId, title, description, targetDate, status, achievedAt, Instant.now(clock));
        return goals.find(userId, id).orElseThrow();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        // tasks.goal_id and habits.goal_id are ON DELETE SET NULL: this unlinks them, it never deletes them.
        if (goals.delete(id, userId) == 0) throw notFound();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private GoalHabitItem toHabitItem(LinkedHabitRow row, LocalDate today) {
        var completed = new HashSet<>(habits.completions(row.id()));
        var streaks = HabitStreaks.compute(row.daysOfWeek(), row.startedOn(), today, completed);
        return new GoalHabitItem(row.id(), row.name(), row.archived(), streaks.current(), streaks.longest());
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
    }

    private static String requireTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "title must not be blank");
        if (trimmed.length() > MAX_TITLE) throw tooLong("title", MAX_TITLE);
        return trimmed;
    }

    private static void requireTargetDate(LocalDate date) {
        if (date == null) return;
        if (date.isBefore(EARLIEST_TARGET)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
        if (date.isAfter(LATEST_TARGET)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_LATE", "Dates after 2100-12-31 are not supported");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Goal not found");
    }

    private static ApiException tooLong(String field, int max) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " must be at most " + max + " characters");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
