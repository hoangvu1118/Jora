package com.sefujo.ingestion.dto;

import com.sefujo.job.entity.Platform;

public record JobDiscoveredEvent(
    String platform,
    RawJobPosting job
) {

}
