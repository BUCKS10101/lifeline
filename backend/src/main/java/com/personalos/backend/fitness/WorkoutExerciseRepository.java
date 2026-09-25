package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.WorkoutExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, UUID> {

    List<WorkoutExercise> findByWorkoutIdOrderByPosition(UUID workoutId);

    List<WorkoutExercise> findByWorkoutIdIn(Collection<UUID> workoutIds);

    List<WorkoutExercise> findByWorkoutIdInAndExerciseId(Collection<UUID> workoutIds, UUID exerciseId);

    Optional<WorkoutExercise> findByIdAndWorkoutId(UUID id, UUID workoutId);

    boolean existsByWorkoutIdAndExerciseId(UUID workoutId, UUID exerciseId);

    int countByWorkoutId(UUID workoutId);

    boolean existsByExerciseId(UUID exerciseId);
}
