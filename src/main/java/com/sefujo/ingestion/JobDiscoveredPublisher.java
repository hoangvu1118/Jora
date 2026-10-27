package com.sefujo.ingestion;

import com.sefujo.ingestion.dto.CrawlRequestEvent;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import com.sefujo.ingestion.dto.RawJobPosting;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobDiscoveredPublisher {
    private final KafkaTemplate<String, JobDiscoveredEvent> kafkaTemplate;

    public void publish(JobDiscoveredEvent event) {
        kafkaTemplate.send("job.discovered", event.platform() + ":" + event.job().externalId(), event);
//                                  topic                              key                            value
    }
}
