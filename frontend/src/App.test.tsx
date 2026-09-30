import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from './test/utils'

describe('App routing', () => {
  afterEach(() => vi.restoreAllMocks())

  it('shows a landing page with clear calls to action for visitors', () => {
    renderApp('/')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/how your experience fits every job/i)
    expect(screen.getByRole('link', { name: 'Create a free account' })).toHaveAttribute('href', '/register')
    expect(screen.getAllByRole('link', { name: 'Sign in' })[0]).toHaveAttribute('href', '/login')
    expect(screen.getByRole('heading', { name: 'Resume match analysis' })).toBeInTheDocument()
    // The landing page makes no API calls and shows no debug output.
    expect(screen.queryByText(/api status/i)).not.toBeInTheDocument()
  })

  it('offers signed-in users a way straight back into the app', () => {
    storeSession(makeSession())
    renderApp('/')
    expect(screen.getAllByRole('link', { name: 'Open dashboard' })[0]).toHaveAttribute('href', '/dashboard')
    expect(screen.queryByRole('link', { name: 'Create a free account' })).not.toBeInTheDocument()
  })

  it('renders the app shell immediately and the lazily loaded page once its chunk arrives', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 500 }, 500))
    storeSession(makeSession())
    renderApp('/analytics')
    // The shell (navigation) does not wait for the page chunk.
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Applications' })).toHaveAttribute('href', '/applications')
    expect(await screen.findByRole('heading', { name: 'Analytics' })).toBeInTheDocument()
  })

  it('shows a not-found page for unknown routes', () => {
    renderApp('/nope')
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
