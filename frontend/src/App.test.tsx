import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from './test/utils'

describe('App routing', () => {
  afterEach(() => vi.restoreAllMocks())

  it('home shows connected state when the API responds', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 'ok', service: 'joblens-api' }))
    renderApp('/')
    expect(await screen.findByText(/connected to joblens-api/i)).toBeInTheDocument()
  })

  it('home shows a retryable error when the API is unreachable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('network'))
    renderApp('/')
    expect(await screen.findByRole('alert')).toHaveTextContent(/backend unreachable/i)
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
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
