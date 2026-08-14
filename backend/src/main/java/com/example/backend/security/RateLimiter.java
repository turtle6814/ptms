package com.example.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 60_000;

    private record Window(AtomicInteger count, long windowStart) {}

    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    public void allowOrThrow(String key) {
        long now = System.currentTimeMillis();
        Window window = attempts.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart() > WINDOW_MILLIS) {
                return new Window(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (window.count().get() > MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
        }
    }
}
