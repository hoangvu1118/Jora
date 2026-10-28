package com.sefujo.job.dto;

import com.sefujo.job.entity.JobPosting;
import org.springframework.stereotype.Component;


public record JobPostingResult(
        boolean jobExisted,
        JobPosting jobPosting
) { }
