import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Spinner } from '../../components/ui/Spinner'
import { ApiError } from '../../lib/api'
import { listResumes } from '../resume/api'
import { AnalysisView } from './AnalysisView'
import { analyzeJob, getAnalysis, listAnalyses, scoreLevel } from './api'

export function AnalysisPanel({ jobId }: { jobId: string }) {
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [resumeId, setResumeId] = useState('')

  const resumes = useQuery({ queryKey: ['resumes'], queryFn: listResumes })
  const history = useQuery({ queryKey: ['analyses', jobId], queryFn: () => listAnalyses(jobId) })

  const activeId = selectedId ?? history.data?.[0]?.id ?? null
  const detail = useQuery({
    queryKey: ['analysis', activeId],
    queryFn: () => getAnalysis(activeId!),
    enabled: activeId !== null,
  })

  const analyze = useMutation({
    mutationFn: () => analyzeJob(jobId, resumeId || undefined),
    onSuccess: (analysis) => {
      queryClient.setQueryData(['analysis', analysis.id], analysis)
      setSelectedId(analysis.id)
      return queryClient.invalidateQueries({ queryKey: ['analyses', jobId] })
    },
  })

  return (
    <section aria-labelledby="analysis-heading" className="space-y-4">
      <h2 id="analysis-heading" className="text-lg font-semibold">Resume match</h2>

      {resumes.isPending && <Spinner label="Checking your resume…" />}
      {resumes.isError && <ErrorState message="Could not load your resumes." onRetry={() => void resumes.refetch()} />}

      {resumes.isSuccess && resumes.data.length === 0 && (
        <EmptyState
          title="Upload a resume first"
          description="JobLens compares your resume with this job description to find matching skills, gaps and improvements."
          action={<Link to="/resume" className="text-sm font-medium underline">Go to Resume</Link>}
        />
      )}

      {resumes.isSuccess && resumes.data.length > 0 && (
        <div className="flex flex-wrap items-end gap-3">
          {resumes.data.length > 1 && (
            <div>
              <label htmlFor="analysis-resume" className="block text-sm font-medium text-slate-700">Resume version</label>
              <select id="analysis-resume" value={resumeId} onChange={(e) => setResumeId(e.target.value)}
                disabled={analyze.isPending}
                className="mt-1 block rounded-md border border-slate-300 bg-white px-3 py-2 text-sm">
                <option value="">Latest (v{resumes.data[0].version})</option>
                {resumes.data.map((r) => <option key={r.id} value={r.id}>v{r.version} · {r.fileName}</option>)}
              </select>
            </div>
          )}
          <Button onClick={() => analyze.mutate()} disabled={analyze.isPending}>
            {history.data && history.data.length > 0 ? 'Analyze again' : 'Analyze match'}
          </Button>
        </div>
      )}

      {analyze.isPending && <Spinner label="Analyzing your resume against this job… this can take up to 30 seconds." />}
      {analyze.isError && (
        <ErrorState
          title="Analysis failed"
          message={analyze.error instanceof ApiError ? analyze.error.message : 'Could not reach the server. Try again.'}
          onRetry={() => analyze.mutate()}
        />
      )}

      {history.isError && <ErrorState message="Could not load previous analyses." onRetry={() => void history.refetch()} />}

      {detail.isPending && activeId !== null && <Spinner label="Loading analysis…" />}
      {detail.isError && <ErrorState message="Could not load this analysis." onRetry={() => void detail.refetch()} />}
      {detail.isSuccess && <AnalysisView analysis={detail.data} />}

      {history.isSuccess && history.data.length > 1 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold">Previous analyses</h3>
          <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white text-sm">
            {history.data.map((a) => (
              <li key={a.id}>
                <button type="button" onClick={() => setSelectedId(a.id)}
                  aria-current={a.id === activeId ? 'true' : undefined}
                  className={`flex w-full items-center justify-between gap-3 px-4 py-2 text-left ${a.id === activeId ? 'font-semibold' : 'hover:bg-slate-50'}`}>
                  <span>{new Date(a.createdAt).toLocaleString()} · resume v{a.resumeVersion}</span>
                  <span>{a.overallScore}% · {scoreLevel(a.overallScore).label}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
