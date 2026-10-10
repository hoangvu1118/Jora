package com.sefujo.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JobRecommendGenerationResponse {
    int totalCandidatesScored;
    int totalRecommendationsSaved;
}
