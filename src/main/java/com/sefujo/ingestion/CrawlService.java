package com.sefujo.ingestion;

import com.sefujo.ingestion.dto.CrawlRequestEvent;
import com.sefujo.ingestion.kafka.CrawlRequestPublisher;
import com.sefujo.job.entity.CrawlTarget;
import com.sefujo.job.repository.CrawlTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CrawlService {
    private final CrawlTargetRepository crawlTargetRepository;
    // Record the exact time the bean was instantiated at startup
    private final Instant startupTime = Instant.now();
    private final CrawlRequestPublisher crawlRequestPublisher;

    @Scheduled(cron = "${crawl.schedule.cron}", zone = "${crawl.time.zone}")
    public void scheduleCrawlJob(){
        List<CrawlTarget> crawlTargets = crawlTargetRepository.findEnabledWithPlatform();
        for (CrawlTarget crawlTarget : crawlTargets) {
            try{
                String boardToken = crawlTarget.getCompanyBoardToken();
                String platform = crawlTarget.getPlatform().getName();
                CrawlRequestEvent event = new CrawlRequestEvent(platform, boardToken);
                crawlRequestPublisher.publish(event);
            } catch (RuntimeException e){
                log.error("Crawl job for {} failed", crawlTarget.getCompanyBoardToken(), e);
            }
        }
    }

}
