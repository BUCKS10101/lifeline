package com.personalos.backend.wellness.protein;

import com.personalos.backend.wellness.protein.domain.ProteinEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProteinEntryRepository extends JpaRepository<ProteinEntry, UUID> {

    /** One day's entries, newest first (the id breaks ties so the order is always the same). */
    List<ProteinEntry> findByUserIdAndLogDateOrderByLoggedAtDescIdDesc(UUID userId, LocalDate logDate);

    /** Adds the entry unless the client-generated id already exists, atomically. Returns 1 when added, 0 when the id was taken. */
    @Modifying
    @Query(value = """
            INSERT INTO protein_entries (id, user_id, log_date, grams, label, logged_at, created_at)
            VALUES (:id, :userId, :date, :grams, :label, :now, :now)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID id, UUID userId, LocalDate date, int grams, String label, Instant now);

    /** Returns how many rows were deleted (0 or 1). Only the owner's entry can match. */
    @Modifying
    @Query(value = "DELETE FROM protein_entries WHERE id = :id AND user_id = :userId", nativeQuery = true)
    int deleteEntry(UUID id, UUID userId);
}
