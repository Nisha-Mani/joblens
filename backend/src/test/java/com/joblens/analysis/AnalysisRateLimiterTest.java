package com.joblens.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.analysis.ai.AiProperties;
import com.joblens.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AnalysisRateLimiterTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final AnalysisRateLimiter limiter =
        new AnalysisRateLimiter(new AiProperties("mock", null, null, null, 0, 0, 2), clock);

    @Test
    void allowsUpToTheLimitThenRejectsWithTooManyRequests() {
        UUID user = UUID.randomUUID();
        limiter.acquire(user);
        limiter.acquire(user);
        assertThatThrownBy(() -> limiter.acquire(user))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
            .hasMessageContaining("2 analyses per hour");
    }

    @Test
    void limitsAreIndependentPerUser() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        limiter.acquire(a);
        limiter.acquire(a);
        limiter.acquire(b);
    }

    @Test
    void windowSlidesSoQuotaReturnsAfterAnHour() {
        UUID user = UUID.randomUUID();
        limiter.acquire(user);
        clock.advance(Duration.ofMinutes(30));
        limiter.acquire(user);
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(ApiException.class)
            .hasMessageContaining("31 minutes");
        clock.advance(Duration.ofMinutes(31));
        limiter.acquire(user); // the first attempt has now aged out
    }
}
