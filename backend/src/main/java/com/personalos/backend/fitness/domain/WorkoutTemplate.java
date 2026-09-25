package com.personalos.backend.fitness.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A built-in ({@code ownerId == null}, read-only) or user-owned template. */
@Entity
@Table(name = "workout_templates")
public class WorkoutTemplate extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkoutTemplate() {
    }

    public WorkoutTemplate(UUID ownerId, String name, String notes, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.notes = notes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    @Override public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getNotes() { return notes; }
    public boolean isBuiltIn() { return ownerId == null; }

    public void update(String name, String notes) {
        this.name = name;
        this.notes = notes;
    }
}
