import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from '../../test/utils'
import type { Job } from '../jobs/api'
import type { ApplicationDetail, ApplicationInput, ApplicationStatus } from './api'

let apps: ApplicationDetail[]
let jobs: Job[]
let calls: { method: string; url: string; body?: unknown }[]

const makeJob = (n: number): Job => ({
  id: `j${n}`, company: `Company ${n}`, title: `Engineer ${n}`, location: 'Remote', employmentType: 'FULL_TIME',
  createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', jobDescription: 'desc', sourceUrl: null,
})

const makeApp = (n: number, status: ApplicationStatus = 'APPLIED', overrides: Partial<ApplicationDetail> = {}): ApplicationDetail => ({
  id: `a${n}`, jobId: `j${n}`, company: `Company ${n}`, title: `Engineer ${n}`, location: 'Remote', status,
  appliedAt: '2026-09-01', interviewDate: null, notes: null, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-02T00:00:00Z',
  history: [{ from: null, to: status, changedAt: '2026-09-01T00:00:00Z' }], ...overrides,
})

function listResponse(url: URL) {
  const status = url.searchParams.get('status')
  const q = url.searchParams.get('q')?.toLowerCase()
  const jobId = url.searchParams.get('jobId')
  const size = Number(url.searchParams.get('size'))
  const page = Number(url.searchParams.get('page'))
  const filtered = apps.filter((a) => (!status || a.status === status) && (!jobId || a.jobId === jobId)
    && (!q || `${a.company} ${a.title}`.toLowerCase().includes(q)))
  return jsonResponse({
    content: filtered.slice(page * size, page * size + size), page, size,
    totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size),
  })
}

function mockApi(options: { createConflict?: boolean } = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input), 'http://localhost')
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ method, url: url.pathname + url.search, body })

    if (url.pathname === '/api/jobs' && method === 'GET') {
      return jsonResponse({ content: jobs, page: 0, size: 100, totalElements: jobs.length, totalPages: 1 })
    }
    const jobMatch = url.pathname.match(/^\/api\/jobs\/(j\d+)$/)
    if (jobMatch && method === 'GET') return jsonResponse(jobs.find((j) => j.id === jobMatch[1]))

    if (url.pathname === '/api/applications' && method === 'GET') return listResponse(url)
    if (url.pathname === '/api/applications' && method === 'POST') {
      if (options.createConflict) return jsonResponse({ status: 409, detail: 'This job already has an application' }, 409)
      const input = body as ApplicationInput
      const job = jobs.find((j) => j.id === input.jobId)!
      const created = makeApp(Number(job.id.slice(1)), input.status, { id: `a${apps.length + 1}`, notes: input.notes })
      apps = [...apps, created]
      return jsonResponse(created, 201)
    }
    const match = url.pathname.match(/^\/api\/applications\/(a\d+)(\/status)?$/)
    if (match) {
      const app = apps.find((a) => a.id === match[1])
      if (!app) return jsonResponse({ status: 404, detail: 'Application not found' }, 404)
      if (method === 'GET') return jsonResponse(app)
      if (method === 'PATCH') {
        const next = (body as { status: ApplicationStatus }).status
        const updated = { ...app, status: next, history: [...app.history, { from: app.status, to: next, changedAt: '2026-09-03T00:00:00Z' }] }
        apps = apps.map((a) => (a.id === app.id ? updated : a))
        return jsonResponse(updated)
      }
      if (method === 'PUT') {
        const input = body as ApplicationInput
        const updated = { ...app, ...input } as ApplicationDetail
        apps = apps.map((a) => (a.id === app.id ? updated : a))
        return jsonResponse(updated)
      }
      if (method === 'DELETE') {
        apps = apps.filter((a) => a.id !== app.id)
        return new Response(null, { status: 204 })
      }
    }
    return jsonResponse({ status: 404 }, 404)
  })
}

const lastListUrl = () => [...calls].reverse().find((c) => c.method === 'GET' && c.url.startsWith('/api/applications?'))?.url ?? ''

