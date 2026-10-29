package com.sefujo.ingestion.dto;

public record DiscoveredJobResult(
        boolean shouldPublish,
        Long jobId
) {
}
