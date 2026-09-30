import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../components/layout/PageHeader'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { Pagination } from '../components/ui/Pagination'
import { Spinner } from '../components/ui/Spinner'
import { StatusBadge } from '../components/ui/StatusBadge'
import {
  STATUSES, changeApplicationStatus, searchApplications, statusLabel,
  type ApplicationSearchParams, type ApplicationSortField, type ApplicationStatus,
} from '../features/applications/api'
import { formatDate, formatDateTime } from '../features/applications/dates'
import { useDebouncedValue } from '../hooks/useDebouncedValue'

const SORT_OPTIONS: { value: string; label: string; sortBy: ApplicationSortField; direction: 'asc' | 'desc' }[] = [
  { value: 'updatedAt-desc', label: 'Recently updated', sortBy: 'updatedAt', direction: 'desc' },
  { value: 'appliedAt-desc', label: 'Applied date (newest)', sortBy: 'appliedAt', direction: 'desc' },
  { value: 'interviewDate-asc', label: 'Interview date (soonest)', sortBy: 'interviewDate', direction: 'asc' },
  { value: 'company-asc', label: 'Company A–Z', sortBy: 'company', direction: 'asc' },
  { value: 'status-asc', label: 'Status', sortBy: 'status', direction: 'asc' },
]

const selectClass = 'mt-1 block rounded-md border border-slate-300 bg-white px-3 py-2 text-sm'

export default function ApplicationsPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  const q = searchParams.get('q') ?? ''
  const status = (searchParams.get('status') ?? '') as ApplicationStatus | ''
  const sort = searchParams.get('sort') ?? 'updatedAt-desc'
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

  const params: ApplicationSearchParams = { q, status, sortBy: sortOption.sortBy, direction: sortOption.direction, page }
  const applications = useQuery({
    queryKey: ['applications', params],
    queryFn: () => searchApplications(params),
    placeholderData: keepPreviousData,
  })

  const changeStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ApplicationStatus }) => changeApplicationStatus(id, status),
    onSuccess: (saved) => {
      queryClient.setQueryData(['application', saved.id], saved)
      return queryClient.invalidateQueries({ queryKey: ['applications'] })
    },
  })

  const hasFilters = q !== '' || status !== ''

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <PageHeader title="Applications" description="Every role you are pursuing, and where it stands." />
        <Link to="/applications/new" className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">
          Add application
        </Link>
      </div>

      <form role="search" onSubmit={(e) => e.preventDefault()} className="flex flex-wrap items-end gap-3">
        <div className="min-w-56 flex-1">
          <label htmlFor="app-search" className="block text-sm font-medium text-slate-700">Search</label>
          <input id="app-search" type="search" value={searchText} onChange={(e) => setSearchText(e.target.value)}
            placeholder="Company or role" className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" />
        </div>
        <div>
          <label htmlFor="app-filter-status" className="block text-sm font-medium text-slate-700">Status</label>
          <select id="app-filter-status" value={status} onChange={(e) => update({ status: e.target.value || null })} className={selectClass}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="app-sort" className="block text-sm font-medium text-slate-700">Sort by</label>
          <select id="app-sort" value={sortOption.value} onChange={(e) => update({ sort: e.target.value })} className={selectClass}>
            {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
      </form>

      {changeStatus.isError && <p role="alert" className="text-sm text-red-700">Could not update the status. Try again.</p>}
      {applications.isPending && <Spinner label="Loading applications…" />}
      {applications.isError && <ErrorState message="Could not load your applications." onRetry={() => void applications.refetch()} />}

      {applications.isSuccess && applications.data.totalElements === 0 && (
        hasFilters ? (
          <EmptyState title="No matching applications" description="Try a different search or clear the filters."
            action={<button type="button" className="text-sm font-medium underline"
              onClick={() => { setSearchText(''); setSearchParams({}, { replace: true }) }}>Clear filters</button>} />
        ) : (
          <EmptyState title="No applications yet" description="Track a job to follow its status, interviews and notes."
            action={<Link to="/applications/new" className="text-sm font-medium underline">Track your first application</Link>} />
        )
      )}

      {applications.isSuccess && applications.data.totalElements > 0 && (
        <div className={applications.isPlaceholderData ? 'opacity-60 transition-opacity' : ''}>
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Applications</caption>
              <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
                <tr>
                  <th scope="col" className="px-4 py-2 font-medium">Company</th>
                  <th scope="col" className="px-4 py-2 font-medium">Role</th>
                  <th scope="col" className="px-4 py-2 font-medium">Status</th>
                  <th scope="col" className="px-4 py-2 font-medium">Applied</th>
                  <th scope="col" className="px-4 py-2 font-medium">Interview</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {applications.data.content.map((a) => (
                  <tr key={a.id}>
                    <td className="px-4 py-2 font-medium">{a.company}</td>
                    <td className="px-4 py-2"><Link className="underline" to={`/applications/${a.id}`}>{a.title}</Link></td>
                    <td className="px-4 py-2">
                      <div className="flex items-center gap-2">
                        <StatusBadge status={a.status} />
                        <select aria-label={`Change status for ${a.title} at ${a.company}`} value={a.status}
                          disabled={changeStatus.isPending}
                          onChange={(e) => changeStatus.mutate({ id: a.id, status: e.target.value as ApplicationStatus })}
                          className="rounded-md border border-slate-300 bg-white px-1.5 py-1 text-xs">
                          {STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
                        </select>
                      </div>
                    </td>
                    <td className="px-4 py-2 text-slate-600">{formatDate(a.appliedAt)}</td>
                    <td className="px-4 py-2 text-slate-600">{formatDateTime(a.interviewDate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-3">
            <Pagination page={applications.data.page} totalPages={applications.data.totalPages}
              totalElements={applications.data.totalElements}
              onPageChange={(p) => update({ page: p === 0 ? null : String(p) }, false)} />
          </div>
        </div>
      )}
    </div>
  )
}
