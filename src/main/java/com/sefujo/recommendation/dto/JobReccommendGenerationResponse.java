package com.sefujo.recommendation.dto;

import lombok.Data;

@Data
public class JobReccommendGenerationResponse {
    int totalCandidatesScored;
    int totalRecommendationsSaved;
}
