package com.sefujo.recommendation;

import com.sefujo.job.entity.Job;
import com.sefujo.recommendation.dto.JobReccommendGenerationResponse;
import com.sefujo.recommendation.dto.JobRecommendationResponse;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobRecommendationService {
    private final JobRecommendationRepository jobRecommendationRepository;
    private final JobCandidateService jobCandidateService;
    private final JobMatchingStrategy jobMatchingStrategy;
    private SearchProfileService searchProfileService;

    @Value("${job.matchScore.threshold}")
    private int jobScoreThreshold;

    private JobRecommendation persistRecommendation(SearchProfile profile, MatchResult matchResult, Job job) {
        JobRecommendation jobRecommendation = jobRecommendationRepository
                        .findBySearchProfileAndJob(profile, job)
                        .orElseGet(() -> {
                            JobRecommendation newRecommendation =
                                    new JobRecommendation();

                            newRecommendation.setSearchProfile(profile);
                            newRecommendation.setJob(job);
                            newRecommendation.setStatus(
                                    RecommendationStatus.NEW
                            );
                            newRecommendation.setCreatedAt(
                                    LocalDateTime.now()
                            );

                            return newRecommendation;
                        });
        jobRecommendation.setMatchReasons(matchResult.getReasons());
        jobRecommendation.setMatchScore(matchResult.getScore());
        jobRecommendation.setUpdatedAt(LocalDateTime.now());
        return jobRecommendationRepository.save(jobRecommendation);
    }

//    private JobRecommendationResponse handleJobRecResponse(JobRecommendation jobRecommendation) {
//
//    }

    public JobReccommendGenerationResponse generateRecommendations(SearchProfile searchProfile) {
        List<Job> jobs = jobCandidateService.findCandidates(searchProfile);
        int totalCandidates = 0;
        int totalRecommendations = 0;
        for(Job job : jobs){
            MatchResult result = jobMatchingStrategy.score(searchProfile, job);

            if(result.getScore() >= jobScoreThreshold){
                persistRecommendation(searchProfile, result, job);
                totalRecommendations++;
            }
            totalCandidates++;
        }
        JobReccommendGenerationResponse response = new JobReccommendGenerationResponse();
        response.setTotalRecommendationsSaved(totalRecommendations);
        response.setTotalCandidatesScored(totalCandidates);
        return response;
    }


}
