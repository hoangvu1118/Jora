package com.sefujo.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        // If we exhausted the RETRY attempts --> Publish the record to the different Topic
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition( // Constructing the Topic name for DLT
                                        record.topic() + ".DLT", // --> convert the partition (e.g order_partition ==> order_partition.DLT
                                        record.partition()      //  --> Keep the partition
                                )
                );

        FixedBackOff backOff = // Wait 1 second, retry 3 times
                new FixedBackOff(
                        1000L,
                        3L // total of 4 retries
                );

        return new DefaultErrorHandler(
                recoverer,
                backOff
        );
    }

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

    @Bean
    public NewTopic jobCreatedTopic(){
        return TopicBuilder.name("job.created")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic crawlRequestedTopicDLT(){
        return TopicBuilder.name("crawl.requested.DLT")
                .partitions(3)
                .replicas(1) // replica(n) : n = 1 means only the leader has the copy
                .build();
    }

    @Bean
    public NewTopic jobDiscoveredTopicDLT(){
        return TopicBuilder.name("job.discovered.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobCreatedTopicDLT(){
        return TopicBuilder.name("job.created.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
