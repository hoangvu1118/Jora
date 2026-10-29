package com.sefujo.job.repository;

import com.sefujo.job.entity.JobPosting;
import com.sefujo.job.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface JobPostingRepository extends JpaRepository<JobPosting,Long> {
//    Map<String, JobPosting > findByPlatformAndExternalJobIdIn(Platform platform, Set<String> externalJobIds);
    List<JobPosting> findByPlatformAndExternalJobIdIn(
            Platform platform,
            Set<String> externalJobIds
    );

    Optional<JobPosting> findByPlatformAndExternalJobId(Platform platform, String externalJobId);
}
