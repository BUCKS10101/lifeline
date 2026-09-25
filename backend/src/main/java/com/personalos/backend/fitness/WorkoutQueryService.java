package com.personalos.backend.fitness;

import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.fitness.domain.*;
import com.personalos.backend.fitness.dto.WorkoutDtos.*;
import com.personalos.backend.fitness.mapper.ExerciseMapper;
import com.personalos.backend.fitness.mapper.WorkoutMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of workouts: turns stored rows into DTOs. Personal records, totals and last-session hints
 * are computed here from the sets, at read time; none of it is stored.
 */
@Service
public class WorkoutQueryService {

    private static final Set<String> SORTABLE = Set.of("performedOn", "startedAt");

    private final WorkoutRepository workouts;
    private final WorkoutExerciseRepository workoutExercises;
    private final WorkoutSetRepository sets;
    private final ExerciseRepository exercises;
    private final ExerciseMapper exerciseMapper;
    private final WorkoutMapper mapper;

    public WorkoutQueryService(WorkoutRepository workouts, WorkoutExerciseRepository workoutExercises,
                               WorkoutSetRepository sets, ExerciseRepository exercises,
                               ExerciseMapper exerciseMapper, WorkoutMapper mapper) {
        this.workouts = workouts;
        this.workoutExercises = workoutExercises;
        this.sets = sets;
        this.exercises = exercises;
        this.exerciseMapper = exerciseMapper;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public WorkoutDetail detail(UUID userId, UUID workoutId) {
        Workout workout = workouts.findByIdAndUserId(workoutId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workout not found"));
        return assemble(workout);
    }

    @Transactional(readOnly = true)
    public Optional<WorkoutDetail> current(UUID userId) {
        return workouts.findByUserIdAndStatus(userId, WorkoutStatus.IN_PROGRESS).map(this::assemble);
    }

    @Transactional(readOnly = true)
    public PagedResponse<WorkoutListItem> list(UUID userId, WorkoutStatus status, LocalDate from, LocalDate to,
                                               int page, int size, String sort) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        }
        Specification<Workout> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("userId"), userId));
            where.add(cb.equal(root.get("status"), status));
            if (from != null) where.add(cb.greaterThanOrEqualTo(root.get("performedOn"), from));
            if (to != null) where.add(cb.lessThanOrEqualTo(root.get("performedOn"), to));
            return cb.and(where.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        var result = workouts.findAll(spec, pageable);

        List<UUID> ids = result.getContent().stream().map(Workout::getId).toList();
        Map<UUID, List<WorkoutExercise>> exercisesByWorkout = ids.isEmpty() ? Map.of()
                : workoutExercises.findByWorkoutIdIn(ids).stream().collect(Collectors.groupingBy(WorkoutExercise::getWorkoutId));
        List<UUID> weIds = exercisesByWorkout.values().stream().flatMap(List::stream).map(WorkoutExercise::getId).toList();
        Map<UUID, List<WorkoutSet>> setsByExercise = weIds.isEmpty() ? Map.of()
                : sets.findByWorkoutExerciseIdInOrderBySetNumber(weIds).stream()
                        .collect(Collectors.groupingBy(WorkoutSet::getWorkoutExerciseId));

        return PagedResponse.of(result, w -> {
            List<WorkoutExercise> wes = exercisesByWorkout.getOrDefault(w.getId(), List.of());
            List<WorkoutSet> all = wes.stream().flatMap(we -> setsByExercise.getOrDefault(we.getId(), List.of()).stream()).toList();
            BigDecimal volume = all.stream().filter(s -> !s.isWarmup()).map(WorkoutMapper::volume)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new WorkoutListItem(w.getId(), w.getName(), w.getStatus(), w.getPerformedOn(), w.getStartedAt(),
                    w.getFinishedAt(), mapper.durationMinutes(w), wes.size(), all.size(), volume);
        });
    }

    // ---- assembling a workout ----------------------------------------------------------------

    /** Must run inside a transaction. Also used by the write side to return the updated state. */
    public WorkoutDetail assemble(Workout workout) {
        List<WorkoutExercise> wes = workoutExercises.findByWorkoutIdOrderByPosition(workout.getId());
        List<WorkoutExerciseResponse> entries = entries(workout, wes);

        int allSets = entries.stream().mapToInt(e -> e.sets().size()).sum();
        List<SetResponse> working = entries.stream().flatMap(e -> e.sets().stream()).filter(s -> !s.warmup()).toList();
        BigDecimal volume = working.stream().map(s -> s.weightKg().multiply(BigDecimal.valueOf(s.reps())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new WorkoutDetail(workout.getId(), workout.getName(), workout.getStatus(), workout.getPerformedOn(),
                workout.getStartedAt(), workout.getFinishedAt(), mapper.durationMinutes(workout), workout.getNotes(),
                workout.getTemplateId(), new WorkoutTotals(entries.size(), allSets, working.size(), volume), entries);
    }

    /** One exercise of a workout with its sets, record flags and last-session hint. */
    public WorkoutExerciseResponse entry(Workout workout, WorkoutExercise we) {
        return entries(workout, List.of(we)).get(0);
    }

    private List<WorkoutExerciseResponse> entries(Workout workout, List<WorkoutExercise> wes) {
        if (wes.isEmpty()) return List.of();
        List<UUID> exerciseIds = wes.stream().map(WorkoutExercise::getExerciseId).toList();
        Map<UUID, Exercise> exerciseById = exercises.findAllById(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
        Map<UUID, List<WorkoutSet>> setsByWe = sets
                .findByWorkoutExerciseIdInOrderBySetNumber(wes.stream().map(WorkoutExercise::getId).toList()).stream()
                .collect(Collectors.groupingBy(WorkoutSet::getWorkoutExerciseId));

        // One query for the history of every exercise in this workout: completed workouts up to this one,
        // plus this workout's own sets (which may be in progress).
        Map<UUID, List<HistoryRow>> historyByExercise = sets
                .findHistory(workout.getUserId(), exerciseIds, WorkoutStatus.COMPLETED, workout.getId(), workout.getStartedAt())
                .stream().collect(Collectors.groupingBy(HistoryRow::exerciseId, LinkedHashMap::new, Collectors.toList()));

        List<WorkoutExerciseResponse> result = new ArrayList<>();
        for (WorkoutExercise we : wes) {
            List<HistoryRow> history = historyByExercise.getOrDefault(we.getExerciseId(), List.of());
            Map<UUID, EnumSet<PersonalRecordType>> flags = PersonalRecordCalculator.flag(
                    history.stream().map(HistoryRow::toHistorySet).toList());

            List<SetResponse> setResponses = setsByWe.getOrDefault(we.getId(), List.of()).stream()
                    .map(s -> mapper.toSetResponse(s, flags.get(s.getId()))).toList();
            result.add(new WorkoutExerciseResponse(we.getId(), we.getPosition(), we.getNotes(),
                    exerciseMapper.toResponse(exerciseById.get(we.getExerciseId())),
                    lastSession(workout.getId(), history), setResponses));
        }
        return result;
    }

    /** The working sets of the most recent earlier completed workout that included this exercise. */
    private static LastSession lastSession(UUID currentWorkoutId, List<HistoryRow> chronologicalHistory) {
        UUID lastWorkout = null;
        for (HistoryRow row : chronologicalHistory) {
            if (!row.workoutId().equals(currentWorkoutId) && !row.warmup()) lastWorkout = row.workoutId();
        }
        if (lastWorkout == null) return null;

        UUID target = lastWorkout;
        List<HistoryRow> rows = chronologicalHistory.stream()
                .filter(r -> r.workoutId().equals(target) && !r.warmup()).toList();
        return new LastSession(rows.get(0).performedOn(),
                rows.stream().map(r -> new SessionSet(r.weightKg(), r.reps())).toList());
    }

    /** {@code field,direction} with a whitelisted field; defaults to newest first. */
    private static Sort parseSort(String sort) {
        Sort newestFirst = Sort.by(Sort.Order.desc("performedOn"), Sort.Order.desc("startedAt"), Sort.Order.desc("id"));
        if (sort == null || sort.isBlank()) return newestFirst;

        String[] parts = sort.split(",");
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "asc";
        if (parts.length > 2 || !SORTABLE.contains(field) || !(direction.equals("asc") || direction.equals("desc"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT",
                    "Sort must be one of " + SORTABLE + " followed by ,asc or ,desc");
        }
        Sort.Direction dir = Sort.Direction.fromString(direction);
        return Sort.by(new Sort.Order(dir, field), new Sort.Order(dir, "startedAt"), new Sort.Order(dir, "id"));
    }
}
