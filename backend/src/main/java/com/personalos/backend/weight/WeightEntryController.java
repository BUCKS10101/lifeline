package com.personalos.backend.weight;

import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.weight.dto.WeightDtos.UpsertWeightEntryRequest;
import com.personalos.backend.weight.dto.WeightDtos.WeightEntryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entries are addressed by date, and always belong to the logged-in user: there is no user id in any path, so one
 * user's entries cannot be reached by another.
 */
@RestController
@RequestMapping("/api/v1/weight-entries")
public class WeightEntryController {

    private final WeightEntryService service;

    public WeightEntryController(WeightEntryService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<WeightEntryResponse> list(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(userId, from, to, page, size);
    }

    /** 201 when the day had no entry yet, 200 when an existing one was replaced. */
    @PutMapping("/{date}")
    public ResponseEntity<WeightEntryResponse> upsert(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody UpsertWeightEntryRequest request) {
        WeightEntryService.UpsertResult result = service.upsert(userId, date, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/weight-entries/" + date)).body(result.entry());
        }
        return ResponseEntity.status(HttpStatus.OK).body(result.entry());
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId,
                       @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(userId, date);
    }
}
