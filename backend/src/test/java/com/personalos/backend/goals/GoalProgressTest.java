package com.personalos.backend.goals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests: no database, no clock. */
class GoalProgressTest {

    @Test
    void noTasksIsNullNotZero() {
        assertThat(GoalProgress.percent(0, 0)).isNull();
    }

    @Test
    void allOpenIsZeroPercent() {
        assertThat(GoalProgress.percent(4, 0)).isEqualTo(0);
    }

    @Test
    void allDoneIsAHundredPercent() {
        assertThat(GoalProgress.percent(4, 4)).isEqualTo(100);
    }

    @Test
    void aMixOfDoneAndOpenTasksRoundsToTheNearestWholePercent() {
        assertThat(GoalProgress.percent(3, 1)).isEqualTo(33); // 33.33...
        assertThat(GoalProgress.percent(3, 2)).isEqualTo(67); // 66.66...
        assertThat(GoalProgress.percent(8, 3)).isEqualTo(38); // 37.5 rounds up
        assertThat(GoalProgress.percent(8, 5)).isEqualTo(63); // 62.5 rounds up
    }

    @Test
    void aNegativeTaskCountIsTreatedAsNoTasks() {
        assertThat(GoalProgress.percent(-1, 0)).isNull();
    }
}
