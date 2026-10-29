package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.dto.CrawlRequestEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrawlRequestPublisher {
    private final KafkaTemplate<String, CrawlRequestEvent> kafkaTemplate;

    public void publish(CrawlRequestEvent event) {
        kafkaTemplate.send("crawl.requested", event.boardToken(), event).join();
//                                  topic             key               value | A record in Kafka exists as Key:Value
    }
}
