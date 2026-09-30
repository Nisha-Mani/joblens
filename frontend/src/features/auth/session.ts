import { configureApiClient } from '../../lib/api'
import type { AuthResponse, Session } from './types'

const STORAGE_KEY = 'joblens.session'

type Listener = () => void
const listeners = new Set<Listener>()

function isExpired(session: Session) {
  return new Date(session.expiresAt).getTime() <= Date.now()
}

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (!session.token || isExpired(session)) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(response: AuthResponse): Session {
  const session: Session = {
    token: response.token,
    expiresAt: response.expiresAt,
    user: response.user,
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  return session
}

export function clearSession() {
  localStorage.removeItem(STORAGE_KEY)
  listeners.forEach((listener) => listener())
}

/** Notified when the session is cleared for any reason (logout, expiry, rejected token). */
export function onSessionCleared(listener: Listener) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

// Configured at import time so queries fired by child components always see the token.
configureApiClient({
  getToken: () => loadSession()?.token ?? null,
  onUnauthorized: clearSession,
})
