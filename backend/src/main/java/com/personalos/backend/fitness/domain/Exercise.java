package com.personalos.backend.fitness.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A built-in ({@code ownerId == null}, read-only) or user-owned exercise. */
@Entity
@Table(name = "exercises")
public class Exercise extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_muscle_group", nullable = false)
    private MuscleGroup primaryMuscleGroup;

    private String equipment;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Exercise() {
    }

    public Exercise(UUID ownerId, String name, MuscleGroup primaryMuscleGroup, String equipment, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.primaryMuscleGroup = primaryMuscleGroup;
        this.equipment = equipment;
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
    public MuscleGroup getPrimaryMuscleGroup() { return primaryMuscleGroup; }
    public String getEquipment() { return equipment; }
    public Instant getArchivedAt() { return archivedAt; }

    public boolean isBuiltIn() { return ownerId == null; }
    public boolean isArchived() { return archivedAt != null; }

    public void rename(String name) { this.name = name; }
    public void changeMuscleGroup(MuscleGroup group) { this.primaryMuscleGroup = group; }
    public void changeEquipment(String equipment) { this.equipment = equipment; }
    public void archive(Instant now) { this.archivedAt = now; }
}
