package com.personalos.backend.wellness.protein;

import com.personalos.backend.wellness.protein.dto.ProteinDtos.ProteinSeries;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/protein")
public class ProteinSeriesController {

    private final ProteinService service;

    public ProteinSeriesController(ProteinService service) {
        this.service = service;
    }

    @GetMapping("/series")
    public ProteinSeries series(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.series(userId, from, to);
    }
}
