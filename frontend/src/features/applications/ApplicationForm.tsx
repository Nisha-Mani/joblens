import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ApiError } from '../../lib/api'
import { searchJobs } from '../jobs/api'
import { STATUSES, statusLabel, type ApplicationDetail, type ApplicationInput, type ApplicationStatus } from './api'
import { fromDateTimeLocal, toDateTimeLocal } from './dates'

interface ApplicationFormProps {
  application?: ApplicationDetail
  /** Preselects a job on the create form (e.g. coming from a job page). */
  initialJobId?: string
  submitLabel: string
  onSubmit: (input: ApplicationInput) => Promise<void>
  onCancel?: () => void
}

const fieldClass = 'mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm'

export function ApplicationForm({ application, initialJobId, submitLabel, onSubmit, onCancel }: ApplicationFormProps) {
  const creating = !application
  const [jobId, setJobId] = useState(initialJobId ?? '')
  const [status, setStatus] = useState<ApplicationStatus>(application?.status ?? 'SAVED')
  const [appliedAt, setAppliedAt] = useState(application?.appliedAt ?? '')
  const [interview, setInterview] = useState(toDateTimeLocal(application?.interviewDate ?? null))
  const [notes, setNotes] = useState(application?.notes ?? '')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const jobs = useQuery({
    queryKey: ['jobs', 'picker'],
    queryFn: () => searchJobs({ q: '', employmentType: '', sortBy: 'company', direction: 'asc', page: 0, size: 100 }),
    enabled: creating,
  })

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (creating && !jobId) {
      setError('Choose a job to track.')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await onSubmit({
        ...(creating ? { jobId } : {}),
        status,
        appliedAt: appliedAt || null,
        interviewDate: fromDateTimeLocal(interview),
        notes: notes.trim() || null,
      })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reach the server. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p>}

      {creating && (
        <div>
          <label htmlFor="app-job" className="block text-sm font-medium text-slate-700">Job</label>
          {jobs.isError && <p role="alert" className="text-sm text-red-700">Could not load your jobs.</p>}
          {jobs.isSuccess && jobs.data.totalElements === 0 ? (
            <p className="mt-1 text-sm text-slate-600">
              You have no saved jobs yet. <Link className="underline" to="/jobs/new">Add a job</Link> first.
            </p>
          ) : (
            <select id="app-job" value={jobId} onChange={(e) => setJobId(e.target.value)} className={fieldClass} disabled={jobs.isPending}>
              <option value="">{jobs.isPending ? 'Loading jobs…' : 'Select a job'}</option>
              {jobs.data?.content.map((j) => <option key={j.id} value={j.id}>{j.company} — {j.title}</option>)}
            </select>
          )}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        <div>
          <label htmlFor="app-status" className="block text-sm font-medium text-slate-700">Status</label>
          <select id="app-status" value={status} onChange={(e) => setStatus(e.target.value as ApplicationStatus)} className={fieldClass}>
            {STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="app-applied" className="block text-sm font-medium text-slate-700">Applied on</label>
          <input id="app-applied" type="date" value={appliedAt} onChange={(e) => setAppliedAt(e.target.value)} className={fieldClass} />
        </div>
        <div>
          <label htmlFor="app-interview" className="block text-sm font-medium text-slate-700">Interview</label>
          <input id="app-interview" type="datetime-local" value={interview} onChange={(e) => setInterview(e.target.value)} className={fieldClass} />
        </div>
      </div>

      <div>
        <label htmlFor="app-notes" className="block text-sm font-medium text-slate-700">Notes</label>
        <textarea id="app-notes" rows={5} maxLength={10000} value={notes} onChange={(e) => setNotes(e.target.value)} className={fieldClass} />
      </div>

      <div className="flex gap-3">
        <Button type="submit" disabled={submitting}>{submitting ? 'Saving…' : submitLabel}</Button>
        {onCancel && <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  )
}
