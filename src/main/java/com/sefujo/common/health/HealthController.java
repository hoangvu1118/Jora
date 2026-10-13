package com.sefujo.common.health;

import com.sefujo.common.security.CustomUserDetail;
import com.sefujo.searchprofile.EmploymentLevel;
import com.sefujo.searchprofile.SearchProfile;
import com.sefujo.searchprofile.SearchProfileRepository;
import com.sefujo.user.User;
import com.sefujo.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final SearchProfileRepository searchProfileRepository;
    private final UserRepository userRepository;


    public HealthController(SearchProfileRepository searchProfileRepository, UserRepository userRepository) {
        this.searchProfileRepository = searchProfileRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/test/searchProfile")
    public Map<String, String> test() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            CustomUserDetail customUserDetail = (CustomUserDetail) authentication.getPrincipal();
            assert customUserDetail != null;
            Long userId = customUserDetail.getId();
            User user = userRepository.findById(userId).orElse(null);
            SearchProfile searchProfile = new SearchProfile();
            searchProfile.setName("test");
            searchProfile.setLevel(EmploymentLevel.INTERN);
            searchProfile.setUser(user);
            searchProfileRepository.save(searchProfile);
        }
        return Map.of("status", "Test search profile");
    }
}
