package com.personalos.backend.wellness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GoalProgressTest {

    @Test
    void noGoalMeansNoProgressAndNeverReached() {
        assertThat(GoalProgress.of(1500, null)).isEqualTo(GoalProgress.NONE);
        assertThat(GoalProgress.of(1500, null).reached()).isFalse();
        assertThat(GoalProgress.of(1500, 0)).isEqualTo(GoalProgress.NONE);
    }

    @ParameterizedTest
    @CsvSource({"0, 2000, 0, false", "1, 2000, 0, false", "1250, 2000, 62, false", "1999, 2000, 99, false",
            "2000, 2000, 100, true", "2001, 2000, 100, true", "9000, 2000, 100, true"})
    void percentIsWholeAndStopsAtOneHundredAndReachedMeansAtOrAboveTheGoal(long total, int goal, int percent, boolean reached) {
        GoalProgress progress = GoalProgress.of(total, goal);
        assertThat(progress.percent()).isEqualTo(percent);
        assertThat(progress.reached()).isEqualTo(reached);
    }

    @Test
    void aNegativeTotalIsNeverBelowZeroPercent() {
        assertThat(GoalProgress.of(-5, 2000).percent()).isZero();
    }
}