describe('applications', () => {
  beforeEach(() => {
    storeSession(makeSession())
    apps = []
    jobs = []
    calls = []
  })
  afterEach(() => vi.restoreAllMocks())

  describe('list', () => {
    it('shows an empty state for new users', async () => {
      mockApi()
      renderApp('/applications')
      expect(await screen.findByText('No applications yet')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Track your first application' })).toHaveAttribute('href', '/applications/new')
    })

    it('renders applications with status badges and formatted dates', async () => {
      apps = [makeApp(1, 'INTERVIEW', { interviewDate: '2026-10-15T14:00:00Z' }), makeApp(2, 'OFFER')]
      mockApi()
      renderApp('/applications')
      const table = await screen.findByRole('table', { name: 'Applications' })
      expect(within(table).getByRole('link', { name: 'Engineer 1' })).toHaveAttribute('href', '/applications/a1')
      expect(within(table).getAllByText('Interview').length).toBeGreaterThan(0)
      expect(within(table).getAllByText('Offer').length).toBeGreaterThan(0)
    })

    it('filters by status and sorts on the server', async () => {
      apps = [makeApp(1, 'APPLIED'), makeApp(2, 'OFFER')]
      mockApi()
      renderApp('/applications')
      await screen.findByRole('table')
      await userEvent.selectOptions(screen.getByLabelText('Status'), 'OFFER')
      await waitFor(() => expect(lastListUrl()).toContain('status=OFFER'))
      await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'interviewDate-asc')
      await waitFor(() => expect(lastListUrl()).toContain('sortBy=interviewDate&direction=asc'))
    })

    it('searches with debounce', async () => {
      apps = [makeApp(1), makeApp(2)]
      mockApi()
      renderApp('/applications')
      await screen.findByRole('table')
      await userEvent.type(screen.getByLabelText('Search'), 'Company 2')
      await waitFor(() => expect(lastListUrl()).toContain('q=Company+2'))
      expect(await screen.findByText('Engineer 2')).toBeInTheDocument()
      expect(screen.queryByText('Engineer 1')).not.toBeInTheDocument()
    })

    it('paginates', async () => {
      apps = Array.from({ length: 12 }, (_, i) => makeApp(i + 1))
      mockApi()
      renderApp('/applications')
      await screen.findByText('Page 1 of 2 · 12 total')
      await userEvent.click(screen.getByRole('button', { name: 'Next' }))
      expect(await screen.findByText('Page 2 of 2 · 12 total')).toBeInTheDocument()
    })

    it('changes status inline', async () => {
      apps = [makeApp(1, 'APPLIED')]
      mockApi()
      renderApp('/applications')
      await userEvent.selectOptions(await screen.findByLabelText('Change status for Engineer 1 at Company 1'), 'INTERVIEW')
      await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({ status: 'INTERVIEW' }))
      await waitFor(() => expect(screen.getByLabelText('Change status for Engineer 1 at Company 1')).toHaveValue('INTERVIEW'))
    })

    it('shows a no-matches state for filtered empty results', async () => {
      apps = [makeApp(1)]
      mockApi()
      renderApp('/applications?status=OFFER')
      expect(await screen.findByText('No matching applications')).toBeInTheDocument()
    })
  })

  describe('create', () => {
    it('explains when there are no jobs to track', async () => {
      mockApi()
      renderApp('/applications/new')
      expect(await screen.findByText(/You have no saved jobs yet/)).toBeInTheDocument()
    })

    it('requires choosing a job', async () => {
      jobs = [makeJob(1)]
      mockApi()
      renderApp('/applications/new')
      await userEvent.click(await screen.findByRole('button', { name: 'Save application' }))
      expect(await screen.findByRole('alert')).toHaveTextContent('Choose a job to track.')
      expect(calls.some((c) => c.method === 'POST')).toBe(false)
    })

    it('creates an application and opens its detail page', async () => {
      jobs = [makeJob(1)]
      mockApi()
      renderApp('/applications/new')
      await screen.findByRole('option', { name: 'Company 1 — Engineer 1' })
      await userEvent.selectOptions(screen.getByLabelText('Job'), 'j1')
      await userEvent.selectOptions(screen.getByLabelText('Status'), 'APPLIED')
      await userEvent.type(screen.getByLabelText('Notes'), 'Referred by Sam')
      await userEvent.click(screen.getByRole('button', { name: 'Save application' }))
      expect(await screen.findByRole('heading', { name: 'Engineer 1' })).toBeInTheDocument()
      expect(calls.find((c) => c.method === 'POST')?.body).toMatchObject({ jobId: 'j1', status: 'APPLIED', notes: 'Referred by Sam' })
    })

    it('preselects the job from the query string', async () => {
      jobs = [makeJob(1), makeJob(2)]
      mockApi()
      renderApp('/applications/new?jobId=j2')
      await screen.findByRole('option', { name: 'Company 2 — Engineer 2' })
      expect(screen.getByLabelText('Job')).toHaveValue('j2')
    })

    it('shows the server message when the job is already tracked', async () => {
      jobs = [makeJob(1)]
      mockApi({ createConflict: true })
      renderApp('/applications/new?jobId=j1')
      await screen.findByRole('option', { name: 'Company 1 — Engineer 1' })
      await userEvent.click(screen.getByRole('button', { name: 'Save application' }))
      expect(await screen.findByRole('alert')).toHaveTextContent('This job already has an application')
    })
  })

  describe('detail', () => {
    it('shows details, notes and status history newest first', async () => {
      apps = [makeApp(1, 'REJECTED', {
        notes: 'Great chat',
        history: [
          { from: null, to: 'APPLIED', changedAt: '2026-09-01T00:00:00Z' },
          { from: 'APPLIED', to: 'REJECTED', changedAt: '2026-09-10T00:00:00Z' },
        ],
      })]
      mockApi()
      renderApp('/applications/a1')
      expect(await screen.findByRole('heading', { name: 'Engineer 1' })).toBeInTheDocument()
      expect(screen.getByText('Great chat')).toBeInTheDocument()
      const items = within(screen.getByRole('list')).getAllByRole('listitem')
      expect(items[0]).toHaveTextContent('Applied → Rejected')
      expect(items[1]).toHaveTextContent('Created as Applied')
    })

    it('shows not-found for a missing application', async () => {
      mockApi()
      renderApp('/applications/a99')
      expect(await screen.findByText('Application not found')).toBeInTheDocument()
    })

    it('edits status, interview date and notes', async () => {
      apps = [makeApp(1, 'APPLIED')]
      mockApi()
      renderApp('/applications/a1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
      await userEvent.selectOptions(screen.getByLabelText('Status'), 'INTERVIEW')
      await userEvent.type(screen.getByLabelText('Interview'), '2026-10-15T14:30')
      await userEvent.type(screen.getByLabelText('Notes'), 'Onsite loop')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await waitFor(() => expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument())
      const put = calls.find((c) => c.method === 'PUT')?.body as ApplicationInput
      expect(put.status).toBe('INTERVIEW')
      expect(put.notes).toBe('Onsite loop')
      expect(put.interviewDate).toMatch(/^2026-10-15T/)
    })

    it('deletes after confirmation', async () => {
      apps = [makeApp(1)]
      mockApi()
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      renderApp('/applications/a1')
      await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
      expect(await screen.findByText('No applications yet')).toBeInTheDocument()
    })
  })

  describe('job page integration', () => {
    it('offers to track a job without an application', async () => {
      jobs = [makeJob(1)]
      mockApi()
      renderApp('/jobs/j1')
      expect(await screen.findByRole('link', { name: 'Track this application' })).toHaveAttribute('href', '/applications/new?jobId=j1')
    })

    it('links to the existing application with its status', async () => {
      jobs = [makeJob(1)]
      apps = [makeApp(1, 'SCREENING')]
      mockApi()
      renderApp('/jobs/j1')
      expect(await screen.findByRole('link', { name: 'View application' })).toHaveAttribute('href', '/applications/a1')
      expect(screen.getByText('Screening')).toBeInTheDocument()
    })
  })
})
