package com.personalos.backend.habits;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeekdayMaskTest {

    @Test
    void everyDayIsAllSevenBitsAndScheduledOnEveryDate() {
        assertThat(WeekdayMask.EVERY_DAY).isEqualTo(127);
        assertThat(WeekdayMask.toWeekdays(127)).containsExactly(1, 2, 3, 4, 5, 6, 7);
        for (int i = 0; i < 7; i++) assertThat(WeekdayMask.isScheduled(127, LocalDate.of(2026, 9, 21).plusDays(i))).isTrue();
    }

    @Test
    void mondayIsBitOneAndSundayIsBitSixtyFour() {
        assertThat(WeekdayMask.fromWeekdays(List.of(1))).isEqualTo(1);
        assertThat(WeekdayMask.fromWeekdays(List.of(7))).isEqualTo(64);
        assertThat(WeekdayMask.fromWeekdays(List.of(1, 3, 5))).isEqualTo(1 + 4 + 16);
    }

    @Test
    void toWeekdaysRoundTripsFromWeekdaysRegardlessOfInputOrder() {
        assertThat(WeekdayMask.toWeekdays(WeekdayMask.fromWeekdays(List.of(5, 1, 3)))).containsExactly(1, 3, 5);
    }

    @Test
    void isScheduledMatchesTheRealCalendarWeekday() {
        int weekdaysOnly = WeekdayMask.fromWeekdays(List.of(1, 2, 3, 4, 5));
        // 2026-09-21 is a Monday.
        assertThat(WeekdayMask.isScheduled(weekdaysOnly, LocalDate.of(2026, 9, 21))).isTrue();  // Mon
        assertThat(WeekdayMask.isScheduled(weekdaysOnly, LocalDate.of(2026, 9, 25))).isTrue();  // Fri
        assertThat(WeekdayMask.isScheduled(weekdaysOnly, LocalDate.of(2026, 9, 26))).isFalse(); // Sat
        assertThat(WeekdayMask.isScheduled(weekdaysOnly, LocalDate.of(2026, 9, 27))).isFalse(); // Sun
    }

    @Test
    void aDayOutsideOneToSevenIsRejected() {
        assertThatThrownBy(() -> WeekdayMask.fromWeekdays(List.of(0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeekdayMask.fromWeekdays(List.of(8))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicatesAreDetectedButNotByFromWeekdaysItself() {
        assertThat(WeekdayMask.hasDuplicates(List.of(1, 3, 1))).isTrue();
        assertThat(WeekdayMask.hasDuplicates(List.of(1, 3, 5))).isFalse();
        assertThat(WeekdayMask.fromWeekdays(List.of(1, 1))).isEqualTo(1); // the mask itself just collapses them
    }
}
