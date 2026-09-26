package com.personalos.backend.wellness.water;

import com.personalos.backend.wellness.water.dto.WaterDtos.WaterSeries;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/water")
public class WaterSeriesController {

    private final WaterService service;

    public WaterSeriesController(WaterService service) {
        this.service = service;
    }

    @GetMapping("/series")
    public WaterSeries series(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.series(userId, from, to);
    }
}
