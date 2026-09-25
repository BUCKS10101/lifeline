package com.personalos.backend.fitness;

import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.fitness.PersonalRecordCalculator.Records;
import com.personalos.backend.fitness.domain.Exercise;
import com.personalos.backend.fitness.domain.MuscleGroup;
import com.personalos.backend.fitness.domain.Workout;
import com.personalos.backend.fitness.domain.WorkoutExercise;
import com.personalos.backend.fitness.domain.WorkoutSet;
import com.personalos.backend.fitness.domain.WorkoutStatus;
import com.personalos.backend.fitness.dto.ExerciseDtos.*;
import com.personalos.backend.fitness.mapper.ExerciseMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExerciseService {

    private static final Instant FAR_FUTURE = Instant.parse("3000-01-01T00:00:00Z");

    private final ExerciseRepository exercises;
    private final WorkoutRepository workouts;
    private final WorkoutExerciseRepository workoutExercises;
    private final WorkoutSetRepository sets;
    private final WorkoutTemplateExerciseRepository templateExercises;
    private final ExerciseMapper mapper;
    private final Clock clock;

    public ExerciseService(ExerciseRepository exercises, WorkoutRepository workouts,
                           WorkoutExerciseRepository workoutExercises, WorkoutSetRepository sets,
                           WorkoutTemplateExerciseRepository templateExercises, ExerciseMapper mapper, Clock clock) {
        this.exercises = exercises;
        this.workouts = workouts;
        this.workoutExercises = workoutExercises;
        this.sets = sets;
        this.templateExercises = templateExercises;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---- catalogue ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<ExerciseResponse> list(UUID userId, String q, MuscleGroup muscleGroup,
                                                boolean includeArchived, int page, int size) {
        Specification<Exercise> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.or(cb.isNull(root.get("ownerId")), cb.equal(root.get("ownerId"), userId)));
            if (!includeArchived) where.add(cb.isNull(root.get("archivedAt")));
            if (muscleGroup != null) where.add(cb.equal(root.get("primaryMuscleGroup"), muscleGroup));
            if (q != null && !q.isBlank()) {
                String escaped = q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                where.add(cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\'));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id")));
        return PagedResponse.of(exercises.findAll(spec, pageable), mapper::toResponse);
    }

    @Transactional
    public ExerciseResponse create(UUID userId, CreateExerciseRequest request) {
        String name = request.name().trim();
        if (exercises.nameTaken(name.toLowerCase(), userId, null)) throw duplicateName();

        Exercise exercise = new Exercise(userId, name, request.primaryMuscleGroup(),
                blankToNull(request.equipment()), Instant.now(clock));
        try {
            exercises.saveAndFlush(exercise);
        } catch (DataIntegrityViolationException e) {
            throw duplicateName(); // lost a race with a concurrent create of the same name
        }
        return mapper.toResponse(exercise);
    }

    @Transactional
    public ExerciseResponse update(UUID userId, UUID id, UpdateExerciseRequest request) {
        Exercise exercise = ownedForChange(userId, id);
        if (exercise.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "EXERCISE_ARCHIVED", "This exercise has been removed");
        }
        if (request.name() == null && request.primaryMuscleGroup() == null && request.equipment() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "Provide at least one field to change");
        }

        if (request.name() != null) {
            String name = request.name().trim();
            if (exercises.nameTaken(name.toLowerCase(), userId, id)) throw duplicateName();
            exercise.rename(name);
        }
        if (request.primaryMuscleGroup() != null) exercise.changeMuscleGroup(request.primaryMuscleGroup());
        if (request.equipment() != null) exercise.changeEquipment(blankToNull(request.equipment()));

        try {
            exercises.saveAndFlush(exercise);
        } catch (DataIntegrityViolationException e) {
            throw duplicateName();
        }
        return mapper.toResponse(exercise);
    }

    /** Deletes an exercise nobody has used; archives one that appears in history or a template. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        Exercise exercise = ownedForChange(userId, id);
        if (exercise.isArchived()) return;

        boolean used = workoutExercises.existsByExerciseId(id) || templateExercises.existsByExerciseId(id);
        if (used) {
            exercise.archive(Instant.now(clock));
        } else {
            exercises.delete(exercise);
        }
    }

    // ---- history and records -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<ExerciseHistoryItem> history(UUID userId, UUID exerciseId, int page, int size) {
        requireVisible(userId, exerciseId);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("performedOn"), Sort.Order.desc("startedAt")));
        Page<Workout> workoutPage = workouts.findByStatusContainingExercise(
                userId, WorkoutStatus.COMPLETED, exerciseId, pageable);

        List<UUID> workoutIds = workoutPage.getContent().stream().map(Workout::getId).toList();
        Map<UUID, WorkoutExercise> byWorkout = workoutIds.isEmpty() ? Map.of()
                : workoutExercises.findByWorkoutIdInAndExerciseId(workoutIds, exerciseId).stream()
                        .collect(Collectors.toMap(WorkoutExercise::getWorkoutId, Function.identity()));
        Map<UUID, List<WorkoutSet>> setsByWorkoutExercise = byWorkout.isEmpty() ? Map.of()
                : sets.findByWorkoutExerciseIdInOrderBySetNumber(
                                byWorkout.values().stream().map(WorkoutExercise::getId).toList()).stream()
                        .collect(Collectors.groupingBy(WorkoutSet::getWorkoutExerciseId));

        return PagedResponse.of(workoutPage, workout -> {
            WorkoutExercise we = byWorkout.get(workout.getId());
            List<WorkoutSet> workoutSets = setsByWorkoutExercise.getOrDefault(we.getId(), List.of());
            List<HistorySet> items = workoutSets.stream().map(ExerciseService::toHistorySet).toList();
            List<WorkoutSet> working = workoutSets.stream().filter(s -> !s.isWarmup()).toList();
            HistorySet top = working.stream()
                    .max(Comparator.comparing(WorkoutSet::getWeightKg)
                            .thenComparing(WorkoutSet::getReps)
                            .thenComparing(Comparator.comparingInt(WorkoutSet::getSetNumber).reversed()))
                    .map(ExerciseService::toHistorySet).orElse(null);
            BigDecimal volume = working.stream()
                    .map(s -> s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ExerciseHistoryItem(workout.getId(), workout.getPerformedOn(), items, top, volume);
        });
    }

    @Transactional(readOnly = true)
    public ExerciseRecords records(UUID userId, UUID exerciseId) {
        Exercise exercise = requireVisible(userId, exerciseId);
        List<PersonalRecordCalculator.HistorySet> history = sets
                .findHistory(userId, List.of(exerciseId), WorkoutStatus.COMPLETED, null, FAR_FUTURE).stream()
                .map(HistoryRow::toHistorySet).toList();
        Records records = PersonalRecordCalculator.records(history);

        return new ExerciseRecords(mapper.toResponse(exercise),
                records.heaviestWeight() == null ? null : toMark(records.heaviestWeight()),
                records.bestEstimated1rm() == null ? null : new EstimatedRecordMark(
                        records.bestEstimated1rm().valueKg(), records.bestEstimated1rm().weightKg(),
                        records.bestEstimated1rm().reps(), records.bestEstimated1rm().performedOn()),
                records.bestRepsAtWeight().stream().map(ExerciseService::toMark).toList());
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** Visible to the user (built-in or theirs); anything else is reported as not found. */
    private Exercise requireVisible(UUID userId, UUID id) {
        return exercises.findVisible(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Exercise not found"));
    }

    /** Visible and changeable: built-ins are read-only. */
    private Exercise ownedForChange(UUID userId, UUID id) {
        Exercise exercise = requireVisible(userId, id);
        if (exercise.isBuiltIn()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BUILTIN_READ_ONLY", "Built-in exercises cannot be changed");
        }
        return exercise;
    }

    private static ApiException duplicateName() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EXERCISE", "An exercise with this name already exists");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static HistorySet toHistorySet(WorkoutSet s) {
        return new HistorySet(s.getSetNumber(), s.getWeightKg(), s.getReps(), s.getRpe(), s.isWarmup());
    }

    private static RecordMark toMark(PersonalRecordCalculator.Mark m) {
        return new RecordMark(m.weightKg(), m.reps(), m.performedOn());
    }
}
