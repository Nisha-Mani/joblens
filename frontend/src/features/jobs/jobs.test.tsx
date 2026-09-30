import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from '../../test/utils'
import type { Job, JobInput } from './api'

let jobs: Job[]
let calls: { method: string; url: string; body?: unknown }[]

const makeJob = (n: number, overrides: Partial<Job> = {}): Job => ({
  id: `j${n}`, company: `Company ${n}`, title: `Engineer ${n}`, location: 'Remote', employmentType: 'FULL_TIME',
  createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', jobDescription: `Description ${n}`,
  sourceUrl: null, ...overrides,
})

function listResponse(url: URL) {
  const q = url.searchParams.get('q')?.toLowerCase()
  const type = url.searchParams.get('employmentType')
  const size = Number(url.searchParams.get('size'))
  const page = Number(url.searchParams.get('page'))
  let filtered = jobs.filter((j) => (!type || j.employmentType === type)
    && (!q || `${j.company} ${j.title} ${j.location}`.toLowerCase().includes(q)))
  const sortBy = url.searchParams.get('sortBy') as 'company' | 'title' | 'createdAt'
  const dir = url.searchParams.get('direction') === 'asc' ? 1 : -1
  filtered = [...filtered].sort((a, b) => a[sortBy].localeCompare(b[sortBy]) * dir)
  return jsonResponse({
    content: filtered.slice(page * size, page * size + size), page, size,
    totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size),
  })
}

function mockApi(options: { failList?: boolean; createError?: { status: number; errors: Record<string, string> } } = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input), 'http://localhost')
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ method, url: url.pathname + url.search, body })

    if (url.pathname === '/api/jobs' && method === 'GET') {
      return options.failList ? jsonResponse({ status: 500 }, 500) : listResponse(url)
    }
    if (url.pathname === '/api/jobs' && method === 'POST') {
      if (options.createError) return jsonResponse({ status: 400, detail: 'Validation failed', errors: options.createError.errors }, 400)
      const created = makeJob(jobs.length + 1, body as Partial<Job>)
      jobs = [...jobs, created]
      return jsonResponse(created, 201)
    }
    const match = url.pathname.match(/^\/api\/jobs\/(j\d+)$/)
    if (match) {
      const job = jobs.find((j) => j.id === match[1])
      if (!job) return jsonResponse({ status: 404, detail: 'Job not found' }, 404)
      if (method === 'GET') return jsonResponse(job)
      if (method === 'PUT') {
        const updated = { ...job, ...(body as JobInput) }
        jobs = jobs.map((j) => (j.id === job.id ? updated : j))
        return jsonResponse(updated)
      }
      if (method === 'DELETE') {
        jobs = jobs.filter((j) => j.id !== job.id)
        return new Response(null, { status: 204 })
      }
    }
    return jsonResponse({ status: 404 }, 404)
  })
}

const lastListUrl = () => [...calls].reverse().find((c) => c.method === 'GET' && c.url.startsWith('/api/jobs?'))?.url ?? ''

