package com.personalos.backend.fitness.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.math.BigDecimal;

/** Rate of perceived exertion: 1 to 10 in half steps. {@code null} is valid (RPE is optional). */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidRpe.Validator.class)
public @interface ValidRpe {

    String message() default "must be between 1 and 10 in steps of 0.5";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidRpe, BigDecimal> {
        private static final BigDecimal ONE = BigDecimal.ONE;
        private static final BigDecimal TEN = BigDecimal.TEN;

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) return true;
            boolean inRange = value.compareTo(ONE) >= 0 && value.compareTo(TEN) <= 0;
            boolean halfStep = value.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() <= 0;
            return inRange && halfStep;
        }
    }
}
