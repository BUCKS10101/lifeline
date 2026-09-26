package com.personalos.backend.wellness.water;

import com.personalos.backend.wellness.water.domain.WaterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WaterEntryRepository extends JpaRepository<WaterEntry, UUID> {

    /** One day's drinks, newest first (the id breaks ties so the order is always the same). */
    List<WaterEntry> findByUserIdAndLogDateOrderByLoggedAtDescIdDesc(UUID userId, LocalDate logDate);

    /**
     * Adds the drink unless the client-generated id already exists, in a single atomic statement. Returns 1 when it was
     * added and 0 when the id was already taken, which makes a retry (or a double tap) unable to count twice.
     */
    @Modifying
    @Query(value = """
            INSERT INTO water_entries (id, user_id, log_date, amount_ml, logged_at, created_at)
            VALUES (:id, :userId, :date, :amountMl, :now, :now)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID id, UUID userId, LocalDate date, int amountMl, Instant now);

    /** Returns how many rows were deleted (0 or 1). Only the owner's entry can match. */
    @Modifying
    @Query(value = "DELETE FROM water_entries WHERE id = :id AND user_id = :userId", nativeQuery = true)
    int deleteEntry(UUID id, UUID userId);
}
