package com.sefujo.ingestion;

import com.sefujo.ingestion.dto.CrawlRequestEvent;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.job.source.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CrawlRequestListener {

    private final JobIngestionService ingestionService;
    private final JobSource jobSource;
    private final JobDiscoveredPublisher jobDiscoveredPublisher;

    public CrawlRequestListener(
            JobIngestionService ingestionService,
            JobDiscoveredPublisher jobDiscoveredPublisher,
            @Qualifier("greenhouseJobSource") JobSource jobSource
    ){
        this.jobDiscoveredPublisher = jobDiscoveredPublisher;
        this.ingestionService = ingestionService;
        this.jobSource = jobSource;
    }

    @KafkaListener(topics="crawl.requested")
    public void onCrawlRequested(CrawlRequestEvent event){
        List<RawJobPosting> jobs = ingestionService.ingest(jobSource, event.boardToken());
        String platformName = jobSource.platformName();
        for(RawJobPosting job : jobs){
            JobDiscoveredEvent jobDiscoveredEvent = new JobDiscoveredEvent(
                    platformName, job
            );
            jobDiscoveredPublisher.publish(jobDiscoveredEvent);
        }
    }
}
