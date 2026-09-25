package com.personalos.backend.fitness.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "workout_exercises")
public class WorkoutExercise extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "workout_id", nullable = false, updatable = false)
    private UUID workoutId;

    @Column(name = "exercise_id", nullable = false, updatable = false)
    private UUID exerciseId;

    @Column(nullable = false)
    private int position;

    private String notes;

    protected WorkoutExercise() {
    }

    public WorkoutExercise(UUID workoutId, UUID exerciseId, int position) {
        this.id = UUID.randomUUID();
        this.workoutId = workoutId;
        this.exerciseId = exerciseId;
        this.position = position;
    }

    @Override public UUID getId() { return id; }
    public UUID getWorkoutId() { return workoutId; }
    public UUID getExerciseId() { return exerciseId; }
    public int getPosition() { return position; }
    public String getNotes() { return notes; }

    public void moveTo(int position) { this.position = position; }
    public void changeNotes(String notes) { this.notes = notes; }
}
