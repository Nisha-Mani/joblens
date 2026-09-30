import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from '../../test/utils'
import type { Job } from '../jobs/api'
import type { InterviewQuestion, PrepStatus } from './api'

let jobs: Job[]
let questions: InterviewQuestion[]
let calls: { method: string; url: string; body?: unknown }[]

const makeJob = (n: number): Job => ({
  id: `j${n}`, company: `Company ${n}`, title: `Engineer ${n}`, location: null, employmentType: 'FULL_TIME',
  createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', jobDescription: 'Java', sourceUrl: null,
})

const makeQuestion = (n: number, overrides: Partial<InterviewQuestion> = {}): InterviewQuestion => ({
  id: `q${n}`, jobId: 'j1', company: 'Company 1', jobTitle: 'Engineer 1', question: `Question number ${n}?`,
  category: 'TECHNICAL', difficulty: 'MEDIUM', skills: ['Java'], notes: null, status: 'NOT_STARTED', generated: true,
  createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', ...overrides,
})

function mockApi(options: { generated?: InterviewQuestion[]; generateError?: { status: number; detail: string }; gate?: Promise<void> } = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input), 'http://localhost')
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ method, url: url.pathname + url.search, body })

    if (url.pathname === '/api/jobs' && method === 'GET') {
      return jsonResponse({ content: jobs, page: 0, size: 100, totalElements: jobs.length, totalPages: 1 })
    }
    if (url.pathname === '/api/interviews/questions' && method === 'GET') {
      const jobId = url.searchParams.get('jobId')
      const category = url.searchParams.get('category')
      const status = url.searchParams.get('status')
      const filtered = questions.filter((q) => (!jobId || q.jobId === jobId) && (!category || q.category === category) && (!status || q.status === status))
      const size = Number(url.searchParams.get('size'))
      const page = Number(url.searchParams.get('page'))
      return jsonResponse({ content: filtered.slice(page * size, page * size + size), page, size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size) })
    }
    if (url.pathname === '/api/interviews/questions' && method === 'POST') {
      const created = makeQuestion(questions.length + 1, { ...body, generated: false, skills: [] })
      questions = [...questions, created]
      return jsonResponse(created, 201)
    }
    const generate = url.pathname.match(/^\/api\/jobs\/(j\d+)\/interview-questions\/generate$/)
    if (generate && method === 'POST') {
      if (options.gate) await options.gate
      if (options.generateError) return jsonResponse({ status: options.generateError.status, detail: options.generateError.detail }, options.generateError.status)
      const created = options.generated ?? [makeQuestion(questions.length + 1)]
      questions = [...questions, ...created]
      return jsonResponse(created, 201)
    }
    const match = url.pathname.match(/^\/api\/interviews\/questions\/(q\d+)$/)
    if (match && method === 'PUT') {
      const updated = { ...questions.find((q) => q.id === match[1])!, notes: body.notes, status: body.status as PrepStatus }
      questions = questions.map((q) => (q.id === updated.id ? updated : q))
      return jsonResponse(updated)
    }
    if (match && method === 'DELETE') {
      questions = questions.filter((q) => q.id !== match[1])
      return new Response(null, { status: 204 })
    }
    return jsonResponse({ status: 404 }, 404)
  })
}

const lastListUrl = () => [...calls].reverse().find((c) => c.method === 'GET' && c.url.startsWith('/api/interviews/questions?'))?.url ?? ''

