import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { PageHeader } from '../components/layout/PageHeader'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Spinner } from '../components/ui/Spinner'
import { statusLabel } from '../features/applications/api'
import { formatRate, getDashboard } from '../features/analytics/api'
import { BarList, ChartCard, MonthlyColumns, StatTile } from '../features/analytics/charts'

const RANGES = [6, 12, 24] as const

export default function AnalyticsPage() {
  const [months, setMonths] = useState<(typeof RANGES)[number]>(12)
  const data = useQuery({ queryKey: ['dashboard', months], queryFn: () => getDashboard(months) })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <PageHeader title="Analytics" description="How your job search is progressing over time." />
        <div>
          <label htmlFor="range" className="block text-sm font-medium text-slate-700">Time range</label>
          <select id="range" value={months} onChange={(e) => setMonths(Number(e.target.value) as (typeof RANGES)[number])}
            className="mt-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm">
            {RANGES.map((r) => <option key={r} value={r}>Last {r} months</option>)}
          </select>
        </div>
      </div>

      {data.isPending && <Spinner label="Loading analytics…" />}
      {data.isError && <ErrorState message="Could not load analytics." onRetry={() => void data.refetch()} />}

      {data.isSuccess && data.data.totals.tracked === 0 && (
        <EmptyState title="Nothing to analyze yet" description="Track a few applications and your funnel, response rate and trends will appear here." />
      )}

      {data.isSuccess && data.data.totals.tracked > 0 && (() => {
        const { totals, rates, statusDistribution, applicationsByMonth, topMissingSkills } = data.data
        const funnel = [
          { label: 'Applied', value: totals.applied },
          { label: 'Heard back', value: totals.responded },
          { label: 'Interviewed', value: totals.interviews },
          { label: 'Offers', value: totals.offers },
        ]
        return (
          <>
            <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <StatTile label="Response rate" value={formatRate(rates.responseRate)} hint="Heard back ÷ applied" />
              <StatTile label="Interview rate" value={formatRate(rates.interviewRate)} hint="Interviewed ÷ applied" />
              <StatTile label="Offer rate" value={formatRate(rates.offerRate)} hint="Offers ÷ applied" />
              <StatTile label="Rejections" value={totals.rejections} hint="Currently rejected" />
            </dl>
            <div className="grid gap-4 lg:grid-cols-2">
              <ChartCard title="Application funnel" description="How many applications reached each stage"
                table={{ headers: ['Stage', 'Applications'], rows: funnel.map((f) => [f.label, f.value]) }}>
                <BarList items={funnel} />
              </ChartCard>
              <ChartCard title="Applications by status" description="Current status of every application"
                table={{ headers: ['Status', 'Applications'], rows: statusDistribution.map((s) => [statusLabel(s.status), s.count]) }}>
                <BarList items={statusDistribution.map((s) => ({ label: statusLabel(s.status), value: s.count }))} />
              </ChartCard>
            </div>
            <ChartCard title="Applications over time" description={`Applications submitted per month, last ${months} months`}
              table={{ headers: ['Month', 'Applications'], rows: applicationsByMonth.map((m) => [m.month, m.count]) }}>
              <MonthlyColumns data={applicationsByMonth} />
            </ChartCard>
            <ChartCard title="Top missing skills" description="Most common gaps across your analyzed jobs"
              table={{ headers: ['Skill', 'Jobs'], rows: topMissingSkills.map((s) => [s.skill, s.count]) }}>
              {topMissingSkills.length === 0
                ? <p className="text-sm text-slate-600">Analyze a job against your resume to see skill gaps.</p>
                : <BarList tone="gap" items={topMissingSkills.map((s) => ({ label: s.skill, value: s.count }))} />}
            </ChartCard>
          </>
        )
      })()}
    </div>
  )
}
