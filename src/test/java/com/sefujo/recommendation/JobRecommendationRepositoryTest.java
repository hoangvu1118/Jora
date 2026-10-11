package com.sefujo.recommendation;

import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.Job;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Runs the real cursor queries against Postgres (database sefujo_test,
// see src/test/resources/application-test.properties). Flyway builds the
// schema, and each test is rolled back when it finishes.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class JobRecommendationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JobRecommendationRepository jobRecommendationRepository;

    private SearchProfile profile;
    private SearchProfile otherProfile;
    private Company company;
    private int jobCounter = 0;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setName("Acme");
        company.setCreatedAt(LocalDateTime.now());
        company.setUpdatedAt(LocalDateTime.now());
        entityManager.persist(company);

        profile = persistProfile("owner@test.com");
        otherProfile = persistProfile("other@test.com");
    }

    @Test
    void firstPage_isSortedByScoreThenId_andRespectsLimit() {
        JobRecommendation a = persistRecommendation(profile, 80, RecommendationStatus.NEW);
        JobRecommendation b = persistRecommendation(profile, 60, RecommendationStatus.NEW);
        JobRecommendation c = persistRecommendation(profile, 60, RecommendationStatus.SAVED);
        persistRecommendation(profile, 45, RecommendationStatus.NEW);
        flushAndClear();

        List<JobRecommendation> page = jobRecommendationRepository.findFirstPage(profile.getId(), Limit.of(3));

        // Two rows tie at 60, so the higher id comes first.
        assertThat(ids(page)).containsExactly(a.getId(), c.getId(), b.getId());
    }

    @Test
    void firstPage_excludesDismissedAndOtherProfiles() {
        JobRecommendation kept = persistRecommendation(profile, 70, RecommendationStatus.NEW);
        persistRecommendation(profile, 90, RecommendationStatus.DISMISSED);
        persistRecommendation(otherProfile, 95, RecommendationStatus.NEW);
        flushAndClear();

        List<JobRecommendation> page = jobRecommendationRepository.findFirstPage(profile.getId(), Limit.of(10));

        assertThat(ids(page)).containsExactly(kept.getId());
    }

    @Test
    void nextPage_continuesInsideATieWithoutSkippingOrRepeating() {
        JobRecommendation top = persistRecommendation(profile, 80, RecommendationStatus.NEW);
        JobRecommendation tie1 = persistRecommendation(profile, 60, RecommendationStatus.NEW);
        JobRecommendation tie2 = persistRecommendation(profile, 60, RecommendationStatus.NEW);
        JobRecommendation tie3 = persistRecommendation(profile, 60, RecommendationStatus.NEW);
        JobRecommendation low = persistRecommendation(profile, 45, RecommendationStatus.NEW);
        flushAndClear();

        // Cursor = (60, tie3): page 1 ended in the middle of the score-60 rows.
        List<JobRecommendation> page = jobRecommendationRepository
                .findNextPage(profile.getId(), Limit.of(10), 60, tie3.getId());

        assertThat(ids(page)).containsExactly(tie2.getId(), tie1.getId(), low.getId());
        assertThat(ids(page)).doesNotContain(top.getId(), tie3.getId());
    }

    @Test
    void nextPage_stillWorksWhenCursorRowWasDismissed() {
        JobRecommendation cursorRow = persistRecommendation(profile, 60, RecommendationStatus.DISMISSED);
        JobRecommendation after = persistRecommendation(profile, 50, RecommendationStatus.NEW);
        flushAndClear();

        List<JobRecommendation> page = jobRecommendationRepository
                .findNextPage(profile.getId(), Limit.of(10), 60, cursorRow.getId());

        assertThat(ids(page)).containsExactly(after.getId());
    }

    @Test
    void walkingAllPages_returnsEveryVisibleRowExactlyOnce() {
        List<Long> expected = new ArrayList<>();
        int[] scores = {90, 70, 70, 70, 70, 50, 50, 30};
        for (int score : scores) {
            expected.add(persistRecommendation(profile, score, RecommendationStatus.NEW).getId());
        }
        persistRecommendation(profile, 70, RecommendationStatus.DISMISSED);
        flushAndClear();

        List<Long> seen = new ArrayList<>();
        List<JobRecommendation> page = jobRecommendationRepository.findFirstPage(profile.getId(), Limit.of(3));
        while (!page.isEmpty()) {
            seen.addAll(ids(page));
            JobRecommendation last = page.get(page.size() - 1);
            page = jobRecommendationRepository
                    .findNextPage(profile.getId(), Limit.of(3), last.getMatchScore(), last.getId());
        }

        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(seen).doesNotHaveDuplicates();
    }

    @Test
    void firstPage_loadsJobAndCompanyInTheSameQuery() {
        persistRecommendation(profile, 80, RecommendationStatus.NEW);
        flushAndClear();

        List<JobRecommendation> page = jobRecommendationRepository.findFirstPage(profile.getId(), Limit.of(10));
        entityManager.clear();

        // After clear() lazy loading is impossible, so these only work
        // if JOIN FETCH already loaded the job and its company.
        assertThat(page.get(0).getJob().getTitle()).isEqualTo("Job 1");
        assertThat(page.get(0).getJob().getCompany().getName()).isEqualTo("Acme");
    }

    private SearchProfile persistProfile(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hash");
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        entityManager.persist(user);

        SearchProfile searchProfile = new SearchProfile();
        searchProfile.setUser(user);
        searchProfile.setName("profile of " + email);
        searchProfile.setCreatedAt(LocalDateTime.now());
        searchProfile.setUpdatedAt(LocalDateTime.now());
        entityManager.persist(searchProfile);
        return searchProfile;
    }

    private JobRecommendation persistRecommendation(SearchProfile owner, int score, RecommendationStatus status) {
        jobCounter++;
        Job job = new Job();
        job.setCompany(company);
        job.setTitle("Job " + jobCounter);
        job.setStatus("OPEN");
        job.setCreatedDate(LocalDateTime.now());
        job.setUpdatedDate(LocalDateTime.now());
        entityManager.persist(job);

        JobRecommendation rec = new JobRecommendation();
        rec.setSearchProfile(owner);
        rec.setJob(job);
        rec.setMatchScore(score);
        rec.setStatus(status);
        rec.setMatchReasons(Set.of(MatchReason.TITLE_MATCH));
        rec.setCreatedAt(LocalDateTime.now());
        rec.setUpdatedAt(LocalDateTime.now());
        entityManager.persist(rec);
        return rec;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<Long> ids(List<JobRecommendation> recommendations) {
        List<Long> ids = new ArrayList<>();
        for (JobRecommendation rec : recommendations) {
            ids.add(rec.getId());
        }
        return ids;
    }
}
