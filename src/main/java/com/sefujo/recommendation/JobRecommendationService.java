package com.sefujo.recommendation;

import com.sefujo.common.exception.ResourceNotFound;
import com.sefujo.job.dto.JobSummaryResponse;
import com.sefujo.job.entity.Job;
import com.sefujo.recommendation.dto.JobRecommendGenerationResponse;
import com.sefujo.recommendation.dto.JobRecommendationResponse;
import com.sefujo.recommendation.dto.RecommendationPage;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileService;
import com.sefujo.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                jobRecommendation.setStatus(RecommendationStatus.NEW);
            }
            jobRecommendation.setMatchScore(result.getScore());
            jobRecommendation.setMatchReasons(result.getReasons());
            toSave.add(jobRecommendation);
        }
        jobRecommendationRepository.saveAll(toSave);
        return new JobRecommendGenerationResponse(jobs.size(), toSave.size());
    }

    private List<JobRecommendation> fetchRows(long profileId, Integer lastScore, Long lastId, int limit){
        if(lastScore == null){
            return jobRecommendationRepository.findFirstPage(profileId, Limit.of(limit));
        }
        return jobRecommendationRepository.findNextPage(profileId, Limit.of(limit), lastScore, lastId);
    }

    private JobRecommendationResponse toResponse(JobRecommendation rec) {
        Job job = rec.getJob();
        JobSummaryResponse jobSummaryResponse = new JobSummaryResponse();
        jobSummaryResponse.setId(job.getId());
        jobSummaryResponse.setCompanyName(job.getCompany().getName());
        jobSummaryResponse.setTitle(job.getTitle());
        jobSummaryResponse.setLocation(job.getLocation());
        jobSummaryResponse.setLevel(job.getLevel());
        jobSummaryResponse.setEmploymentType(job.getEmploymentType());
        jobSummaryResponse.setWorkplaceType(job.getWorkplaceType());

        JobRecommendationResponse response = new JobRecommendationResponse();
        response.setId(rec.getId());
        response.setJob(jobSummaryResponse);
        response.setMatchReason(rec.getMatchReasons());
        response.setRecommendationStatus(rec.getStatus());
        return response;
    }

    public RecommendationPage getRecommendations(long profileId, Integer lastScore, Long lastId, int size) {
        List<JobRecommendation> recommendations = fetchRows(profileId, lastScore, lastId, size + 1);
        boolean hasNext =  recommendations.size() > size;
        if(hasNext){
            recommendations = recommendations.subList(0, size);
        }

        List<JobRecommendationResponse> responseItems = new ArrayList<>();
        for(JobRecommendation recommendation : recommendations){
            responseItems.add(toResponse(recommendation));
        }

        Integer pageLastScore = null;
        Long pageLastId = null;
        if (hasNext) {
            JobRecommendation lastRow = recommendations.get(recommendations.size() - 1);
            pageLastScore = lastRow.getMatchScore();
            pageLastId = lastRow.getId();
        }
        return new RecommendationPage(responseItems, hasNext, pageLastScore, pageLastId);

    }

}
