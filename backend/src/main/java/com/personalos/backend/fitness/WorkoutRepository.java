package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.Workout;
import com.personalos.backend.fitness.domain.WorkoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID>, JpaSpecificationExecutor<Workout> {

    Optional<Workout> findByIdAndUserId(UUID id, UUID userId);

    /** Row lock (SELECT ... FOR UPDATE) that serializes concurrent mutations of one workout. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workout w where w.id = :id and w.userId = :userId")
    Optional<Workout> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    /** Completed workouts of the user that include the exercise. Sorting comes from the pageable. */
    @Query(value = """
            select w from Workout w
            where w.userId = :userId and w.status = :status
              and exists (select 1 from WorkoutExercise we where we.workoutId = w.id and we.exerciseId = :exerciseId)
            """,
            countQuery = """
            select count(w) from Workout w
            where w.userId = :userId and w.status = :status
              and exists (select 1 from WorkoutExercise we where we.workoutId = w.id and we.exerciseId = :exerciseId)
            """)
    Page<Workout> findByStatusContainingExercise(UUID userId, WorkoutStatus status, UUID exerciseId, Pageable pageable);

    Optional<Workout> findByUserIdAndStatus(UUID userId, WorkoutStatus status);

    boolean existsByUserIdAndStatus(UUID userId, WorkoutStatus status);
}