describe('jobs', () => {
  beforeEach(() => {
    storeSession(makeSession())
    jobs = []
    calls = []
  })
  afterEach(() => vi.restoreAllMocks())

  describe('list', () => {
    it('shows an empty state with a call to action for new users', async () => {
      mockApi()
      renderApp('/jobs')
      expect(await screen.findByText('No jobs yet')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Add your first job' })).toHaveAttribute('href', '/jobs/new')
    })

    it('renders jobs in an accessible table', async () => {
      jobs = [makeJob(1), makeJob(2)]
      mockApi()
      renderApp('/jobs')
      const table = await screen.findByRole('table', { name: 'Saved jobs' })
      expect(within(table).getByRole('link', { name: 'Engineer 1' })).toHaveAttribute('href', '/jobs/j1')
      expect(within(table).getAllByRole('row')).toHaveLength(3)
    })

    it('shows a retryable error when loading fails', async () => {
      mockApi({ failList: true })
      renderApp('/jobs')
      expect(await screen.findByText('Could not load your jobs.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    })

    it('debounces search and sends it to the server', async () => {
      jobs = [makeJob(1, { company: 'Globex' }), makeJob(2, { company: 'Initech' })]
      mockApi()
      renderApp('/jobs')
      await screen.findByRole('table')
      await userEvent.type(screen.getByLabelText('Search'), 'globex')

      await waitFor(() => expect(lastListUrl()).toContain('q=globex'))
      expect(await screen.findByText('Globex')).toBeInTheDocument()
      expect(screen.queryByText('Initech')).not.toBeInTheDocument()
      // Typing six characters must not have produced six requests.
      expect(calls.filter((c) => c.url.includes('q=')).length).toBeLessThan(3)
    })

    it('shows a no-matches state with a way to clear filters', async () => {
      jobs = [makeJob(1)]
      mockApi()
      renderApp('/jobs')
      await screen.findByRole('table')
      await userEvent.type(screen.getByLabelText('Search'), 'zzz')
      expect(await screen.findByText('No matching jobs')).toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
      expect(await screen.findByRole('table')).toBeInTheDocument()
    })

    it('filters by employment type', async () => {
      jobs = [makeJob(1, { employmentType: 'CONTRACT' }), makeJob(2)]
      mockApi()
      renderApp('/jobs')
      await screen.findByRole('table')
      await userEvent.selectOptions(screen.getByLabelText('Type'), 'CONTRACT')
      await waitFor(() => expect(lastListUrl()).toContain('employmentType=CONTRACT'))
    })

    it('sorts on the server', async () => {
      jobs = [makeJob(1), makeJob(2)]
      mockApi()
      renderApp('/jobs')
      await screen.findByRole('table')
      await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'company-asc')
      await waitFor(() => expect(lastListUrl()).toContain('sortBy=company&direction=asc'))
    })

    it('paginates and resets to the first page when filters change', async () => {
      jobs = Array.from({ length: 12 }, (_, i) => makeJob(i + 1))
      mockApi()
      renderApp('/jobs')
      await screen.findByText('Page 1 of 2 · 12 total')
      await userEvent.click(screen.getByRole('button', { name: 'Next' }))
      expect(await screen.findByText('Page 2 of 2 · 12 total')).toBeInTheDocument()
      expect(lastListUrl()).toContain('page=1')

      await userEvent.selectOptions(screen.getByLabelText('Type'), 'FULL_TIME')
      await waitFor(() => expect(lastListUrl()).toContain('page=0'))
    })

    it('reads initial state from the URL', async () => {
      jobs = [makeJob(1)]
      mockApi()
      renderApp('/jobs?q=engineer&type=FULL_TIME&sort=title-asc')
      await screen.findByRole('table')
      expect(screen.getByLabelText('Search')).toHaveValue('engineer')
      expect(screen.getByLabelText('Type')).toHaveValue('FULL_TIME')
      expect(screen.getByLabelText('Sort by')).toHaveValue('title-asc')
      expect(lastListUrl()).toContain('sortBy=title&direction=asc')
    })
  })

  describe('create', () => {
    async function fillAndSubmit(description = 'Build things') {
      await userEvent.type(await screen.findByLabelText('Company'), 'Acme')
      await userEvent.type(screen.getByLabelText('Job title'), 'Backend Engineer')
      await userEvent.type(screen.getByLabelText('Job description'), description)
      await userEvent.click(screen.getByRole('button', { name: 'Save job' }))
    }

    it('validates required fields before calling the API', async () => {
      mockApi()
      renderApp('/jobs/new')
      await userEvent.click(await screen.findByRole('button', { name: 'Save job' }))
      expect(await screen.findByText('Company is required')).toBeInTheDocument()
      expect(screen.getByText('Title is required')).toBeInTheDocument()
      expect(screen.getByText('Job description is required')).toBeInTheDocument()
      expect(calls.some((c) => c.method === 'POST')).toBe(false)
    })

    it('rejects non-http source URLs', async () => {
      mockApi()
      renderApp('/jobs/new')
      await userEvent.type(await screen.findByLabelText('Source URL (optional)'), 'javascript:alert(1)')
      await userEvent.click(screen.getByRole('button', { name: 'Save job' }))
      expect(await screen.findByText('Source URL must start with http:// or https://')).toBeInTheDocument()
    })

    it('creates the job and opens its detail page', async () => {
      mockApi()
      renderApp('/jobs/new')
      await fillAndSubmit()
      expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument()
      const post = calls.find((c) => c.method === 'POST')
      expect(post?.body).toMatchObject({ company: 'Acme', location: null, sourceUrl: null, employmentType: 'FULL_TIME' })
    })

    it('maps server field errors onto the form', async () => {
      mockApi({ createError: { status: 400, errors: { company: 'Company is required by server' } } })
      renderApp('/jobs/new')
      await fillAndSubmit()
      expect(await screen.findByText('Company is required by server')).toBeInTheDocument()
    })
  })

  describe('detail', () => {
    it('shows the job description and source link', async () => {
      jobs = [makeJob(1, { sourceUrl: 'https://example.com/job' })]
      mockApi()
      renderApp('/jobs/j1')
      expect(await screen.findByRole('heading', { name: 'Engineer 1' })).toBeInTheDocument()
      expect(screen.getByText('Description 1')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'https://example.com/job' })).toHaveAttribute('rel', 'noopener noreferrer')
    })

    it('shows a not-found message for a missing job', async () => {
      mockApi()
      renderApp('/jobs/j99')
      expect(await screen.findByText('Job not found')).toBeInTheDocument()
    })

    it('edits a job', async () => {
      jobs = [makeJob(1)]
      mockApi()
      renderApp('/jobs/j1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
      const title = screen.getByLabelText('Job title')
      await userEvent.clear(title)
      await userEvent.type(title, 'Staff Engineer')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      expect(await screen.findByRole('heading', { name: 'Staff Engineer' })).toBeInTheDocument()
      expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({ title: 'Staff Engineer' })
    })

    it('deletes a job after confirmation and returns to the list', async () => {
      jobs = [makeJob(1)]
      mockApi()
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      renderApp('/jobs/j1')
      await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
      expect(await screen.findByText('No jobs yet')).toBeInTheDocument()
      expect(jobs).toHaveLength(0)
    })

    it('keeps the job when deletion is declined', async () => {
      jobs = [makeJob(1)]
      mockApi()
      vi.spyOn(window, 'confirm').mockReturnValue(false)
      renderApp('/jobs/j1')
      await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
      expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    })
  })
})
