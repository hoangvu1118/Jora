package com.sefujo.ingestion;

import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.ingestion.dto.RawJobSummary;
import com.sefujo.job.JobLocationFilter;
import com.sefujo.job.JobPostingPersistenceService;
import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.JobPosting;
import com.sefujo.job.entity.Platform;
import com.sefujo.job.repository.CompanyRepository;
import com.sefujo.job.repository.PlatformRepository;
import com.sefujo.job.source.JobSource;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class JobIngestionService {
    private final JobLocationFilter locationFilter;
    JobPostingPersistenceService persistenceService;
    CompanyRepository companyRepository;
    PlatformRepository platformRepository;

    public Company findOrCreateCompany(String name, String website) {
        return companyRepository.findByName(name)
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setName(name);
                    company.setWebsite(website);
                    return companyRepository.save(company);
                });
    }
    public Platform findOrCreatePlatform(String name, String website) {
        return platformRepository.findByName(name)
                .orElseGet(() -> {
                    Platform platform = new Platform();
                    platform.setName(name);
                    platform.setWebsite(website);
                    return platformRepository.save(platform);
                });
    }

    public List<RawJobPosting> ingest(
            JobSource source,
            String companyName // the company name
    ) {
        List<RawJobSummary> summaries =
                source.fetchJobSummaries(companyName);

        // 2. Resolve Platform
        Platform platform = findOrCreatePlatform(source.platformName(), "");
        // 3. Filter location
        List<RawJobSummary> candidates  = summaries.stream()
                .filter(s -> locationFilter.isTargetLocation(s.location()))
                .toList();

        // 4. Check if posting existed
        Set<String> externalIds = candidates.stream()
                .map(RawJobSummary::externalId)
                .collect(Collectors.toSet());

        Map<String, JobPosting> existingPostings =
                persistenceService.findExistingPostings(
                        platform, externalIds
                );
        // 5. Post-filter job -> save to db, or update postings
        List<RawJobPosting> results = new ArrayList<>();
        for (RawJobSummary summary : candidates) {
            JobPosting existing = existingPostings.get(summary.externalId());
            // 5.1 Job posting deduplication
            if (existing != null) {
                persistenceService.markSeen(existing);
                continue;
            }
            RawJobPosting raw =
                    source.fetchJobDetail(companyName, summary.externalId());
            results.add(raw);
        }
        return results;
    }
}
