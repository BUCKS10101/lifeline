package com.personalos.backend.fitness.dto;

import com.personalos.backend.fitness.domain.MuscleGroup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SummaryDtos {

    private SummaryDtos() {
    }

    public record MuscleGroupVolume(MuscleGroup muscleGroup, BigDecimal volumeKg, int sets) {}

    /** Totals over completed workouts. {@code totalSets} and volume count working sets only, not warm-ups. */
    public record FitnessSummary(LocalDate from, LocalDate to, int workoutCount, int totalSets,
                                 BigDecimal totalVolumeKg, long totalDurationMinutes,
                                 List<MuscleGroupVolume> volumeByMuscleGroup) {}
}
