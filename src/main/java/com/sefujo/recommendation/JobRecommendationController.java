package com.sefujo.recommendation;

import com.sefujo.common.exception.InvalidCursorException;
import com.sefujo.recommendation.dto.JobRecommendGenerationResponse;
import com.sefujo.recommendation.dto.JobRecommendationResponse;
import com.sefujo.recommendation.dto.RecommendationPage;
import com.sefujo.recommendation.dto.UpdateRecommendationStatusRequest;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileService;
import com.sefujo.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    public ResponseEntity<Void> updateJobRecommendationStatus(@PathVariable int id, @Valid @RequestBody UpdateRecommendationStatusRequest request){
        SearchProfile profile = jobRecommendationService.findSearchProfile();
        jobRecommendationService.updateRecommendationStatus(profile.getId(), id, request.getRecommendationStatus());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<RecommendationPage> searchJobRecommendation(
            @RequestParam(required = false) Integer lastScore,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ){
        if (lastScore == null && lastId != null) {
            throw new InvalidCursorException("lastScore and lastId must be sent together");
        }
        if (lastScore != null && lastId == null) {
            throw new InvalidCursorException("lastScore and lastId must be sent together");
        }
        SearchProfile profile = jobRecommendationService.findSearchProfile();
        Long profileId = profile.getId();
        RecommendationPage response = jobRecommendationService.getRecommendations(profileId, lastScore, lastId, size);
        return ResponseEntity.ok(response);
    }

}
