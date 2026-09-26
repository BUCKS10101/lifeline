package com.personalos.backend.wellness;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** "Today" in the person's own timezone, and the range of dates an entry may be logged for. */
@Component
public class DateRules {

    /** Matches the CHECK constraints on the log_date columns. */
    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    private final ProfileService profiles;
    private final Clock clock;

    public DateRules(ProfileService profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    public LocalDate today(UUID userId) {
        return LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
    }

    /** The requested date, or today when none was given; never in the future or before 2000. */
    public LocalDate resolve(UUID userId, LocalDate requested) {
        LocalDate today = today(userId);
        if (requested == null) return today;
        if (requested.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_IN_FUTURE", "You cannot use a future date");
        }
        if (requested.isBefore(EARLIEST)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
        return requested;
    }
}
