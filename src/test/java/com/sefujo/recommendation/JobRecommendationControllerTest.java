package com.sefujo.recommendation;

import com.sefujo.common.security.JwtAuthenticationFilter;
import com.sefujo.recommendation.dto.RecommendationPage;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileService;
import com.sefujo.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web layer only: checks request parameters, validation and status codes.
// Security filters are switched off; the service is mocked.
@WebMvcTest(JobRecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobRecommendationControllerTest {

    private static final long PROFILE_ID = 1L;
    private static final String URL = "/api/v1/jobRecommendation";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobRecommendationService jobRecommendationService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private SearchProfileService searchProfileService;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SearchProfile profile = new SearchProfile();
        profile.setId(PROFILE_ID);
        when(jobRecommendationService.findSearchProfile()).thenReturn(profile);
        when(jobRecommendationService.getRecommendations(anyLong(), any(), any(), anyInt()))
                .thenReturn(new RecommendationPage(List.of(), false, null, null));
    }

    @Test
    void noParams_returnsFirstPageWithDefaultSize20() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(jobRecommendationService).getRecommendations(PROFILE_ID, null, null, 20);
    }

    @Test
    void withCursor_passesCursorToService() throws Exception {
        mockMvc.perform(get(URL).param("lastScore", "60").param("lastId", "903").param("size", "10"))
                .andExpect(status().isOk());

        verify(jobRecommendationService).getRecommendations(PROFILE_ID, 60, 903L, 10);
    }

    @Test
    void sizeAboveMax_returns400() throws Exception {
        mockMvc.perform(get(URL).param("size", "51"))
                .andExpect(status().isBadRequest());

        verify(jobRecommendationService, never()).getRecommendations(anyLong(), any(), any(), anyInt());
    }

    @Test
    void sizeZero_returns400() throws Exception {
        mockMvc.perform(get(URL).param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sizeAtMax_isAccepted() throws Exception {
        mockMvc.perform(get(URL).param("size", "50"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyLastScore_returns400WithMessage() throws Exception {
        mockMvc.perform(get(URL).param("lastScore", "60"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lastScore and lastId must be sent together"));
    }

    @Test
    void onlyLastId_returns400() throws Exception {
        mockMvc.perform(get(URL).param("lastId", "903"))
                .andExpect(status().isBadRequest());
    }
}
