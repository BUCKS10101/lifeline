package com.personalos.backend.habits;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 2026-09-21 is a Monday, so a Mon-Wed-Fri schedule is scheduled on 21st, 23rd, 25th, 28th, 30th and so on. */
class HabitStreaksTest {

    private static final int DAILY = WeekdayMask.EVERY_DAY;
    private static final int MON_WED_FRI = WeekdayMask.fromWeekdays(List.of(1, 3, 5));

    private static Set<LocalDate> dates(String... isoDates) {
        return java.util.Arrays.stream(isoDates).map(LocalDate::parse).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void aBrandNewHabitStartingTodayWithNothingTickedHasNoStreak() {
        LocalDate today = LocalDate.of(2026, 9, 24);
        var r = HabitStreaks.compute(DAILY, today, today, Set.of());
        assertThat(r.current()).isZero();
        assertThat(r.longest()).isZero();
    }

    @Test
    void everyDayTickedGivesAStreakEqualToTheNumberOfDaysIncludingToday() {
        LocalDate start = LocalDate.of(2026, 9, 20);
        LocalDate today = LocalDate.of(2026, 9, 24);
        var completed = dates("2026-09-20", "2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24");
        var r = HabitStreaks.compute(DAILY, start, today, completed);
        assertThat(r.current()).isEqualTo(5);
        assertThat(r.longest()).isEqualTo(5);
    }

    @Test
    void todayNotYetDoneDoesNotBreakTheStreak() {
        LocalDate start = LocalDate.of(2026, 9, 20);
        LocalDate today = LocalDate.of(2026, 9, 24);
        var completed = dates("2026-09-20", "2026-09-21", "2026-09-22", "2026-09-23"); // today missing
        var r = HabitStreaks.compute(DAILY, start, today, completed);
        assertThat(r.current()).isEqualTo(4);
        assertThat(r.longest()).isEqualTo(4);
    }

    @Test
    void yesterdayMissedBreaksTheStreakEvenIfTodayIsDone() {
        LocalDate start = LocalDate.of(2026, 9, 20);
        LocalDate today = LocalDate.of(2026, 9, 24);
        var completed = dates("2026-09-20", "2026-09-21", "2026-09-24"); // 22nd and 23rd missing
        var r = HabitStreaks.compute(DAILY, start, today, completed);
        assertThat(r.current()).isEqualTo(1);   // just today
        assertThat(r.longest()).isEqualTo(2);   // the 20th-21st run
    }

    @Test
    void aGapInTheMiddleResetsTheRunningCountButLongestRemembersTheEarlierRun() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate today = LocalDate.of(2026, 9, 10);
        // done 1-5 (5 days), missed 6, done 7-10 (4 days)
        var completed = dates("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04", "2026-09-05",
                "2026-09-07", "2026-09-08", "2026-09-09", "2026-09-10");
        var r = HabitStreaks.compute(DAILY, start, today, completed);
        assertThat(r.current()).isEqualTo(4);
        assertThat(r.longest()).isEqualTo(5);
    }

    @Test
    void unscheduledDaysAreSkippedAndNeitherBreakNorExtendTheStreak() {
        // Mon-Wed-Fri only: 21st Mon, 23rd Wed, 25th Fri are the scheduled days; 22nd/24th/26th/27th are not scheduled.
        LocalDate start = LocalDate.of(2026, 9, 21);
        LocalDate today = LocalDate.of(2026, 9, 25);
        var completed = dates("2026-09-21", "2026-09-23", "2026-09-25");
        var r = HabitStreaks.compute(MON_WED_FRI, start, today, completed);
        assertThat(r.current()).isEqualTo(3);
        assertThat(r.longest()).isEqualTo(3);
    }

    @Test
    void aMissedScheduledDayBreaksTheStreakEvenWithUnscheduledDaysAroundIt() {
        LocalDate start = LocalDate.of(2026, 9, 21); // Mon
        LocalDate today = LocalDate.of(2026, 9, 28); // next Mon
        // 21 Mon done, 23 Wed MISSED, 25 Fri done, 28 Mon done
        var completed = dates("2026-09-21", "2026-09-25", "2026-09-28");
        var r = HabitStreaks.compute(MON_WED_FRI, start, today, completed);
        assertThat(r.current()).isEqualTo(2);   // 25th, 28th
        assertThat(r.longest()).isEqualTo(2);
    }

    @Test
    void todaysDateNotBeingAScheduledDayIsJustSkippedNotSpecialCased() {
        // Today (Saturday) is not scheduled for a Mon-Wed-Fri habit, whether or not it's "done" is meaningless (never ticked).
        LocalDate start = LocalDate.of(2026, 9, 21); // Mon
        LocalDate today = LocalDate.of(2026, 9, 26); // Sat
        var completed = dates("2026-09-21", "2026-09-23", "2026-09-25");
        var r = HabitStreaks.compute(MON_WED_FRI, start, today, completed);
        assertThat(r.current()).isEqualTo(3);
        assertThat(r.longest()).isEqualTo(3);
    }

    @Test
    void backfillingAnEarlierDayCanExtendTheCurrentStreak() {
        LocalDate start = LocalDate.of(2026, 9, 20);
        LocalDate today = LocalDate.of(2026, 9, 24);
        var before = HabitStreaks.compute(DAILY, start, today, dates("2026-09-20", "2026-09-22", "2026-09-23", "2026-09-24"));
        assertThat(before.current()).isEqualTo(3); // 22-24; the 21st gap breaks it from the 20th
        var after = HabitStreaks.compute(DAILY, start, today, dates("2026-09-20", "2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24"));
        assertThat(after.current()).isEqualTo(5); // backfilling the 21st joins the two runs
    }

    @Test
    void nothingBeforeTheStartDateCountsEvenIfItWasSomehowTicked() {
        LocalDate start = LocalDate.of(2026, 9, 22);
        LocalDate today = LocalDate.of(2026, 9, 24);
        // A date before the start is irrelevant: compute() only walks from startedOn, so it is never looked at.
        var r = HabitStreaks.compute(DAILY, start, today, dates("2026-09-20", "2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24"));
        assertThat(r.current()).isEqualTo(3);
        assertThat(r.longest()).isEqualTo(3);
    }

    @Test
    void aHabitStartingInTheFutureRelativeToTodayHasNoStreak() {
        var r = HabitStreaks.compute(DAILY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 24), Set.of());
        assertThat(r.current()).isZero();
        assertThat(r.longest()).isZero();
    }

    @Test
    void oneLongUnbrokenRunOverAFewYearsIsCountedCorrectly() {
        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate today = LocalDate.of(2026, 1, 1);
        Set<LocalDate> completed = new java.util.HashSet<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) completed.add(d);
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1;
        var r = HabitStreaks.compute(DAILY, start, today, completed);
        assertThat(r.current()).isEqualTo(days);
        assertThat(r.longest()).isEqualTo(days);
    }
}
