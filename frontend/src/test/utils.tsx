import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../features/auth/AuthContext'
import type { Session } from '../features/auth/types'

export function makeSession(overrides: Partial<Session> = {}): Session {
  return {
    token: 'test-token',
    expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    user: { id: '1', email: 'user@example.com', role: 'USER', createdAt: '2026-01-01T00:00:00Z' },
    ...overrides,
  }
}

export function storeSession(session: Session) {
  localStorage.setItem('joblens.session', JSON.stringify(session))
}

export function renderApp(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

export function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status })
}
