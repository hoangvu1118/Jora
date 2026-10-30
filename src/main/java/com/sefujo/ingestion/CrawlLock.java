package com.sefujo.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CrawlLock {

    private static final Duration COOLDOWN = Duration.ofMinutes(30);
    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(String platform, String token) {
        String lockKey = "crawl:" + platform + ":" + token;
        // setIfAbsent -> SET key value NX EX : only if it does not have before
        // Redis store record as "KEY:VALUE" pair
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, Instant.now().toString(), COOLDOWN);
        return Boolean.TRUE.equals(acquired);
    }

    public void release(String platform, String boardToken) {
        redisTemplate.delete("crawl:" + platform + ":" + boardToken);
    }
}
