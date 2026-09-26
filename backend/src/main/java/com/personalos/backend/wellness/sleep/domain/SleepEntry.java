package com.personalos.backend.wellness.sleep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One night's sleep, keyed by the local date the person woke up. Read-only on purpose: every write goes through the
 * repository's atomic upsert, so two requests for the same night can never race into a duplicate.
 */
@Entity
@Table(name = "sleep_entries")
@Immutable
public class SleepEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sleep_date", nullable = false)
    private LocalDate sleepDate;

    @Column(name = "bedtime_at", nullable = false)
    private Instant bedtimeAt;

    @Column(name = "woke_at", nullable = false)
    private Instant wokeAt;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SleepEntry() {
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getSleepDate() { return sleepDate; }
    public Instant getBedtimeAt() { return bedtimeAt; }
    public Instant getWokeAt() { return wokeAt; }
    public String getZoneId() { return zoneId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
