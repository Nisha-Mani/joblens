import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Spinner } from '../components/ui/Spinner'
import { deleteResume, getResume, listResumes } from '../features/resume/api'
import { ParsedResumeForm } from '../features/resume/ParsedResumeForm'
import { ResumeUpload } from '../features/resume/ResumeUpload'

const formatDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { dateStyle: 'medium' })

export default function ResumePage() {
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const resumes = useQuery({ queryKey: ['resumes'], queryFn: listResumes })
  // Default to the newest version until the user picks another.
  const activeId = selectedId ?? resumes.data?.[0]?.id ?? null
  const detail = useQuery({
    queryKey: ['resume', activeId],
    queryFn: () => getResume(activeId!),
    enabled: activeId !== null,
  })

  const remove = useMutation({
    mutationFn: deleteResume,
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: ['resume', id] })
      if (selectedId === id) setSelectedId(null)
      return queryClient.invalidateQueries({ queryKey: ['resumes'] })
    },
  })

  return (
    <div className="space-y-8">
      <PageHeader title="Resume" description="Upload your resume to extract your skills and experience." />
      <ResumeUpload onUploaded={(r) => setSelectedId(r.id)} />

      {resumes.isPending && <Spinner label="Loading resumes…" />}
      {resumes.isError && <ErrorState message="Could not load your resumes." onRetry={() => void resumes.refetch()} />}
      {resumes.isSuccess && resumes.data.length === 0 && (
        <EmptyState title="No resume yet" description="Upload a PDF resume above. JobLens extracts your details so you can review and correct them." />
      )}

      {resumes.isSuccess && resumes.data.length > 0 && (
        <>
          <section aria-labelledby="versions-heading">
            <h2 id="versions-heading" className="mb-2 text-lg font-semibold">Versions</h2>
            <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
              {resumes.data.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-3 px-4 py-2 text-sm">
                  <button
                    type="button"
                    onClick={() => setSelectedId(r.id)}
                    aria-current={r.id === activeId ? 'true' : undefined}
                    className={`text-left ${r.id === activeId ? 'font-semibold' : 'hover:underline'}`}
                  >
                    v{r.version} · {r.fileName}
                    <span className="ml-2 font-normal text-slate-500">{formatDate(r.createdAt)}</span>
                  </button>
                  <Button
                    variant="secondary"
                    aria-label={`Delete version ${r.version}`}
                    disabled={remove.isPending}
                    onClick={() => {
                      if (window.confirm(`Delete ${r.fileName} (v${r.version})? This cannot be undone.`)) remove.mutate(r.id)
                    }}
                  >
                    Delete
                  </Button>
                </li>
              ))}
            </ul>
            {remove.isError && <p role="alert" className="mt-2 text-sm text-red-700">Could not delete the resume. Try again.</p>}
          </section>

          <section aria-labelledby="parsed-heading">
            <h2 id="parsed-heading" className="mb-2 text-lg font-semibold">Extracted information</h2>
            {detail.isPending && <Spinner label="Loading resume…" />}
            {detail.isError && <ErrorState message="Could not load this resume." onRetry={() => void detail.refetch()} />}
            {detail.isSuccess && <ParsedResumeForm key={detail.data.id} resume={detail.data} />}
          </section>
        </>
      )}
    </div>
  )
}
