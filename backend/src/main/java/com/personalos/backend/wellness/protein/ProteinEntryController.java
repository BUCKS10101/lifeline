package com.personalos.backend.wellness.protein;

import com.personalos.backend.wellness.protein.dto.ProteinDtos.AddProteinRequest;
import com.personalos.backend.wellness.protein.dto.ProteinDtos.ProteinAddedResponse;
import com.personalos.backend.wellness.protein.dto.ProteinDtos.ProteinDayResponse;
import com.personalos.backend.wellness.protein.dto.ProteinDtos.ProteinSuggestion;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Protein entries always belong to the logged-in user: there is no user id in any path or body. */
@RestController
@RequestMapping("/api/v1")
public class ProteinEntryController {

    private final ProteinService service;

    public ProteinEntryController(ProteinService service) {
        this.service = service;
    }

    /** One day's entries and total. The date defaults to today in the person's timezone. */
    @GetMapping("/protein-entries")
    public ProteinDayResponse day(@AuthenticationPrincipal(expression = "id") UUID userId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.day(userId, date);
    }

    /** 201 when the entry was added, 200 when a retry with the same client-generated id found the existing one. */
    @PostMapping("/protein-entries")
    public ResponseEntity<ProteinAddedResponse> add(@AuthenticationPrincipal(expression = "id") UUID userId,
                                                    @Valid @RequestBody AddProteinRequest request) {
        ProteinService.AddResult result = service.add(userId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.added());
    }

    @DeleteMapping("/protein-entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    /** Up to six of the person's own past labels, most used first, each with the grams they last logged with it. */
    @GetMapping("/protein/suggestions")
    public List<ProteinSuggestion> suggestions(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return service.suggestions(userId);
    }
}
