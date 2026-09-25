package com.personalos.backend.weight;

import com.personalos.backend.weight.dto.WeightDtos.Direction;
import com.personalos.backend.weight.dto.WeightDtos.Progress;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TargetProgressTest {

    private static Progress compute(String start, String current, String target) {
        return TargetProgress.compute(new BigDecimal(start), new BigDecimal(current), new BigDecimal(target));
    }

    @Test
    void losingWeightIsPartWayThere() {
        Progress p = compute("90", "85", "80");
        assertThat(p.direction()).isEqualTo(Direction.LOSE);
        assertThat(p.percent()).isEqualByComparingTo("50.0");
        assertThat(p.remainingKg()).isEqualByComparingTo("5.00");
        assertThat(p.reached()).isFalse();
    }

    @Test
    void gainingWeightIsPartWayThere() {
        Progress p = compute("60", "63", "70");
        assertThat(p.direction()).isEqualTo(Direction.GAIN);
        assertThat(p.percent()).isEqualByComparingTo("30.0");
        assertThat(p.remainingKg()).isEqualByComparingTo("7.00");
    }

    @Test
    void reachingOrPassingTheTargetIsOneHundredPercentAndReached() {
        for (String current : new String[]{"80", "78"}) {
            Progress p = compute("90", current, "80");
            assertThat(p.percent()).isEqualByComparingTo("100.0");
            assertThat(p.remainingKg()).isEqualByComparingTo("0");
            assertThat(p.reached()).isTrue();
        }
        assertThat(compute("60", "72", "70").reached()).isTrue();
    }

    @Test
    void movingTheWrongWayIsZeroPercentNotNegative() {
        Progress lose = compute("90", "93", "80");
        assertThat(lose.percent()).isEqualByComparingTo("0");
        assertThat(lose.remainingKg()).isEqualByComparingTo("13.00"); // further away than when it started
        Progress gain = compute("60", "58", "70");
        assertThat(gain.percent()).isEqualByComparingTo("0");
        assertThat(gain.remainingKg()).isEqualByComparingTo("12.00");
    }

    @Test
    void targetEqualToStartMeansMaintainWithNoPercentage() {
        Progress on = compute("75", "75", "75");
        assertThat(on.direction()).isEqualTo(Direction.MAINTAIN);
        assertThat(on.percent()).isNull();
        assertThat(on.reached()).isTrue();
        assertThat(on.remainingKg()).isEqualByComparingTo("0");

        Progress off = compute("75", "77.5", "75");
        assertThat(off.direction()).isEqualTo(Direction.MAINTAIN);
        assertThat(off.percent()).isNull();
        assertThat(off.reached()).isFalse();
        assertThat(off.remainingKg()).isEqualByComparingTo("2.50");
    }

    @Test
    void percentIsRoundedToOneDecimal() {
        assertThat(compute("90", "87", "78").percent()).isEqualByComparingTo("25.0");
        assertThat(compute("90", "89", "87").percent()).isEqualByComparingTo("33.3");
    }
}
