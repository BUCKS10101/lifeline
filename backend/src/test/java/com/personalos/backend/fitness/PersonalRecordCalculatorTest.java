package com.personalos.backend.fitness;

import com.personalos.backend.fitness.PersonalRecordCalculator.HistorySet;
import com.personalos.backend.fitness.PersonalRecordCalculator.Records;
import com.personalos.backend.fitness.domain.PersonalRecordType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.personalos.backend.fitness.domain.PersonalRecordType.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests: no Spring, no database. */
class PersonalRecordCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private final List<HistorySet> history = new ArrayList<>();
    private int counter = 0;

    /** Adds a working set and returns its id. */
    private UUID set(String weight, int reps) { return add(weight, reps, false, 0); }

    private UUID warmup(String weight, int reps) { return add(weight, reps, true, 0); }

    private UUID setOnDay(int dayOffset, String weight, int reps) { return add(weight, reps, false, dayOffset); }

    private UUID add(String weight, int reps, boolean warmup, int dayOffset) {
        UUID id = new UUID(0, ++counter);
        history.add(new HistorySet(id, new BigDecimal(weight), reps, warmup, DAY.plusDays(dayOffset)));
        return id;
    }

    private Map<UUID, EnumSet<PersonalRecordType>> flags() { return PersonalRecordCalculator.flag(history); }

    // ---- baseline and ties -------------------------------------------------------------------

    @Test
    void emptyHistoryHasNoRecords() {
        assertThat(flags()).isEmpty();
        Records records = PersonalRecordCalculator.records(history);
        assertThat(records.heaviestWeight()).isNull();
        assertThat(records.bestEstimated1rm()).isNull();
        assertThat(records.bestRepsAtWeight()).isEmpty();
    }

    @Test
    void theFirstWorkingSetIsABaselineNotARecord() {
        set("100", 5);
        assertThat(flags()).isEmpty();
    }

    @Test
    void repeatingTheSameSetIsATieAndNotARecord() {
        set("100", 5);
        set("100", 5);
        set("100", 5);
        assertThat(flags()).isEmpty();
    }

    // ---- each record type --------------------------------------------------------------------

    @Test
    void aHeavierSetIsAWeightRecord() {
        set("100", 5);
        UUID heavier = set("102.5", 3);
        assertThat(flags().get(heavier)).contains(WEIGHT);
    }

    @Test
    void equalWeightIsNotAWeightRecord() {
        set("100", 5);
        UUID same = set("100", 6);
        assertThat(flags().get(same)).doesNotContain(WEIGHT);
    }

    @Test
    void moreRepsAtTheSameWeightIsARepsRecordAndUsuallyAnEstimateRecordToo() {
        set("100", 5);
        UUID moreReps = set("100", 6);
        assertThat(flags().get(moreReps)).containsExactlyInAnyOrder(REPS_AT_WEIGHT, ESTIMATED_1RM);
    }

    @Test
    void aNewHeaviestWeightIsAWeightRecordButNotARepsRecordBecauseThereIsNoMarkToBeat() {
        set("100", 5);
        UUID heavier = set("110", 1);
        assertThat(flags().get(heavier)).contains(WEIGHT).doesNotContain(REPS_AT_WEIGHT);
    }

    @Test
    void repsAtALighterWeightDoNotBeatARepMarkAtAHeavierWeight() {
        set("100", 8);
        UUID lighterMoreReps = set("90", 8); // 8 reps at 90 does not beat 8 reps at 100
        assertThat(flags()).doesNotContainKey(lighterMoreReps);
    }

    @Test
    void repsAtAHeavierWeightBeatLighterRepMarks() {
        set("80", 10);
        UUID heavierSameReps = set("90", 10); // same reps at more weight: better
        // No earlier set at >= 90 kg, so it is a WEIGHT and ESTIMATED_1RM record, not a REPS record.
        assertThat(flags().get(heavierSameReps)).containsExactlyInAnyOrder(WEIGHT, ESTIMATED_1RM);
    }

    @Test
    void repsRecordMustBeatEverySetAtTheSameOrHeavierWeight() {
        set("100", 5);
        set("110", 8);
        UUID at100 = set("100", 7);  // 7 < 8 reps done at the heavier 110 kg: not a record at all
        assertThat(flags()).doesNotContainKey(at100);
        UUID at100Again = set("100", 9); // 9 > 8: record
        assertThat(flags().get(at100Again)).contains(REPS_AT_WEIGHT);
    }

    @Test
    void estimatedOneRepMaxUsesEpleyAndComparesExactly() {
        // 100 x 5 -> 100 * (1 + 5/30) = 116.67. 90 x 10 -> 90 * (1 + 10/30) = 120.
        // 10 reps at 90 also beats the 5 reps done at 90 kg or heavier, so it is a reps record too.
        set("100", 5);
        UUID better = set("90", 10);
        assertThat(flags().get(better)).containsExactlyInAnyOrder(ESTIMATED_1RM, REPS_AT_WEIGHT);
    }

    @Test
    void equalEstimatesAreATie() {
        // 90 x 10 -> 90 * 40 = 3600; 120 x 5 -> 120 * 35 = 4200 (higher); 84 x 15 -> 84 * 45 = 3780.
        // 100 x 8 = 100 * 38 = 3800 and 95 x 10 = 95 * 40 = 3800: exactly equal.
        set("100", 8);
        UUID tie = set("95", 10);
        assertThat(flags().getOrDefault(tie, EnumSet.noneOf(PersonalRecordType.class))).doesNotContain(ESTIMATED_1RM);
    }

    @Test
    void aSingleRepEstimateIsTheWeightItself() {
        assertThat(PersonalRecordCalculator.estimatedOneRepMax(new BigDecimal("140"), 1)).isEqualByComparingTo("140.00");
        assertThat(PersonalRecordCalculator.estimatedOneRepMax(new BigDecimal("100"), 5)).isEqualByComparingTo("116.67");
        assertThat(PersonalRecordCalculator.estimatedOneRepMax(new BigDecimal("60"), 12)).isEqualByComparingTo("84.00");
    }

    // ---- warm-ups, ordering, bodyweight ------------------------------------------------------

    @Test
    void warmUpSetsAreIgnoredEntirely() {
        UUID first = set("100", 5);
        UUID heavyWarmup = warmup("200", 5);      // would be a record if it counted
        UUID working = set("101", 5);
        assertThat(flags()).doesNotContainKey(heavyWarmup);
        assertThat(flags().get(working)).contains(WEIGHT); // beats 100, not the warm-up's 200
        assertThat(flags()).doesNotContainKey(first);
    }

    @Test
    void aWarmupCannotBeTheBaseline() {
        warmup("60", 10);
        UUID firstWorking = set("100", 5);
        assertThat(flags()).doesNotContainKey(firstWorking); // still the baseline
    }

    @Test
    void onlyEarlierSetsCountSoLaterHistoryNeverChangesEarlierFlags() {
        set("100", 5);
        UUID record = set("105", 5);
        Set<PersonalRecordType> before = flags().get(record);

        set("200", 1); // a much later, much heavier set

        assertThat(flags().get(record)).isEqualTo(before);
    }

    @Test
    void removingAnEarlierSetChangesLaterFlags() {
        UUID a = set("100", 5);
        set("110", 5);
        UUID c = set("105", 5);
        assertThat(flags()).doesNotContainKey(c); // 110 came first

        history.removeIf(h -> h.reps() == 5 && h.weightKg().compareTo(new BigDecimal("110")) == 0);

        assertThat(flags().get(c)).contains(WEIGHT); // now it beats the remaining 100
        assertThat(flags()).doesNotContainKey(a);
    }

    @Test
    void bodyweightSetsAtZeroKgTrackRepsOnly() {
        set("0", 8);
        UUID moreReps = set("0", 10);
        UUID fewer = set("0", 9);
        assertThat(flags().get(moreReps)).containsExactly(REPS_AT_WEIGHT);
        assertThat(flags()).doesNotContainKey(fewer);
    }

    // ---- records() ---------------------------------------------------------------------------

    @Test
    void recordsPickTheHeaviestSetPreferringMoreRepsThenTheEarlierOne() {
        setOnDay(0, "100", 5);
        setOnDay(1, "120", 3);
        setOnDay(2, "120", 5);   // same weight, more reps
        setOnDay(3, "120", 5);   // exact tie: earlier one is kept
        Records records = PersonalRecordCalculator.records(history);
        assertThat(records.heaviestWeight().weightKg()).isEqualByComparingTo("120");
        assertThat(records.heaviestWeight().reps()).isEqualTo(5);
        assertThat(records.heaviestWeight().performedOn()).isEqualTo(DAY.plusDays(2));
    }

    @Test
    void recordsIncludeTheBestEstimatedOneRepMaxWithItsSet() {
        set("100", 5);   // 116.67
        set("90", 10);   // 120.00
        Records records = PersonalRecordCalculator.records(history);
        assertThat(records.bestEstimated1rm().valueKg()).isEqualByComparingTo("120.00");
        assertThat(records.bestEstimated1rm().weightKg()).isEqualByComparingTo("90");
        assertThat(records.bestEstimated1rm().reps()).isEqualTo(10);
    }

    @Test
    void repsRecordsFormAFrontierHeaviestFirst() {
        set("60", 12);
        set("80", 8);
        set("100", 5);
        set("100", 3);   // dominated by 100 x 5
        set("70", 6);    // dominated by 80 x 8
        set("50", 12);   // dominated by 60 x 12
        Records records = PersonalRecordCalculator.records(history);
        assertThat(records.bestRepsAtWeight()).extracting(m -> m.weightKg().intValue(), PersonalRecordCalculator.Mark::reps)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(100, 5),
                        org.assertj.core.groups.Tuple.tuple(80, 8),
                        org.assertj.core.groups.Tuple.tuple(60, 12));
    }

    @Test
    void recordsIgnoreWarmUps() {
        set("100", 5);
        warmup("300", 5);
        Records records = PersonalRecordCalculator.records(history);
        assertThat(records.heaviestWeight().weightKg()).isEqualByComparingTo("100");
    }

    @Test
    void recordsWithOnlyWarmUpsAreEmpty() {
        warmup("40", 10);
        Records records = PersonalRecordCalculator.records(history);
        assertThat(records.heaviestWeight()).isNull();
        assertThat(records.bestRepsAtWeight()).isEmpty();
    }
}
