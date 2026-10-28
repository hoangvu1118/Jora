package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.dto.JobCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobCreatedPublisher {
    private final KafkaTemplate<String, JobCreatedEvent> kafkaTemplate;

    public void publish(JobCreatedEvent jobCreatedEvent) {
        kafkaTemplate.send("job.created", String.valueOf(jobCreatedEvent.jobId()), jobCreatedEvent);
    }
}
