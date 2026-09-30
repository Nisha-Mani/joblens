import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from '../../test/utils'
import type { Job } from '../jobs/api'
import type { ResumeSummary } from '../resume/api'
import { scoreLevel, type Analysis } from './api'

let resumes: ResumeSummary[]
let analyses: Analysis[]
let calls: { method: string; url: string; body?: unknown }[]

const job: Job = {
  id: 'j1', company: 'Globex', title: 'Platform Engineer', location: 'Remote', employmentType: 'FULL_TIME',
  createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', jobDescription: 'Java and Docker', sourceUrl: null,
}

const makeResume = (version: number): ResumeSummary => ({
  id: `r${version}`, fileName: `cv-v${version}.pdf`, version, sizeBytes: 1000, createdAt: '2026-09-01T00:00:00Z',
})

const makeAnalysis = (n: number, overrides: Partial<Analysis> = {}): Analysis => ({
  id: `an${n}`, jobId: 'j1', resumeId: 'r1', resumeVersion: 1, overallScore: 82,
  matchingSkills: ['Java', 'React'], missingSkills: ['Kubernetes'], keywordGaps: ['observability'],
  experienceAssessment: 'Solid backend experience.', suggestions: ['Quantify impact'], interviewTopics: ['System design'],
  model: 'mock-analyzer', createdAt: `2026-09-0${n}T10:00:00Z`, ...overrides,
})

function mockApi(options: { analyzeError?: { status: number; detail: string }; analyzeResult?: Analysis; analyzeGate?: Promise<void> } = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input), 'http://localhost')
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ method, url: url.pathname + url.search, body })

    if (url.pathname === '/api/jobs/j1' && method === 'GET') return jsonResponse(job)
    if (url.pathname === '/api/applications') return jsonResponse({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 })
    if (url.pathname === '/api/resumes') return jsonResponse([...resumes].sort((a, b) => b.version - a.version))
    if (url.pathname === '/api/jobs/j1/analyses') {
      return jsonResponse([...analyses].reverse().map(({ id, resumeVersion, overallScore, model, createdAt }) =>
        ({ id, resumeVersion, overallScore, model, createdAt })))
    }
    if (url.pathname === '/api/jobs/j1/analyze' && method === 'POST') {
      if (options.analyzeGate) await options.analyzeGate
      if (options.analyzeError) return jsonResponse({ status: options.analyzeError.status, detail: options.analyzeError.detail }, options.analyzeError.status)
      const created = options.analyzeResult ?? makeAnalysis(analyses.length + 1)
      analyses = [...analyses, created]
      return jsonResponse(created, 201)
    }
    const match = url.pathname.match(/^\/api\/analyses\/(an\d+)$/)
    if (match) return jsonResponse(analyses.find((a) => a.id === match[1]))
    return jsonResponse({ status: 404 }, 404)
  })
}

describe('scoreLevel', () => {
  it.each([[95, 'Excellent match'], [70, 'Strong match'], [55, 'Partial match'], [39, 'Weak match'], [0, 'Weak match']])(
    'labels %i as %s', (score, label) => expect(scoreLevel(score).label).toBe(label))
})

describe('analysis panel', () => {
  beforeEach(() => {
    storeSession(makeSession())
    resumes = []
    analyses = []
    calls = []
  })
  afterEach(() => vi.restoreAllMocks())

  it('asks the user to upload a resume first', async () => {
    mockApi()
    renderApp('/jobs/j1')
    expect(await screen.findByText('Upload a resume first')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to Resume' })).toHaveAttribute('href', '/resume')
    expect(screen.queryByRole('button', { name: 'Analyze match' })).not.toBeInTheDocument()
  })

  it('runs an analysis and shows every part of the result', async () => {
    resumes = [makeResume(1)]
    mockApi()
    renderApp('/jobs/j1')
    await userEvent.click(await screen.findByRole('button', { name: 'Analyze match' }))

    expect(await screen.findByText('Strong match')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Overall match score' })).toHaveAttribute('aria-valuenow', '82')
    expect(screen.getByText('Matching skills')).toBeInTheDocument()
    expect(screen.getByText('Java')).toBeInTheDocument()
    expect(screen.getByText('Kubernetes')).toBeInTheDocument()
    expect(screen.getByText('observability')).toBeInTheDocument()
    expect(screen.getByText('Solid backend experience.')).toBeInTheDocument()
    expect(screen.getByText('Quantify impact')).toBeInTheDocument()
    expect(screen.getByText('System design')).toBeInTheDocument()
    expect(screen.getByText(/AI-generated; verify before acting on it/)).toBeInTheDocument()
    expect(calls.find((c) => c.method === 'POST' && c.url.endsWith('/analyze'))?.body).toBeUndefined()
  })

  it('shows a progress message while the model is working', async () => {
    resumes = [makeResume(1)]
    let release: () => void = () => {}
    const gate = new Promise<void>((resolve) => { release = resolve })
    mockApi({ analyzeGate: gate })
    renderApp('/jobs/j1')
    await userEvent.click(await screen.findByRole('button', { name: 'Analyze match' }))
    expect(await screen.findByText(/this can take up to 30 seconds/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Analyze match' })).toBeDisabled()
    release()
    expect(await screen.findByText('Strong match')).toBeInTheDocument()
  })

  it.each([
    [503, 'AI analysis is not configured on this server.'],
    [504, 'The AI service took too long to respond. Please try again.'],
    [429, 'You have reached the limit of 20 analyses per hour. Try again in about 12 minutes.'],
    [502, 'The AI service returned an unusable response. Please try again.'],
  ])('shows the server message for a %i failure', async (status, detail) => {
    resumes = [makeResume(1)]
    mockApi({ analyzeError: { status, detail } })
    renderApp('/jobs/j1')
    await userEvent.click(await screen.findByRole('button', { name: 'Analyze match' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Analysis failed')
    expect(alert).toHaveTextContent(detail)
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('lets the user pick a resume version', async () => {
    resumes = [makeResume(1), makeResume(2)]
    mockApi()
    renderApp('/jobs/j1')
    await userEvent.selectOptions(await screen.findByLabelText('Resume version'), 'r1')
    await userEvent.click(screen.getByRole('button', { name: 'Analyze match' }))
    await screen.findByText('Strong match')
    expect(calls.find((c) => c.method === 'POST' && c.url.endsWith('/analyze'))?.body).toEqual({ resumeId: 'r1' })
  })

  it('shows the latest analysis immediately and lets the user open earlier ones', async () => {
    resumes = [makeResume(1)]
    analyses = [makeAnalysis(1, { overallScore: 30 }), makeAnalysis(2, { overallScore: 90 })]
    mockApi()
    renderApp('/jobs/j1')
    expect(await screen.findByText('Excellent match')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /30% · Weak match/ }))
    expect(await screen.findByRole('meter', { name: 'Overall match score' })).toHaveAttribute('aria-valuenow', '30')
    await waitFor(() => expect(screen.getByText('Weak match', { selector: 'p' })).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Analyze again' })).toBeInTheDocument()
  })

  it('renders friendly text for empty lists', async () => {
    resumes = [makeResume(1)]
    analyses = [makeAnalysis(1, { overallScore: 100, missingSkills: [], keywordGaps: [], suggestions: [], interviewTopics: [] })]
    mockApi()
    renderApp('/jobs/j1')
    expect(await screen.findByText('No missing skills. Nice.')).toBeInTheDocument()
    expect(screen.getByText('No notable keyword gaps.')).toBeInTheDocument()
    expect(screen.getByText('No suggestions.')).toBeInTheDocument()
  })
})
