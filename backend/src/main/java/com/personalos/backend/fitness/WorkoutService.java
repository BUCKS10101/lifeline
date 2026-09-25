package com.personalos.backend.fitness;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.fitness.domain.*;
import com.personalos.backend.fitness.dto.WorkoutDtos.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Write side of workouts.
 *
 * <p>Every mutation locks the workout row ({@code SELECT ... FOR UPDATE}) after the ownership check, so
 * concurrent requests for one workout run one at a time. That is what keeps set numbers and exercise
 * positions consistent and lets "finish" and "add a set" race safely. The unique constraints in the
 * database are the backstop.
 */
@Service
public class WorkoutService {

    static final int MAX_EXERCISES = 30;
    static final int MAX_SETS_PER_EXERCISE = 50;

    private final WorkoutRepository workouts;
    private final WorkoutExerciseRepository workoutExercises;
    private final WorkoutSetRepository sets;
    private final ExerciseRepository exercises;
    private final WorkoutTemplateRepository templates;
    private final WorkoutTemplateExerciseRepository templateExercises;
    private final WorkoutQueryService query;
    private final ProfileService profiles;
    private final Clock clock;

    public WorkoutService(WorkoutRepository workouts, WorkoutExerciseRepository workoutExercises,
                          WorkoutSetRepository sets, ExerciseRepository exercises, WorkoutTemplateRepository templates,
                          WorkoutTemplateExerciseRepository templateExercises, WorkoutQueryService query,
                          ProfileService profiles, Clock clock) {
        this.workouts = workouts;
        this.workoutExercises = workoutExercises;
        this.sets = sets;
        this.exercises = exercises;
        this.templates = templates;
        this.templateExercises = templateExercises;
        this.query = query;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** A set plus whether this call created it (false when a retry found the existing one). */
    public record SetResult(SetResponse set, boolean created) {}

    // ---- workout lifecycle -------------------------------------------------------------------

    @Transactional
    public WorkoutDetail start(UUID userId, StartWorkoutRequest request) {
        WorkoutTemplate template = request.templateId() == null ? null
                : templates.findVisible(request.templateId(), userId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Template not found"));
        if (workouts.existsByUserIdAndStatus(userId, WorkoutStatus.IN_PROGRESS)) throw alreadyInProgress();

        Instant now = Instant.now(clock);
        // "Today" is the user's day, not the server's: performed_on is fixed at start and never shifts.
        LocalDate performedOn = LocalDate.ofInstant(now, profiles.timezoneOf(userId));
        String name = request.name() != null ? request.name().trim() : template != null ? template.getName() : "Workout";
        Workout workout = new Workout(userId, template == null ? null : template.getId(), name, performedOn, now);
        try {
            workouts.saveAndFlush(workout);
        } catch (DataIntegrityViolationException e) {
            throw alreadyInProgress(); // lost a race: the unique index allows one active workout per user
        }

        if (template != null) {
            List<WorkoutTemplateExercise> rows = templateExercises.findByTemplateIdOrderByPosition(template.getId());
            Map<UUID, Exercise> byId = exercises.findAllById(rows.stream().map(WorkoutTemplateExercise::getExerciseId).toList())
                    .stream().collect(Collectors.toMap(Exercise::getId, Function.identity()));
            int position = 1;
            for (WorkoutTemplateExercise row : rows) {
                if (byId.get(row.getExerciseId()).isArchived()) continue; // removed since the template was made
                workoutExercises.save(new WorkoutExercise(workout.getId(), row.getExerciseId(), position++));
            }
        }
        return query.assemble(workout);
    }

    /** Name and notes stay editable after completion; nothing else does. */
    @Transactional
    public WorkoutDetail update(UUID userId, UUID id, UpdateWorkoutRequest request) {
        Workout workout = lock(userId, id);
        if (request.name() == null && request.notes() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Provide name, notes, or both");
        }
        if (request.name() != null) workout.rename(request.name().trim());
        if (request.notes() != null) workout.changeNotes(blankToNull(request.notes()));
        return query.assemble(workout);
    }

    /** Discards an active workout or deletes a completed one. The database removes its exercises and sets. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        workouts.delete(lock(userId, id));
    }

    /**
     * Finishing is idempotent: an already completed workout is returned unchanged. It needs at least one set;
     * exercises with no sets are dropped.
     */
    @Transactional
    public WorkoutDetail finish(UUID userId, UUID id) {
        Workout workout = lock(userId, id);
        if (!workout.isInProgress()) return query.assemble(workout);

        List<WorkoutExercise> wes = workoutExercises.findByWorkoutIdOrderByPosition(id);
        Map<UUID, Integer> setCounts = new HashMap<>();
        for (WorkoutExercise we : wes) setCounts.put(we.getId(), sets.countByWorkoutExerciseId(we.getId()));
        if (setCounts.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKOUT_EMPTY", "Log at least one set before finishing");
        }

        wes.stream().filter(we -> setCounts.get(we.getId()) == 0).forEach(workoutExercises::delete);
        workoutExercises.flush();
        renumberExercises(id);

        workout.complete(Instant.now(clock));
        return query.assemble(workout);
    }

    // ---- exercises in a workout --------------------------------------------------------------

    @Transactional
    public WorkoutExerciseResponse addExercise(UUID userId, UUID id, UUID exerciseId) {
        Workout workout = lockInProgress(userId, id);
        Exercise exercise = exercises.findVisible(exerciseId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Exercise not found"));
        if (exercise.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "EXERCISE_ARCHIVED", "This exercise has been removed");
        }
        if (workoutExercises.existsByWorkoutIdAndExerciseId(id, exerciseId)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_WORKOUT_EXERCISE", "This exercise is already in the workout");
        }
        int count = workoutExercises.countByWorkoutId(id);
        if (count >= MAX_EXERCISES) limitExceeded("A workout can have at most " + MAX_EXERCISES + " exercises");

        WorkoutExercise we = workoutExercises.saveAndFlush(new WorkoutExercise(id, exerciseId, count + 1));
        return query.entry(workout, we);
    }

    @Transactional
    public void removeExercise(UUID userId, UUID id, UUID workoutExerciseId) {
        lockInProgress(userId, id);
        workoutExercises.delete(exerciseOf(id, workoutExerciseId)); // the database removes its sets
        workoutExercises.flush();
        renumberExercises(id);
    }

    @Transactional
    public WorkoutDetail reorder(UUID userId, UUID id, List<UUID> orderedIds) {
        Workout workout = lockInProgress(userId, id);
        List<WorkoutExercise> current = workoutExercises.findByWorkoutIdOrderByPosition(id);
        Set<UUID> currentIds = current.stream().map(WorkoutExercise::getId).collect(Collectors.toSet());
        if (orderedIds.size() != currentIds.size() || !currentIds.equals(new HashSet<>(orderedIds))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER",
                    "The list must contain each exercise of the workout exactly once");
        }
        Map<UUID, WorkoutExercise> byId = current.stream().collect(Collectors.toMap(WorkoutExercise::getId, Function.identity()));
        int position = 1;
        for (UUID weId : orderedIds) byId.get(weId).moveTo(position++);
        workoutExercises.flush();
        return query.assemble(workout);
    }

    @Transactional
    public WorkoutExerciseResponse updateExerciseNotes(UUID userId, UUID id, UUID workoutExerciseId, String notes) {
        Workout workout = lockInProgress(userId, id);
        WorkoutExercise we = exerciseOf(id, workoutExerciseId);
        we.changeNotes(blankToNull(notes));
        return query.entry(workout, we);
    }

    // ---- sets --------------------------------------------------------------------------------

    @Transactional
    public SetResult addSet(UUID userId, UUID id, UUID workoutExerciseId, CreateSetRequest request) {
        Workout workout = lockInProgress(userId, id);
        WorkoutExercise we = exerciseOf(id, workoutExerciseId);

        if (request.id() != null) {
            Optional<WorkoutSet> existing = sets.findById(request.id());
            if (existing.isPresent()) {
                // A retry of a request that already succeeded: return what exists. The same id under a different
                // exercise (or user) is a collision and must not reveal anything about that set.
                if (existing.get().getWorkoutExerciseId().equals(we.getId())) {
                    return new SetResult(setResponse(workout, we, existing.get().getId()), false);
                }
                throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "This set id is already in use");
            }
        }
        if (sets.countByWorkoutExerciseId(we.getId()) >= MAX_SETS_PER_EXERCISE) {
            limitExceeded("An exercise can have at most " + MAX_SETS_PER_EXERCISE + " sets");
        }

        WorkoutSet set = new WorkoutSet(request.id(), we.getId(), sets.maxSetNumber(we.getId()) + 1,
                request.weightKg(), request.reps(), request.rpe(), Boolean.TRUE.equals(request.warmup()), Instant.now(clock));
        sets.saveAndFlush(set);
        return new SetResult(setResponse(workout, we, set.getId()), true);
    }

