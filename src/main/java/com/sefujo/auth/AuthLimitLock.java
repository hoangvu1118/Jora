package com.sefujo.auth;

import com.sefujo.common.security.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthLimitLock {
    private final RateLimiterService rateLimiterService;
    private static final String serviceNameRatelimit = "auth";
    private static final int maxRequests = 5;
    private static final int windowSeconds = 900;

    public boolean allowAuthRequest(String email){
        return rateLimiterService.allowRequest(serviceNameRatelimit, email, maxRequests, windowSeconds);
    }
}
