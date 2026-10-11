package com.sefujo.recommendation.dto;

import java.util.List;

public record RecommendationPage(
        List<JobRecommendationResponse> items,
        boolean hasNext,
        // The Client will send this back in the next request (the cursor)
        Integer lastScore,
        Long lastId
) {}
