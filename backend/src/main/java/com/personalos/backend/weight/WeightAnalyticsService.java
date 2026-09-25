package com.personalos.backend.weight;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.weight.dto.WeightDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WeightAnalyticsService {

    private static final int DEFAULT_RANGE_DAYS = 90;

    private final WeightAnalyticsQueries queries;
    private final WeightTargetService targets;
    private final ProfileService profiles;
    private final Clock clock;

    public WeightAnalyticsService(WeightAnalyticsQueries queries, WeightTargetService targets, ProfileService profiles, Clock clock) {
        this.queries = queries;
        this.targets = targets;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** Defaults to the last 90 days ending today (the user's today), as daily points. */
    public WeightSeries series(UUID userId, LocalDate from, LocalDate to, Granularity granularity) {
        LocalDate today = today(userId);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1);
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        }
        Granularity g = granularity != null ? granularity : Granularity.DAILY;
        var points = switch (g) {
            case DAILY -> queries.daily(userId, start, end);
            case WEEKLY -> queries.buckets(userId, start, end, "week");
            case MONTHLY -> queries.buckets(userId, start, end, "month");
        };
        return new WeightSeries(g.name(), start, end, points);
    }

    /**
     * Everything is derived from the entries as they are now, so editing an old entry can change the starting
     * weight and therefore the direction of the goal.
     */
    public WeightSummary summary(UUID userId) {
        LocalDate today = today(userId);
        Optional<WeightPoint> current = queries.latest(userId);
        Optional<WeightTargetResponse> target = targets.find(userId);

        // The starting weight is the reading in force when the goal began (the latest on or before that day),
        // or the first reading after it if there was none before.
        Optional<WeightPoint> starting = target.flatMap(t ->
                queries.onOrBefore(userId, t.startedOn()).or(() -> queries.onOrAfter(userId, t.startedOn())));

        Progress progress = target.isPresent() && current.isPresent() && starting.isPresent()
                ? TargetProgress.compute(starting.get().weightKg(), current.get().weightKg(), target.get().targetWeightKg())
                : null;

        ChangeWindows change = current.map(c -> new ChangeWindows(change(userId, c, 7), change(userId, c, 30))).orElse(null);

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeekAverages weeks = new WeekAverages(
                week(userId, weekStart), week(userId, weekStart.minusWeeks(1)));

        return new WeightSummary(current.orElse(null), starting.orElse(null), target.orElse(null), progress, change, weeks,
                queries.count(userId));
    }

    /** Change since the most recent entry on or before (latest date - days); null if there was none that old. */
    private Change change(UUID userId, WeightPoint latest, int days) {
        return queries.onOrBefore(userId, latest.date().minusDays(days))
                .map(b -> new Change(latest.weightKg().subtract(b.weightKg()), b.weightKg(), b.date()))
                .orElse(null);
    }

    private WeekAverage week(UUID userId, LocalDate monday) {
        return queries.average(userId, monday, monday.plusDays(6))
                .map(a -> new WeekAverage(monday, a.averageKg(), a.entries()))
                .orElse(null);
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
    }
}
