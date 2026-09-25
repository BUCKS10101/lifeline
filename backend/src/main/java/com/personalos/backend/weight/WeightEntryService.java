package com.personalos.backend.weight;

import com.personalos.backend.auth.ProfileService;
import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.weight.dto.WeightDtos.UpsertWeightEntryRequest;
import com.personalos.backend.weight.dto.WeightDtos.WeightEntryResponse;
import com.personalos.backend.weight.mapper.WeightEntryMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class WeightEntryService {

    /** Matches the CHECK constraint on weight_entries.entry_date. */
    static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);

    private final WeightEntryRepository entries;
    private final WeightEntryMapper mapper;
    private final ProfileService profiles;
    private final Clock clock;

    public WeightEntryService(WeightEntryRepository entries, WeightEntryMapper mapper, ProfileService profiles, Clock clock) {
        this.entries = entries;
        this.mapper = mapper;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** The saved entry, and whether this call created it (true) or replaced an existing one (false). */
    public record UpsertResult(WeightEntryResponse entry, boolean created) {}

    /**
     * Logs or replaces the weight for one day. Idempotent: repeating the same request changes nothing, so a retry
     * after a network failure is safe.
     */
    @Transactional
    public UpsertResult upsert(UUID userId, LocalDate date, UpsertWeightEntryRequest request) {
        requireLoggableDate(userId, date);
        BigDecimal weight = request.weightKg().setScale(2); // the DTO allows at most 2 decimals, so this never rounds
        String notes = blankToNull(request.notes());

        boolean created = entries.upsert(UUID.randomUUID(), userId, date, weight, notes, Instant.now(clock));
        return new UpsertResult(new WeightEntryResponse(date, weight, notes), created);
    }

    @Transactional(readOnly = true)
    public PagedResponse<WeightEntryResponse> list(UUID userId, LocalDate from, LocalDate to, int page, int size) {
        LocalDate start = from != null ? from : EARLIEST_DATE;
        LocalDate end = to != null ? to : LATEST_DATE;
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "entryDate"));
        return PagedResponse.of(entries.findByUserIdAndEntryDateBetween(userId, start, end, pageable), mapper::toResponse);
    }

    @Transactional
    public void delete(UUID userId, LocalDate date) {
        if (entries.deleteEntry(userId, date) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No weight entry for that date");
        }
    }

    /** A day can be logged if it is not before 2000 and not after today in the user's own timezone. */
    private void requireLoggableDate(UUID userId, LocalDate date) {
        LocalDate today = LocalDate.ofInstant(Instant.now(clock), profiles.timezoneOf(userId));
        if (date.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_IN_FUTURE", "You cannot log a weight for a future date");
        }
        if (date.isBefore(EARLIEST_DATE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_TOO_EARLY", "Dates before 2000-01-01 are not supported");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
