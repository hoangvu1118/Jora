package com.sefujo.recommendation;

import com.sefujo.job.JobService;
import com.sefujo.job.entity.Job;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileService;
import com.sefujo.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for recommendForNewJob: one new job scored against every search profile.
// The threshold is 50 in these tests.
@ExtendWith(MockitoExtension.class)
class RecommendForNewJobTest {

    private static final long JOB_ID = 42L;
    private static final int THRESHOLD = 50;

    @Mock
    private JobRecommendationRepository jobRecommendationRepository;
    @Mock
    private JobCandidateService jobCandidateService;
    @Mock
    private JobMatchingStrategy jobMatchingStrategy;
    @Mock
    private SearchProfileService searchProfileService;
    @Mock
    private UserService userService;
    @Mock
    private JobService jobService;

    @InjectMocks
    private JobRecommendationService jobRecommendationService;

    @BeforeEach
    void setThreshold() {
        ReflectionTestUtils.setField(jobRecommendationService, "jobScoreThreshold", THRESHOLD);
    }

    @Test
    void jobNotFound_returnsZero_andSavesNothing() {
        when(jobService.getJob(JOB_ID)).thenReturn(null);

        int saved = jobRecommendationService.recommendForNewJob(JOB_ID);

        assertThat(saved).isEqualTo(0);
        verify(searchProfileService, never()).getAllSearchProfiles();
        verify(jobRecommendationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void closedJob_returnsZero_andSavesNothing() {
        when(jobService.getJob(JOB_ID)).thenReturn(job("CLOSED"));

        int saved = jobRecommendationService.recommendForNewJob(JOB_ID);

        assertThat(saved).isEqualTo(0);
        verify(jobRecommendationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void noSearchProfiles_returnsZero_andSavesNothing() {
        when(jobService.getJob(JOB_ID)).thenReturn(job("OPEN"));
        when(searchProfileService.getAllSearchProfiles()).thenReturn(new ArrayList<>());

        int saved = jobRecommendationService.recommendForNewJob(JOB_ID);

        assertThat(saved).isEqualTo(0);
        verify(jobRecommendationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void profilesAtOrAboveThreshold_getNewRecommendation_belowThreshold_skipped() {
        Job job = job("OPEN");
        SearchProfile low = profile(1L);
        SearchProfile exact = profile(2L);
        SearchProfile high = profile(3L);
        when(jobService.getJob(JOB_ID)).thenReturn(job);
        when(searchProfileService.getAllSearchProfiles()).thenReturn(List.of(low, exact, high));
        when(jobRecommendationRepository.findByJobId(JOB_ID)).thenReturn(new ArrayList<>());
        when(jobMatchingStrategy.score(low, job)).thenReturn(match(THRESHOLD - 1));
        when(jobMatchingStrategy.score(exact, job)).thenReturn(match(THRESHOLD));
        when(jobMatchingStrategy.score(high, job)).thenReturn(match(80));

        int saved = jobRecommendationService.recommendForNewJob(JOB_ID);

        assertThat(saved).isEqualTo(2);
        List<JobRecommendation> toSave = captureSaved();
        assertThat(toSave).hasSize(2);

        JobRecommendation first = toSave.get(0);
        assertThat(first.getSearchProfile()).isSameAs(exact);
        assertThat(first.getJob()).isSameAs(job);
        assertThat(first.getStatus()).isEqualTo(RecommendationStatus.NEW);
        assertThat(first.getMatchScore()).isEqualTo(THRESHOLD);

        JobRecommendation second = toSave.get(1);
        assertThat(second.getSearchProfile()).isSameAs(high);
        assertThat(second.getMatchScore()).isEqualTo(80);
        assertThat(second.getMatchReasons()).containsExactly(MatchReason.TITLE_MATCH);
    }

    @Test
    void existingRecommendation_isMatchedByProfileId_andUpdatedNotDuplicated() {
        // Profile 2 already has a recommendation for this job (a redelivered job.created).
        // Profile 1 does not. The map must be keyed by the PROFILE id.
        Job job = job("OPEN");
        SearchProfile profileWithout = profile(1L);
        SearchProfile profileWith = profile(2L);
        JobRecommendation existing = new JobRecommendation();
        existing.setSearchProfile(profileWith);
        existing.setJob(job);
        existing.setStatus(RecommendationStatus.DISMISSED);
        existing.setMatchScore(60);

        when(jobService.getJob(JOB_ID)).thenReturn(job);
        when(searchProfileService.getAllSearchProfiles()).thenReturn(List.of(profileWithout, profileWith));
        when(jobRecommendationRepository.findByJobId(JOB_ID)).thenReturn(List.of(existing));
        when(jobMatchingStrategy.score(profileWithout, job)).thenReturn(match(70));
        when(jobMatchingStrategy.score(profileWith, job)).thenReturn(match(90));

        jobRecommendationService.recommendForNewJob(JOB_ID);

        List<JobRecommendation> toSave = captureSaved();
        assertThat(toSave).hasSize(2);

        JobRecommendation forProfile1 = toSave.get(0);
        assertThat(forProfile1).isNotSameAs(existing);
        assertThat(forProfile1.getSearchProfile()).isSameAs(profileWithout);
        assertThat(forProfile1.getStatus()).isEqualTo(RecommendationStatus.NEW);

        JobRecommendation forProfile2 = toSave.get(1);
        assertThat(forProfile2).isSameAs(existing);
        assertThat(forProfile2.getMatchScore()).isEqualTo(90);
        // the user's own status (e.g. they dismissed it) must not be reset to NEW
        assertThat(forProfile2.getStatus()).isEqualTo(RecommendationStatus.DISMISSED);
    }

    @Test
    void sameMessageTwice_secondRunUpdatesTheSameRow() {
        Job job = job("OPEN");
        SearchProfile profile = profile(1L);
        when(jobService.getJob(JOB_ID)).thenReturn(job);
        when(searchProfileService.getAllSearchProfiles()).thenReturn(List.of(profile));
        when(jobMatchingStrategy.score(profile, job)).thenReturn(match(70));

        // run 1: nothing exists yet
        when(jobRecommendationRepository.findByJobId(JOB_ID)).thenReturn(new ArrayList<>());
        jobRecommendationService.recommendForNewJob(JOB_ID);
        JobRecommendation createdInRun1 = captureSaved().get(0);

        // run 2: the row from run 1 now exists
        when(jobRecommendationRepository.findByJobId(JOB_ID)).thenReturn(List.of(createdInRun1));
        jobRecommendationService.recommendForNewJob(JOB_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JobRecommendation>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRecommendationRepository, org.mockito.Mockito.times(2)).saveAllAndFlush(captor.capture());
        JobRecommendation savedInRun2 = captor.getAllValues().get(1).get(0);
        assertThat(savedInRun2).isSameAs(createdInRun1);
    }

    private List<JobRecommendation> captureSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JobRecommendation>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRecommendationRepository).saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    private Job job(String status) {
        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(status);
        return job;
    }

    private SearchProfile profile(long id) {
        SearchProfile profile = new SearchProfile();
        profile.setId(id);
        return profile;
    }

    private MatchResult match(int score) {
        return new MatchResult(score, Set.of(MatchReason.TITLE_MATCH));
    }
}
