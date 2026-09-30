import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { Role } from './types'

export function ProtectedRoute({ role }: { role?: Role }) {
  const { isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (role && user?.role !== role) {
    return <Navigate to="/dashboard" replace />
  }
  return <Outlet />
}
