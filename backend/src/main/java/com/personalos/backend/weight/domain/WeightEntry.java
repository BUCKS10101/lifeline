package com.personalos.backend.weight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One body-weight reading for one day. Read-only on purpose: every write goes through the repository's atomic
 * upsert, so two requests for the same day can never race into a duplicate or a lost update. Mapping the table as
 * an entity still means Hibernate validates the schema against it at startup.
 */
@Entity
@Table(name = "weight_entries")
@Immutable
public class WeightEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WeightEntry() {
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getEntryDate() { return entryDate; }
    public BigDecimal getWeightKg() { return weightKg; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
