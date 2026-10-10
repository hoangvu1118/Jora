package com.sefujo.recommendation;

import com.sefujo.common.exception.ResourceNotFound;
import com.sefujo.job.entity.Job;
import com.sefujo.recommendation.dto.JobRecommendGenerationResponse;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileService;
import com.sefujo.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobRecommendationService {
    private final JobRecommendationRepository jobRecommendationRepository;
    private final JobCandidateService jobCandidateService;
    private final JobMatchingStrategy jobMatchingStrategy;
    private final SearchProfileService searchProfileService;
    private final UserService userService;

    @Value("${job.matchScore.threshold}")
    private int jobScoreThreshold;

    public void updateRecommendationStatus(long profileId, long recommendationId, RecommendationStatus newStatus) {
        JobRecommendation jobRecommendation = jobRecommendationRepository.findByIdAndSearchProfileId(recommendationId, profileId)
                .orElseThrow(() -> new ResourceNotFound("Recommendation not found with id " + recommendationId));
        jobRecommendation.setStatus(newStatus);
        jobRecommendation.setUpdatedAt(LocalDateTime.now());
        jobRecommendationRepository.save(jobRecommendation);
    }

    @Transactional
    public SearchProfile findSearchProfile() {
        long userId = userService.getCurrentUserId();
        return searchProfileService.getSearchProfileByUSerID(userId);
    }

    @Transactional
    public JobRecommendGenerationResponse generateRecommendations(SearchProfile searchProfile) {
        List<Job> jobs = jobCandidateService.findCandidates(searchProfile);
        Map<Long, JobRecommendation> existingByJobId = jobRecommendationRepository.findBySearchProfile(searchProfile)
                .stream()
                .collect(Collectors.toMap(rec -> rec.getJob().getId(), rec -> rec));
        LocalDateTime now = LocalDateTime.now();
        List<JobRecommendation> toSave = new ArrayList<>();

        for(Job job : jobs){
            MatchResult result = jobMatchingStrategy.score(searchProfile, job);
            if (result.getScore() < jobScoreThreshold) {
                continue;
            }
            JobRecommendation jobRecommendation = existingByJobId.get(job.getId());
            if (jobRecommendation == null) {
                jobRecommendation =  new JobRecommendation();
                jobRecommendation.setSearchProfile(searchProfile);
                jobRecommendation.setJob(job);
                jobRecommendation.setCreatedAt(now);
                jobRecommendation.setStatus(RecommendationStatus.NEW);
            }
            jobRecommendation.setMatchScore(result.getScore());
            jobRecommendation.setMatchReasons(result.getReasons());
            jobRecommendation.setUpdatedAt(now);
            toSave.add(jobRecommendation);
        }
        jobRecommendationRepository.saveAll(toSave);
        return new JobRecommendGenerationResponse(jobs.size(), toSave.size());
    }


}
