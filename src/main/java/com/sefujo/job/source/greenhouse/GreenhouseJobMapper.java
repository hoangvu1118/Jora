package com.sefujo.job.source.greenhouse;

import com.sefujo.ingestion.dto.RawJobPosting;
import org.springframework.stereotype.Component;

@Component
public class GreenhouseJobMapper {

    public RawJobPosting toRawJobPosting(
            String boardToken,
            GreenhouseJobDetail detail
    ) {
        return new RawJobPosting(
                boardToken,
                detail.id().toString(),
                detail.companyName(),
                detail.title(),
                detail.location() == null
                        ? null
                        : detail.location().name(),
                detail.content(),
                detail.absoluteUrl(),
                detail.updatedAt().toLocalDateTime(),
                detail.firstPublished().toLocalDateTime()
        );
    }
}