package com.personalos.backend.fitness;

import com.personalos.backend.fitness.domain.PersonalRecordType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Derives personal records from set history. Pure: no database access, no state, no clock.
 *
 * <p>Input is one exercise's sets in chronological order (workout start, then set number).
 * Warm-up sets are ignored. The first working set ever is a baseline, not a record, and a set only
 * counts as a record when it is <em>strictly</em> better than everything before it, so ties are
 * never records.
 */
public final class PersonalRecordCalculator {

    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    private PersonalRecordCalculator() {
    }

    public record HistorySet(UUID setId, BigDecimal weightKg, int reps, boolean warmup, LocalDate performedOn) {
    }

    /** A weight and the reps done with it. */
    public record Mark(BigDecimal weightKg, int reps, LocalDate performedOn) {
    }

    public record EstimatedMark(BigDecimal valueKg, BigDecimal weightKg, int reps, LocalDate performedOn) {
    }

    public record Records(Mark heaviestWeight, EstimatedMark bestEstimated1rm, List<Mark> bestRepsAtWeight) {
    }

    /**
     * Which sets are records, and of which kind. Sets that are not records are absent from the map.
     *
     * <ul>
     *   <li>{@code WEIGHT}: heavier than any earlier set.</li>
     *   <li>{@code ESTIMATED_1RM}: a higher Epley estimate than any earlier set.</li>
     *   <li>{@code REPS_AT_WEIGHT}: more reps than every earlier set at the same or a heavier weight.
     *       It needs at least one such earlier set; a brand-new heaviest weight is a {@code WEIGHT}
     *       record only.</li>
     * </ul>
     */
    public static Map<UUID, EnumSet<PersonalRecordType>> flag(List<HistorySet> chronological) {
        Map<UUID, EnumSet<PersonalRecordType>> flagged = new HashMap<>();
        boolean seenWorkingSet = false;
        BigDecimal bestWeight = BigDecimal.ZERO;
        BigDecimal bestEstimate = BigDecimal.ZERO;
        TreeMap<BigDecimal, Integer> bestRepsByWeight = new TreeMap<>();

        for (HistorySet set : chronological) {
            if (set.warmup()) continue;
            BigDecimal estimate = estimateNumerator(set.weightKg(), set.reps());

            if (seenWorkingSet) {
                EnumSet<PersonalRecordType> types = EnumSet.noneOf(PersonalRecordType.class);
                if (set.weightKg().compareTo(bestWeight) > 0) types.add(PersonalRecordType.WEIGHT);
                if (estimate.compareTo(bestEstimate) > 0) types.add(PersonalRecordType.ESTIMATED_1RM);
                Integer bestRepsAtOrAbove = bestRepsByWeight.tailMap(set.weightKg(), true).values().stream()
                        .max(Integer::compare).orElse(null);
                if (bestRepsAtOrAbove != null && set.reps() > bestRepsAtOrAbove) {
                    types.add(PersonalRecordType.REPS_AT_WEIGHT);
                }
                if (!types.isEmpty()) flagged.put(set.setId(), types);
            }

            seenWorkingSet = true;
            if (set.weightKg().compareTo(bestWeight) > 0) bestWeight = set.weightKg();
            if (estimate.compareTo(bestEstimate) > 0) bestEstimate = estimate;
            bestRepsByWeight.merge(set.weightKg(), set.reps(), Math::max);
        }
        return flagged;
    }

    /** The current bests over a chronological history (warm-ups ignored). */
    public static Records records(List<HistorySet> chronological) {
        Mark heaviest = null;
        EstimatedMark bestEstimate = null;
        BigDecimal bestEstimateNumerator = null;
        // For each weight: the most reps done at exactly that weight, and when that was first done.
        TreeMap<BigDecimal, Mark> bestAtWeight = new TreeMap<>();

        for (HistorySet set : chronological) {
            if (set.warmup()) continue;
            Mark mark = new Mark(set.weightKg(), set.reps(), set.performedOn());

            if (heaviest == null
                    || set.weightKg().compareTo(heaviest.weightKg()) > 0
                    || (set.weightKg().compareTo(heaviest.weightKg()) == 0 && set.reps() > heaviest.reps())) {
                heaviest = mark;
            }

            BigDecimal numerator = estimateNumerator(set.weightKg(), set.reps());
            if (bestEstimateNumerator == null || numerator.compareTo(bestEstimateNumerator) > 0) {
                bestEstimateNumerator = numerator;
                bestEstimate = new EstimatedMark(estimatedOneRepMax(set.weightKg(), set.reps()),
                        set.weightKg(), set.reps(), set.performedOn());
            }

            Mark existing = bestAtWeight.get(set.weightKg());
            if (existing == null || set.reps() > existing.reps()) bestAtWeight.put(set.weightKg(), mark);
        }

        // Keep only marks that no heavier-or-equal set beats on reps: the frontier of rep records.
        List<Mark> frontier = new ArrayList<>();
        int bestRepsHeavier = 0;
        for (Mark mark : bestAtWeight.descendingMap().values()) {
            if (mark.reps() > bestRepsHeavier) {
                frontier.add(mark);
                bestRepsHeavier = mark.reps();
            }
        }
        frontier.sort(Comparator.comparing(Mark::weightKg).reversed());
        return new Records(heaviest, bestEstimate, frontier);
    }

    /** Epley: weight x (1 + reps / 30); a single rep is the weight itself. Rounded to 2 decimals. */
    public static BigDecimal estimatedOneRepMax(BigDecimal weightKg, int reps) {
        return estimateNumerator(weightKg, reps).divide(THIRTY, 2, RoundingMode.HALF_UP);
    }

    /** The estimate multiplied by 30, which keeps comparisons exact (no division, no rounding). */
    private static BigDecimal estimateNumerator(BigDecimal weightKg, int reps) {
        return reps == 1 ? weightKg.multiply(THIRTY) : weightKg.multiply(BigDecimal.valueOf(30L + reps));
    }
}
