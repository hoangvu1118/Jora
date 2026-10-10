package com.sefujo.recommendation.dto;

import com.sefujo.recommendation.RecommendationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class UpdateRecommendationStatusRequest {
    @Valid
    @NotNull
    public RecommendationStatus recommendationStatus;
}
