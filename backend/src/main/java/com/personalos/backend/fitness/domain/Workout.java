package com.personalos.backend.fitness.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "workouts")
public class Workout extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkoutStatus status;

    @Column(name = "performed_on", nullable = false, updatable = false)
    private LocalDate performedOn;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Workout() {
    }

    public Workout(UUID userId, UUID templateId, String name, LocalDate performedOn, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.templateId = templateId;
        this.name = name;
        this.status = WorkoutStatus.IN_PROGRESS;
        this.performedOn = performedOn;
        this.startedAt = startedAt;
        this.createdAt = startedAt;
        this.updatedAt = startedAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    @Override public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTemplateId() { return templateId; }
    public String getName() { return name; }
    public WorkoutStatus getStatus() { return status; }
    public LocalDate getPerformedOn() { return performedOn; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getNotes() { return notes; }

    public boolean isInProgress() { return status == WorkoutStatus.IN_PROGRESS; }

    public void rename(String name) { this.name = name; }
    public void changeNotes(String notes) { this.notes = notes; }

    public void complete(Instant now) {
        this.status = WorkoutStatus.COMPLETED;
        this.finishedAt = now;
    }
}
