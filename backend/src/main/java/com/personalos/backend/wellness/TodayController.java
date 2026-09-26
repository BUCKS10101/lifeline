package com.personalos.backend.wellness;

import com.personalos.backend.wellness.dto.WellnessDtos.TodayResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wellness")
public class TodayController {

    private final TodayService service;

    public TodayController(TodayService service) {
        this.service = service;
    }

    @GetMapping("/today")
    public TodayResponse today(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return service.today(userId);
    }
}
