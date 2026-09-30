import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { ErrorState } from '../components/ui/ErrorState'
import { Spinner } from '../components/ui/Spinner'
import { StatusBadge } from '../components/ui/StatusBadge'
import { ApiError } from '../lib/api'
import { deleteApplication, getApplication, statusLabel, updateApplication, type ApplicationInput } from '../features/applications/api'
import { ApplicationForm } from '../features/applications/ApplicationForm'
import { formatDate, formatDateTime } from '../features/applications/dates'

export default function ApplicationDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)

  const application = useQuery({
    queryKey: ['application', id],
    queryFn: () => getApplication(id),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 1,
  })

  const update = useMutation({
    mutationFn: (input: ApplicationInput) => updateApplication(id, input),
    onSuccess: (saved) => {
      queryClient.setQueryData(['application', id], saved)
      void queryClient.invalidateQueries({ queryKey: ['applications'] })
      setEditing(false)
    },
  })
  const remove = useMutation({
    mutationFn: () => deleteApplication(id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['application', id] })
      await queryClient.invalidateQueries({ queryKey: ['applications'] })
      navigate('/applications', { replace: true })
    },
  })

  if (application.isPending) return <Spinner label="Loading application…" />
  if (application.isError) {
    const notFound = application.error instanceof ApiError && application.error.status === 404
    return (
      <div className="space-y-4">
        <ErrorState title={notFound ? 'Application not found' : 'Something went wrong'}
          message={notFound ? 'This application does not exist or was deleted.' : 'Could not load this application.'}
          onRetry={notFound ? undefined : () => void application.refetch()} />
        <Link to="/applications" className="text-sm font-medium underline">Back to applications</Link>
      </div>
    )
  }

  const data = application.data
  if (editing) {
    return (
      <div>
        <PageHeader title="Edit application" description={`${data.title} at ${data.company}`} />
        <ApplicationForm application={data} submitLabel="Save changes" onCancel={() => setEditing(false)}
          onSubmit={async (input) => { await update.mutateAsync(input) }} />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <Link to="/applications" className="text-sm text-slate-600 underline">← All applications</Link>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <PageHeader title={data.title} description={`${data.company}${data.location ? ` · ${data.location}` : ''}`} />
          <StatusBadge status={data.status} />
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setEditing(true)}>Edit</Button>
          <Button variant="secondary" disabled={remove.isPending}
            onClick={() => { if (window.confirm(`Stop tracking ${data.title} at ${data.company}? The job itself is kept.`)) remove.mutate() }}>
            Delete
          </Button>
        </div>
      </div>
      {remove.isError && <p role="alert" className="text-sm text-red-700">Could not delete the application. Try again.</p>}

      <dl className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 text-sm sm:grid-cols-3">
        <div><dt className="text-slate-500">Applied</dt><dd className="font-medium">{formatDate(data.appliedAt)}</dd></div>
        <div><dt className="text-slate-500">Interview</dt><dd className="font-medium">{formatDateTime(data.interviewDate)}</dd></div>
        <div><dt className="text-slate-500">Job</dt><dd className="font-medium"><Link className="underline" to={`/jobs/${data.jobId}`}>View job posting</Link></dd></div>
      </dl>

      <section aria-labelledby="notes-heading">
        <h2 id="notes-heading" className="mb-2 text-lg font-semibold">Notes</h2>
        {data.notes
          ? <div className="whitespace-pre-wrap rounded-lg border border-slate-200 bg-white p-4 text-sm leading-relaxed">{data.notes}</div>
          : <p className="text-sm text-slate-600">No notes yet. Use Edit to add some.</p>}
      </section>

      <section aria-labelledby="history-heading">
        <h2 id="history-heading" className="mb-2 text-lg font-semibold">Status history</h2>
        <ol className="space-y-2 text-sm">
          {[...data.history].reverse().map((h, i) => (
            <li key={i} className="flex items-center gap-2">
              <span className="text-slate-500">{formatDateTime(h.changedAt)}</span>
              <span>{h.from ? `${statusLabel(h.from)} → ${statusLabel(h.to)}` : `Created as ${statusLabel(h.to)}`}</span>
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}
