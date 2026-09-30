import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from '../../test/utils'
import type { Profile, UserSkill } from './api'

interface ServerState {
  profile: Profile
  skills: UserSkill[]
}

let state: ServerState
let calls: { method: string; url: string; body?: unknown }[]

function mockApi(overrides: { failSkills?: boolean } = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ method, url, body })

    if (url.endsWith('/api/users/me/profile') && method === 'GET') return jsonResponse(state.profile)
    if (url.endsWith('/api/users/me/profile') && method === 'PUT') {
      state.profile = body
      return jsonResponse(state.profile)
    }
    if (url.endsWith('/api/users/me/skills') && method === 'GET') {
      return overrides.failSkills ? jsonResponse({ status: 500 }, 500) : jsonResponse(state.skills)
    }
    if (url.endsWith('/api/users/me/skills') && method === 'POST') {
      const skill: UserSkill = { skillId: `id-${state.skills.length}`, name: body.name, category: body.category, proficiency: body.proficiency }
      state.skills = [...state.skills, skill]
      return jsonResponse(skill)
    }
    if (url.includes('/api/users/me/skills/') && method === 'DELETE') {
      state.skills = state.skills.filter((s) => !url.endsWith(s.skillId))
      return new Response(null, { status: 204 })
    }
    if (url.includes('/api/skills/suggestions')) return jsonResponse([])
    return jsonResponse({ status: 404 }, 404)
  })
}

describe('profile page', () => {
  beforeEach(() => {
    storeSession(makeSession())
    state = { profile: { name: null, headline: null, location: null, yearsOfExperience: null, summary: null }, skills: [] }
    calls = []
  })
  afterEach(() => vi.restoreAllMocks())

  it('shows empty states for a new user', async () => {
    mockApi()
    renderApp('/profile')
    expect(await screen.findByText('No skills yet')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('')
  })

  it('saves profile edits, converting years of experience to a number', async () => {
    mockApi()
    renderApp('/profile')
    await userEvent.type(await screen.findByLabelText('Name'), 'Alice Dev')
    await userEvent.type(screen.getByLabelText('Years of experience'), '7')
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Profile saved.')).toBeInTheDocument()
    const put = calls.find((c) => c.method === 'PUT')
    expect(put?.body).toMatchObject({ name: 'Alice Dev', yearsOfExperience: 7 })
  })

  it('validates years of experience before saving', async () => {
    mockApi()
    renderApp('/profile')
    await userEvent.type(await screen.findByLabelText('Years of experience'), 'abc')
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(await screen.findByText('Enter a whole number from 0 to 60')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('adds a skill and lists it', async () => {
    mockApi()
    renderApp('/profile')
    await userEvent.type(await screen.findByLabelText('Skill'), 'Kubernetes')
    await userEvent.selectOptions(screen.getByLabelText('Proficiency'), 'ADVANCED')
    await userEvent.click(screen.getByRole('button', { name: 'Add skill' }))

    const list = await screen.findByRole('list')
    expect(within(list).getByText('Kubernetes')).toBeInTheDocument()
    expect(calls.find((c) => c.method === 'POST')?.body).toMatchObject({ name: 'Kubernetes', proficiency: 'ADVANCED' })
  })

  it('removes a skill', async () => {
    state.skills = [{ skillId: 's1', name: 'Java', category: 'LANGUAGE', proficiency: 'EXPERT' }]
    mockApi()
    renderApp('/profile')
    await userEvent.click(await screen.findByRole('button', { name: 'Remove Java' }))
    await waitFor(() => expect(screen.getByText('No skills yet')).toBeInTheDocument())
  })

  it('does not allow adding an empty skill', async () => {
    mockApi()
    renderApp('/profile')
    expect(await screen.findByRole('button', { name: 'Add skill' })).toBeDisabled()
  })

  it('shows a retryable error when skills fail to load', async () => {
    mockApi({ failSkills: true })
    renderApp('/profile')
    expect(await screen.findByText('Could not load your skills.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
