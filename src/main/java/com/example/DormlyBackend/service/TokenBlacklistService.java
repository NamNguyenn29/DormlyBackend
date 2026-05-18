package com.example.DormlyBackend.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank())
            return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(token)));
    }

    public void blacklist(String token, Duration ttl) {
        if (token == null || token.isBlank())
            return;
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofHours(1);
        }
        redisTemplate.opsForValue().set(blacklistKey(token), "1", ttl);
    }

    private String blacklistKey(String token) {
        return KEY_PREFIX + token;
    }
}
