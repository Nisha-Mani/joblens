import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { PageHeader } from '../components/layout/PageHeader'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Spinner } from '../components/ui/Spinner'
import { StatusBadge } from '../components/ui/StatusBadge'
import { statusLabel } from '../features/applications/api'
import { formatDate, formatDateTime } from '../features/applications/dates'
import { formatRate, getDashboard } from '../features/analytics/api'
import { BarList, ChartCard, MonthlyColumns, StatTile } from '../features/analytics/charts'

export default function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['dashboard', 12], queryFn: () => getDashboard(12) })

  return (
    <div className="space-y-6">
      <PageHeader title="Dashboard" description="Your job search at a glance." />

      {dashboard.isPending && <Spinner label="Loading your dashboard…" />}
      {dashboard.isError && <ErrorState message="Could not load your dashboard." onRetry={() => void dashboard.refetch()} />}

      {dashboard.isSuccess && dashboard.data.totals.tracked === 0 && (
        <EmptyState title="Your dashboard is waiting for data"
          description="Save a job and track an application, and your progress, interviews and skill gaps will show up here."
          action={<div className="flex justify-center gap-4 text-sm font-medium">
            <Link className="underline" to="/jobs/new">Add a job</Link>
            <Link className="underline" to="/resume">Upload your resume</Link>
          </div>} />
      )}

      {dashboard.isSuccess && dashboard.data.totals.tracked > 0 && (() => {
        const { totals, rates, statusDistribution, applicationsByMonth, upcomingInterviews, recentApplications, topMissingSkills } = dashboard.data
        return (
          <>
            <dl className="grid grid-cols-2 gap-3 lg:grid-cols-5">
              <StatTile label="Applications" value={totals.applied} hint={`${totals.tracked} tracked`} />
              <StatTile label="Interviews" value={totals.interviews} />
              <StatTile label="Offers" value={totals.offers} />
              <StatTile label="Response rate" value={formatRate(rates.responseRate)} hint={totals.applied === 0 ? 'Apply to see this' : undefined} />
              <StatTile label="Applied this month" value={totals.appliedThisMonth} />
            </dl>

            <div className="grid gap-4 lg:grid-cols-2">
              <ChartCard title="Applications by status" description="Where each application stands today"
                table={{ headers: ['Status', 'Applications'], rows: statusDistribution.map((s) => [statusLabel(s.status), s.count]) }}>
                <BarList items={statusDistribution.map((s) => ({ label: statusLabel(s.status), value: s.count }))} />
              </ChartCard>
              <ChartCard title="Application activity" description="Applications submitted per month, last 12 months"
                table={{ headers: ['Month', 'Applications'], rows: applicationsByMonth.map((m) => [m.month, m.count]) }}>
                <MonthlyColumns data={applicationsByMonth} />
              </ChartCard>
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
              <section aria-labelledby="upcoming-heading" className="rounded-lg border border-slate-200 bg-white p-4">
                <h3 id="upcoming-heading" className="text-sm font-semibold">Upcoming interviews</h3>
                {upcomingInterviews.length === 0
                  ? <p className="mt-3 text-sm text-slate-600">No interviews scheduled. Add an interview date to an application and it will appear here.</p>
                  : <ul className="mt-3 divide-y divide-slate-100 text-sm">
                      {upcomingInterviews.map((i) => (
                        <li key={i.applicationId} className="flex items-center justify-between gap-3 py-2">
                          <Link className="underline" to={`/applications/${i.applicationId}`}>{i.title} · {i.company}</Link>
                          <span className="shrink-0 text-slate-600">{formatDateTime(i.interviewDate)}</span>
                        </li>
                      ))}
                    </ul>}
              </section>
              <section aria-labelledby="recent-heading" className="rounded-lg border border-slate-200 bg-white p-4">
                <h3 id="recent-heading" className="text-sm font-semibold">Recent applications</h3>
                <ul className="mt-3 divide-y divide-slate-100 text-sm">
                  {recentApplications.map((a) => (
                    <li key={a.applicationId} className="flex items-center justify-between gap-3 py-2">
                      <Link className="underline" to={`/applications/${a.applicationId}`}>{a.title} · {a.company}</Link>
                      <span className="flex shrink-0 items-center gap-2"><StatusBadge status={a.status} /><span className="text-slate-500">{formatDate(a.updatedAt)}</span></span>
                    </li>
                  ))}
                </ul>
              </section>
            </div>

            <ChartCard title="Top missing skills" description="Skills your analyzed jobs asked for that your resume does not show"
              table={{ headers: ['Skill', 'Jobs'], rows: topMissingSkills.map((s) => [s.skill, s.count]) }}>
              {topMissingSkills.length === 0
                ? <p className="text-sm text-slate-600">No skill gaps yet. <Link className="underline" to="/jobs">Analyze a job</Link> against your resume to find them.</p>
                : <BarList tone="gap" items={topMissingSkills.map((s) => ({ label: s.skill, value: s.count }))} />}
            </ChartCard>
          </>
        )
      })()}
    </div>
  )
}
