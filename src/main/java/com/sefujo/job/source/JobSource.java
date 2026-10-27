package com.sefujo.job.source;

import com.sefujo.ingestion.dto.RawJobPosting;
import com.sefujo.ingestion.dto.RawJobSummary;

import java.util.List;

public interface JobSource {
    // sourceKey = board_token (e.g axon)

    String platformName();
    List<RawJobSummary> fetchJobSummaries(String sourceKey);

    RawJobPosting fetchJobDetail(
            String sourceKey,
            String externalId
    );
}
