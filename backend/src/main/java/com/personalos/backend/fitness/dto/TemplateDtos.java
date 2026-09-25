package com.personalos.backend.fitness.dto;

import com.personalos.backend.fitness.dto.ExerciseDtos.ExerciseResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public final class TemplateDtos {

    private TemplateDtos() {
    }

    public record TemplateSummary(UUID id, String name, boolean builtIn, int exerciseCount) {}

    public record TemplateExerciseResponse(ExerciseResponse exercise, int position, Integer targetSets) {}

    public record TemplateDetail(UUID id, String name, String notes, boolean builtIn,
                                 List<TemplateExerciseResponse> exercises) {}

    public record TemplateExerciseInput(
            @NotNull UUID exerciseId,
            @Min(1) @Max(20) Integer targetSets
    ) {}

    /** Used for both create and full replace. The list order is the exercise order. */
    public record TemplateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String notes,
            @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid TemplateExerciseInput> exercises
    ) {}
}
