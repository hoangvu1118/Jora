package com.sefujo.searchprofile;

import com.sefujo.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name="search_profiles")
public class SearchProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(name="level")
    private String level; // e.g Intern, Fresher, etc.

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(
            name="search_profile_job_titles",
            joinColumns = @JoinColumn(name="search_profile_id")
    )
    @Column(name="job_title")
    private Set<String> jobTitles = new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name="search_profile_skills",
            joinColumns = @JoinColumn(name="search_profile_id")
    )
    @Column(name="skill")
    private Set<String> skills = new HashSet<>();

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name="search_profile_employment_types",
            joinColumns = @JoinColumn(name="search_profile_id")
    )
    @Column(name="employment_type") // Full Time, Part Time, etc.
    private Set<EmploymentType> employmentTypes = new HashSet<>();


    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "search_profile_workplace_types",
            joinColumns = @JoinColumn(name = "search_profile_id")
    )
    @Column(name = "workplace_type")
    private Set<WorkplaceType> workplaceTypes =
            new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "search_profile_locations",
            joinColumns = @JoinColumn(name = "search_profile_id")
    )
    @Column(name = "location")
    private Set<String> locations =
            new HashSet<>();


}
