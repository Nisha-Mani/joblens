import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
}

describe('App', () => {
  afterEach(() => vi.restoreAllMocks())

  it('shows connected state when the API responds', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 'ok', service: 'joblens-api', time: 'now' })),
    )
    renderApp()
    expect(await screen.findByText(/connected to joblens-api/i)).toBeInTheDocument()
  })

  it('shows an error when the API is unreachable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('network'))
    renderApp()
    expect(await screen.findByRole('alert')).toHaveTextContent(/backend unreachable/i)
  })
})
