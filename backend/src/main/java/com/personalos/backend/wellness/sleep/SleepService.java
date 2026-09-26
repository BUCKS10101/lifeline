package com.personalos.backend.wellness.sleep;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.wellness.sleep.SleepTimes.InvalidSleepException;
import com.personalos.backend.wellness.sleep.SleepTimes.Resolved;
import com.personalos.backend.wellness.sleep.dto.SleepDtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class SleepService {

    /** Matches the CHECK constraint on sleep_entries.sleep_date. */
    static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);
    private static final int DEFAULT_SERIES_DAYS = 30;
    private static final int MAX_SERIES_DAYS = 366;

    private final SleepEntryRepository entries;
    private final SleepEntryMapper mapper;
    private final SleepQueries queries;
    private final ProfileService profiles;
    private final Clock clock;

    public SleepService(SleepEntryRepository entries, SleepEntryMapper mapper, SleepQueries queries, ProfileService profiles, Clock clock) {
        this.entries = entries;
        this.mapper = mapper;
        this.queries = queries;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** The saved night, and whether this call created it (true) or replaced an existing one (false). */
    public record UpsertResult(SleepEntryResponse entry, boolean created) {}

    /** Logs or replaces the night the person woke up on {@code date}. Idempotent: repeating a request changes nothing. */
    @Transactional
    public UpsertResult upsert(UUID userId, LocalDate date, UpsertSleepEntryRequest request) {
        ZoneId zone = profiles.timezoneOf(userId);
        requireLoggableDate(date, zone);

        Resolved times;
        try {
            times = SleepTimes.resolve(date, LocalTime.parse(request.bedtime()), LocalTime.parse(request.wakeTime()), zone);
        } catch (InvalidSleepException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLEEP_DURATION_INVALID", e.getMessage());
        }

        boolean created = entries.upsert(UUID.randomUUID(), userId, date, times.bedtimeAt(), times.wokeAt(), zone.getId(), Instant.now(clock));
        return new UpsertResult(mapper.toResponse(date, times.bedtimeAt(), times.wokeAt(), zone.getId()), created);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SleepEntryResponse> list(UUID userId, LocalDate from, LocalDate to, int page, int size) {
        LocalDate start = from != null ? from : EARLIEST_DATE;
        LocalDate end = to != null ? to : LATEST_DATE;
        requireOrderedRange(start, end);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sleepDate"));
        return PagedResponse.of(entries.findByUserIdAndSleepDateBetween(userId, start, end, pageable), mapper::toResponse);
    }

    @Transactional
    public void delete(UUID userId, LocalDate date) {
        if (entries.deleteNight(userId, date) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No sleep entry for that date");
        }
    }

    /** Defaults to the last 30 days ending today in the person's timezone. */
    @Transactional(readOnly = true)
    public SleepSeries series(UUID userId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : today(profiles.timezoneOf(userId));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_SERIES_DAYS - 1);
        requireOrderedRange(start, end);
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_SERIES_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "The range can be at most " + MAX_SERIES_DAYS + " days");
        }
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = start.minusDays(days);
        return new SleepSeries(start, end, queries.points(userId, start, end),
                queries.average(userId, start, end).orElse(null),
                queries.average(userId, previousStart, previousEnd).orElse(null));
    }

    /** A night can be logged if it is not before 2000 and not after today in the person's own timezone. */
    private void requireLoggableDate(LocalDate date, ZoneId zone) {
        if (date.isAfter(today(zone))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_IN_FUTURE", "You cannot log sleep for a future date");
        }
        if (date.isBefore(EARLIEST_DATE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
    }

    private static void requireOrderedRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        }
    }

    private LocalDate today(ZoneId zone) {
        return LocalDate.ofInstant(Instant.now(clock), zone);
    }
}
