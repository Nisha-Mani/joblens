import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../components/layout/PageHeader'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Pagination } from '../components/ui/Pagination'
import { Spinner } from '../components/ui/Spinner'
import { EMPLOYMENT_TYPES, employmentLabel, searchJobs, type EmploymentType, type JobSearchParams, type JobSortField } from '../features/jobs/api'
import { useDebouncedValue } from '../hooks/useDebouncedValue'

const SORT_OPTIONS: { value: string; label: string; sortBy: JobSortField; direction: 'asc' | 'desc' }[] = [
  { value: 'createdAt-desc', label: 'Newest first', sortBy: 'createdAt', direction: 'desc' },
  { value: 'createdAt-asc', label: 'Oldest first', sortBy: 'createdAt', direction: 'asc' },
  { value: 'company-asc', label: 'Company A–Z', sortBy: 'company', direction: 'asc' },
  { value: 'title-asc', label: 'Title A–Z', sortBy: 'title', direction: 'asc' },
]

const selectClass = 'mt-1 block rounded-md border border-slate-300 bg-white px-3 py-2 text-sm'

export default function JobsPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  // The URL is the source of truth so filters survive reloads, back/forward and sharing.
  const q = searchParams.get('q') ?? ''
  const employmentType = (searchParams.get('type') ?? '') as EmploymentType | ''
  const sort = searchParams.get('sort') ?? 'createdAt-desc'
  const page = Math.max(Number(searchParams.get('page') ?? '0') || 0, 0)
  const sortOption = SORT_OPTIONS.find((o) => o.value === sort) ?? SORT_OPTIONS[0]

  const [searchText, setSearchText] = useState(q)
  const debouncedSearch = useDebouncedValue(searchText)

  function update(changes: Record<string, string | null>, resetPage = true) {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
      if (resetPage) next.delete('page')
      return next
    }, { replace: true })
  }

  useEffect(() => {
    if (debouncedSearch !== q) update({ q: debouncedSearch.trim() || null })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const params: JobSearchParams = { q, employmentType, sortBy: sortOption.sortBy, direction: sortOption.direction, page }
  const jobs = useQuery({
    queryKey: ['jobs', params],
    queryFn: () => searchJobs(params),
    placeholderData: keepPreviousData,
  })

  const hasFilters = q !== '' || employmentType !== ''

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <PageHeader title="Jobs" description="Roles you are considering or have applied to." />
        <Link to="/jobs/new" className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">
          Add job
        </Link>
      </div>

      <form role="search" onSubmit={(e) => e.preventDefault()} className="flex flex-wrap items-end gap-3">
        <div className="min-w-56 flex-1">
          <label htmlFor="job-search" className="block text-sm font-medium text-slate-700">Search</label>
          <input id="job-search" type="search" value={searchText} onChange={(e) => setSearchText(e.target.value)}
            placeholder="Company, title or location"
            className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" />
        </div>
        <div>
          <label htmlFor="job-type" className="block text-sm font-medium text-slate-700">Type</label>
          <select id="job-type" value={employmentType} onChange={(e) => update({ type: e.target.value || null })} className={selectClass}>
            <option value="">All types</option>
            {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{employmentLabel(t)}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="job-sort" className="block text-sm font-medium text-slate-700">Sort by</label>
          <select id="job-sort" value={sortOption.value} onChange={(e) => update({ sort: e.target.value })} className={selectClass}>
            {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
      </form>

      {jobs.isPending && <Spinner label="Loading jobs…" />}
      {jobs.isError && <ErrorState message="Could not load your jobs." onRetry={() => void jobs.refetch()} />}

      {jobs.isSuccess && jobs.data.totalElements === 0 && (
        hasFilters ? (
          <EmptyState title="No matching jobs" description="Try a different search or clear the filters."
            action={<button type="button" className="text-sm font-medium underline"
              onClick={() => { setSearchText(''); setSearchParams({}, { replace: true }) }}>Clear filters</button>} />
        ) : (
          <EmptyState title="No jobs yet" description="Save a job and paste its description to see how your resume matches."
            action={<Link to="/jobs/new" className="text-sm font-medium underline">Add your first job</Link>} />
        )
      )}

      {jobs.isSuccess && jobs.data.totalElements > 0 && (
        <div className={jobs.isPlaceholderData ? 'opacity-60 transition-opacity' : ''}>
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Saved jobs</caption>
              <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
                <tr>
                  <th scope="col" className="px-4 py-2 font-medium">Company</th>
                  <th scope="col" className="px-4 py-2 font-medium">Title</th>
                  <th scope="col" className="px-4 py-2 font-medium">Location</th>
                  <th scope="col" className="px-4 py-2 font-medium">Type</th>
                  <th scope="col" className="px-4 py-2 font-medium">Added</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {jobs.data.content.map((job) => (
                  <tr key={job.id}>
                    <td className="px-4 py-2 font-medium">{job.company}</td>
                    <td className="px-4 py-2"><Link className="underline" to={`/jobs/${job.id}`}>{job.title}</Link></td>
                    <td className="px-4 py-2 text-slate-600">{job.location ?? '—'}</td>
                    <td className="px-4 py-2 text-slate-600">{employmentLabel(job.employmentType)}</td>
                    <td className="px-4 py-2 text-slate-600">{new Date(job.createdAt).toLocaleDateString(undefined, { dateStyle: 'medium' })}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-3">
            <Pagination page={jobs.data.page} totalPages={jobs.data.totalPages} totalElements={jobs.data.totalElements}
              onPageChange={(p) => update({ page: p === 0 ? null : String(p) }, false)} />
          </div>
        </div>
      )}
    </div>
  )
}
