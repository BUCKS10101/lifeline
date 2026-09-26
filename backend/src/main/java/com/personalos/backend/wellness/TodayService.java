package com.personalos.backend.wellness;

import com.personalos.backend.wellness.dto.WellnessDtos.*;
import com.personalos.backend.wellness.protein.ProteinQueries;
import com.personalos.backend.wellness.sleep.SleepEntryMapper;
import com.personalos.backend.wellness.sleep.SleepEntryRepository;
import com.personalos.backend.wellness.water.WaterQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/** Gathers what the Today view shows: one small indexed lookup per visible metric, nothing stored. */
@Service
public class TodayService {

    private final DateRules dates;
    private final WellnessPreferencesService preferences;
    private final SleepEntryRepository sleep;
    private final SleepEntryMapper sleepMapper;
    private final WaterQueries water;
    private final ProteinQueries protein;

    public TodayService(DateRules dates, WellnessPreferencesService preferences, SleepEntryRepository sleep,
                        SleepEntryMapper sleepMapper, WaterQueries water, ProteinQueries protein) {
        this.dates = dates;
        this.preferences = preferences;
        this.sleep = sleep;
        this.sleepMapper = sleepMapper;
        this.water = water;
        this.protein = protein;
    }

    @Transactional(readOnly = true)
    public TodayResponse today(UUID userId) {
        LocalDate today = dates.today(userId);
        PreferencesResponse prefs = preferences.get(userId);

        SleepToday sleepToday = null;
        if (prefs.sleepEnabled()) {
            var entry = sleep.findByUserIdAndSleepDate(userId, today).map(sleepMapper::toResponse).orElse(null);
            GoalProgress progress = entry == null ? GoalProgress.NONE : GoalProgress.of(entry.durationMinutes(), prefs.sleepGoalMinutes());
            sleepToday = new SleepToday(entry, prefs.sleepGoalMinutes(), progress.percent(), progress.reached());
        }

        WaterToday waterToday = null;
        if (prefs.waterEnabled()) {
            var total = water.dayTotal(userId, today);
            GoalProgress progress = GoalProgress.of(total.totalMl(), prefs.waterGoalMl());
            waterToday = new WaterToday(total.totalMl(), prefs.waterGoalMl(), progress.percent(), progress.reached(), total.entries());
        }

        ProteinToday proteinToday = null;
        if (prefs.proteinEnabled()) {
            var total = protein.dayTotal(userId, today);
            GoalProgress progress = GoalProgress.of(total.totalG(), prefs.proteinGoalG());
            proteinToday = new ProteinToday(total.totalG(), prefs.proteinGoalG(), progress.percent(), progress.reached(), total.entries());
        }

        return new TodayResponse(today, prefs, sleepToday, waterToday, proteinToday);
    }
}
