package com.personalos.backend.weight;

import com.personalos.backend.weight.dto.WeightDtos.UpsertWeightTargetRequest;
import com.personalos.backend.weight.dto.WeightDtos.WeightTargetResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The logged-in user's single target. PUT is idempotent: it sets the target, whether or not one existed. */
@RestController
@RequestMapping("/api/v1/weight/target")
public class WeightTargetController {

    private final WeightTargetService service;

    public WeightTargetController(WeightTargetService service) {
        this.service = service;
    }

    @GetMapping
    public WeightTargetResponse get(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return service.get(userId);
    }

    @PutMapping
    public WeightTargetResponse put(@AuthenticationPrincipal(expression = "id") UUID userId,
                                    @Valid @RequestBody UpsertWeightTargetRequest request) {
        return service.upsert(userId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal(expression = "id") UUID userId) {
        service.delete(userId);
    }
}
