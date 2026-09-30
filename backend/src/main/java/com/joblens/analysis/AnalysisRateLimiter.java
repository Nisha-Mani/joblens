package com.joblens.analysis;

import com.joblens.analysis.ai.AiProperties;
import com.joblens.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Per-user sliding-window limit on AI requests (analyses and question generation), to cap cost and abuse. State is in memory, so it
 * is per application instance and resets on restart; a shared store would be needed to enforce
 * the limit across several instances.
 */
@Component
public class AnalysisRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int limit;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public AnalysisRateLimiter(AiProperties properties, Clock clock) {
        this.limit = properties.rateLimitPerHour();
        this.clock = clock;
    }

    /** Records an attempt, or throws 429 if the user has used up the window. */
    public void acquire(UUID userId) {
        Instant now = clock.instant();
        Deque<Instant> window = attempts.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                long minutes = Math.max(1, Duration.between(now, window.peekFirst().plus(WINDOW)).toMinutes() + 1);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have reached the limit of " + limit + " AI requests per hour. Try again in about "
                        + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
            }
            window.addLast(now);
        }
    }
}
