package com.personalos.backend.wellness.protein;

import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.wellness.DateRules;
import com.personalos.backend.wellness.GoalProgress;
import com.personalos.backend.wellness.WellnessPreferencesService;
import com.personalos.backend.wellness.protein.ProteinQueries.DayTotal;
import com.personalos.backend.wellness.protein.domain.ProteinEntry;
import com.personalos.backend.wellness.protein.dto.ProteinDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ProteinService {

    private final ProteinEntryRepository entries;
    private final ProteinQueries queries;
    private final WellnessPreferencesService preferences;
    private final DateRules dates;
    private final Clock clock;

    public ProteinService(ProteinEntryRepository entries, ProteinQueries queries, WellnessPreferencesService preferences, DateRules dates, Clock clock) {
        this.entries = entries;
        this.queries = queries;
        this.preferences = preferences;
        this.dates = dates;
        this.clock = clock;
    }

    /** The entry, the day's total after it, and whether this call added it (false when a retry found the existing one). */
    public record AddResult(ProteinAddedResponse added, boolean created) {}

    @Transactional
    public AddResult add(UUID userId, AddProteinRequest request) {
        LocalDate date = dates.resolve(userId, request.date());
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        String label = request.label() == null || request.label().isBlank() ? null : request.label().trim();

        boolean created = entries.insertIfAbsent(id, userId, date, request.grams(), label, Instant.now(clock)) == 1;
        ProteinEntry entry = entries.findById(id).orElseThrow();
        if (!entry.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "This entry id is already in use");
        }
        return new AddResult(new ProteinAddedResponse(toResponse(entry), queries.dayTotal(userId, entry.getLogDate()).totalG()), created);
    }

    @Transactional(readOnly = true)
    public ProteinDayResponse day(UUID userId, LocalDate requested) {
        LocalDate date = dates.resolve(userId, requested);
        DayTotal total = queries.dayTotal(userId, date);
        Integer goal = preferences.get(userId).proteinGoalG();
        GoalProgress progress = GoalProgress.of(total.totalG(), goal);
        List<ProteinEntryResponse> list = entries.findByUserIdAndLogDateOrderByLoggedAtDescIdDesc(userId, date).stream().map(ProteinService::toResponse).toList();
        return new ProteinDayResponse(date, total.totalG(), goal, progress.percent(), progress.reached(), list);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (entries.deleteEntry(id, userId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Protein entry not found");
        }
    }

    @Transactional(readOnly = true)
    public List<ProteinSuggestion> suggestions(UUID userId) {
        return queries.suggestions(userId);
    }

    private static ProteinEntryResponse toResponse(ProteinEntry e) {
        return new ProteinEntryResponse(e.getId(), e.getLogDate(), e.getGrams(), e.getLabel(), e.getLoggedAt());
    }

    private static final int DEFAULT_SERIES_DAYS = 14;
    private static final int MAX_SERIES_DAYS = 366;

    /** Defaults to the last 14 days ending today in the person's timezone; at most 366 days. */
    @Transactional(readOnly = true)
    public ProteinSeries series(UUID userId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : dates.today(userId);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_SERIES_DAYS - 1);
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "'from' must not be after 'to'");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_SERIES_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "The range can be at most " + MAX_SERIES_DAYS + " days");
        }
        return new ProteinSeries(start, end, preferences.get(userId).proteinGoalG(), queries.series(userId, start, end),
                queries.average(userId, start, end).orElse(null),
                queries.average(userId, start.minusDays(days), start.minusDays(1)).orElse(null));
    }
}
