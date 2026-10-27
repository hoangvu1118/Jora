package com.sefujo.job.source.greenhouse;

import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.ingestion.dto.RawJobSummary;
import com.sefujo.job.source.JobSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class GreenhouseJobSource implements JobSource {
    private final WebClient webClient;
    GreenhouseJobMapper mapper;

    public GreenhouseJobSource(
            @Qualifier("greenhouseWebClient") WebClient webClient,
            GreenhouseJobMapper mapper
    ) {
        this.webClient = webClient;
        this.mapper = mapper;
    }

    @Override
    public String platformName() {
        return "Greenhouse";
    }

    @Override
    public List<RawJobSummary> fetchJobSummaries(String boardToken) {
        GreenhouseJobsResponse response = webClient
                .get()
                .uri("/v1/boards/{boardToken}/jobs", boardToken)
                .retrieve()
                .bodyToMono(GreenhouseJobsResponse.class)
                .block(); // make the method synchronous

        if (response == null || response.jobs() == null) {
            throw new IllegalStateException(
                    "Greenhouse returned an invalid job list"
            );
        }

        return response.jobs().stream()
                .map(job -> new RawJobSummary(
                        job.id().toString(),
                        job.title(),
                        job.location() == null
                                ? null
                                : job.location().name(),
                        job.absoluteUrl(),
                        job.updatedAt()
                ))
                .toList();
    }

    @Override
    public RawJobPosting fetchJobDetail(
            String boardToken,
            String externalId
    ) {
        GreenhouseJobDetail detail = webClient
                .get()
                .uri(
                        "/v1/boards/{boardToken}/jobs/{jobId}",
                        boardToken,
                        externalId
                )
                .retrieve()
                .bodyToMono(GreenhouseJobDetail.class)
                .block();

        if (detail == null) {
            throw new IllegalStateException(
                    "Greenhouse returned an empty detail response"
            );
        }

        return mapper.toRawJobPosting(boardToken, detail);
    }
}
