package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.dto.JobCreatedEvent;
import com.sefujo.recommendation.JobRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobCreatedListener {
    private final JobRecommendationService jobRecommendationService;

    @KafkaListener(topics = "job.created")
    public void onJobCreated(JobCreatedEvent jobCreatedEvent) {
        Long jobId = Long.parseLong(jobCreatedEvent.jobId());
        int createdRecommendation = jobRecommendationService.recommendForNewJob(jobId);
        log.info("job {}: {} recommendations created/ updated", jobId, createdRecommendation);
    }
}
