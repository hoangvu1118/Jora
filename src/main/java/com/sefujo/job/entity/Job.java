package com.sefujo.job.entity;

import com.sefujo.searchprofile.EmploymentType;
import com.sefujo.searchprofile.WorkplaceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Table(name = "jobs")
@Entity
@Getter
@Setter
public class Job {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private String title;

    @Column(name="normalized_title")
    private String normalizedTitle;

    @Column(columnDefinition = "TEXT")
    private String description;
    private String location;

    @Column(name = "employment_type")
    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @Column(name = "workplace_type")
    @Enumerated(EnumType.STRING)
    private WorkplaceType workplaceType;

    private String status;

    @Column(name = "experience_level")
    private String level;

    @Column(name="created_at")
    private LocalDateTime createdDate;

    @Column(name="updated_at")
    private LocalDateTime updatedDate;

    @Column(name="closed_at")
    private LocalDateTime closeDate;

}
