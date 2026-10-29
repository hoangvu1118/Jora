package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.JobDiscoveredPersistService;
import com.sefujo.ingestion.dto.DiscoveredJobResult;
import com.sefujo.ingestion.dto.JobCreatedEvent;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;

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

        DiscoveredJobResult result = jobDiscoveredPersistService.handleDiscoveredJob(event);
        if(result.shouldPublish()){
            JobCreatedEvent jobCreatedEvent = new JobCreatedEvent(
                    String.valueOf(result.jobId())
            );
            jobCreatedPublisher.publish(jobCreatedEvent);
        }
    }
}
