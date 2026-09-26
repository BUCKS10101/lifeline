package com.personalos.backend.wellness.water;

import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.wellness.DateRules;
import com.personalos.backend.wellness.GoalProgress;
import com.personalos.backend.wellness.WellnessPreferencesService;
import com.personalos.backend.wellness.water.WaterQueries.DayTotal;
import com.personalos.backend.wellness.water.domain.WaterEntry;
import com.personalos.backend.wellness.water.dto.WaterDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class WaterService {

    private final WaterEntryRepository entries;
    private final WaterQueries queries;
    private final WellnessPreferencesService preferences;
    private final DateRules dates;
    private final Clock clock;

    public WaterService(WaterEntryRepository entries, WaterQueries queries, WellnessPreferencesService preferences, DateRules dates, Clock clock) {
        this.entries = entries;
        this.queries = queries;
        this.preferences = preferences;
        this.dates = dates;
        this.clock = clock;
    }

    /** The drink, the day's total after it, and whether this call added it (false when a retry found the existing one). */
    public record AddResult(WaterAddedResponse added, boolean created) {}

    @Transactional
    public AddResult add(UUID userId, AddWaterRequest request) {
        LocalDate date = dates.resolve(userId, request.date());
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();

        boolean created = entries.insertIfAbsent(id, userId, date, request.amountMl(), Instant.now(clock)) == 1;
        WaterEntry entry = entries.findById(id).orElseThrow();
        if (!entry.getUserId().equals(userId)) {
            // Somebody else's entry already has this id. Say nothing about it beyond the id being taken.
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "This entry id is already in use");
        }
        // A retry returns what already exists, even if the retried request differs: the first one won.
        return new AddResult(new WaterAddedResponse(toResponse(entry), queries.dayTotal(userId, entry.getLogDate()).totalMl()), created);
    }

    @Transactional(readOnly = true)
    public WaterDayResponse day(UUID userId, LocalDate requested) {
        LocalDate date = dates.resolve(userId, requested);
        DayTotal total = queries.dayTotal(userId, date);
        Integer goal = preferences.get(userId).waterGoalMl();
        GoalProgress progress = GoalProgress.of(total.totalMl(), goal);
        List<WaterEntryResponse> list = entries.findByUserIdAndLogDateOrderByLoggedAtDescIdDesc(userId, date).stream().map(WaterService::toResponse).toList();
        return new WaterDayResponse(date, total.totalMl(), goal, progress.percent(), progress.reached(), list);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (entries.deleteEntry(id, userId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Water entry not found");
        }
    }

    private static WaterEntryResponse toResponse(WaterEntry e) {
        return new WaterEntryResponse(e.getId(), e.getLogDate(), e.getAmountMl(), e.getLoggedAt());
    }
}
