package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.JobDiscoveredPersistService;
import com.sefujo.ingestion.dto.JobCreatedEvent;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import com.sefujo.job.dto.JobPostingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobDiscoveredListener {
    private final JobDiscoveredPersistService jobDiscoveredPersistService;
    private final JobCreatedPublisher jobCreatedPublisher;

    @KafkaListener(topics = "job.discovered")
    public void onJobRequest(JobDiscoveredEvent event){
        JobPostingResult result = jobDiscoveredPersistService.persistDiscoveredJob(
                event.job(),
                event.platform(),
                event.job().sourceKey()
        );
        if(!result.jobExisted()){
            JobCreatedEvent jobCreatedEvent = new JobCreatedEvent(
                    result.jobPosting().getJob().getId()
            );
            jobCreatedPublisher.publish(jobCreatedEvent);
        }
    }
}
