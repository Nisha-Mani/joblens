import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from '../../test/utils'
import { formatRate, monthLabel, type Dashboard } from './api'

const STATUSES = ['SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'] as const

function dashboard(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    totals: { tracked: 6, applied: 5, appliedThisMonth: 2, responded: 3, interviews: 2, offers: 1, rejections: 1 },
    rates: { responseRate: 0.6, interviewRate: 0.4, offerRate: 0.2 },
    statusDistribution: STATUSES.map((status) => ({ status, count: status === 'APPLIED' ? 3 : status === 'OFFER' ? 1 : 0 })),
    applicationsByMonth: [{ month: '2026-07', count: 0 }, { month: '2026-08', count: 3 }, { month: '2026-09', count: 2 }],
    upcomingInterviews: [{ applicationId: 'a1', company: 'Globex', title: 'Platform Engineer', status: 'INTERVIEW', interviewDate: '2030-05-20T10:30:00Z' }],
    recentApplications: [{ applicationId: 'a2', company: 'Initech', title: 'Frontend Dev', status: 'APPLIED', updatedAt: '2026-09-20T10:00:00Z' }],
    topMissingSkills: [{ skill: 'Docker', count: 2 }, { skill: 'Kubernetes', count: 1 }],
    ...overrides,
  }
}

let requested: string[]

function mockDashboard(data: Dashboard | 'error') {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    requested.push(String(input))
    if (data === 'error') return jsonResponse({ status: 500 }, 500)
    return jsonResponse(data)
  })
}

describe('formatting helpers', () => {
  it('formats rates as whole percentages and null as a dash', () => {
    expect(formatRate(0.756)).toBe('76%')
    expect(formatRate(0)).toBe('0%')
    expect(formatRate(null)).toBe('—')
  })
  it('labels months in UTC', () => expect(monthLabel('2026-01')).toBe('Jan'))
})

describe('dashboard', () => {
  beforeEach(() => {
    storeSession(makeSession())
    requested = []
  })
  afterEach(() => vi.restoreAllMocks())

  it('shows a helpful empty state for a new user instead of empty charts', async () => {
    mockDashboard(dashboard({ totals: { tracked: 0, applied: 0, appliedThisMonth: 0, responded: 0, interviews: 0, offers: 0, rejections: 0 } }))
    renderApp('/dashboard')
    expect(await screen.findByText('Your dashboard is waiting for data')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Add a job' })).toHaveAttribute('href', '/jobs/new')
    expect(screen.queryByText('Applications by status')).not.toBeInTheDocument()
  })

  it('renders the server-provided numbers without recomputing them', async () => {
    mockDashboard(dashboard())
    renderApp('/dashboard')
    const tiles = await screen.findByText('Response rate')
    const tile = tiles.closest('div') as HTMLElement
    expect(within(tile).getByText('60%')).toBeInTheDocument()
    expect(screen.getByText('Applied this month').closest('div')).toHaveTextContent('2')
    expect(screen.getByText('Interviews').closest('div')).toHaveTextContent('2')
    expect(screen.getByText('5', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('6 tracked')).toBeInTheDocument()
    expect(requested[0]).toContain('/api/analytics/dashboard?months=12')
  })

  it('lists upcoming interviews and recent applications with links', async () => {
    mockDashboard(dashboard())
    renderApp('/dashboard')
    expect(await screen.findByRole('link', { name: 'Platform Engineer · Globex' })).toHaveAttribute('href', '/applications/a1')
    expect(screen.getByRole('link', { name: 'Frontend Dev · Initech' })).toHaveAttribute('href', '/applications/a2')
  })

  it('explains when there are no upcoming interviews', async () => {
    mockDashboard(dashboard({ upcomingInterviews: [] }))
    renderApp('/dashboard')
    expect(await screen.findByText(/No interviews scheduled/)).toBeInTheDocument()
  })

  it('shows top missing skills, or a prompt to analyze a job when there are none', async () => {
    mockDashboard(dashboard())
    const { unmount } = renderApp('/dashboard')
    const card = (await screen.findByRole('region', { name: 'Top missing skills' }))
    expect(within(card).getAllByText('Docker').length).toBeGreaterThan(0)
    unmount()
    vi.restoreAllMocks()
    mockDashboard(dashboard({ topMissingSkills: [] }))
    renderApp('/dashboard')
    expect(await screen.findByText(/No skill gaps yet/)).toBeInTheDocument()
  })

  it('offers every chart as a table so nothing depends on seeing the graphic', async () => {
    mockDashboard(dashboard())
    renderApp('/dashboard')
    const card = await screen.findByRole('region', { name: 'Applications by status' })
    const table = within(card).getByRole('table', { hidden: true })
    const rows = within(table).getAllByRole('row', { hidden: true })
    expect(rows).toHaveLength(1 + 7)
    expect(within(table).getByText('Applied', { selector: 'td' })).toBeInTheDocument()
  })

  it('describes the activity chart for assistive technology', async () => {
    mockDashboard(dashboard())
    renderApp('/dashboard')
    expect(await screen.findByRole('img', { name: 'Applications per month over 3 months, 5 in total' })).toBeInTheDocument()
  })

  it('shows a dash, not 0%, when the response rate is undefined', async () => {
    mockDashboard(dashboard({ totals: { tracked: 1, applied: 0, appliedThisMonth: 0, responded: 0, interviews: 0, offers: 0, rejections: 0 },
      rates: { responseRate: null, interviewRate: null, offerRate: null } }))
    renderApp('/dashboard')
    const tile = (await screen.findByText('Response rate')).closest('div') as HTMLElement
    expect(within(tile).getByText('—')).toBeInTheDocument()
    expect(within(tile).getByText('Apply to see this')).toBeInTheDocument()
  })

  it('shows a retryable error when loading fails', async () => {
    mockDashboard('error')
    renderApp('/dashboard')
    expect(await screen.findByText('Could not load your dashboard.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})

describe('analytics page', () => {
  beforeEach(() => {
    storeSession(makeSession())
    requested = []
  })
  afterEach(() => vi.restoreAllMocks())

  it('shows the funnel and conversion rates', async () => {
    mockDashboard(dashboard())
    renderApp('/analytics')
    const funnel = await screen.findByRole('region', { name: 'Application funnel' })
    expect(within(funnel).getAllByText('Heard back').length).toBeGreaterThan(0)
    expect(within(funnel).getByText('Interviewed', { selector: 'td' })).toBeInTheDocument()
    expect(screen.getByText('Interview rate').closest('div')).toHaveTextContent('40%')
    expect(screen.getByText('Offer rate').closest('div')).toHaveTextContent('20%')
  })

  it('asks the server for a different range when the selection changes', async () => {
    mockDashboard(dashboard())
    renderApp('/analytics')
    await screen.findByRole('region', { name: 'Application funnel' })
    await userEvent.selectOptions(screen.getByLabelText('Time range'), '24')
    await waitFor(() => expect(requested.some((r) => r.endsWith('months=24'))).toBe(true))
  })

  it('shows an empty state with no tracked applications', async () => {
    mockDashboard(dashboard({ totals: { tracked: 0, applied: 0, appliedThisMonth: 0, responded: 0, interviews: 0, offers: 0, rejections: 0 } }))
    renderApp('/analytics')
    expect(await screen.findByText('Nothing to analyze yet')).toBeInTheDocument()
  })
})
