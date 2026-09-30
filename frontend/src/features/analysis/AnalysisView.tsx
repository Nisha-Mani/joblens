import { scoreLevel, type Analysis } from './api'

function Chips({ items, tone, empty }: { items: string[]; tone: 'good' | 'warn' | 'neutral'; empty: string }) {
  if (items.length === 0) return <p className="text-sm text-slate-500">{empty}</p>
  const styles = {
    good: 'bg-green-50 text-green-800 border-green-200',
    warn: 'bg-amber-50 text-amber-800 border-amber-200',
    neutral: 'bg-slate-50 text-slate-700 border-slate-200',
  }
  return (
    <ul className="flex flex-wrap gap-2">
      {items.map((item) => (
        <li key={item} className={`rounded-full border px-2.5 py-0.5 text-sm ${styles[tone]}`}>{item}</li>
      ))}
    </ul>
  )
}

function Block({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-2">
      <h4 className="text-sm font-semibold text-slate-900">{title}</h4>
      {children}
    </section>
  )
}

export function AnalysisView({ analysis }: { analysis: Analysis }) {
  const level = scoreLevel(analysis.overallScore)
  return (
    <div className="space-y-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center gap-4">
        <div className={`rounded-lg border px-4 py-3 ${level.classes}`}>
          <p className="text-3xl font-semibold tabular-nums">
            {analysis.overallScore}<span className="text-lg">%</span>
          </p>
          <p className="text-sm font-medium">{level.label}</p>
        </div>
        <div
          role="meter"
          aria-label="Overall match score"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={analysis.overallScore}
          className="h-2 min-w-40 flex-1 overflow-hidden rounded-full bg-slate-100"
        >
          <div className="h-full bg-slate-900" style={{ width: `${analysis.overallScore}%` }} />
        </div>
      </div>

      <div className="grid gap-6 sm:grid-cols-2">
        <Block title="Matching skills">
          <Chips items={analysis.matchingSkills} tone="good" empty="No matching skills were identified." />
        </Block>
        <Block title="Missing skills">
          <Chips items={analysis.missingSkills} tone="warn" empty="No missing skills. Nice." />
        </Block>
      </div>

      <Block title="Keyword gaps">
        <Chips items={analysis.keywordGaps} tone="neutral" empty="No notable keyword gaps." />
      </Block>

      <Block title="Experience match">
        <p className="text-sm leading-relaxed">{analysis.experienceAssessment}</p>
      </Block>

      <Block title="Resume suggestions">
        {analysis.suggestions.length === 0
          ? <p className="text-sm text-slate-500">No suggestions.</p>
          : <ul className="list-disc space-y-1 pl-5 text-sm">{analysis.suggestions.map((s) => <li key={s}>{s}</li>)}</ul>}
      </Block>

      <Block title="Interview topics to prepare">
        <Chips items={analysis.interviewTopics} tone="neutral" empty="No topics suggested." />
      </Block>

      <p className="text-xs text-slate-500">
        Based on resume v{analysis.resumeVersion} · generated {new Date(analysis.createdAt).toLocaleString()} ·
        model {analysis.model}. AI-generated; verify before acting on it.
      </p>
    </div>
  )
}
