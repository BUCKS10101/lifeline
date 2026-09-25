package com.personalos.backend.fitness;

import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.fitness.domain.MuscleGroup;
import com.personalos.backend.fitness.dto.ExerciseDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The user id comes from the session principal's {@code id} property; the fitness module never imports auth types. */
@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<ExerciseResponse> list(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MuscleGroup muscleGroup,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(userId, q, muscleGroup, includeArchived, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseResponse create(@AuthenticationPrincipal(expression = "id") UUID userId,
                                   @Valid @RequestBody CreateExerciseRequest request) {
        return service.create(userId, request);
    }

    @PatchMapping("/{id}")
    public ExerciseResponse update(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateExerciseRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    @GetMapping("/{id}/history")
    public PagedResponse<ExerciseHistoryItem> history(
            @AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.history(userId, id, page, size);
    }

    @GetMapping("/{id}/records")
    public ExerciseRecords records(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.records(userId, id);
    }
}
