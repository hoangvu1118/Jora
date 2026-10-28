package com.sefujo.ingestion;

import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobDiscoveredListener {
    private final JobDiscoveredPersistService jobDiscoveredPersistService;

    @KafkaListener(topics = "job.discovered")
    public void onJobRequest(JobDiscoveredEvent event){
        jobDiscoveredPersistService.persistDiscoveredJob(
                event.job(),
                event.platform(),
                event.job().companyName()
        );
    }
}
