package com.sefujo.job;

import com.sefujo.common.exception.ResourceNotFound;
import com.sefujo.job.dto.JobDetailResponse;
import com.sefujo.job.dto.JobPage;
import com.sefujo.job.dto.JobSummaryResponse;
import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.Job;
import com.sefujo.job.repository.CompanyRepository;
import com.sefujo.job.repository.JobRepository;
import com.sefujo.job.repository.JobSpecification;
import com.sefujo.job.source.greenhouse.GreenhouseLocation;
import com.sefujo.searchprofile.EmploymentType;
import com.sefujo.searchprofile.WorkplaceType;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
public class JobService {
    private final JobRepository jobRepository;

    public JobPage getJobs(String title, EmploymentType employmentType, WorkplaceType workplaceType, String location, LocalDateTime lastCreated, Long lastId, int size) {
        Specification<Job> spec =
                Specification.where(JobSpecification.isOpen())
                        .and(JobSpecification.hasTitle(title))
                        .and(JobSpecification.hasEmploymentType(employmentType))
                        .and(JobSpecification.hasWorkplaceType(workplaceType))
                        .and(JobSpecification.hasLocation(location))
                        .and(JobSpecification.isAfterCursor(lastCreated, lastId));

        Sort sort = Sort.by(Sort.Direction.DESC, "createdDate").and(Sort.by(Sort.Direction.DESC, "id"));


        List<Job> jobs = jobRepository.findBy(spec, query ->
                query.sortBy(sort)
                        .limit(size + 1)
                        .all());
        boolean hasNext = jobs.size() > size;
        if(hasNext) {
            jobs = jobs.subList(0, size);
        }

        List<JobSummaryResponse> jobResponses = new ArrayList<>();
        for (Job job : jobs) {
            JobSummaryResponse jobResponse = new JobSummaryResponse();
            jobResponse.setId(job.getId());
            jobResponse.setCompanyName(job.getCompany().getName());
            jobResponse.setLevel(job.getLevel());
            jobResponse.setTitle(job.getTitle());
            jobResponse.setEmploymentType(job.getEmploymentType());
            jobResponse.setWorkplaceType(job.getWorkplaceType());
            jobResponse.setLocation(job.getLocation());
            jobResponses.add(jobResponse);
        }

        // The new last page -> allow client to send back for next request
        LocalDateTime pageLastCreated = null;
        Long pageLastId = null;
        if(hasNext) {
            Job job =  jobs.get(jobs.size() - 1);
            pageLastCreated = job.getCreatedDate();
            pageLastId = job.getId();
        }

        return new JobPage(jobResponses, hasNext, pageLastCreated, pageLastId);
    }

    public JobDetailResponse getJobDetails(long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Job not found"));
        JobDetailResponse response = new JobDetailResponse();
        response.setId(job.getId());
        response.setCompanyName(job.getCompany().getName());
        response.setCompanyWebsite(job.getCompany().getWebsite());
        response.setLevel(job.getLevel());
        response.setTitle(job.getTitle());
        response.setEmploymentType(job.getEmploymentType());
        response.setWorkplaceType(job.getWorkplaceType());
        response.setLocation(job.getLocation());
        response.setDescription(job.getDescription());
        response.setStatus(job.getStatus());
        return response;
    }

    public Job getJob(long id) {
        return jobRepository.findById(id)
                .orElse(null);
    }


}