    @Transactional
    public SetResponse updateSet(UUID userId, UUID id, UUID workoutExerciseId, UUID setId, UpdateSetRequest request) {
        Workout workout = lockInProgress(userId, id);
        WorkoutExercise we = exerciseOf(id, workoutExerciseId);
        WorkoutSet set = setOf(we, setId);

        boolean clearRpe = Boolean.TRUE.equals(request.clearRpe());
        if (request.weightKg() == null && request.reps() == null && request.rpe() == null
                && !clearRpe && request.warmup() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Provide at least one field to change");
        }
        if (clearRpe && request.rpe() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Send either rpe or clearRpe, not both");
        }
        if (request.weightKg() != null) set.changeWeight(request.weightKg());
        if (request.reps() != null) set.changeReps(request.reps());
        if (request.rpe() != null) set.changeRpe(request.rpe());
        if (clearRpe) set.changeRpe(null);
        if (request.warmup() != null) set.changeWarmup(request.warmup());
        sets.flush();
        return setResponse(workout, we, setId);
    }

    @Transactional
    public void deleteSet(UUID userId, UUID id, UUID workoutExerciseId, UUID setId) {
        lockInProgress(userId, id);
        WorkoutExercise we = exerciseOf(id, workoutExerciseId);
        sets.delete(setOf(we, setId));
        sets.flush();

        int number = 1;
        for (WorkoutSet remaining : sets.findByWorkoutExerciseIdOrderBySetNumber(we.getId())) {
            if (remaining.getSetNumber() != number) remaining.renumber(number);
            number++;
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** Locks the user's workout row. Another user's or an unknown id is "not found". */
    private Workout lock(UUID userId, UUID id) {
        return workouts.findByIdAndUserIdForUpdate(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workout not found"));
    }

    private Workout lockInProgress(UUID userId, UUID id) {
        Workout workout = lock(userId, id);
        if (!workout.isInProgress()) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKOUT_NOT_IN_PROGRESS",
                    "This workout is finished; only its name and notes can be changed");
        }
        return workout;
    }

    private WorkoutExercise exerciseOf(UUID workoutId, UUID workoutExerciseId) {
        return workoutExercises.findByIdAndWorkoutId(workoutExerciseId, workoutId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Exercise not found in this workout"));
    }

    private WorkoutSet setOf(WorkoutExercise we, UUID setId) {
        return sets.findByIdAndWorkoutExerciseId(setId, we.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Set not found"));
    }

    private SetResponse setResponse(Workout workout, WorkoutExercise we, UUID setId) {
        return query.entry(workout, we).sets().stream().filter(s -> s.id().equals(setId)).findFirst().orElseThrow();
    }

    /** Keeps positions contiguous (1..n) after an exercise is removed. */
    private void renumberExercises(UUID workoutId) {
        int position = 1;
        for (WorkoutExercise we : workoutExercises.findByWorkoutIdOrderByPosition(workoutId)) {
            if (we.getPosition() != position) we.moveTo(position);
            position++;
        }
    }

    private static ApiException alreadyInProgress() {
        return new ApiException(HttpStatus.CONFLICT, "WORKOUT_IN_PROGRESS",
                "You already have a workout in progress; finish or discard it first");
    }

    private static void limitExceeded(String message) {
        throw new ApiException(HttpStatus.CONFLICT, "LIMIT_EXCEEDED", message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
