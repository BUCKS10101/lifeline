package com.personalos.backend.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.calendar.dto.CalendarEventDtos.CalendarEventResponse;
import com.personalos.backend.calendar.dto.CalendarEventDtos.CreateEventRequest;
import com.personalos.backend.calendar.dto.CalendarEventDtos.UpdateEventRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Calendar events always belong to the logged-in user: there is no user id in any path or body. */
@RestController
@RequestMapping("/api/v1/calendar/events")
public class CalendarEventController {

    private final CalendarEventService service;
    private final ObjectMapper mapper;

    public CalendarEventController(CalendarEventService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Every event touching the inclusive local-date range [from, to], each on whichever day(s) it falls in its own zone. */
    @GetMapping
    public List<CalendarEventResponse> list(@AuthenticationPrincipal(expression = "id") UUID userId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(userId, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse create(@AuthenticationPrincipal(expression = "id") UUID userId,
                                        @Valid @RequestBody CreateEventRequest request) {
        return service.create(userId, request);
    }

    /** A partial edit: a field left out is unchanged; an explicit null clears description, endDate or endTime. */
    @PatchMapping("/{id}")
    public CalendarEventResponse update(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                                        @RequestBody JsonNode body) {
        return service.update(userId, id, UpdateEventRequest.from(body, mapper));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
