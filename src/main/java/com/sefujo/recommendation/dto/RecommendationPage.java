package com.sefujo.recommendation.dto;

import java.util.ArrayList;
import java.util.List;

public record RecommendationPage(
        List<JobRecommendationResponse> items,
        boolean hasNext,
        Integer nextScore,
        Long nextId
) {}
