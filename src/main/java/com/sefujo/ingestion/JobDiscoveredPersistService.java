package com.sefujo.ingestion;

import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.job.JobPostingPersistenceService;
import com.sefujo.job.dto.JobPostingResult;
import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.JobPosting;
import com.sefujo.job.entity.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobDiscoveredPersistService {
    private final JobIngestionService jobIngestionService;
    private final JobPostingPersistenceService persistenceService;

    public JobPostingResult persistDiscoveredJob(RawJobPosting raw, String platformName, String companyName) {
        Company company = jobIngestionService.findOrCreateCompany(companyName, "");
        Platform platform = jobIngestionService.findOrCreatePlatform(platformName, "");
        // 5.2 Job deduplication
        return persistenceService.save(raw, company, platform);
    }
}
