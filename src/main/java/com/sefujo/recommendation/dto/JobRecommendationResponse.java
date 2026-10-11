package com.sefujo.recommendation.dto;

import com.sefujo.job.dto.JobSummaryResponse;
import com.sefujo.job.entity.Job;
import com.sefujo.recommendation.JobRecommendation;
import com.sefujo.recommendation.MatchReason;
import com.sefujo.recommendation.RecommendationStatus;
import com.sefujo.searchprofile.EmploymentType;
import com.sefujo.searchprofile.WorkplaceType;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class JobRecommendationResponse {
    private Long id;
    private JobSummaryResponse job;
    private Set<MatchReason> matchReason;
    private RecommendationStatus recommendationStatus;

}
