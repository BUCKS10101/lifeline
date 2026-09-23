package com.personalos.backend.common.error;

import java.time.Instant;
import java.util.List;

/** Standard error body returned by every API endpoint. */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> violations
) {
    public record FieldViolation(String field, String message) {}
}
