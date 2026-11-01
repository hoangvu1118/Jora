package com.sefujo.job.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Table(name = "crawl_targets")
@Entity
public class CrawlTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Column(name = "company_board_token")
    // This is a BOARD TOKEN
    private String companyName;

    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // When the instance was first created in the database

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // When the instance was most recently modified in the database

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getCompanyBoardToken() {
        return companyName;
    }


}
