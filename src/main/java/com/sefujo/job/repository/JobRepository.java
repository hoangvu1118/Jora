package com.sefujo.job.repository;

import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.Job;
import jakarta.persistence.Entity;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job,Long>, JpaSpecificationExecutor<Job> {
    @Override
    @EntityGraph(attributePaths = "company")
    List<Job> findAll( // temporarily no need since the BatchSize can fetch the company in the memory
            Specification<Job> spec,
            Sort sort
    );
    // Hibernate will fetch the company as part of the query plan,
    // instead of issuing a new company query for every job.

    Optional<Job> findById(long id);
    // Find the matching job
    Optional<Job> findByCompanyAndNormalizedTitleAndLocation(Company company, String normalizedTitle, String location);



}
