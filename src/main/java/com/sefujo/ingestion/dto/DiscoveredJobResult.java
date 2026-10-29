package com.sefujo.ingestion.dto;

public record DiscoveredJobResult(
        boolean publishedJobCreated,
        Long jobId
) {
}
