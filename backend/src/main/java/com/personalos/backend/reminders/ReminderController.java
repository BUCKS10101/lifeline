package com.personalos.backend.reminders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.reminders.dto.ReminderDtos.CreateReminderRequest;
import com.personalos.backend.reminders.dto.ReminderDtos.ReminderResponse;
import com.personalos.backend.reminders.dto.ReminderDtos.UpdateReminderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Reminders always belong to the logged-in user: there is no user id in any path or body. */
@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final ReminderService service;
    private final ObjectMapper mapper;

    public ReminderController(ReminderService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<ReminderResponse> list(@AuthenticationPrincipal(expression = "id") UUID userId,
                                       @RequestParam(defaultValue = "false") boolean includeCompleted) {
        return service.list(userId, includeCompleted);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse create(@AuthenticationPrincipal(expression = "id") UUID userId,
                                   @Valid @RequestBody CreateReminderRequest request) {
        return service.create(userId, request);
    }

    /** A partial edit: a field left out is unchanged. Nothing here can be cleared. */
    @PatchMapping("/{id}")
    public ReminderResponse update(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                   @RequestBody JsonNode body) {
        return service.update(userId, id, UpdateReminderRequest.from(body, mapper));
    }

    /** Idempotent: completing an already-completed reminder returns it unchanged. */
    @PostMapping("/{id}/complete")
    public ReminderResponse complete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.complete(userId, id);
    }

    /** Idempotent: reopening a pending reminder returns it unchanged. */
    @PostMapping("/{id}/reopen")
    public ReminderResponse reopen(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        return service.reopen(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
