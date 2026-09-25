package com.personalos.backend.weight;

import com.personalos.backend.weight.dto.WeightDtos.Direction;
import com.personalos.backend.weight.dto.WeightDtos.Progress;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Progress toward a weight target. Pure: no database, no clock.
 *
 * <p>The direction is worked out from where you started and where you are heading, not assumed:
 * a target below the starting weight is a loss, above it a gain, equal to it means maintaining.
 */
public final class TargetProgress {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private TargetProgress() {
    }

    /**
     * @param start   the starting weight (derived from the entries, never stored)
     * @param current the latest weight
     * @param target  the target weight
     */
    public static Progress compute(BigDecimal start, BigDecimal current, BigDecimal target) {
        int cmp = target.compareTo(start);
        if (cmp == 0) {
            // Nothing to travel: there is no percentage, only how far you are from the number.
            BigDecimal away = current.subtract(target).abs();
            return new Progress(Direction.MAINTAIN, null, away.setScale(2, RoundingMode.HALF_UP), away.signum() == 0);
        }

        boolean loss = cmp < 0;
        BigDecimal travelled = loss ? start.subtract(current) : current.subtract(start);
        BigDecimal total = loss ? start.subtract(target) : target.subtract(start);
        BigDecimal remaining = loss ? current.subtract(target) : target.subtract(current);

        // Moving away from the target counts as no progress (0%), and going past it counts as done (100%).
        BigDecimal percent = travelled.multiply(HUNDRED).divide(total, 10, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(HUNDRED).setScale(1, RoundingMode.HALF_UP);
        boolean reached = remaining.signum() <= 0;
        BigDecimal remainingKg = remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return new Progress(loss ? Direction.LOSE : Direction.GAIN, percent, remainingKg, reached);
    }
}
