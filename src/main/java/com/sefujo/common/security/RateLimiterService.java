package com.sefujo.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RateLimiterService {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    private String formulateKey(String service, String userId){
        // e.g ratelimit:llm:12345 or ratelimit:auth:12345
        return "ratelimit:" + service + ":" + userId;
    }

    /**
     * @param userId Unique identifier for the client/IP
     * @param maxRequests Maximum allowed requests per window (e.g., 10)
     * @param windowSeconds Window length in seconds (e.g., 60)
     * @return true if allowed, false if rate limited
     */
    public boolean allowRequest(String service, Long userId, int maxRequests, int windowSeconds){
        String userIdString = String.valueOf(userId);
        String key = formulateKey(service, userIdString);
        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(maxRequests),
                String.valueOf(windowSeconds)
        );
        return result != null && result == 1L;
    }
}
