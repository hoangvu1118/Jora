package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobDiscoveredPublisher {
    private final KafkaTemplate<String, JobDiscoveredEvent> kafkaTemplate;

    public void publish(JobDiscoveredEvent event) {
        kafkaTemplate.send("job.discovered", event.platform() + ":" + event.job().externalId(), event).join();
//                                  topic                              key                            value
    }
}
