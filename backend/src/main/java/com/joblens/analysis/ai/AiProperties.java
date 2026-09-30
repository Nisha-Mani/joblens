package com.joblens.analysis.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, String apiKey, String model, String baseUrl,
                           int timeoutSeconds, int maxOutputTokens, int rateLimitPerHour) {

    public AiProperties {
        provider = provider == null || provider.isBlank() ? "mock" : provider.strip().toLowerCase();
        model = model == null || model.isBlank() ? "gpt-4o-mini" : model.strip();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : baseUrl.strip();
        timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : 1500;
        rateLimitPerHour = rateLimitPerHour > 0 ? rateLimitPerHour : 20;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
