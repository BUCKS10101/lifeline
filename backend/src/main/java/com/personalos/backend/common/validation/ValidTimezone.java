package com.personalos.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** The value must be an IANA timezone identifier. {@code null} is valid; combine with {@code @NotNull} to require one. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimezoneValidator.class)
public @interface ValidTimezone {

    String message() default "must be an IANA timezone identifier such as Asia/Kolkata";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
