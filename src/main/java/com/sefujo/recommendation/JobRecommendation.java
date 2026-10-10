package com.sefujo.recommendation;

import com.sefujo.job.entity.Job;
import com.sefujo.searchprofile.SearchProfile;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Data
public class JobRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="job_id")
    private Job job;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="search_profile_id")
    private SearchProfile searchProfile;


    @Column(name = "match_score")
    private int matchScore;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "job_recommendation_match_reasons",
            joinColumns = @JoinColumn(name = "job_recommendation_id")
    )
    @Column(name = "match_reason")
    private Set<MatchReason> matchReasons;

    @Enumerated(EnumType.STRING)
    private RecommendationStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
