package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID>, JpaSpecificationExecutor<Exercise> {

    /** An exercise the user may see: a built-in, or one they own. Anything else looks like "not found". */
    @Query("select e from Exercise e where e.id = :id and (e.ownerId is null or e.ownerId = :userId)")
    Optional<Exercise> findVisible(UUID id, UUID userId);

    @Query("select e from Exercise e where e.id in :ids and (e.ownerId is null or e.ownerId = :userId)")
    List<Exercise> findVisibleByIds(Collection<UUID> ids, UUID userId);

    /** Is this (lower-cased) name taken by an active built-in or by one of the user's active exercises? */
    @Query("""
            select count(e) > 0 from Exercise e
            where lower(e.name) = :lowerName and e.archivedAt is null
              and (e.ownerId is null or e.ownerId = :userId)
              and (:excludeId is null or e.id <> :excludeId)
            """)
    boolean nameTaken(String lowerName, UUID userId, UUID excludeId);
}
