package com.personalos.backend.fitness;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.fitness.FitnessAnalyticsQueries.NamedHistoryRow;
import com.personalos.backend.fitness.domain.MovementGroup;
import com.personalos.backend.fitness.domain.PersonalRecordType;
import com.personalos.backend.fitness.dto.AnalyticsDtos.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Training analytics derived at read time from the existing workout data. Nothing here is stored or cached.
 *
 * <p>Volume is the sum of weight x reps over working (non-warm-up) sets of completed workouts. Personal records come
 * from {@link PersonalRecordCalculator}, the same code that flags records in a workout, so both always agree.
 */
@Service
@Transactional(readOnly = true)
public class FitnessAnalyticsService {

    private static final int DEFAULT_WEEKS = 12;
    private static final int DEFAULT_MONTHS = 12;
    private static final int DEFAULT_PROGRESSION_DAYS = 365;
    /** Bounds the zero-filled series a single request can produce. */
    private static final int MAX_RANGE_DAYS = 1830;

    private final FitnessAnalyticsQueries queries;
    private final ExerciseRepository exercises;
    private final ProfileService profiles;
    private final Clock clock;

    public FitnessAnalyticsService(FitnessAnalyticsQueries queries, ExerciseRepository exercises, ProfileService profiles, Clock clock) {
        this.queries = queries;
        this.exercises = exercises;
        this.profiles = profiles;
        this.clock = clock;
    }

    // ---- volume -------------------------------------------------------------------------------

    /** Defaults: weekly, the last 12 weeks (or 12 months when monthly) ending today in the user's timezone. */
    public VolumeSeries volume(UUID userId, LocalDate from, LocalDate to, String granularity) {
        boolean monthly = parseGranularity(granularity);
        LocalDate end = to != null ? to : today(userId);
        LocalDate start = from != null ? from : (monthly ? end.minusMonths(DEFAULT_MONTHS).plusDays(1) : end.minusWeeks(DEFAULT_WEEKS).plusDays(1));
        requireRange(start, end);
        String unit = monthly ? "month" : "week";

        Map<LocalDate, Map<MovementGroup, long[]>> sets = new HashMap<>();
        Map<LocalDate, Map<MovementGroup, BigDecimal>> volume = new HashMap<>();
        for (var row : queries.bucketMuscles(userId, start, end, unit)) {
            MovementGroup g = MovementGroup.of(row.muscleGroup());
            sets.computeIfAbsent(row.periodStart(), k -> new EnumMap<>(MovementGroup.class)).computeIfAbsent(g, k -> new long[1])[0] += row.sets();
            volume.computeIfAbsent(row.periodStart(), k -> new EnumMap<>(MovementGroup.class)).merge(g, row.volumeKg(), BigDecimal::add);
        }

        List<VolumePoint> points = queries.buckets(userId, start, end, unit).stream().map(b -> {
            List<MovementGroupVolume> groups = Arrays.stream(MovementGroup.values()).map(g -> new MovementGroupVolume(g,
                    volume.getOrDefault(b.periodStart(), Map.of()).getOrDefault(g, BigDecimal.ZERO),
                    (int) sets.getOrDefault(b.periodStart(), Map.of()).getOrDefault(g, new long[1])[0])).toList();
            return new VolumePoint(b.periodStart(), b.workouts(), groups.stream().mapToInt(MovementGroupVolume::sets).sum(),
                    groups.stream().map(MovementGroupVolume::volumeKg).reduce(BigDecimal.ZERO, BigDecimal::add), groups);
        }).toList();
        return new VolumeSeries(unit.equals("week") ? "weekly" : "monthly", start, end, points);
    }

    // ---- progression --------------------------------------------------------------------------

