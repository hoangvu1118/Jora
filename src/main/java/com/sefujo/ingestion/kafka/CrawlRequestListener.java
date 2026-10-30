package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.CrawlLock;
import com.sefujo.ingestion.JobIngestionService;
import com.sefujo.ingestion.dto.CrawlRequestEvent;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.job.source.JobSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CrawlRequestListener {

    private final JobIngestionService ingestionService;
    private final JobSource jobSource;
    private final JobDiscoveredPublisher jobDiscoveredPublisher;
    private final CrawlLock crawlLock;

    public CrawlRequestListener(
            JobIngestionService ingestionService,
            JobDiscoveredPublisher jobDiscoveredPublisher,
            @Qualifier("greenhouseJobSource") JobSource jobSource,
            CrawlLock crawlLock){
        this.jobDiscoveredPublisher = jobDiscoveredPublisher;
        this.ingestionService = ingestionService;
        this.jobSource = jobSource;
        this.crawlLock = crawlLock;
    }

    @KafkaListener(topics="crawl.requested")
    public void onCrawlRequested(CrawlRequestEvent event){
        // Redis lock to prevent duplicate crawl
        if(!crawlLock.tryAcquire(event.platform(), event.boardToken())){
            log.info("Skipped crawl for {}: crawled recently", event.boardToken());
            return;
        }
        try {
            List<RawJobPosting> jobs = ingestionService.ingest(jobSource, event.boardToken());
            String platformName = jobSource.platformName();
            for (RawJobPosting job : jobs) {
                JobDiscoveredEvent jobDiscoveredEvent = new JobDiscoveredEvent(
                        platformName, job
                );
                jobDiscoveredPublisher.publish(jobDiscoveredEvent);
            }
        } catch (RuntimeException e) {
            crawlLock.release(event.platform(), event.boardToken());
            throw e;
        }
    }
}
