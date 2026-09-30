import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiGet, apiPost, configureApiClient } from './api'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status })
}

describe('api client', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    configureApiClient({ getToken: () => null, onUnauthorized: () => {} })
  })

  it('parses problem details into ApiError with field errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ status: 400, detail: 'Validation failed', errors: { email: 'invalid' } }, 400),
    )
    const error = await apiPost('/api/x', {}).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).message).toBe('Validation failed')
    expect((error as ApiError).fieldErrors).toEqual({ email: 'invalid' })
  })

  it('falls back to a generic message for non-JSON errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('oops', { status: 502 }))
    await expect(apiGet('/api/x')).rejects.toThrow('Request failed with status 502')
  })

  it('sends the bearer token when available', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}))
    configureApiClient({ getToken: () => 'abc' })
    await apiGet('/api/x')
    const headers = fetchSpy.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer abc')
  })

  it('invokes the unauthorized handler on 401 when a token was sent', async () => {
    const onUnauthorized = vi.fn()
    configureApiClient({ getToken: () => 'expired', onUnauthorized })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 401 }, 401))
    await expect(apiGet('/api/x')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not treat 401 without a token as session expiry', async () => {
    const onUnauthorized = vi.fn()
    configureApiClient({ getToken: () => null, onUnauthorized })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 401 }, 401))
    await expect(apiGet('/api/x')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
