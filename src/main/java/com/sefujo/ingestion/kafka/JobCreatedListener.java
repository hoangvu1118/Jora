package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.dto.JobCreatedEvent;
import com.sefujo.recommendation.JobRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobCreatedListener {
    private final JobRecommendationService jobRecommendationService;

    @KafkaListener(topics = "job.created")
    public void onJobCreated(JobCreatedEvent jobCreatedEvent) {

    }
}
