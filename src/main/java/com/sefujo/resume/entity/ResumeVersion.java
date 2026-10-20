package com.sefujo.resume.entity;

import com.sefujo.job.entity.Job;
import com.sefujo.resume.content.ResumeContent;
import com.sefujo.resume.ResumeVersionStatus;
import com.sefujo.resume.ResumeVersionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "resume_version")
public class ResumeVersion {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name="id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="job_id")
    @Setter
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_version_id")
    @Setter
    private ResumeVersion parentVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="resume_id", nullable=false)
    @Setter
    private Resume resume;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb")
    private ResumeContent content;

    @Enumerated(EnumType.STRING)
    @Setter
    private ResumeVersionType type;

    @Enumerated(EnumType.STRING)
    private ResumeVersionStatus editStatus = ResumeVersionStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ResumeVersion() {

    }

    public ResumeVersion(Resume resume, ResumeContent content) {
        this.resume = resume;
        this.content = content;
        this.type = ResumeVersionType.BASE;
    }

    public void updateContent(ResumeContent newContent) {
        if (editStatus == ResumeVersionStatus.FINAL) {
            throw new IllegalStateException("Version is locked");
        }
        this.content = newContent;
    }

    public void submit() {
        this.editStatus = ResumeVersionStatus.FINAL;
    }
}
