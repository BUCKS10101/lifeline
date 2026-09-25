package com.personalos.backend.weight;

import com.personalos.backend.weight.dto.WeightDtos.Granularity;
import com.personalos.backend.weight.dto.WeightDtos.WeightSeries;
import com.personalos.backend.weight.dto.WeightDtos.WeightSummary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/weight")
public class WeightAnalyticsController {

    private final WeightAnalyticsService service;

    public WeightAnalyticsController(WeightAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/series")
    public WeightSeries series(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Granularity granularity) {
        return service.series(userId, from, to, granularity);
    }

    @GetMapping("/summary")
    public WeightSummary summary(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return service.summary(userId);
    }
}
