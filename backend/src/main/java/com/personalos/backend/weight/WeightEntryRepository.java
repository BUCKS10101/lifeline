package com.personalos.backend.weight;

import com.personalos.backend.weight.domain.WeightEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {

    /** Entries in an inclusive date range. Sorting comes from the pageable. */
    Page<WeightEntry> findByUserIdAndEntryDateBetween(UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    /**
     * Creates the entry for that day or replaces the existing one, in a single atomic statement. The unique
     * constraint on (user_id, entry_date) decides which, so concurrent requests for the same day cannot create two
     * rows or fail. Returns true when a new row was inserted, false when an existing one was updated ({@code xmax}
     * is 0 only for a freshly inserted row). An existing entry keeps its id.
     */
    @Query(value = """
            INSERT INTO weight_entries (id, user_id, entry_date, weight_kg, notes, created_at, updated_at)
            VALUES (:id, :userId, :date, :weightKg, :notes, :now, :now)
            ON CONFLICT (user_id, entry_date)
            DO UPDATE SET weight_kg = EXCLUDED.weight_kg, notes = EXCLUDED.notes, updated_at = EXCLUDED.updated_at
            RETURNING (xmax = 0)
            """, nativeQuery = true)
    boolean upsert(UUID id, UUID userId, LocalDate date, BigDecimal weightKg, String notes, Instant now);

    /** Returns how many rows were deleted (0 or 1). */
    @Modifying
    @Query(value = "DELETE FROM weight_entries WHERE user_id = :userId AND entry_date = :date", nativeQuery = true)
    int deleteEntry(UUID userId, LocalDate date);
}
