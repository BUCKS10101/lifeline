package com.personalos.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TimezoneValidator implements ConstraintValidator<ValidTimezone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || Timezones.isValid(value);
    }
}
