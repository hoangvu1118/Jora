package com.sefujo.ingestion;

import com.sefujo.ingestion.dto.DiscoveredJobResult;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.job.JobPostingPersistenceService;
import com.sefujo.job.dto.JobPostingResult;
import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.JobPosting;
import com.sefujo.job.entity.Platform;
import com.sefujo.job.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobDiscoveredPersistService {
    private final JobIngestionService jobIngestionService;
    private final JobPostingPersistenceService persistenceService;

    public DiscoveredJobResult handleDiscoveredJob(JobDiscoveredEvent event){
        String externalId = event.job().externalId();
        Platform platform = jobIngestionService.findOrCreatePlatform(event.platform(), "");
        JobPosting existingPosting = persistenceService.findExistingPosting(platform, externalId)
                .orElse(null);

        //  If posting exists -> a retry, publish again
        if(existingPosting != null){
            return new DiscoveredJobResult(
                    true,
                    existingPosting.getJob().getId()
            );
        }
        //   If posting never existed before -> Save new posting
        JobPostingResult saved = persistDiscoveredJob(
                event.job(),
                platform,
                event.job().sourceKey()
        );

        if(saved.jobExisted()){
            // Same job but from another posting -> no job.created
            return new DiscoveredJobResult(false, null);
        }
        return new DiscoveredJobResult(true, saved.jobPosting().getId());
    }

    public JobPostingResult persistDiscoveredJob(RawJobPosting raw, Platform platform, String companyName) {
        Company company = jobIngestionService.findOrCreateCompany(companyName, "");
        // 5.2 Job deduplication
        return persistenceService.save(raw, company, platform);
    }
}
