package com.sefujo.ingestion;

import com.sefujo.ingestion.dto.DiscoveredJobResult;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.job.JobPostingPersistenceService;
import com.sefujo.job.dto.JobPostingResult;
import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.Job;
import com.sefujo.job.entity.JobPosting;
import com.sefujo.job.entity.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for handleDiscoveredJob: decides whether job.created must be published,
// and with which id. The id must always be the JORA JOB id, never the posting id
// and never the Greenhouse external id.
@ExtendWith(MockitoExtension.class)
class JobDiscoveredPersistServiceTest {

    private static final String EXTERNAL_ID = "12345";
    private static final long JOB_ID = 42L;
    private static final long POSTING_ID = 7L;

    @Mock
    private JobIngestionService jobIngestionService;
    @Mock
    private JobPostingPersistenceService persistenceService;

    @InjectMocks
    private JobDiscoveredPersistService service;

    private final Platform platform = new Platform();
    private final Company company = new Company();

    @Test
    void postingAlreadySaved_retry_publishesExistingJobId_andDoesNotSaveAgain() {
        JobDiscoveredEvent event = event();
        when(jobIngestionService.findOrCreatePlatform("greenhouse", "")).thenReturn(platform);
        when(persistenceService.findExistingPosting(platform, EXTERNAL_ID))
                .thenReturn(Optional.of(posting(job(JOB_ID))));

        DiscoveredJobResult result = service.handleDiscoveredJob(event);

        assertThat(result.shouldPublish()).isTrue();
        assertThat(result.jobId()).isEqualTo(JOB_ID);
        verify(persistenceService, never()).save(any(), any(), any());
    }

    @Test
    void newPosting_newJob_publishesNewJobId_notPostingId() {
        JobDiscoveredEvent event = event();
        when(jobIngestionService.findOrCreatePlatform("greenhouse", "")).thenReturn(platform);
        when(persistenceService.findExistingPosting(platform, EXTERNAL_ID)).thenReturn(Optional.empty());
        when(jobIngestionService.findOrCreateCompany("axon", "")).thenReturn(company);
        when(persistenceService.save(event.job(), company, platform))
                .thenReturn(new JobPostingResult(false, posting(job(JOB_ID))));

        DiscoveredJobResult result = service.handleDiscoveredJob(event);

        assertThat(result.shouldPublish()).isTrue();
        assertThat(result.jobId()).isEqualTo(JOB_ID);
        assertThat(result.jobId()).isNotEqualTo(POSTING_ID);
    }

    @Test
    void newPosting_jobAlreadyExisted_doesNotPublish() {
        JobDiscoveredEvent event = event();
        when(jobIngestionService.findOrCreatePlatform("greenhouse", "")).thenReturn(platform);
        when(persistenceService.findExistingPosting(platform, EXTERNAL_ID)).thenReturn(Optional.empty());
        when(jobIngestionService.findOrCreateCompany("axon", "")).thenReturn(company);
        when(persistenceService.save(event.job(), company, platform))
                .thenReturn(new JobPostingResult(true, posting(job(JOB_ID))));

        DiscoveredJobResult result = service.handleDiscoveredJob(event);

        assertThat(result.shouldPublish()).isFalse();
        assertThat(result.jobId()).isNull();
    }

    @Test
    void companyIsLookedUpByBoardToken_notByGreenhouseCompanyName() {
        // RawJobPosting: sourceKey = "axon", companyName = "Axon Enterprise"
        JobDiscoveredEvent event = event();
        when(jobIngestionService.findOrCreatePlatform("greenhouse", "")).thenReturn(platform);
        when(persistenceService.findExistingPosting(platform, EXTERNAL_ID)).thenReturn(Optional.empty());
        when(jobIngestionService.findOrCreateCompany("axon", "")).thenReturn(company);
        when(persistenceService.save(event.job(), company, platform))
                .thenReturn(new JobPostingResult(false, posting(job(JOB_ID))));

        service.handleDiscoveredJob(event);

        verify(jobIngestionService).findOrCreateCompany("axon", "");
        verify(jobIngestionService, never()).findOrCreateCompany("Axon Enterprise", "");
    }

    private JobDiscoveredEvent event() {
        RawJobPosting raw = new RawJobPosting(
                "axon",
                EXTERNAL_ID,
                "Axon Enterprise",
                "Backend Engineer",
                "Ho Chi Minh City",
                "description",
                "https://example.com/jobs/12345",
                LocalDateTime.of(2026, 10, 1, 9, 0),
                LocalDateTime.of(2026, 10, 1, 9, 0)
        );
        return new JobDiscoveredEvent("greenhouse", raw);
    }

    private Job job(long id) {
        Job job = new Job();
        job.setId(id);
        return job;
    }

    private JobPosting posting(Job job) {
        JobPosting posting = new JobPosting();
        posting.setId(POSTING_ID);
        posting.setJob(job);
        posting.setExternalJobId(EXTERNAL_ID);
        return posting;
    }
}
