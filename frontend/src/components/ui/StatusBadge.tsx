import { statusLabel, type ApplicationStatus } from '../../features/applications/api'

const styles: Record<ApplicationStatus, string> = {
  SAVED: 'bg-slate-100 text-slate-700',
  APPLIED: 'bg-blue-50 text-blue-800',
  SCREENING: 'bg-indigo-50 text-indigo-800',
  INTERVIEW: 'bg-amber-50 text-amber-800',
  OFFER: 'bg-green-50 text-green-800',
  REJECTED: 'bg-red-50 text-red-800',
  WITHDRAWN: 'bg-slate-100 text-slate-600',
}

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${styles[status]}`}>
      {statusLabel(status)}
    </span>
  )
}
