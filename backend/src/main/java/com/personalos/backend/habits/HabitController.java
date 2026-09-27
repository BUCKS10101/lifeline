package com.personalos.backend.habits;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.habits.dto.HabitDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Habits and their completions always belong to the logged-in user: there is no user id in any path or body. */
@RestController
@RequestMapping("/api/v1/habits")
public class HabitController {

    private final HabitService service;
    private final ObjectMapper mapper;

    public HabitController(HabitService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<HabitResponse> list(@AuthenticationPrincipal(expression = "id") UUID userId,
                                    @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(userId, includeArchived);
    }

    /** Only what is scheduled today; archived habits never appear here. For the dashboard. */
    @GetMapping("/today")
    public List<HabitResponse> today(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return service.todayList(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitResponse create(@AuthenticationPrincipal(expression = "id") UUID userId,
                                @Valid @RequestBody CreateHabitRequest request) {
        return service.create(userId, request);
    }

    /** A partial edit: a field left out is unchanged, and an explicit null clears goalId only. */
    @PatchMapping("/{id}")
    public HabitResponse update(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                @RequestBody JsonNode body) {
        return service.update(userId, id, UpdateHabitRequest.from(body, mapper));
    }

    @PostMapping("/{id}/archive")
    public HabitResponse archive(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.archive(userId, id);
    }

    @PostMapping("/{id}/unarchive")
    public HabitResponse unarchive(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.unarchive(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    /** 201 when the day was newly ticked, 200 when it was already ticked. */
    @PutMapping("/{id}/completions/{date}")
    public ResponseEntity<HabitResponse> tick(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        HabitService.TickResult result = service.tick(userId, id, date);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.habit());
    }

    @DeleteMapping("/{id}/completions/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untick(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.untick(userId, id, date);
    }

    @GetMapping("/{id}/history")
    public HabitHistory history(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(userId, id, from, to);
    }
}
