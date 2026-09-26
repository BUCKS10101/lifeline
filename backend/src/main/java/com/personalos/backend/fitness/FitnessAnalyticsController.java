package com.personalos.backend.fitness;

import com.personalos.backend.fitness.dto.AnalyticsDtos.ExercisePersonalRecord;
import com.personalos.backend.fitness.dto.AnalyticsDtos.VolumeSeries;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fitness")
public class FitnessAnalyticsController {

    private final FitnessAnalyticsService service;

    public FitnessAnalyticsController(FitnessAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/analytics/volume")
    public VolumeSeries volume(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String granularity) {
        return service.volume(userId, from, to, granularity);
    }

    @GetMapping("/personal-records")
    public List<ExercisePersonalRecord> personalRecords(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return service.recentRecords(userId, limit);
    }
}
