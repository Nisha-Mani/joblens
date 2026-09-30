package com.joblens.analytics.dto;

import com.joblens.application.ApplicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pre-aggregated job-search metrics. Rates are fractions between 0 and 1, or null when there is
 * nothing to divide by (so "no data" is never confused with 0%).
 */
public record DashboardResponse(Totals totals, Rates rates, List<StatusCount> statusDistribution,
                                List<MonthCount> applicationsByMonth,
                                List<UpcomingInterview> upcomingInterviews,
                                List<RecentApplication> recentApplications,
                                List<SkillCount> topMissingSkills) {

    /**
     * @param tracked         every application, including saved ones
     * @param applied         applications that ever moved beyond SAVED
     * @param appliedThisMonth applications whose applied date is in the current UTC month
     * @param responded       applications that ever reached SCREENING, INTERVIEW, OFFER or REJECTED
     * @param interviews      applications that ever reached INTERVIEW
     * @param offers          applications that ever reached OFFER
     * @param rejections      applications currently REJECTED
     */
    public record Totals(long tracked, long applied, long appliedThisMonth, long responded,
                         long interviews, long offers, long rejections) { }

    public record Rates(Double responseRate, Double interviewRate, Double offerRate) { }

    public record StatusCount(ApplicationStatus status, long count) { }

    public record MonthCount(String month, long count) { }

    public record UpcomingInterview(UUID applicationId, String company, String title,
                                    ApplicationStatus status, Instant interviewDate) { }

    public record RecentApplication(UUID applicationId, String company, String title,
                                    ApplicationStatus status, Instant updatedAt) { }

    public record SkillCount(String skill, long count) { }
}
