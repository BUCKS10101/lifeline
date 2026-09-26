package com.personalos.backend.wellness.water.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One drink. Read-only on purpose: every write goes through the repository's atomic insert-if-absent. */
@Entity
@Table(name = "water_entries")
@Immutable
public class WaterEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "amount_ml", nullable = false)
    private int amountMl;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WaterEntry() {
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getLogDate() { return logDate; }
    public int getAmountMl() { return amountMl; }
    public Instant getLoggedAt() { return loggedAt; }
}
