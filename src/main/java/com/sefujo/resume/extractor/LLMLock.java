package com.sefujo.resume.extractor;

import com.sefujo.common.security.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LLMLock {
    private final RateLimiterService rateLimiterService;
    private static final String serviceNameRatelimit = "llm4Resume";
    private static final int maxRequests = 10;
    private static final int windowSeconds = 60;

    public boolean allowLLMRequest(Long userId){
        String userIdString = String.valueOf(userId);
        return rateLimiterService.allowRequest(serviceNameRatelimit, userIdString, maxRequests, windowSeconds);
    }

}
