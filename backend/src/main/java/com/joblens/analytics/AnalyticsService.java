package com.joblens.analytics;

import com.joblens.analytics.dto.DashboardResponse;
import com.joblens.analytics.dto.DashboardResponse.Rates;
import com.joblens.analytics.dto.DashboardResponse.StatusCount;
import com.joblens.analytics.dto.DashboardResponse.Totals;
import com.joblens.application.ApplicationStatus;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    static final int MIN_MONTHS = 1;
    static final int MAX_MONTHS = 24;
    static final int LIST_SIZE = 5;
    static final int SKILL_LIST_SIZE = 10;

    private final AnalyticsRepository repository;

    public AnalyticsService(AnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID userId, int months) {
        int window = Math.min(Math.max(months, MIN_MONTHS), MAX_MONTHS);
        Totals totals = repository.totals(userId);
        return new DashboardResponse(
            totals,
            rates(totals),
            statusDistribution(userId),
            repository.applicationsByMonth(userId, window),
            repository.upcomingInterviews(userId, LIST_SIZE),
            repository.recentApplications(userId, LIST_SIZE),
            repository.topMissingSkills(userId, SKILL_LIST_SIZE));
    }

    /** Every status appears, in pipeline order, so charts need no gap-filling. */
    private List<StatusCount> statusDistribution(UUID userId) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        repository.statusCounts(userId).forEach(c -> counts.put(c.status(), c.count()));
        return Arrays.stream(ApplicationStatus.values())
            .map(s -> new StatusCount(s, counts.getOrDefault(s, 0L)))
            .toList();
    }

    static Rates rates(Totals totals) {
        return new Rates(ratio(totals.responded(), totals.applied()),
            ratio(totals.interviews(), totals.applied()), ratio(totals.offers(), totals.applied()));
    }

    private static Double ratio(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }
}
