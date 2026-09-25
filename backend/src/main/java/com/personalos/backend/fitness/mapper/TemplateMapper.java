package com.personalos.backend.fitness.mapper;

import com.personalos.backend.fitness.domain.Exercise;
import com.personalos.backend.fitness.domain.WorkoutTemplate;
import com.personalos.backend.fitness.domain.WorkoutTemplateExercise;
import com.personalos.backend.fitness.dto.TemplateDtos.TemplateDetail;
import com.personalos.backend.fitness.dto.TemplateDtos.TemplateExerciseResponse;
import com.personalos.backend.fitness.dto.TemplateDtos.TemplateSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TemplateMapper {

    private final ExerciseMapper exerciseMapper;

    public TemplateMapper(ExerciseMapper exerciseMapper) {
        this.exerciseMapper = exerciseMapper;
    }

    public TemplateSummary toSummary(WorkoutTemplate template, int exerciseCount) {
        return new TemplateSummary(template.getId(), template.getName(), template.isBuiltIn(), exerciseCount);
    }

    public TemplateDetail toDetail(WorkoutTemplate template, List<WorkoutTemplateExercise> rows,
                                   Map<UUID, Exercise> exercisesById) {
        List<TemplateExerciseResponse> exercises = rows.stream()
                .map(row -> new TemplateExerciseResponse(
                        exerciseMapper.toResponse(exercisesById.get(row.getExerciseId())),
                        row.getPosition(), row.getTargetSets()))
                .toList();
        return new TemplateDetail(template.getId(), template.getName(), template.getNotes(),
                template.isBuiltIn(), exercises);
    }
}
