package com.personalos.backend.wellness;

import com.personalos.backend.wellness.dto.WellnessDtos.PreferencesResponse;
import com.personalos.backend.wellness.dto.WellnessDtos.UpdatePreferencesRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The logged-in user's own wellness preferences. PUT is idempotent and replaces the whole thing. */
@RestController
@RequestMapping("/api/v1/wellness/preferences")
public class WellnessPreferencesController {

    private final WellnessPreferencesService service;

    public WellnessPreferencesController(WellnessPreferencesService service) {
        this.service = service;
    }

    @GetMapping
    public PreferencesResponse get(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return service.get(userId);
    }

    @PutMapping
    public PreferencesResponse put(@AuthenticationPrincipal(expression = "id") UUID userId,
                                   @Valid @RequestBody UpdatePreferencesRequest request) {
        return service.replace(userId, request);
    }
}
