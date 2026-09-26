package com.personalos.backend.fitness.dto;

import com.personalos.backend.fitness.domain.MovementGroup;
import com.personalos.backend.fitness.domain.PersonalRecordType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    // ---- volume -------------------------------------------------------------------------------

    public record MovementGroupVolume(MovementGroup movementGroup, BigDecimal volumeKg, int sets) {}

    /**
     * One week (Monday start) or calendar month. {@code periodStart} can be before the requested {@code from} for
     * the first bucket; only workouts inside the requested range are counted. Always lists all four groups.
     */
    public record VolumePoint(LocalDate periodStart, int workouts, int workingSets, BigDecimal volumeKg,
                              List<MovementGroupVolume> byMovementGroup) {}

    public record VolumeSeries(String granularity, LocalDate from, LocalDate to, List<VolumePoint> points) {}

    // ---- progression --------------------------------------------------------------------------

    public record TopSet(BigDecimal weightKg, int reps) {}

    /** One completed session of the exercise. Everything ignores warm-ups. */
    public record ProgressionPoint(UUID workoutId, LocalDate performedOn, TopSet topSet, BigDecimal bestEstimated1rmKg,
                                   BigDecimal volumeKg, int workingSets) {}

    public record ExerciseProgression(LocalDate from, LocalDate to, List<ProgressionPoint> points) {}

    // ---- personal records ---------------------------------------------------------------------

    /** A set that was a record of the given type when it was performed. */
    public record PersonalRecordEvent(LocalDate performedOn, UUID workoutId, UUID setId, PersonalRecordType type,
                                      BigDecimal weightKg, int reps, BigDecimal estimated1rmKg) {}

    public record ExerciseRef(UUID id, String name) {}

    public record ExercisePersonalRecord(ExerciseRef exercise, PersonalRecordEvent record) {}
}
