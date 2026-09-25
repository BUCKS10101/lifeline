package com.personalos.backend.fitness.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One set. Weight is always in kilograms; 0 means bodyweight / no added load. */
@Entity
@Table(name = "workout_sets")
public class WorkoutSet extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "workout_exercise_id", nullable = false, updatable = false)
    private UUID workoutExerciseId;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private int reps;

    private BigDecimal rpe;

    @Column(name = "is_warmup", nullable = false)
    private boolean warmup;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkoutSet() {
    }

    /** {@code id} may come from the client so a retried request cannot create a duplicate. */
    public WorkoutSet(UUID id, UUID workoutExerciseId, int setNumber, BigDecimal weightKg, int reps,
                      BigDecimal rpe, boolean warmup, Instant now) {
        this.id = id != null ? id : UUID.randomUUID();
        this.workoutExerciseId = workoutExerciseId;
        this.setNumber = setNumber;
        this.weightKg = weightKg;
        this.reps = reps;
        this.rpe = rpe;
        this.warmup = warmup;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    @Override public UUID getId() { return id; }
    public UUID getWorkoutExerciseId() { return workoutExerciseId; }
    public int getSetNumber() { return setNumber; }
    public BigDecimal getWeightKg() { return weightKg; }
    public int getReps() { return reps; }
    public BigDecimal getRpe() { return rpe; }
    public boolean isWarmup() { return warmup; }

    public void renumber(int setNumber) { this.setNumber = setNumber; }
    public void changeWeight(BigDecimal weightKg) { this.weightKg = weightKg; }
    public void changeReps(int reps) { this.reps = reps; }
    public void changeRpe(BigDecimal rpe) { this.rpe = rpe; }
    public void changeWarmup(boolean warmup) { this.warmup = warmup; }
}
