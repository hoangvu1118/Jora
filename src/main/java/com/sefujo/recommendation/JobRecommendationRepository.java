package com.sefujo.recommendation;

import com.sefujo.job.entity.Job;
import com.sefujo.searchprofile.SearchProfile;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.sefujo.recommendation.RecommendationStatus;

import java.util.List;
import java.util.Optional;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, Long> {
    List<JobRecommendation> findBySearchProfile(SearchProfile searchProfile);
    Optional<JobRecommendation> findByIdAndSearchProfileId(Long id, long searchProfileId);

    @Query("""
           SELECT r FROM JobRecommendation r JOIN FETCH r.job j JOIN FETCH j.company
           WHERE r.searchProfile.id = :searchProfileId
           AND r.status <> com.sefujo.recommendation.RecommendationStatus.DISMISSED
           ORDER BY r.matchScore DESC, r.id DESC
            """)
    List<JobRecommendation> findFirstPage(long searchProfileId, Limit limit);

    @Query("""
        SELECT r FROM JobRecommendation r JOIN FETCH r.job j JOIN FETCH j.company
        WHERE r.searchProfile.id = :searchProfileId
        AND r.status <> com.sefujo.recommendation.RecommendationStatus.DISMISSED
        AND (r.matchScore < :lastScore OR (r.matchScore = :lastScore AND r.id < :lastId))
        ORDER BY r.matchScore DESC, r.id DESC
    """)
    List<JobRecommendation> findNextPage(long searchProfileId, Limit limit, int lastScore, long lastId);

}
