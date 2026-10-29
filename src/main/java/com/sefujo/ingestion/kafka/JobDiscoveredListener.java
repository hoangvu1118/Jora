package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.JobDiscoveredPersistService;
import com.sefujo.ingestion.JobIngestionService;
import com.sefujo.ingestion.dto.DiscoveredJobResult;
import com.sefujo.ingestion.dto.JobCreatedEvent;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import com.sefujo.job.JobPostingPersistenceService;
import com.sefujo.job.dto.JobPostingResult;
import com.sefujo.job.entity.JobPosting;
import com.sefujo.job.entity.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JobDiscoveredListener {
    private final JobDiscoveredPersistService jobDiscoveredPersistService;
    private final JobCreatedPublisher jobCreatedPublisher;

    @KafkaListener(topics = "job.discovered")
    public void onJobRequest(JobDiscoveredEvent event){

        DiscoveredJobResult result = jobDiscoveredPersistService.handleDiscoveredJob(event);
        if(!result.publishedJobCreated()){
            JobCreatedEvent jobCreatedEvent = new JobCreatedEvent(
                    String.valueOf(result.jobId())
            );
            jobCreatedPublisher.publish(jobCreatedEvent);
        }
    }
}
