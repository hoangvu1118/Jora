package com.sefujo.recommendation;

import com.sefujo.recommendation.dto.JobRecommendGenerationResponse;
import com.sefujo.recommendation.dto.JobRecommendationResponse;
import com.sefujo.recommendation.dto.UpdateRecommendationStatusRequest;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileService;
import com.sefujo.user.UserService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobRecommendation")
@RequiredArgsConstructor
public class JobRecommendationController {
    private final JobRecommendationService jobRecommendationService;
    private final UserService userService;
    private final SearchProfileService searchProfileService;

    @PostMapping("/generate")
    public ResponseEntity<JobRecommendGenerationResponse> generateJobRecommendation() {
        SearchProfile profile = jobRecommendationService.findSearchProfile();
        JobRecommendGenerationResponse response = jobRecommendationService.generateRecommendations(profile);
        return  ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateJobRecommendationStatus(@PathVariable int id, @RequestBody UpdateRecommendationStatusRequest request){
        SearchProfile profile = jobRecommendationService.findSearchProfile();
        jobRecommendationService.updateRecommendationStatus(profile.getId(), id, request.getRecommendationStatus());
        return ResponseEntity.noContent().build();
    }


}
