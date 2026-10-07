package com.personalos.backend.goals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.goals.dto.GoalDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Goals always belong to the logged-in user: there is no user id in any path or body. */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService service;
    private final ObjectMapper mapper;

    public GoalController(GoalService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** One status of the person's goals: active (default), achieved or archived. */
    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal(expression = "id") UUID userId,
                                   @RequestParam(required = false) String status) {
        return service.list(userId, GoalStatus.parse(status));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthenticationPrincipal(expression = "id") UUID userId,
                               @Valid @RequestBody CreateGoalRequest request) {
        return service.create(userId, request);
    }

    /** The goal plus its linked tasks (open first) and habits. */
    @GetMapping("/{id}")
    public GoalDetail get(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    /** A partial edit: a field left out is unchanged; an explicit null clears description or targetDate. Achieving
     * sets achievedAt; any other status clears it. */
    @PatchMapping("/{id}")
    public GoalResponse update(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                              @RequestBody JsonNode body) {
        return service.update(userId, id, UpdateGoalRequest.from(body, mapper));
    }

    /** Tasks and habits linked to this goal are unlinked, not deleted. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
