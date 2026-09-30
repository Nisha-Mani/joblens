import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, makeSession, renderApp, storeSession } from '../../test/utils'

describe('authentication flow', () => {
  afterEach(() => vi.restoreAllMocks())

  it('redirects unauthenticated users to the login page', () => {
    renderApp('/dashboard')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('does not render an expired stored session as authenticated', () => {
    storeSession(makeSession({ expiresAt: new Date(Date.now() - 1000).toISOString() }))
    renderApp('/dashboard')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('validates the login form before calling the API', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email'), 'bad')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument()
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('logs in, stores the session and lands on the dashboard', async () => {
    const session = makeSession()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(session, 200))
    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email'), 'user@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(localStorage.getItem('joblens.session')).toContain('test-token')
  })

  it('shows the server message for invalid credentials', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ status: 401, detail: 'Invalid email or password' }, 401),
    )
    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email'), 'user@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
  })

  it('maps server field errors onto the registration form', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ status: 400, detail: 'Validation failed', errors: { email: 'Email is already taken' } }, 400),
    )
    renderApp('/register')
    await userEvent.type(screen.getByLabelText('Email'), 'user@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Email is already taken')).toBeInTheDocument()
  })

  it('signs out and returns to the login page', async () => {
    storeSession(makeSession())
    renderApp('/dashboard')
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(localStorage.getItem('joblens.session')).toBeNull()
  })

  it('signs out when the API rejects the token', async () => {
    storeSession(makeSession())
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 401 }, 401))
    renderApp('/dashboard')
    const { apiGet } = await import('../../lib/api')
    await apiGet('/api/users/me').catch(() => undefined)
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument())
  })

  it('blocks non-admin users from admin routes by role', async () => {
    const { ProtectedRoute } = await import('./ProtectedRoute')
    const { MemoryRouter, Routes, Route } = await import('react-router-dom')
    const { render } = await import('@testing-library/react')
    const { AuthProvider } = await import('./AuthContext')
    const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query')
    storeSession(makeSession())
    render(
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider>
          <MemoryRouter initialEntries={['/admin']}>
            <Routes>
              <Route path="/dashboard" element={<p>dashboard</p>} />
              <Route element={<ProtectedRoute role="ADMIN" />}>
                <Route path="/admin" element={<p>admin area</p>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>,
    )
    expect(screen.queryByText('admin area')).not.toBeInTheDocument()
    expect(screen.getByText('dashboard')).toBeInTheDocument()
  })
})
