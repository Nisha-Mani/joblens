export interface ProblemDetail {
  status: number
  detail?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

let tokenProvider: () => string | null = () => null
let unauthorizedHandler: () => void = () => {}

/** Wired up by the auth layer so the client stays independent of React. */
export function configureApiClient(options: {
  getToken?: () => string | null
  onUnauthorized?: () => void
}) {
  if (options.getToken) tokenProvider = options.getToken
  if (options.onUnauthorized) unauthorizedHandler = options.onUnauthorized
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail | undefined
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // non-JSON error body; fall back to a generic message
  }
  const message = problem?.detail ?? `Request failed with status ${response.status}`
  return new ApiError(response.status, message, problem?.errors)
}

export async function apiRequest<T>(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const isForm = options.body instanceof FormData
  // For FormData the browser sets the multipart Content-Type (with boundary) itself.
  if (options.body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  const token = tokenProvider()
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body:
      options.body === undefined ? undefined : isForm ? (options.body as FormData) : JSON.stringify(options.body),
  })

  if (response.status === 401 && token) unauthorizedHandler()
  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const apiGet = <T>(path: string) => apiRequest<T>(path)
export const apiPost = <T>(path: string, body?: unknown) =>
  apiRequest<T>(path, { method: 'POST', body })
export const apiUpload = <T>(path: string, form: FormData) =>
  apiRequest<T>(path, { method: 'POST', body: form })
export const apiPut = <T>(path: string, body?: unknown) =>
  apiRequest<T>(path, { method: 'PUT', body })
export const apiDelete = <T = void>(path: string) => apiRequest<T>(path, { method: 'DELETE' })
