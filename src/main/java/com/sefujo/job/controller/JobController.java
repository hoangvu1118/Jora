package com.sefujo.job.controller;

import com.sefujo.common.exception.InvalidCursorException;
import com.sefujo.job.JobService;
import com.sefujo.job.dto.JobDetailResponse;
import com.sefujo.job.dto.JobPage;
import com.sefujo.job.dto.JobSummaryResponse;
import com.sefujo.job.entity.Job;
import com.sefujo.searchprofile.EmploymentType;
import com.sefujo.searchprofile.WorkplaceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/job")
@AllArgsConstructor
public class JobController {
    JobService jobService;

    @GetMapping()
    public ResponseEntity<JobPage> getJobs(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) WorkplaceType workplaceType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                LocalDateTime lastCreated,
            @RequestParam(required = false)Long lastId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size

            ) {
        if (lastCreated == null && lastId != null) {
            throw new InvalidCursorException("lastCreated and lastId must be sent together");
        }
        if (lastCreated != null && lastId == null) {
            throw new InvalidCursorException("lastCreated and lastId must be sent together");
        }
        return ResponseEntity.ok(
                jobService.getJobs(title, employmentType, workplaceType, location, lastCreated, lastId, size)
        );
    }

    @GetMapping("{id}")
    public ResponseEntity<JobDetailResponse> getJobDetails(@PathVariable long id) {
        JobDetailResponse jobDetailResponse = jobService.getJobDetails(id);
        return ResponseEntity.ok(jobDetailResponse);
    }
}