describe('interview prep', () => {
  beforeEach(() => {
    storeSession(makeSession())
    jobs = [makeJob(1), makeJob(2)]
    questions = []
    calls = []
  })
  afterEach(() => vi.restoreAllMocks())

  it('asks the user to add a job first when there are none', async () => {
    jobs = []
    mockApi()
    renderApp('/interview-prep')
    expect(await screen.findByText('Add a job first')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Generate questions' })).not.toBeInTheDocument()
  })

  it('prompts to choose a job and keeps generate disabled until one is selected', async () => {
    mockApi()
    renderApp('/interview-prep')
    expect(await screen.findByText('No questions yet')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate questions' })).toBeDisabled()
    await screen.findByRole('option', { name: 'Company 1 — Engineer 1' })
    await userEvent.selectOptions(screen.getByLabelText('Job'), 'j1')
    expect(screen.getByRole('button', { name: 'Generate questions' })).toBeEnabled()
  })

  it('generates questions and lists them with category, difficulty and skills', async () => {
    mockApi({ generated: [makeQuestion(1, { category: 'BEHAVIORAL', difficulty: 'EASY', skills: [] }), makeQuestion(2, { skills: ['Docker'] })] })
    renderApp('/interview-prep?jobId=j1')
    await screen.findByRole('option', { name: 'Company 1 — Engineer 1' })
    await userEvent.click(screen.getByRole('button', { name: 'Generate questions' }))

    expect(await screen.findByText('Added 2 new questions.')).toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'Interview questions' })
    expect(within(list).getByText('Question number 1?')).toBeInTheDocument()
    expect(within(list).getByText('Behavioral')).toBeInTheDocument()
    expect(within(list).getByText('Easy')).toBeInTheDocument()
    expect(within(list).getByText('Docker')).toBeInTheDocument()
  })

  it('says so when generation adds nothing new', async () => {
    questions = [makeQuestion(1)]
    mockApi({ generated: [] })
    renderApp('/interview-prep?jobId=j1')
    await screen.findByText('Question number 1?')
    await userEvent.click(screen.getByRole('button', { name: 'Generate questions' }))
    expect(await screen.findByText(/No new questions/)).toBeInTheDocument()
  })

  it('shows progress while generating and disables the button', async () => {
    let release: () => void = () => {}
    mockApi({ gate: new Promise<void>((resolve) => { release = resolve }) })
    renderApp('/interview-prep?jobId=j1')
    await screen.findByRole('option', { name: 'Company 1 — Engineer 1' })
    await userEvent.click(screen.getByRole('button', { name: 'Generate questions' }))
    expect(await screen.findByText(/this can take up to 30 seconds/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generating…' })).toBeDisabled()
    release()
    expect(await screen.findByText(/Added 1 new question\./)).toBeInTheDocument()
  })

  it.each([
    [503, 'AI analysis is not configured on this server.'],
    [429, 'You have reached the limit of 20 AI requests per hour. Try again in about 5 minutes.'],
    [504, 'The AI service took too long to respond. Please try again.'],
  ])('shows the server message when generation fails with %i', async (status, detail) => {
    mockApi({ generateError: { status, detail } })
    renderApp('/interview-prep?jobId=j1')
    await screen.findByRole('option', { name: 'Company 1 — Engineer 1' })
    await userEvent.click(screen.getByRole('button', { name: 'Generate questions' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(detail)
  })

  it('saves notes and preparation progress', async () => {
    questions = [makeQuestion(1)]
    mockApi()
    renderApp('/interview-prep?jobId=j1')
    const save = await screen.findByRole('button', { name: /Save notes for: Question number 1\?/ })
    expect(save).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Your notes'), 'Mention the migration project')
    await userEvent.selectOptions(screen.getByLabelText('Preparation'), 'IN_PROGRESS')
    expect(save).toBeEnabled()
    await userEvent.click(save)

    expect(await screen.findByText('Saved')).toBeInTheDocument()
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ notes: 'Mention the migration project', status: 'IN_PROGRESS' })
  })

  it('filters by category and progress on the server, and clears filters', async () => {
    questions = [makeQuestion(1), makeQuestion(2, { category: 'BEHAVIORAL', skills: [] })]
    mockApi()
    renderApp('/interview-prep?jobId=j1')
    await screen.findAllByRole('listitem')
    await userEvent.selectOptions(screen.getByLabelText('Category'), 'BEHAVIORAL')
    await waitFor(() => expect(lastListUrl()).toContain('category=BEHAVIORAL'))
    await waitFor(() => expect(screen.queryByText('Question number 1?')).not.toBeInTheDocument())

    await userEvent.selectOptions(screen.getByLabelText('Progress'), 'PREPARED')
    expect(await screen.findByText('No matching questions')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(await screen.findByText('Question number 1?')).toBeInTheDocument()
  })

  it('shows the job on each card only when viewing all jobs', async () => {
    questions = [makeQuestion(1)]
    mockApi()
    const { unmount } = renderApp('/interview-prep')
    expect(await screen.findByText(/Engineer 1 at Company 1/)).toBeInTheDocument()
    unmount()
    renderApp('/interview-prep?jobId=j1')
    await screen.findByText('Question number 1?')
    expect(screen.queryByText(/Engineer 1 at Company 1/)).not.toBeInTheDocument()
  })

  it('adds a custom question for the selected job', async () => {
    mockApi()
    renderApp('/interview-prep?jobId=j1')
    await userEvent.type(await screen.findByLabelText('Question'), 'Why do you want this role?')
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'BEHAVIORAL')
    await userEvent.click(screen.getByRole('button', { name: 'Add question' }))
    expect(await screen.findByText('Why do you want this role?')).toBeInTheDocument()
    expect(calls.find((c) => c.method === 'POST' && c.url === '/api/interviews/questions')?.body)
      .toMatchObject({ jobId: 'j1', question: 'Why do you want this role?', category: 'BEHAVIORAL', difficulty: 'MEDIUM' })
  })

  it('deletes a question after confirmation', async () => {
    questions = [makeQuestion(1)]
    mockApi()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/interview-prep?jobId=j1')
    await userEvent.click(await screen.findByRole('button', { name: /Delete question: Question number 1\?/ }))
    await waitFor(() => expect(screen.getByText('No questions yet')).toBeInTheDocument())
  })

  it('keeps the question when deletion is declined', async () => {
    questions = [makeQuestion(1)]
    mockApi()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderApp('/interview-prep?jobId=j1')
    await userEvent.click(await screen.findByRole('button', { name: /Delete question/ }))
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
  })

  it('is reachable from navigation and links from the job page', async () => {
    mockApi()
    renderApp('/interview-prep')
    expect(await screen.findByRole('link', { name: 'Interview prep' })).toHaveAttribute('href', '/interview-prep')
  })
})
