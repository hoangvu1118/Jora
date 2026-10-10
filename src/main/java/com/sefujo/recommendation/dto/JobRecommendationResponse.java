package com.sefujo.recommendation.dto;

import com.sefujo.job.entity.Job;
import com.sefujo.recommendation.JobRecommendation;
import com.sefujo.recommendation.MatchReason;
import com.sefujo.recommendation.RecommendationStatus;
import com.sefujo.searchprofile.EmploymentType;
import com.sefujo.searchprofile.WorkplaceType;
import lombok.Data;

import java.util.List;

@Data
public class JobRecommendationResponse {
    private Long id;
    private Job job;
    private MatchReason matchReason;
    private RecommendationStatus recommendationStatus;

}
