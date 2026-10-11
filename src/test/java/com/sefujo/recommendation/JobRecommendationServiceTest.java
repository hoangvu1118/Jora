package com.sefujo.recommendation;

import com.sefujo.job.entity.Company;
import com.sefujo.job.entity.Job;
import com.sefujo.recommendation.dto.JobRecommendationResponse;
import com.sefujo.recommendation.dto.RecommendationPage;
import com.sefujo.searchprofile.SearchProfileService;
import com.sefujo.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for the paging logic only. The repository is mocked,
// so these check what the service does with the rows it gets back.
@ExtendWith(MockitoExtension.class)
class JobRecommendationServiceTest {

    private static final long PROFILE_ID = 1L;

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

    @InjectMocks
    private JobRecommendationService jobRecommendationService;

    @Test
    void firstPage_withoutCursor_callsFirstPageQueryAndAsksForOneExtraRow() {
        when(jobRecommendationRepository.findFirstPage(PROFILE_ID, Limit.of(4)))
                .thenReturn(new ArrayList<>());

        jobRecommendationService.getRecommendations(PROFILE_ID, null, null, 3);

        verify(jobRecommendationRepository).findFirstPage(PROFILE_ID, Limit.of(4));
        verify(jobRecommendationRepository, never()).findNextPage(anyLong(), any(), anyInt(), anyLong());
    }

    @Test
    void nextPage_withCursor_passesCursorToNextPageQuery() {
        when(jobRecommendationRepository.findNextPage(PROFILE_ID, Limit.of(4), 60, 903L))
                .thenReturn(new ArrayList<>());

        jobRecommendationService.getRecommendations(PROFILE_ID, 60, 903L, 3);

        verify(jobRecommendationRepository).findNextPage(PROFILE_ID, Limit.of(4), 60, 903L);
        verify(jobRecommendationRepository, never()).findFirstPage(anyLong(), any());
    }

    @Test
    void moreRowsThanSize_hasNextAndDropsExtraRow_cursorIsLastReturnedRow() {
        // size 3, repository returns 4 rows (size + 1)
        List<JobRecommendation> rows = new ArrayList<>();
        rows.add(recommendation(812L, 80));
        rows.add(recommendation(640L, 75));
        rows.add(recommendation(903L, 60));
        rows.add(recommendation(455L, 60));
        when(jobRecommendationRepository.findFirstPage(PROFILE_ID, Limit.of(4))).thenReturn(rows);

        RecommendationPage page = jobRecommendationService.getRecommendations(PROFILE_ID, null, null, 3);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).extracting(JobRecommendationResponse::getId)
                .containsExactly(812L, 640L, 903L);
        // The bookmark must be row 3 (903), not the extra row 4 (455),
        // otherwise row 455 would be skipped on the next page.
        assertThat(page.nextScore()).isEqualTo(60);
        assertThat(page.nextId()).isEqualTo(903L);
    }

    @Test
    void exactlySizeRows_noNextPage_cursorIsNull() {
        List<JobRecommendation> rows = new ArrayList<>();
        rows.add(recommendation(812L, 80));
        rows.add(recommendation(640L, 75));
        rows.add(recommendation(903L, 60));
        when(jobRecommendationRepository.findFirstPage(PROFILE_ID, Limit.of(4))).thenReturn(rows);

        RecommendationPage page = jobRecommendationService.getRecommendations(PROFILE_ID, null, null, 3);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.items()).hasSize(3);
        assertThat(page.nextScore()).isNull();
        assertThat(page.nextId()).isNull();
    }

    @Test
    void noRows_returnsEmptyPageWithoutError() {
        when(jobRecommendationRepository.findFirstPage(PROFILE_ID, Limit.of(21))).thenReturn(new ArrayList<>());

        RecommendationPage page = jobRecommendationService.getRecommendations(PROFILE_ID, null, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextScore()).isNull();
        assertThat(page.nextId()).isNull();
    }

    @Test
    void response_containsJobSummaryAndRecommendationFields() {
        List<JobRecommendation> rows = new ArrayList<>();
        rows.add(recommendation(812L, 80));
        when(jobRecommendationRepository.findFirstPage(PROFILE_ID, Limit.of(21))).thenReturn(rows);

        RecommendationPage page = jobRecommendationService.getRecommendations(PROFILE_ID, null, null, 20);

        JobRecommendationResponse item = page.items().get(0);
        assertThat(item.getId()).isEqualTo(812L);
        assertThat(item.getRecommendationStatus()).isEqualTo(RecommendationStatus.NEW);
        assertThat(item.getMatchReason()).containsExactly(MatchReason.TITLE_MATCH);
        assertThat(item.getJob().getId()).isEqualTo(7812L);
        assertThat(item.getJob().getTitle()).isEqualTo("Backend Intern");
        assertThat(item.getJob().getCompanyName()).isEqualTo("Acme");
    }

    private JobRecommendation recommendation(long id, int score) {
        Company company = new Company();
        company.setName("Acme");

        Job job = new Job();
        job.setId(7000L + id);
        job.setTitle("Backend Intern");
        job.setCompany(company);

        JobRecommendation rec = new JobRecommendation();
        rec.setId(id);
        rec.setMatchScore(score);
        rec.setJob(job);
        rec.setStatus(RecommendationStatus.NEW);
        rec.setMatchReasons(Set.of(MatchReason.TITLE_MATCH));
        return rec;
    }
}
