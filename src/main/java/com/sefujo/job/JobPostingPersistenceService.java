package com.sefujo.job;

import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.job.dto.JobPostingResult;
import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.Job;
import com.sefujo.job.entity.JobPosting;
import com.sefujo.job.entity.Platform;
import com.sefujo.job.repository.JobPostingRepository;
import com.sefujo.job.repository.JobRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class JobPostingPersistenceService {

    private final JobRepository jobRepository;
    // Inject the required repositories through the constructor.
    JobPostingRepository jobPostingRepository;

    @Transactional
    public JobPostingResult save(
            RawJobPosting raw,
            Company company,
            Platform platform
    ) {
        boolean jobExisted = true;
        // 1. Map raw fields into a Job.
        String normalizedTitle = raw.title().toLowerCase();
        String location = raw.location().toLowerCase();
        // Find if job exist ? if no create new, otherwise map to the postings
        Job job = jobRepository.findByCompanyAndNormalizedTitleAndLocation(company, normalizedTitle, location)
                .orElse(null);
        if(job == null) {
            jobExisted = false;
            job = createJob(raw);
        }
        // 2. Link the Job to the Company.
        job.setCompany(company);
        // 3. Save the Job.
        jobRepository.save(job);
        // 4. Create its JobPosting.
        JobPosting posting = createJobPosting(raw, platform);
        // 5. Link the posting to the saved Job and Platform.
        posting.setJob(job);
        // 6. Save the JobPosting.
        return new JobPostingResult(
                jobExisted,
                jobPostingRepository.save(posting)
        );
    }

    private JobPosting createJobPosting(RawJobPosting raw, Platform platform) {
        JobPosting jobPosting = new JobPosting();
        jobPosting.setExternalJobId(raw.externalId());
        jobPosting.setSourceUrl(raw.sourceUrl());
        jobPosting.setPlatform(platform);
        jobPosting.setSourcePublishedAt(raw.sourcePublishedAt());
        jobPosting.setFirstSeenAt(LocalDateTime.now());
        jobPosting.setLastSeenAt(LocalDateTime.now());
        jobPosting.setStatus("OPEN");
        return jobPosting;
    }

    private Job createJob(RawJobPosting raw) {
        Job job = new Job();
        job.setTitle(raw.title());
        job.setLocation(raw.location());
        job.setDescription(raw.description());
        job.setNormalizedTitle(raw.title().toLowerCase());
        job.setCreatedDate(raw.sourcePublishedAt());
        job.setUpdatedDate(raw.sourceUpdatedAt());
        job.setStatus("OPEN");
        return job;
    }

    public Map<String, JobPosting> findExistingPostings(Platform platform, Set<String> externalIds) {
        List<JobPosting> postings =
                jobPostingRepository.findByPlatformAndExternalJobIdIn(
                        platform,
                        externalIds
                );

        Map<String, JobPosting> postingMap =
                postings.stream()
                        .collect(Collectors.toMap(
                                JobPosting::getExternalJobId,
                                Function.identity()
                        ));
        return postingMap;
    }

    public void markSeen(JobPosting existing) {
        existing.setLastSeenAt(LocalDateTime.now());
        jobPostingRepository.save(existing);
    }
}