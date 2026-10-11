package com.sefujo.job.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Table(name="job_postings")
@Data
@Getter
@Setter
public class JobPosting {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="platform_id")
    private Platform platform;

    @Column(name="external_job_id")
    private String externalJobId; // the actual ID that the platform provide

    private String status;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name="source_published_at")
    private LocalDateTime sourcePublishedAt; // Track when the Platform publishes
    @Column(name="first_seen_at")
    private LocalDateTime firstSeenAt; // When the crawler first discover it
    @Column(name="last_seen_at")
    private LocalDateTime lastSeenAt; // When the crawler most recently confirmed the posting still exists
    @Column(name="closed_at")
    private LocalDateTime closedAt; // Determine when the posting is closed
    @Column(name="created_at")
    private LocalDateTime createdAt; // When the instance was first created in the database
    @Column(name="updated_at")
    private LocalDateTime updatedAt; // When the instance was most recently modified in the database
}
