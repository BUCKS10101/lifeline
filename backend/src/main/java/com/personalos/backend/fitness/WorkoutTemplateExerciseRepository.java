package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.WorkoutTemplateExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkoutTemplateExerciseRepository extends JpaRepository<WorkoutTemplateExercise, UUID> {

    List<WorkoutTemplateExercise> findByTemplateIdOrderByPosition(UUID templateId);

    List<WorkoutTemplateExercise> findByTemplateIdInOrderByTemplateIdAscPositionAsc(Collection<UUID> templateIds);

    boolean existsByExerciseId(UUID exerciseId);

    /** Executes immediately, so the delete is not reordered after the inserts that replace these rows. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkoutTemplateExercise x where x.templateId = :templateId")
    void deleteByTemplateId(UUID templateId);
}
