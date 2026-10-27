package com.sefujo.ingestion.dto;

import java.time.LocalDateTime;

public record RawJobPosting(
        String sourceKey, // e.g axon
        String externalId,
        String companyName,
        String title,
        String location,
        String description,
        String sourceUrl,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime sourcePublishedAt
) {}
