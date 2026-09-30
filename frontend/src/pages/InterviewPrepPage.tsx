import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Pagination } from '../components/ui/Pagination'
import { Spinner } from '../components/ui/Spinner'
import { ApiError } from '../lib/api'
import { searchJobs } from '../features/jobs/api'
import {
  CATEGORIES, DIFFICULTIES, PREP_STATUSES, addQuestion, generateQuestions, label, searchQuestions,
  type Difficulty, type InterviewCategory, type PrepStatus, type QuestionSearchParams,
} from '../features/interview/api'
import { QuestionCard } from '../features/interview/QuestionCard'

const selectClass = 'mt-1 block rounded-md border border-slate-300 bg-white px-3 py-2 text-sm'

export default function InterviewPrepPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  const jobId = searchParams.get('jobId') ?? ''
  const category = (searchParams.get('category') ?? '') as InterviewCategory | ''
  const status = (searchParams.get('status') ?? '') as PrepStatus | ''
  const page = Math.max(Number(searchParams.get('page') ?? '0') || 0, 0)

  function update(changes: Record<string, string | null>, resetPage = true) {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
      if (resetPage) next.delete('page')
      return next
    }, { replace: true })
  }

  const jobs = useQuery({
    queryKey: ['jobs', 'picker'],
    queryFn: () => searchJobs({ q: '', employmentType: '', sortBy: 'company', direction: 'asc', page: 0, size: 100 }),
  })

  const params: QuestionSearchParams = { jobId, category, status, page }
  const questions = useQuery({
    queryKey: ['questions', params],
    queryFn: () => searchQuestions(params),
    placeholderData: keepPreviousData,
  })

  const generate = useMutation({
    mutationFn: () => generateQuestions(jobId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['questions'] }),
  })

  const [custom, setCustom] = useState('')
  const [customCategory, setCustomCategory] = useState<InterviewCategory>('TECHNICAL')
  const [customDifficulty, setCustomDifficulty] = useState<Difficulty>('MEDIUM')
  const add = useMutation({
    mutationFn: addQuestion,
    onSuccess: () => {
      setCustom('')
      return queryClient.invalidateQueries({ queryKey: ['questions'] })
    },
  })

  function submitCustom(event: FormEvent) {
    event.preventDefault()
    if (!jobId || !custom.trim()) return
    add.mutate({ jobId, question: custom.trim(), category: customCategory, difficulty: customDifficulty })
  }

  const hasFilters = category !== '' || status !== ''
  const noJobs = jobs.isSuccess && jobs.data.totalElements === 0
  const generateError = generate.error ?? add.error

  return (
    <div className="space-y-6">
      <PageHeader title="Interview prep" description="Practice questions for each role, with your own notes and progress." />

      {noJobs && (
        <EmptyState title="Add a job first" description="Interview questions are generated from a job description."
          action={<Link to="/jobs/new" className="text-sm font-medium underline">Add a job</Link>} />
      )}

      {!noJobs && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-56">
            <label htmlFor="prep-job" className="block text-sm font-medium text-slate-700">Job</label>
            <select id="prep-job" value={jobId} onChange={(e) => update({ jobId: e.target.value || null })}
              disabled={jobs.isPending} className={`${selectClass} w-full`}>
              <option value="">{jobs.isPending ? 'Loading jobs…' : 'All jobs'}</option>
              {jobs.data?.content.map((j) => <option key={j.id} value={j.id}>{j.company} — {j.title}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="prep-category" className="block text-sm font-medium text-slate-700">Category</label>
            <select id="prep-category" value={category} onChange={(e) => update({ category: e.target.value || null })} className={selectClass}>
              <option value="">All categories</option>
              {CATEGORIES.map((c) => <option key={c} value={c}>{label(c)}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="prep-status" className="block text-sm font-medium text-slate-700">Progress</label>
            <select id="prep-status" value={status} onChange={(e) => update({ status: e.target.value || null })} className={selectClass}>
              <option value="">Any progress</option>
              {PREP_STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
            </select>
          </div>
          <Button onClick={() => generate.mutate()} disabled={!jobId || generate.isPending}
            title={jobId ? undefined : 'Choose a job to generate questions for'}>
            {generate.isPending ? 'Generating…' : 'Generate questions'}
          </Button>
        </div>
      )}

      {generate.isPending && <Spinner label="Generating questions for this job… this can take up to 30 seconds." />}
      {generateError && (
        <ErrorState title="That didn't work" message={generateError instanceof ApiError ? generateError.message : 'Could not reach the server. Try again.'} />
      )}
      {generate.isSuccess && (
        <p role="status" className="text-sm text-green-700">
          {generate.data.length === 0
            ? 'No new questions: everything generated is already in your list.'
            : `Added ${generate.data.length} new question${generate.data.length === 1 ? '' : 's'}.`}
        </p>
      )}

      {questions.isPending && !noJobs && <Spinner label="Loading questions…" />}
      {questions.isError && <ErrorState message="Could not load your questions." onRetry={() => void questions.refetch()} />}

      {questions.isSuccess && questions.data.totalElements === 0 && !noJobs && (
        hasFilters ? (
          <EmptyState title="No matching questions" description="Try different filters."
            action={<button type="button" className="text-sm font-medium underline"
              onClick={() => update({ category: null, status: null })}>Clear filters</button>} />
        ) : (
          <EmptyState title="No questions yet"
            description={jobId ? 'Generate a set of questions for this job, or add your own.' : 'Choose a job, then generate a set of questions for it.'} />
        )
      )}

      {questions.isSuccess && questions.data.totalElements > 0 && (
        <div className={questions.isPlaceholderData ? 'opacity-60 transition-opacity' : ''}>
          <ul className="space-y-3" aria-label="Interview questions">
            {questions.data.content.map((q) => <QuestionCard key={q.id} question={q} showJob={!jobId} />)}
          </ul>
          <div className="mt-3">
            <Pagination page={questions.data.page} totalPages={questions.data.totalPages}
              totalElements={questions.data.totalElements} onPageChange={(p) => update({ page: p === 0 ? null : String(p) }, false)} />
          </div>
        </div>
      )}

      {!noJobs && jobId && (
        <form onSubmit={submitCustom} aria-label="Add your own question" className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
          <h2 className="text-sm font-semibold">Add your own question</h2>
          <div>
            <label htmlFor="custom-question" className="block text-sm font-medium text-slate-700">Question</label>
            <input id="custom-question" value={custom} maxLength={500} onChange={(e) => setCustom(e.target.value)}
              className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" />
          </div>
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label htmlFor="custom-category" className="block text-sm font-medium text-slate-700">Type</label>
              <select id="custom-category" value={customCategory} onChange={(e) => setCustomCategory(e.target.value as InterviewCategory)} className={selectClass}>
                {CATEGORIES.map((c) => <option key={c} value={c}>{label(c)}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="custom-difficulty" className="block text-sm font-medium text-slate-700">Difficulty</label>
              <select id="custom-difficulty" value={customDifficulty} onChange={(e) => setCustomDifficulty(e.target.value as Difficulty)} className={selectClass}>
                {DIFFICULTIES.map((d) => <option key={d} value={d}>{label(d)}</option>)}
              </select>
            </div>
            <Button type="submit" disabled={add.isPending || !custom.trim()}>Add question</Button>
          </div>
        </form>
      )}
    </div>
  )
}
