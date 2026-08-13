package com.Chung_Woon.Chung_Woon.api.dto;

import com.Chung_Woon.Chung_Woon.domain.yard.OccupancyChoice;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OccupancyConfirmResponse(String batchId, OccupancyChoice choice, int appliedCount) {
}
