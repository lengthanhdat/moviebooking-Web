package com.movie.movie_booking_api.util;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public boolean allow(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        Deque<Long> q = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > windowMillis) {
            q.pollFirst();
        }
        if (q.size() >= maxRequests) return false;
        q.addLast(now);
        return true;
    }
}