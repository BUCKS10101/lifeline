package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.WorkoutTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutTemplateRepository extends JpaRepository<WorkoutTemplate, UUID> {

    @Query("select t from WorkoutTemplate t where t.id = :id and (t.ownerId is null or t.ownerId = :userId)")
    Optional<WorkoutTemplate> findVisible(UUID id, UUID userId);

    @Query("select t from WorkoutTemplate t where t.ownerId is null or t.ownerId = :userId")
    Page<WorkoutTemplate> findAllVisible(UUID userId, Pageable pageable);
}
