import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { ErrorState } from '../components/ui/ErrorState'
import { Spinner } from '../components/ui/Spinner'
import { ApiError } from '../lib/api'
import { searchApplications } from '../features/applications/api'
import { StatusBadge } from '../components/ui/StatusBadge'
import { AnalysisPanel } from '../features/analysis/AnalysisPanel'
import { deleteJob, employmentLabel, getJob, updateJob } from '../features/jobs/api'
import { JobForm } from '../features/jobs/JobForm'

export default function JobDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)

  const job = useQuery({
    queryKey: ['job', id],
    queryFn: () => getJob(id),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 1,
  })

  const tracking = useQuery({
    queryKey: ['applications', { jobId: id }],
    queryFn: () => searchApplications({ q: '', status: '', jobId: id, sortBy: 'updatedAt', direction: 'desc', page: 0, size: 1 }),
  })

  const update = useMutation({
    mutationFn: (input: Parameters<typeof updateJob>[1]) => updateJob(id, input),
    onSuccess: (saved) => {
      queryClient.setQueryData(['job', id], saved)
      void queryClient.invalidateQueries({ queryKey: ['jobs'] })
      setEditing(false)
    },
  })
  const remove = useMutation({
    mutationFn: () => deleteJob(id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['job', id] })
      await queryClient.invalidateQueries({ queryKey: ['jobs'] })
      navigate('/jobs', { replace: true })
    },
  })

  if (job.isPending) return <Spinner label="Loading job…" />
  if (job.isError) {
    const notFound = job.error instanceof ApiError && job.error.status === 404
    return (
      <div className="space-y-4">
        <ErrorState title={notFound ? 'Job not found' : 'Something went wrong'}
          message={notFound ? 'This job does not exist or was deleted.' : 'Could not load this job.'}
          onRetry={notFound ? undefined : () => void job.refetch()} />
        <Link to="/jobs" className="text-sm font-medium underline">Back to jobs</Link>
      </div>
    )
  }

  const data = job.data
  if (editing) {
    return (
      <div>
        <PageHeader title="Edit job" />
        <JobForm job={data} submitLabel="Save changes" onCancel={() => setEditing(false)}
          onSubmit={async (input) => { await update.mutateAsync(input) }} />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <Link to="/jobs" className="text-sm text-slate-600 underline">← All jobs</Link>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader title={data.title} description={`${data.company}${data.location ? ` · ${data.location}` : ''} · ${employmentLabel(data.employmentType)}`} />
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setEditing(true)}>Edit</Button>
          <Button variant="secondary" disabled={remove.isPending}
            onClick={() => { if (window.confirm(`Delete ${data.title} at ${data.company}? This cannot be undone.`)) remove.mutate() }}>
            Delete
          </Button>
        </div>
      </div>
      {remove.isError && <p role="alert" className="text-sm text-red-700">Could not delete the job. Try again.</p>}
      {data.sourceUrl && (
        <p className="text-sm">
          Source: <a className="underline" href={data.sourceUrl} target="_blank" rel="noopener noreferrer">{data.sourceUrl}</a>
        </p>
      )}
      <section aria-labelledby="tracking-heading">
        <h2 id="tracking-heading" className="mb-2 text-lg font-semibold">Application</h2>
        {tracking.isPending && <Spinner label="Checking application…" />}
        {tracking.isError && <p className="text-sm text-red-700">Could not check application status.</p>}
        {tracking.isSuccess && tracking.data.content.length > 0 && (
          <p className="flex items-center gap-2 text-sm">
            <StatusBadge status={tracking.data.content[0].status} />
            <Link className="underline" to={`/applications/${tracking.data.content[0].id}`}>View application</Link>
          </p>
        )}
        {tracking.isSuccess && tracking.data.content.length === 0 && (
          <Link to={`/applications/new?jobId=${id}`} className="text-sm font-medium underline">Track this application</Link>
        )}
      </section>
      <AnalysisPanel jobId={id} />
      <section aria-labelledby="description-heading">
        <h2 id="description-heading" className="mb-2 text-lg font-semibold">Job description</h2>
        <div className="whitespace-pre-wrap rounded-lg border border-slate-200 bg-white p-4 text-sm leading-relaxed">
          {data.jobDescription}
        </div>
      </section>
    </div>
  )
}
