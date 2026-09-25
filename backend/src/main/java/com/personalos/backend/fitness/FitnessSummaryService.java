package com.personalos.backend.fitness;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.fitness.domain.MuscleGroup;
import com.personalos.backend.fitness.domain.Workout;
import com.personalos.backend.fitness.domain.WorkoutStatus;
import com.personalos.backend.fitness.dto.SummaryDtos.FitnessSummary;
import com.personalos.backend.fitness.dto.SummaryDtos.MuscleGroupVolume;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Basic totals over completed workouts. Computed on demand from the sets; nothing is cached or stored. */
@Service
public class FitnessSummaryService {

    private static final int MAX_RANGE_DAYS = 366;

    private final WorkoutRepository workouts;
    private final WorkoutSetRepository sets;
    private final ProfileService profiles;
    private final Clock clock;

    public FitnessSummaryService(WorkoutRepository workouts, WorkoutSetRepository sets, ProfileService profiles, Clock clock) {
        this.workouts = workouts;
        this.sets = sets;
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * With neither date the range is the current Monday to Sunday in the user's timezone. With only one, the range
     * is seven days starting at {@code from} or ending at {@code to}.
     */
    @Transactional(readOnly = true)
    public FitnessSummary summary(UUID userId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
        if (from == null && to == null) {
            from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            to = from.plusDays(6);
        } else if (to == null) {
            to = from.plusDays(6);
        } else if (from == null) {
            from = to.minusDays(6);
        }
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "The range can be at most " + MAX_RANGE_DAYS + " days");
        }

        LocalDate start = from;
        LocalDate end = to;
        List<Workout> completed = workouts.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                cb.equal(root.get("status"), WorkoutStatus.COMPLETED),
                cb.between(root.get("performedOn"), start, end)));
        long minutes = completed.stream()
                .mapToLong(w -> Duration.between(w.getStartedAt(), w.getFinishedAt()).toMinutes()).sum();

        Map<MuscleGroup, BigDecimal> volumeByGroup = new EnumMap<>(MuscleGroup.class);
        Map<MuscleGroup, Integer> setsByGroup = new EnumMap<>(MuscleGroup.class);
        for (SetVolumeRow row : sets.findWorkingSetVolumes(userId, WorkoutStatus.COMPLETED, from, to)) {
            volumeByGroup.merge(row.muscleGroup(), row.weightKg().multiply(BigDecimal.valueOf(row.reps())), BigDecimal::add);
            setsByGroup.merge(row.muscleGroup(), 1, Integer::sum);
        }
        List<MuscleGroupVolume> byGroup = volumeByGroup.entrySet().stream()
                .map(e -> new MuscleGroupVolume(e.getKey(), e.getValue(), setsByGroup.get(e.getKey())))
                .sorted(Comparator.comparing(MuscleGroupVolume::volumeKg).reversed()
                        .thenComparing(v -> v.muscleGroup().name()))
                .toList();

        return new FitnessSummary(from, to, completed.size(),
                byGroup.stream().mapToInt(MuscleGroupVolume::sets).sum(),
                byGroup.stream().map(MuscleGroupVolume::volumeKg).reduce(BigDecimal.ZERO, BigDecimal::add),
                minutes, byGroup);
    }
}
