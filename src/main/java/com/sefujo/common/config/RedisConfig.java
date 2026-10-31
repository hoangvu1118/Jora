package com.sefujo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {
    private static final String RATE_LIMIT_LUA =
        "local exists = redis.call('EXISTS', KEYS[1]) " +
        "if exists == 0 then " +
        "    redis.call('SET', KEYS[1], tonumber(ARGV[1]) - 1, 'EX', tonumber(ARGV[2])) " +
        "    return 1 " +
        "else " +
        "    local current = tonumber(redis.call('GET', KEYS[1])) " +
        "    if current > 0 then " +
        "        redis.call('DECR', KEYS[1]) " +
        "        return 1 " +
        "    else " +
        "        return 0 " +
        "    end " +
        "end";

    @Bean
    public RedisScript<Long> rateLimitScript(){
        return new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class);
    }
}
