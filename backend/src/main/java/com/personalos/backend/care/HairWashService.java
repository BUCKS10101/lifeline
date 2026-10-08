package com.personalos.backend.care;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.care.HairWashRepository.Row;
import com.personalos.backend.care.dto.HairWashDtos.HairWashEntry;
import com.personalos.backend.care.dto.HairWashDtos.HairWashSummary;
import com.personalos.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** A plain date log of one personal-care activity: hair wash. No products, no conditions, just dates. */
@Service
public class HairWashService {

    /** Matches the CHECK constraint on hair_wash_entries.wash_date. */
    static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    /** Postgres reports the unique index by this name; used to tell a date clash from any other constraint failure. */
    private static final String DUPLICATE_DATE_INDEX = "uq_hair_wash_entries_user_date";

    private final HairWashRepository entries;
    private final ProfileService profiles;
    private final Clock clock;

    public HairWashService(HairWashRepository entries, ProfileService profiles, Clock clock) {
        this.entries = entries;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HairWashSummary summary(UUID userId, YearMonth month) {
        LocalDate today = today(userId);
        YearMonth m = month != null ? month : YearMonth.from(today);
        LocalDate lastWashedOn = entries.latest(userId).orElse(null);
        Integer daysAgo = lastWashedOn == null ? null : (int) ChronoUnit.DAYS.between(lastWashedOn, today);
        var rows = entries.listInRange(userId, m.atDay(1), m.atEndOfMonth());
        return new HairWashSummary(lastWashedOn, daysAgo, m.toString(), rows.stream().map(r -> new HairWashEntry(r.id(), r.washDate())).toList());
    }

    /** The entry for the given date (today when the date is omitted), and whether this call newly created it. */
    public record MarkResult(HairWashEntry entry, boolean created) {}

    @Transactional
    public MarkResult mark(UUID userId, LocalDate date) {
        LocalDate today = today(userId);
        LocalDate washDate = date != null ? date : today;
        requireDate(washDate, today);

        UUID id = UUID.randomUUID();
        boolean created = entries.insertIfAbsent(id, userId, washDate, Instant.now(clock)) == 1;
        UUID finalId = created ? id : entries.idForDate(userId, washDate).orElseThrow();
        return new MarkResult(new HairWashEntry(finalId, washDate), created);
    }

    @Transactional
    public HairWashEntry edit(UUID userId, UUID id, LocalDate date) {
        require(userId, id);
        LocalDate today = today(userId);
        requireDate(date, today);
        try {
            if (entries.updateDate(id, userId, date, Instant.now(clock)) == 0) throw notFound();
        } catch (DataIntegrityViolationException e) {
            throw duplicateDateOr(e);
        }
        return new HairWashEntry(id, date);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (entries.delete(id, userId) == 0) throw notFound();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Row require(UUID userId, UUID id) {
        return entries.find(userId, id).orElseThrow(HairWashService::notFound);
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
    }

    private static void requireDate(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_IN_FUTURE", "A hair wash cannot be in the future");
        }
        if (date.isBefore(EARLIEST)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
    }

    private static ApiException duplicateDateOr(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains(DUPLICATE_DATE_INDEX)) {
            return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_ENTRY", "That date already has a hair-wash entry");
        }
        throw e;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Hair-wash entry not found");
    }
}
