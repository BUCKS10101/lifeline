package com.personalos.backend.fitness;

import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.fitness.dto.TemplateDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workout-templates")
public class WorkoutTemplateController {

    private final TemplateService service;

    public WorkoutTemplateController(TemplateService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<TemplateSummary> list(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(userId, page, size);
    }

    @GetMapping("/{id}")
    public TemplateDetail get(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateDetail create(@AuthenticationPrincipal(expression = "id") UUID userId,
                                 @Valid @RequestBody TemplateRequest request) {
        return service.create(userId, request);
    }

    @PutMapping("/{id}")
    public TemplateDetail replace(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                  @Valid @RequestBody TemplateRequest request) {
        return service.replace(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateDetail duplicate(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.duplicate(userId, id);
    }
}
