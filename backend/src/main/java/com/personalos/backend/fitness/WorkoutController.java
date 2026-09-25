package com.personalos.backend.fitness;

import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.fitness.domain.WorkoutStatus;
import com.personalos.backend.fitness.dto.WorkoutDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workouts")
public class WorkoutController {

    private final WorkoutService service;
    private final WorkoutQueryService queries;

    public WorkoutController(WorkoutService service, WorkoutQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    // ---- workouts ----------------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutDetail start(@AuthenticationPrincipal(expression = "id") UUID userId,
                               @Valid @RequestBody(required = false) StartWorkoutRequest request) {
        return service.start(userId, request == null ? new StartWorkoutRequest(null, null) : request);
    }

    /** 200 with the active workout, or 204 when there is none. */
    @GetMapping("/current")
    public ResponseEntity<WorkoutDetail> current(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return queries.current(userId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public PagedResponse<WorkoutListItem> list(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(defaultValue = "COMPLETED") WorkoutStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.list(userId, status, from, to, page, size, sort);
    }

    @GetMapping("/{id}")
    public WorkoutDetail get(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return queries.detail(userId, id);
    }

    @PatchMapping("/{id}")
    public WorkoutDetail update(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                @Valid @RequestBody UpdateWorkoutRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    @PostMapping("/{id}/finish")
    public WorkoutDetail finish(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.finish(userId, id);
    }

    // ---- exercises in a workout --------------------------------------------------------------

    @PostMapping("/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutExerciseResponse addExercise(@AuthenticationPrincipal(expression = "id") UUID userId,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody AddWorkoutExerciseRequest request) {
        return service.addExercise(userId, id, request.exerciseId());
    }

    @PutMapping("/{id}/exercises/order")
    public WorkoutDetail reorder(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                 @Valid @RequestBody ReorderRequest request) {
        return service.reorder(userId, id, request.workoutExerciseIds());
    }

    @PatchMapping("/{id}/exercises/{workoutExerciseId}")
    public WorkoutExerciseResponse updateExercise(@AuthenticationPrincipal(expression = "id") UUID userId,
                                                  @PathVariable UUID id, @PathVariable UUID workoutExerciseId,
                                                  @Valid @RequestBody UpdateWorkoutExerciseRequest request) {
        return service.updateExerciseNotes(userId, id, workoutExerciseId, request.notes());
    }

    @DeleteMapping("/{id}/exercises/{workoutExerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeExercise(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                               @PathVariable UUID workoutExerciseId) {
        service.removeExercise(userId, id, workoutExerciseId);
    }

    // ---- sets --------------------------------------------------------------------------------

    /** 201 when created, 200 when a retry with the same client-generated id found the existing set. */
    @PostMapping("/{id}/exercises/{workoutExerciseId}/sets")
    public ResponseEntity<SetResponse> addSet(@AuthenticationPrincipal(expression = "id") UUID userId,
                                              @PathVariable UUID id, @PathVariable UUID workoutExerciseId,
                                              @Valid @RequestBody CreateSetRequest request) {
        WorkoutService.SetResult result = service.addSet(userId, id, workoutExerciseId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.set());
    }

    @PatchMapping("/{id}/exercises/{workoutExerciseId}/sets/{setId}")
    public SetResponse updateSet(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                 @PathVariable UUID workoutExerciseId, @PathVariable UUID setId,
                                 @Valid @RequestBody UpdateSetRequest request) {
        return service.updateSet(userId, id, workoutExerciseId, setId, request);
    }

    @DeleteMapping("/{id}/exercises/{workoutExerciseId}/sets/{setId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSet(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                          @PathVariable UUID workoutExerciseId, @PathVariable UUID setId) {
        service.deleteSet(userId, id, workoutExerciseId, setId);
    }
}
