package com.personalos.backend.wellness.sleep;

import com.personalos.backend.wellness.sleep.domain.SleepEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SleepEntryRepository extends JpaRepository<SleepEntry, UUID> {

    /** The night that ended on this date, if it was logged. */
    Optional<SleepEntry> findByUserIdAndSleepDate(UUID userId, LocalDate sleepDate);

    /** Nights in an inclusive range of wake-up dates. Sorting comes from the pageable. */
    Page<SleepEntry> findByUserIdAndSleepDateBetween(UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    /**
     * Creates the night or replaces the existing one, in a single atomic statement decided by the unique key on
     * (user_id, sleep_date). Returns true when a new row was inserted, false when one was updated ({@code xmax} is 0 only
     * for a freshly inserted row). An existing night keeps its id.
     */
    @Query(value = """
            INSERT INTO sleep_entries (id, user_id, sleep_date, bedtime_at, woke_at, zone_id, created_at, updated_at)
            VALUES (:id, :userId, :date, :bedtimeAt, :wokeAt, :zoneId, :now, :now)
            ON CONFLICT (user_id, sleep_date)
            DO UPDATE SET bedtime_at = EXCLUDED.bedtime_at, woke_at = EXCLUDED.woke_at, zone_id = EXCLUDED.zone_id,
                          updated_at = EXCLUDED.updated_at
            RETURNING (xmax = 0)
            """, nativeQuery = true)
    boolean upsert(UUID id, UUID userId, LocalDate date, Instant bedtimeAt, Instant wokeAt, String zoneId, Instant now);

    /** Returns how many rows were deleted (0 or 1). */
    @Modifying
    @Query(value = "DELETE FROM sleep_entries WHERE user_id = :userId AND sleep_date = :date", nativeQuery = true)
    int deleteNight(UUID userId, LocalDate date);
}
