package com.sefujo.recommendation;

import com.sefujo.job.entity.Job;
import com.sefujo.searchprofile.SearchProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, Long> {

    List<JobRecommendation> findBySearchProfile(SearchProfile searchProfile);
    Optional<JobRecommendation> findByIdAndSearchProfileId(Long id, long searchProfileId);

}
