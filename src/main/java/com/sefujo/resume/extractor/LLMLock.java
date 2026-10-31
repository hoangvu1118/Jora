package com.sefujo.resume.extractor;

import com.sefujo.common.security.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LLMLock {
    private final RateLimiterService rateLimiterService;
    private String serviceNameRatelimit = "llm4Resume";
    private int maxRequests = 10;
    private int windowSeconds = 60;

    public boolean allowLLMRequest(Long userId){
        return rateLimiterService.allowRequest(serviceNameRatelimit, userId, maxRequests, windowSeconds);
    }

}
