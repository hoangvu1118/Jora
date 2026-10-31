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

    private String formulateKey(String service, String uniqueId){
        // e.g ratelimit:llm:12345 or ratelimit:auth:12345
        return "ratelimit:" + service + ":" + uniqueId;
    }

    /**
     * @param uniqueId Unique identifier for the client/IP can be userId or email
     * @param maxRequests Maximum allowed requests per window (e.g., 10)
     * @param windowSeconds Window length in seconds (e.g., 60)
     * @return true if allowed, false if rate limited
     */
    public boolean allowRequest(String service, String uniqueId, int maxRequests, int windowSeconds){
        String key = formulateKey(service, uniqueId);
        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(maxRequests),
                String.valueOf(windowSeconds)
        );
        return result != null && result == 1L;
    }

    public void release(String service, String uniqueId){
        redisTemplate.delete(formulateKey(service, uniqueId));
    }
}
