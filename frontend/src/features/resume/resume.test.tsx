import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from '../../test/utils'
import { validateResumeFile, type ParsedResume, type ResumeDetail } from './api'

const emptyParsed: ParsedResume = {
  name: 'Jane Developer', email: 'jane@example.com', phone: null, summary: 'Engineer',
  skills: ['Java', 'React'], experience: ['Acme\nBuilt APIs'], education: [], projects: [], certifications: ['AWS'],
}

let resumes: ResumeDetail[]
let calls: { method: string; url: string; body?: unknown }[]

function detail(version: number, parsed = emptyParsed): ResumeDetail {
  return {
    id: `r${version}`, fileName: `resume-v${version}.pdf`, version, sizeBytes: 1000, extractedTextLength: 500,
    createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', parsed,
  }
}

function mockApi(options: { uploadStatus?: number; uploadDetail?: string } = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    const body = init?.body instanceof FormData ? init.body : init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ method, url, body })

    if (url.endsWith('/api/resumes') && method === 'GET') {
      return jsonResponse([...resumes].sort((a, b) => b.version - a.version))
    }
    if (url.endsWith('/api/resumes') && method === 'POST') {
      if (options.uploadStatus && options.uploadStatus >= 400) {
        return jsonResponse({ status: options.uploadStatus, detail: options.uploadDetail }, options.uploadStatus)
      }
      const created = detail(resumes.length + 1)
      resumes = [...resumes, created]
      return jsonResponse(created, 201)
    }
    const match = url.match(/\/api\/resumes\/(r\d+)(\/parsed)?$/)
    if (match && method === 'GET') return jsonResponse(resumes.find((r) => r.id === match[1]))
    if (match && match[2] && method === 'PUT') {
      const current = resumes.find((r) => r.id === match[1])
      if (!current) return jsonResponse({ status: 404 }, 404)
      const updated: ResumeDetail = { ...current, parsed: body as ParsedResume }
      resumes = resumes.map((r) => (r.id === updated.id ? updated : r))
      return jsonResponse(updated)
    }
    if (match && method === 'DELETE') {
      resumes = resumes.filter((r) => r.id !== match[1])
      return new Response(null, { status: 204 })
    }
    return jsonResponse({ status: 404 }, 404)
  })
}

const pdf = (name = 'cv.pdf', size = 100) =>
  new File([new Uint8Array(size)], name, { type: 'application/pdf' })

describe('validateResumeFile', () => {
  it('accepts a normal pdf', () => expect(validateResumeFile(pdf())).toBeNull())
  it('rejects non-pdf', () =>
    expect(validateResumeFile(new File(['x'], 'a.txt', { type: 'text/plain' }))).toMatch(/only pdf/i))
  it('rejects empty files', () => expect(validateResumeFile(pdf('a.pdf', 0))).toMatch(/empty/i))
  it('rejects oversized files', () =>
    expect(validateResumeFile(pdf('a.pdf', 6 * 1024 * 1024))).toMatch(/too large/i))
})

describe('resume page', () => {
  beforeEach(() => {
    storeSession(makeSession())
    resumes = []
    calls = []
  })
  afterEach(() => vi.restoreAllMocks())

  it('shows an empty state for new users', async () => {
    mockApi()
    renderApp('/resume')
    expect(await screen.findByText('No resume yet')).toBeInTheDocument()
  })

  it('uploads a pdf and shows the extracted data for editing', async () => {
    mockApi()
    renderApp('/resume')
    await userEvent.upload(await screen.findByLabelText(/upload a resume/i), pdf())

    expect(await screen.findByDisplayValue('Jane Developer')).toBeInTheDocument()
    expect(screen.getByLabelText('Skills')).toHaveValue('Java, React')
    const form = calls.find((c) => c.method === 'POST')?.body
    if (!(form instanceof FormData)) throw new Error('expected a multipart upload')
    expect(form.get('file')).toBeInstanceOf(File)
  })

  it('blocks invalid files on the client without calling the API', async () => {
    mockApi()
    renderApp('/resume')
    const input = await screen.findByLabelText(/upload a resume/i)
    await userEvent.upload(input, new File(['x'], 'notes.txt', { type: 'text/plain' }), { applyAccept: false })
    expect(await screen.findByRole('alert')).toHaveTextContent('Only PDF files are supported.')
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('shows the server error when upload is rejected', async () => {
    mockApi({ uploadStatus: 422, uploadDetail: 'No text found in this PDF.' })
    renderApp('/resume')
    await userEvent.upload(await screen.findByLabelText(/upload a resume/i), pdf())
    expect(await screen.findByRole('alert')).toHaveTextContent('No text found in this PDF.')
  })

  it('lists versions, newest first, and switches between them', async () => {
    resumes = [detail(1, { ...emptyParsed, name: 'Old Name' }), detail(2, { ...emptyParsed, name: 'New Name' })]
    mockApi()
    renderApp('/resume')
    expect(await screen.findByDisplayValue('New Name')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /v1 · resume-v1\.pdf/ }))
    expect(await screen.findByDisplayValue('Old Name')).toBeInTheDocument()
  })

  it('saves corrected data as structured lists', async () => {
    resumes = [detail(1)]
    mockApi()
    renderApp('/resume')
    const skills = await screen.findByLabelText('Skills')
    await userEvent.clear(skills)
    await userEvent.type(skills, 'Go, Rust')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Changes saved.')).toBeInTheDocument()
    const put = calls.find((c) => c.method === 'PUT')
    expect(put?.body).toMatchObject({ skills: ['Go', 'Rust'], name: 'Jane Developer' })
  })

  it('validates the email field before saving', async () => {
    resumes = [detail(1)]
    mockApi()
    renderApp('/resume')
    const email = await screen.findByLabelText('Email')
    await userEvent.clear(email)
    await userEvent.type(email, 'nope')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('deletes a version after confirmation', async () => {
    resumes = [detail(1)]
    mockApi()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/resume')
    await userEvent.click(await screen.findByRole('button', { name: 'Delete version 1' }))
    await waitFor(() => expect(screen.getByText('No resume yet')).toBeInTheDocument())
  })

  it('does not delete when the confirmation is declined', async () => {
    resumes = [detail(1)]
    mockApi()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderApp('/resume')
    await userEvent.click(await screen.findByRole('button', { name: 'Delete version 1' }))
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    expect(within(screen.getByRole('list')).getByText(/resume-v1\.pdf/)).toBeInTheDocument()
  })
})
