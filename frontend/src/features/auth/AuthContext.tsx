import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiPost } from '../../lib/api'
import { clearSession, loadSession, onSessionCleared, saveSession } from './session'
import type { AuthResponse, Session, User } from './types'

interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => loadSession())

  // Drop local state when the session is cleared elsewhere (e.g. a 401 from the API).
  useEffect(
    () =>
      onSessionCleared(() => {
        setSession(null)
        queryClient.clear()
      }),
    [queryClient],
  )

  // Log out automatically when the token expires.
  useEffect(() => {
    if (!session) return
    const msUntilExpiry = new Date(session.expiresAt).getTime() - Date.now()
    const timer = setTimeout(clearSession, Math.max(msUntilExpiry, 0))
    return () => clearTimeout(timer)
  }, [session])

  const authenticate = useCallback(async (path: string, email: string, password: string) => {
    const response = await apiPost<AuthResponse>(path, { email, password })
    setSession(saveSession(response))
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      login: (email, password) => authenticate('/api/auth/login', email, password),
      register: (email, password) => authenticate('/api/auth/register', email, password),
      logout: clearSession,
    }),
    [session, authenticate],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
