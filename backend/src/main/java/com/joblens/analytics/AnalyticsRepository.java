package com.joblens.analytics;

import com.joblens.analytics.dto.DashboardResponse.MonthCount;
import com.joblens.analytics.dto.DashboardResponse.RecentApplication;
import com.joblens.analytics.dto.DashboardResponse.SkillCount;
import com.joblens.analytics.dto.DashboardResponse.StatusCount;
import com.joblens.analytics.dto.DashboardResponse.Totals;
import com.joblens.analytics.dto.DashboardResponse.UpcomingInterview;
import com.joblens.application.ApplicationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Aggregations run in PostgreSQL, scoped to one user, so the API never ships raw rows to the
 * browser to be counted there. "Ever reached" questions use the status history table because the
 * current status forgets, for example, that a rejected application had an interview. Months are UTC.
 */
@Repository
public class AnalyticsRepository {

    private final JdbcClient jdbc;

    public AnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Totals totals(UUID userId) {
        return jdbc.sql("""
            WITH reached AS (
                SELECT h.application_id,
                       bool_or(h.to_status <> 'SAVED')                                    AS applied,
                       bool_or(h.to_status IN ('SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED')) AS responded,
                       bool_or(h.to_status = 'INTERVIEW')                                 AS interviewed,
                       bool_or(h.to_status = 'OFFER')                                     AS offered
                FROM application_status_history h
                JOIN applications a ON a.id = h.application_id
                WHERE a.user_id = :userId
                GROUP BY h.application_id
            )
            SELECT count(*)                                              AS tracked,
                   count(*) FILTER (WHERE r.applied)                     AS applied,
                   count(*) FILTER (WHERE a.applied_at >= date_trunc('month', timezone('UTC', now()))::date
                                      AND a.applied_at <  (date_trunc('month', timezone('UTC', now())) + interval '1 month')::date)
                                                                         AS applied_this_month,
                   count(*) FILTER (WHERE r.responded)                   AS responded,
                   count(*) FILTER (WHERE r.interviewed)                 AS interviews,
                   count(*) FILTER (WHERE r.offered)                     AS offers,
                   count(*) FILTER (WHERE a.status = 'REJECTED')         AS rejections
            FROM applications a
            LEFT JOIN reached r ON r.application_id = a.id
            WHERE a.user_id = :userId
            """)
            .param("userId", userId)
            .query((rs, row) -> new Totals(rs.getLong("tracked"), rs.getLong("applied"),
                rs.getLong("applied_this_month"), rs.getLong("responded"), rs.getLong("interviews"),
                rs.getLong("offers"), rs.getLong("rejections")))
            .single();
    }

    public List<StatusCount> statusCounts(UUID userId) {
        return jdbc.sql("SELECT status, count(*) AS count FROM applications WHERE user_id = :userId GROUP BY status")
            .param("userId", userId)
            .query((rs, row) -> new StatusCount(ApplicationStatus.valueOf(rs.getString("status")), rs.getLong("count")))
            .list();
    }

    /** One row per month for the last {@code months} months, including months with no applications. */
    public List<MonthCount> applicationsByMonth(UUID userId, int months) {
        return jdbc.sql("""
            SELECT to_char(m.month, 'YYYY-MM') AS month, count(a.id) AS count
            FROM generate_series(
                     date_trunc('month', timezone('UTC', now())) - make_interval(months => :months - 1),
                     date_trunc('month', timezone('UTC', now())),
                     interval '1 month') AS m(month)
            LEFT JOIN applications a
                   ON a.user_id = :userId
                  AND a.applied_at >= m.month::date
                  AND a.applied_at <  (m.month + interval '1 month')::date
            GROUP BY m.month
            ORDER BY m.month
            """)
            .param("userId", userId)
            .param("months", months)
            .query((rs, row) -> new MonthCount(rs.getString("month"), rs.getLong("count")))
            .list();
    }

    public List<UpcomingInterview> upcomingInterviews(UUID userId, int limit) {
        return jdbc.sql("""
            SELECT a.id, j.company, j.title, a.status, a.interview_date
            FROM applications a JOIN jobs j ON j.id = a.job_id
            WHERE a.user_id = :userId
              AND a.interview_date >= now()
              AND a.status NOT IN ('REJECTED', 'WITHDRAWN')
            ORDER BY a.interview_date, a.id
            LIMIT :limit
            """)
            .param("userId", userId)
            .param("limit", limit)
            .query((rs, row) -> new UpcomingInterview(rs.getObject("id", UUID.class), rs.getString("company"),
                rs.getString("title"), ApplicationStatus.valueOf(rs.getString("status")),
                rs.getObject("interview_date", java.time.OffsetDateTime.class).toInstant()))
            .list();
    }

    public List<RecentApplication> recentApplications(UUID userId, int limit) {
        return jdbc.sql("""
            SELECT a.id, j.company, j.title, a.status, a.updated_at
            FROM applications a JOIN jobs j ON j.id = a.job_id
            WHERE a.user_id = :userId
            ORDER BY a.updated_at DESC, a.id
            LIMIT :limit
            """)
            .param("userId", userId)
            .param("limit", limit)
            .query((rs, row) -> new RecentApplication(rs.getObject("id", UUID.class), rs.getString("company"),
                rs.getString("title"), ApplicationStatus.valueOf(rs.getString("status")),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()))
            .list();
    }

    /**
     * Most common missing skills, counting only each job's latest analysis so that re-running an
     * analysis does not inflate a skill's count.
     */
    public List<SkillCount> topMissingSkills(UUID userId, int limit) {
        return jdbc.sql("""
            SELECT min(skill) AS skill, count(*) AS count
            FROM (
                SELECT DISTINCT ON (job_id) missing_skills
                FROM resume_analyses
                WHERE user_id = :userId
                ORDER BY job_id, created_at DESC
            ) latest,
            jsonb_array_elements_text(latest.missing_skills) AS skill
            GROUP BY lower(skill)
            ORDER BY count(*) DESC, min(skill)
            LIMIT :limit
            """)
            .param("userId", userId)
            .param("limit", limit)
            .query((rs, row) -> new SkillCount(rs.getString("skill"), rs.getLong("count")))
            .list();
    }
}
