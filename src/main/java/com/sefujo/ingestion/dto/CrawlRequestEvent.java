package com.sefujo.ingestion.dto;

public record CrawlRequestEvent(
        String platform,
        String boardToken
) { }
