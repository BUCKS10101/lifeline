package com.personalos.backend.fitness;

import com.personalos.backend.fitness.dto.SummaryDtos.FitnessSummary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fitness")
public class FitnessSummaryController {

    private final FitnessSummaryService service;

    public FitnessSummaryController(FitnessSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public FitnessSummary summary(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(userId, from, to);
    }
}
