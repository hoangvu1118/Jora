package com.sefujo.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic crawlRequestedTopic(){
        return TopicBuilder.name("crawl.requested")
                .partitions(3)
                .replicas(1) // replica(n) : n = 1 means only the leader has the copy
                .build();
    }

    @Bean
    public NewTopic jobDiscoveredTopic(){
        return TopicBuilder.name("job.discovered")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
