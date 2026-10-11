package com.sefujo.job.dto;

import java.time.LocalDateTime;
import java.util.List;

public record JobPage(
        List<JobSummaryResponse> jobs,
        boolean hasNext,
        // The Client will send this back in the next request (the cursor)
        LocalDateTime lastCreated,
        Long lastId
) {
}
