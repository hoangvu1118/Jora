package com.sefujo.resume;

import com.sefujo.resume.entity.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {
    Optional<ResumeVersion> findByResumeIdAndType(Long resumeId, ResumeVersionType type);

    Optional<ResumeVersion> findByIdAndResumeIdAndResumeUserId(Long versionId, Long resumeId, Long userId);
}
