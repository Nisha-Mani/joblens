package com.joblens.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties("", 60, "joblens"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", 60, "joblens"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonPositiveExpiry() {
        assertThatThrownBy(() -> new JwtProperties("x".repeat(40), 0, "joblens"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsValidConfiguration() {
        assertThatCode(() -> new JwtProperties("x".repeat(40), 60, "joblens"))
            .doesNotThrowAnyException();
    }
}
