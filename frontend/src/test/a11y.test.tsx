import { waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { configureAxe } from 'vitest-axe'
import { jsonResponse, makeSession, renderApp, storeSession } from './utils'

// jsdom has no layout engine, so colour-contrast cannot be evaluated here (it is checked by eye and
// by the palette validator). Every other axe rule runs.
const axe = configureAxe({ rules: { 'color-contrast': { enabled: false } } })

const job = {
  id: 'j1', company: 'Globex', title: 'Platform Engineer', location: 'Remote', employmentType: 'FULL_TIME',
  createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', jobDescription: 'Java and Docker', sourceUrl: 'https://example.com/j',
}
const application = {
  id: 'a1', jobId: 'j1', company: 'Globex', title: 'Platform Engineer', location: 'Remote', status: 'INTERVIEW',
  appliedAt: '2026-09-01', interviewDate: '2030-05-20T10:30:00Z', notes: 'Notes', createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-02T00:00:00Z', history: [{ from: null, to: 'APPLIED', changedAt: '2026-09-01T00:00:00Z' }],
}
const question = {
  id: 'q1', jobId: 'j1', company: 'Globex', jobTitle: 'Platform Engineer', question: 'Explain Docker layers',
  category: 'TECHNICAL', difficulty: 'MEDIUM', skills: ['Docker'], notes: null, status: 'NOT_STARTED', generated: true,
  createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z',
}
const analysis = {
  id: 'an1', jobId: 'j1', resumeId: 'r1', resumeVersion: 1, overallScore: 62, matchingSkills: ['Java'], missingSkills: ['Docker'],
  keywordGaps: ['containers'], experienceAssessment: 'Good fit.', suggestions: ['Add Docker'], interviewTopics: ['Docker'],
  model: 'mock-analyzer', createdAt: '2026-09-01T10:00:00Z',
}
const resume = { id: 'r1', fileName: 'cv.pdf', version: 1, sizeBytes: 1000, createdAt: '2026-09-01T00:00:00Z' }
const page = <T,>(content: T[]) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1 })

const dashboard = {
  totals: { tracked: 3, applied: 2, appliedThisMonth: 2, responded: 1, interviews: 1, offers: 0, rejections: 0 },
  rates: { responseRate: 0.5, interviewRate: 0.5, offerRate: 0 },
  statusDistribution: ['SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'].map((status) => ({ status, count: 1 })),
  applicationsByMonth: [{ month: '2026-08', count: 1 }, { month: '2026-09', count: 2 }],
  upcomingInterviews: [{ applicationId: 'a1', company: 'Globex', title: 'Platform Engineer', status: 'INTERVIEW', interviewDate: '2030-05-20T10:30:00Z' }],
  recentApplications: [{ applicationId: 'a1', company: 'Globex', title: 'Platform Engineer', status: 'INTERVIEW', updatedAt: '2026-09-02T00:00:00Z' }],
  topMissingSkills: [{ skill: 'Docker', count: 2 }],
}

function mockApi() {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const path = new URL(String(input), 'http://localhost').pathname
    if (path === '/api/jobs') return jsonResponse(page([job]))
    if (path === '/api/jobs/j1') return jsonResponse(job)
    if (path === '/api/applications') return jsonResponse(page([{ ...application }]))
    if (path === '/api/applications/a1') return jsonResponse(application)
    if (path === '/api/resumes') return jsonResponse([resume])
    if (path === '/api/resumes/r1') return jsonResponse({ ...resume, extractedTextLength: 10, updatedAt: resume.createdAt,
      parsed: { name: 'Jane', email: 'j@x.com', phone: null, summary: 'Hi', skills: ['Java'], experience: ['A'], education: [], projects: [], certifications: [] } })
    if (path === '/api/users/me/profile') return jsonResponse({ name: 'Jane', headline: null, location: null, yearsOfExperience: 3, summary: null })
    if (path === '/api/users/me/skills') return jsonResponse([{ skillId: 's1', name: 'Java', category: 'LANGUAGE', proficiency: 'ADVANCED' }])
    if (path === '/api/jobs/j1/analyses') return jsonResponse([{ id: 'an1', resumeVersion: 1, overallScore: 62, model: 'm', createdAt: analysis.createdAt }])
    if (path === '/api/analyses/an1') return jsonResponse(analysis)
    if (path === '/api/interviews/questions') return jsonResponse(page([question]))
    if (path === '/api/analytics/dashboard') return jsonResponse(dashboard)
    return jsonResponse({ status: 404 }, 404)
  })
}

const SCREENS: [string, string, string | RegExp][] = [
  ['landing', '/', 'Everything for the search, in one place'],
  ['login', '/login', 'Sign in'],
  ['register', '/register', 'Create your account'],
  ['dashboard', '/dashboard', 'Applications by status'],
  ['analytics', '/analytics', 'Application funnel'],
  ['jobs list', '/jobs', 'Saved jobs'],
  ['new job', '/jobs/new', 'Save job'],
  ['job detail with analysis', '/jobs/j1', 'Strong match|Partial match|Weak match'],
  ['applications list', '/applications', 'Applications'],
  ['new application', '/applications/new', 'Save application'],
  ['application detail', '/applications/a1', 'Status history'],
  ['resume', '/resume', 'Extracted information'],
  ['profile', '/profile', 'Skills'],
  ['interview prep', '/interview-prep', 'Explain Docker layers'],
  ['not found', '/nope', 'Page not found'],
]

describe('accessibility (axe)', () => {
  beforeEach(() => {
    mockApi()
  })
  afterEach(() => vi.restoreAllMocks())

  it.each(SCREENS)('%s has no detectable violations', async (_name, path, waitFor_) => {
    // Sign-in and registration are only reachable when logged out; everything else needs a session.
    if (path !== '/' && path !== '/login' && path !== '/register') storeSession(makeSession())
    const { container, findAllByText } = renderApp(path)
    const text = typeof waitFor_ === 'string' && waitFor_.includes('|') ? new RegExp(waitFor_) : waitFor_
    await waitFor(async () => expect((await findAllByText(text)).length).toBeGreaterThan(0))
    expect(await axe(container)).toHaveNoViolations()
  })
})
