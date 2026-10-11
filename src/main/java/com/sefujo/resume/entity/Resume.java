package com.sefujo.resume.entity;

import com.sefujo.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "resume")
public class Resume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private LocalDateTime archivedAt;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public void archive() {
        this.archivedAt = LocalDateTime.now();
    }



}
