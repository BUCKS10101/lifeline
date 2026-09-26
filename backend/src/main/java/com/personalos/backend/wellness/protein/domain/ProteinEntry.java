package com.personalos.backend.wellness.protein.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One protein entry: grams and an optional label. Read-only on purpose: writes go through the atomic insert-if-absent. */
@Entity
@Table(name = "protein_entries")
@Immutable
public class ProteinEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    private int grams;

    private String label;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProteinEntry() {
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getLogDate() { return logDate; }
    public int getGrams() { return grams; }
    public String getLabel() { return label; }
    public Instant getLoggedAt() { return loggedAt; }
}
