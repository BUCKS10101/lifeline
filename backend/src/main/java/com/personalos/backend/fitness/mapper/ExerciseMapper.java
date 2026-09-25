package com.personalos.backend.fitness.mapper;

import com.personalos.backend.fitness.domain.Exercise;
import com.personalos.backend.fitness.dto.ExerciseDtos.ExerciseResponse;
import org.springframework.stereotype.Component;

@Component
public class ExerciseMapper {

    public ExerciseResponse toResponse(Exercise e) {
        return new ExerciseResponse(e.getId(), e.getName(), e.getPrimaryMuscleGroup(), e.getEquipment(),
                e.isBuiltIn(), e.isArchived());
    }
}
