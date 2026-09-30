import type { ReactNode } from 'react'
import { monthLabel, monthLabelLong } from './api'

/** Colors for data marks. Text always uses text colors; marks carry identity only. */
const MARK = { volume: '#2a78d6', gap: '#eb6834' } as const

export function ChartCard({ title, description, children, table }: {
  title: string
  description?: string
  children: ReactNode
  table: { headers: [string, string]; rows: [string, number][] }
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4" aria-label={title}>
      <h2 className="text-sm font-semibold">{title}</h2>
      {description && <p className="mt-0.5 text-xs text-slate-500">{description}</p>}
      <div className="mt-4">{children}</div>
      <details className="mt-4 text-xs text-slate-600">
        <summary className="cursor-pointer">View data as table</summary>
        <table className="mt-2 w-full text-left">
          <thead>
            <tr>
              <th scope="col" className="py-1 font-medium">{table.headers[0]}</th>
              <th scope="col" className="py-1 text-right font-medium">{table.headers[1]}</th>
            </tr>
          </thead>
          <tbody>
            {table.rows.map(([label, value]) => (
              <tr key={label} className="border-t border-slate-100">
                <td className="py-1">{label}</td>
                <td className="py-1 text-right tabular-nums">{value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </section>
  )
}

/** Horizontal bars for comparing magnitudes across named categories; every bar is labelled directly. */
export function BarList({ items, tone = 'volume' }: { items: { label: string; value: number }[]; tone?: keyof typeof MARK }) {
  const max = Math.max(1, ...items.map((i) => i.value))
  return (
    <ul className="space-y-2">
      {items.map((item) => (
        <li key={item.label} className="grid grid-cols-[7rem_1fr_2.5rem] items-center gap-3 text-sm sm:grid-cols-[9rem_1fr_2.5rem]">
          <span className="truncate text-slate-700" title={item.label}>{item.label}</span>
          <span className="h-3 rounded-r bg-slate-100" aria-hidden="true">
            {item.value > 0 && (
              <span className="block h-full rounded-r" style={{ width: `${(item.value / max) * 100}%`, backgroundColor: MARK[tone] }} />
            )}
          </span>
          <span className="text-right font-medium tabular-nums">{item.value}</span>
        </li>
      ))}
    </ul>
  )
}

/** Vertical columns over time. Hover/focus shows the exact value; zero months stay visible as a baseline tick. */
export function MonthlyColumns({ data }: { data: { month: string; count: number }[] }) {
  const max = Math.max(1, ...data.map((d) => d.count))
  const total = data.reduce((sum, d) => sum + d.count, 0)
  return (
    <div role="img" aria-label={`Applications per month over ${data.length} months, ${total} in total`}>
      <div className="flex h-40 items-end gap-1 border-b border-slate-200">
        {data.map((d) => (
          <div key={d.month} className="group relative flex h-full flex-1 flex-col items-center justify-end" title={`${monthLabelLong(d.month)}: ${d.count}`}>
            <span className="mb-1 text-xs font-medium tabular-nums text-slate-700">{d.count > 0 ? d.count : ''}</span>
            <span className="block w-full max-w-8 rounded-t"
              style={{ height: d.count > 0 ? `${Math.max((d.count / max) * 100, 4)}%` : '2px', backgroundColor: d.count > 0 ? MARK.volume : '#e2e8f0' }} />
          </div>
        ))}
      </div>
      <div className="mt-1 flex gap-1">
        {data.map((d, i) => (
          <span key={d.month} className="flex-1 text-center text-[11px] text-slate-500">
            {data.length <= 12 || i % 2 === 0 || i === data.length - 1 ? monthLabel(d.month) : ''}
          </span>
        ))}
      </div>
    </div>
  )
}

export function StatTile({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <dt className="text-sm text-slate-600">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold tabular-nums">{value}</dd>
      {hint && <dd className="mt-0.5 text-xs text-slate-500">{hint}</dd>}
    </div>
  )
}
