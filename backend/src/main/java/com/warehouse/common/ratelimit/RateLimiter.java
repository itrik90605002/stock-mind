package com.warehouse.common.ratelimit;

import com.warehouse.common.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter keyed by an arbitrary string (user id, client IP ...).
 *
 * <p>Good enough for a single instance; a multi-instance deployment would move this to Redis.
 * Idle keys are pruned opportunistically so the map cannot grow without bound.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records one hit for {@code key} and throws when more than {@code perMinute} hits happened in
     * the last minute.
     *
     * @throws TooManyRequestsException when the limit is exceeded
     */
    public void check(String key, int perMinute, String message) {
        long now = clock.millis();
        if (hits.size() > PRUNE_THRESHOLD) {
            prune(now);
        }
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= perMinute) {
                throw new TooManyRequestsException(message);
            }
            window.addLast(now);
        }
    }

    private void prune(long now) {
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long last = entry.getValue().peekLast();
                return last == null || now - last >= WINDOW_MILLIS;
            }
        });
    }
}
