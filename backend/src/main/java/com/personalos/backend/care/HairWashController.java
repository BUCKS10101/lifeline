package com.personalos.backend.care;

import com.personalos.backend.care.dto.HairWashDtos.EditHairWashRequest;
import com.personalos.backend.care.dto.HairWashDtos.HairWashEntry;
import com.personalos.backend.care.dto.HairWashDtos.HairWashSummary;
import com.personalos.backend.care.dto.HairWashDtos.MarkHairWashRequest;
import com.personalos.backend.common.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** Hair-wash entries always belong to the logged-in user: there is no user id in any path or body. */
@RestController
@RequestMapping("/api/v1/personal-care/hair-wash")
public class HairWashController {

    private final HairWashService service;

    public HairWashController(HairWashService service) {
        this.service = service;
    }

    /** The whole-history last-washed summary, plus one month's entries (defaults to the person's current month) for the history view. */
    @GetMapping
    public HairWashSummary summary(@AuthenticationPrincipal(expression = "id") UUID userId,
                                   @RequestParam(required = false) String month) {
        return service.summary(userId, parseMonth(month));
    }

    /** 201 when a new entry was marked, 200 when that date already had one (idempotent, same date twice changes nothing). */
    @PostMapping
    public ResponseEntity<HairWashEntry> mark(@AuthenticationPrincipal(expression = "id") UUID userId,
                                              @RequestBody(required = false) MarkHairWashRequest request) {
        HairWashService.MarkResult result = service.mark(userId, request == null ? null : request.date());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.entry());
    }

    @PatchMapping("/{id}")
    public HairWashEntry edit(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id,
                              @Valid @RequestBody EditHairWashRequest request) {
        return service.edit(userId, id, request.date());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    private static YearMonth parseMonth(String value) {
        if (value == null) return null;
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "month must look like 2026-10");
        }
    }
}
