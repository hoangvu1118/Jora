package com.sefujo.job.controller;

import com.sefujo.ingestion.kafka.CrawlRequestPublisher;
import com.sefujo.ingestion.dto.CrawlRequestEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ingestion")
public class JobIngestionController {
//    private final JobIngestionService ingestionService;
//    JobSource greenhouseSource;
    private final CrawlRequestPublisher crawlRequestPublisher;

    public JobIngestionController(
//            JobIngestionService ingestionService,
//            @Qualifier("greenhouseJobSource") JobSource greenhouseSource,
            CrawlRequestPublisher crawlRequestPublisher) {

//        this.ingestionService = ingestionService;
//        this.greenhouseSource = greenhouseSource;
        this.crawlRequestPublisher = crawlRequestPublisher;
    }

    @PostMapping("/greenhouse/{boardToken}")
//    public ResponseEntity<List<RawJobPosting>> ingest(
//            @PathVariable String boardToken
//    ) {
//        List<RawJobPosting> jobs =
//                ingestionService.ingest(greenhouseSource, boardToken);
//        return ResponseEntity.ok(jobs);
//    }
    public ResponseEntity<Void> ingest(
            @PathVariable String boardToken
    ) {
        CrawlRequestEvent event = new CrawlRequestEvent("greenhouse", boardToken);
        crawlRequestPublisher.publish(event);
        return ResponseEntity.accepted().build();
    }
}