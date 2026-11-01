package com.sefujo.job.repository;

import com.sefujo.job.entity.CrawlTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import java.util.List;


public interface CrawlTargetRepository extends JpaRepository<CrawlTarget,Long> {
    @Query("SELECT t FROM CrawlTarget t JOIN FETCH t.platform WHERE t.enabled = true")
    List<CrawlTarget> findEnabledWithPlatform();
}