    /** Defaults to the last 365 days. A 404 for an exercise the caller cannot see. */
    public ExerciseProgression progression(UUID userId, UUID exerciseId, LocalDate from, LocalDate to) {
        requireVisible(userId, exerciseId);
        LocalDate end = to != null ? to : today(userId);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_PROGRESSION_DAYS - 1);
        requireRange(start, end);
        return new ExerciseProgression(start, end, queries.progression(userId, exerciseId, start, end));
    }

    // ---- personal records ---------------------------------------------------------------------

    /** Newest first (see {@link #EVENT_ORDER}). Paginated in memory over the derived events of this one exercise. */
    public PagedResponse<PersonalRecordEvent> exerciseRecords(UUID userId, UUID exerciseId, int page, int size) {
        requireVisible(userId, exerciseId);
        List<PersonalRecordEvent> events = events(queries.history(userId, exerciseId)).stream()
                .sorted(EVENT_ORDER).map(Event::event).toList();
        int fromIndex = (int) Math.min((long) page * size, events.size());
        List<PersonalRecordEvent> slice = events.subList(fromIndex, Math.min(fromIndex + size, events.size()));
        return PagedResponse.of(new PageImpl<>(slice, PageRequest.of(page, size), events.size()), e -> e);
    }

    /** The most recent {@code limit} personal-record events across all of the user's exercises. */
    public List<ExercisePersonalRecord> recentRecords(UUID userId, int limit) {
        return events(queries.history(userId, null)).stream().sorted(EVENT_ORDER).limit(limit)
                .map(e -> new ExercisePersonalRecord(e.exercise(), e.event())).toList();
    }

    private record Event(ExerciseRef exercise, PersonalRecordEvent event, Instant startedAt, int setNumber) {}

    /**
     * Total order, so pages never overlap or skip: newest date, latest workout start, highest set number, then exercise
     * name and id (for sets of different exercises in one workout), then a fixed type order (a set can be several records).
     */
    private static final Comparator<Event> EVENT_ORDER = Comparator
            .comparing((Event e) -> e.event().performedOn()).reversed()
            .thenComparing(Comparator.comparing(Event::startedAt).reversed())
            .thenComparing(Comparator.comparingInt(Event::setNumber).reversed())
            .thenComparing(e -> e.exercise().name())
            .thenComparing(e -> e.exercise().id())
            .thenComparing(e -> e.event().type());

    private List<Event> events(List<NamedHistoryRow> rows) {
        List<Event> result = new ArrayList<>();
        Map<UUID, List<NamedHistoryRow>> byExercise = new LinkedHashMap<>();
        rows.forEach(r -> byExercise.computeIfAbsent(r.row().exerciseId(), k -> new ArrayList<>()).add(r));

        for (var entry : byExercise.entrySet()) {
            List<NamedHistoryRow> history = entry.getValue();
            var flags = PersonalRecordCalculator.flag(history.stream().map(r -> r.row().toHistorySet()).toList());
            ExerciseRef ref = new ExerciseRef(entry.getKey(), history.get(0).exerciseName());
            for (NamedHistoryRow r : history) {
                var types = flags.get(r.row().setId());
                if (types == null) continue;
                for (PersonalRecordType type : types) {
                    result.add(new Event(ref, new PersonalRecordEvent(r.row().performedOn(), r.row().workoutId(), r.row().setId(), type,
                            r.row().weightKg(), r.row().reps(), PersonalRecordCalculator.estimatedOneRepMax(r.row().weightKg(), r.row().reps())),
                            r.row().startedAt(), r.row().setNumber()));
                }
            }
        }
        return result;
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void requireVisible(UUID userId, UUID exerciseId) {
        exercises.findVisible(exerciseId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Exercise not found"));
    }

    private static boolean parseGranularity(String value) {
        if (value == null || value.equalsIgnoreCase("weekly")) return false;
        if (value.equalsIgnoreCase("monthly")) return true;
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRANULARITY", "granularity must be 'weekly' or 'monthly'");
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "The range can be at most " + MAX_RANGE_DAYS + " days");
        }
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
    }
}
