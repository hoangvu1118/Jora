package com.sefujo.recommendation;

import com.sefujo.recommendation.dto.JobReccommendGenerationResponse;
import com.sefujo.recommendation.dto.JobRecommendationResponse;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileService;
import com.sefujo.user.UserService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobRecommendation")
@RequiredArgsConstructor
public class JobRecommendationController {
    private final JobRecommendationService jobRecommendationService;
    private final UserService userService;
    private final SearchProfileService searchProfileService;

    @PostMapping("/generate")
    public ResponseEntity<JobReccommendGenerationResponse> generateJobRecommendation() {
        long userId = userService.getCurrentUserId();
        SearchProfile profile = searchProfileService.getSearchProfileByUSerID(userId);
        JobReccommendGenerationResponse response = jobRecommendationService.generateRecommendations(profile);
        return  ResponseEntity.ok(response);
    }


}
