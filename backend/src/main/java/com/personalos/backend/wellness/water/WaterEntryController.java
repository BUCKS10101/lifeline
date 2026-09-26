package com.personalos.backend.wellness.water;

import com.personalos.backend.wellness.water.dto.WaterDtos.AddWaterRequest;
import com.personalos.backend.wellness.water.dto.WaterDtos.WaterAddedResponse;
import com.personalos.backend.wellness.water.dto.WaterDtos.WaterDayResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/** Drinks always belong to the logged-in user: there is no user id in any path or body. */
@RestController
@RequestMapping("/api/v1/water-entries")
public class WaterEntryController {

    private final WaterService service;

    public WaterEntryController(WaterService service) {
        this.service = service;
    }

    /** One day's drinks and total. The date defaults to today in the person's timezone. */
    @GetMapping
    public WaterDayResponse day(@AuthenticationPrincipal(expression = "id") UUID userId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.day(userId, date);
    }

    /** 201 when the drink was added, 200 when a retry with the same client-generated id found the existing one. */
    @PostMapping
    public ResponseEntity<WaterAddedResponse> add(@AuthenticationPrincipal(expression = "id") UUID userId,
                                                  @Valid @RequestBody AddWaterRequest request) {
        WaterService.AddResult result = service.add(userId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.added());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
