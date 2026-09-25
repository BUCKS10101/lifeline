package com.personalos.backend.weight.mapper;

import com.personalos.backend.weight.domain.WeightEntry;
import com.personalos.backend.weight.dto.WeightDtos.WeightEntryResponse;
import org.springframework.stereotype.Component;

@Component
public class WeightEntryMapper {

    public WeightEntryResponse toResponse(WeightEntry entry) {
        return new WeightEntryResponse(entry.getEntryDate(), entry.getWeightKg(), entry.getNotes());
    }
}
