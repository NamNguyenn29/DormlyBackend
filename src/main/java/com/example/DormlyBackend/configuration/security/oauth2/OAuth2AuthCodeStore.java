package com.example.DormlyBackend.configuration.security.oauth2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lưu short-lived auth code (mặc định 5 phút) để frontend đổi lấy JWT.
 * Single-instance: dùng ConcurrentHashMap.
 * Multi-instance / production: thay bằng Redis.
 */
@Component
public class OAuth2AuthCodeStore {

    private record Entry(String email, long expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom       rng   = new SecureRandom();

    @Value("${app.oauth2.auth-code-expiry-ms:300000}")
    private long expiryMs;

    public OAuth2AuthCodeStore() {
        // Dọn expired entry mỗi 10 phút
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::evictExpired, 10, 10, TimeUnit.MINUTES);
    }

    /** Tạo code 32-byte ngẫu nhiên, map với email, trả về code */
    public String generate(String email) {
        byte[] bytes = new byte[32];
        rng.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.put(code, new Entry(email, System.currentTimeMillis() + expiryMs));
        return code;
    }

    /** One-time consume: trả về email nếu hợp lệ, null nếu invalid/expired */
    public String consumeEmail(String code) {
        Entry entry = store.remove(code);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) return null;
        return entry.email();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}