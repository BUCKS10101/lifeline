package com.personalos.backend.fitness.mapper;

import com.personalos.backend.fitness.domain.Workout;
import com.personalos.backend.fitness.domain.WorkoutSet;
import com.personalos.backend.fitness.dto.WorkoutDtos.SetResponse;
import com.personalos.backend.fitness.domain.PersonalRecordType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

@Component
public class WorkoutMapper {

    public SetResponse toSetResponse(WorkoutSet set, Collection<PersonalRecordType> records) {
        return new SetResponse(set.getId(), set.getSetNumber(), set.getWeightKg(), set.getReps(), set.getRpe(),
                set.isWarmup(), records == null ? List.of() : records.stream().sorted().toList());
    }

    /** Whole minutes between start and finish; null while the workout is still in progress. */
    public Long durationMinutes(Workout workout) {
        return workout.getFinishedAt() == null ? null
                : Duration.between(workout.getStartedAt(), workout.getFinishedAt()).toMinutes();
    }

    public static BigDecimal volume(WorkoutSet set) {
        return set.getWeightKg().multiply(BigDecimal.valueOf(set.getReps()));
    }
}
