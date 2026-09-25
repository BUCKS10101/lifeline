package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, UUID> {

    List<WorkoutSet> findByWorkoutExerciseIdOrderBySetNumber(UUID workoutExerciseId);

    List<WorkoutSet> findByWorkoutExerciseIdInOrderBySetNumber(Collection<UUID> workoutExerciseIds);

    Optional<WorkoutSet> findByIdAndWorkoutExerciseId(UUID id, UUID workoutExerciseId);

    int countByWorkoutExerciseId(UUID workoutExerciseId);

    /**
     * Every set of the given exercises for one user, oldest first: completed workouts plus the
     * (possibly in-progress) current workout, up to {@code upTo}. Warm-ups are included; callers decide.
     */
    @Query("""
            select new com.personalos.backend.fitness.HistoryRow(we.exerciseId, w.id, w.performedOn, w.startedAt,
                                                                 s.id, s.setNumber, s.weightKg, s.reps, s.warmup)
            from WorkoutSet s
            join WorkoutExercise we on we.id = s.workoutExerciseId
            join Workout w on w.id = we.workoutId
            where w.userId = :userId and we.exerciseId in :exerciseIds
              and (w.status = :completed or w.id = :currentWorkoutId)
              and w.startedAt <= :upTo
            order by w.startedAt, w.id, s.setNumber
            """)
    List<HistoryRow> findHistory(UUID userId, Collection<UUID> exerciseIds,
                                 com.personalos.backend.fitness.domain.WorkoutStatus completed,
                                 UUID currentWorkoutId, java.time.Instant upTo);

    /** Working sets of a user's completed workouts in a date range, with each exercise's muscle group. */
    @Query("""
            select new com.personalos.backend.fitness.SetVolumeRow(e.primaryMuscleGroup, s.weightKg, s.reps)
            from WorkoutSet s
            join WorkoutExercise we on we.id = s.workoutExerciseId
            join Workout w on w.id = we.workoutId
            join Exercise e on e.id = we.exerciseId
            where w.userId = :userId and w.status = :completed
              and w.performedOn between :from and :to and s.warmup = false
            """)
    List<SetVolumeRow> findWorkingSetVolumes(UUID userId, com.personalos.backend.fitness.domain.WorkoutStatus completed,
                                             java.time.LocalDate from, java.time.LocalDate to);

    @Query("select coalesce(max(s.setNumber), 0) from WorkoutSet s where s.workoutExerciseId = :workoutExerciseId")
    int maxSetNumber(UUID workoutExerciseId);
}
