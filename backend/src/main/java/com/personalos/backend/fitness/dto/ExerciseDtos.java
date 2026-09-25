package com.personalos.backend.fitness.dto;

import com.personalos.backend.fitness.domain.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ExerciseDtos {

    private ExerciseDtos() {
    }

    public record ExerciseResponse(UUID id, String name, MuscleGroup primaryMuscleGroup, String equipment,
                                   boolean builtIn, boolean archived) {}

    public record CreateExerciseRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull MuscleGroup primaryMuscleGroup,
            @Size(max = 30) String equipment
    ) {}

    /** All fields optional; at least one is required. A blank {@code equipment} clears it. */
    public record UpdateExerciseRequest(
            @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
            MuscleGroup primaryMuscleGroup,
            @Size(max = 30) String equipment
    ) {}

    public record HistorySet(int setNumber, BigDecimal weightKg, int reps, BigDecimal rpe, boolean warmup) {}

    /** One completed workout's sets for the exercise. Top set and volume ignore warm-ups. */
    public record ExerciseHistoryItem(UUID workoutId, LocalDate performedOn, List<HistorySet> sets,
                                      HistorySet topSet, BigDecimal volumeKg) {}

    public record RecordMark(BigDecimal weightKg, int reps, LocalDate performedOn) {}

    public record EstimatedRecordMark(BigDecimal valueKg, BigDecimal weightKg, int reps, LocalDate performedOn) {}

    public record ExerciseRecords(ExerciseResponse exercise, RecordMark heaviestWeight,
                                  EstimatedRecordMark bestEstimated1rm, List<RecordMark> bestRepsAtWeight) {}
}
