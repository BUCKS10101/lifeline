package com.personalos.backend.fitness.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "workout_template_exercises")
public class WorkoutTemplateExercise extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(nullable = false)
    private int position;

    @Column(name = "target_sets")
    private Integer targetSets;

    protected WorkoutTemplateExercise() {
    }

    public WorkoutTemplateExercise(UUID templateId, UUID exerciseId, int position, Integer targetSets) {
        this.id = UUID.randomUUID();
        this.templateId = templateId;
        this.exerciseId = exerciseId;
        this.position = position;
        this.targetSets = targetSets;
    }

    @Override public UUID getId() { return id; }
    public UUID getTemplateId() { return templateId; }
    public UUID getExerciseId() { return exerciseId; }
    public int getPosition() { return position; }
    public Integer getTargetSets() { return targetSets; }
}
