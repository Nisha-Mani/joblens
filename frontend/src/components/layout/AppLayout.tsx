import { Suspense } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { Spinner } from '../ui/Spinner'
import { useAuth } from '../../features/auth/AuthContext'
import { Button } from '../ui/Button'

const navItems = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/resume', label: 'Resume' },
  { to: '/jobs', label: 'Jobs' },
  { to: '/applications', label: 'Applications' },
  { to: '/interview-prep', label: 'Interview prep' },
  { to: '/analytics', label: 'Analytics' },
  { to: '/profile', label: 'Profile' },
]

export function AppLayout() {
  const { user, logout } = useAuth()
  return (
    <div className="min-h-screen md:flex">
      <aside className="border-b border-slate-200 bg-white md:w-56 md:border-r md:border-b-0">
        <div className="px-4 py-4 text-lg font-semibold tracking-tight">JobLens</div>
        <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-2 pb-2 md:flex-col md:pb-0">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `rounded-md px-3 py-2 text-sm font-medium whitespace-nowrap ${
                  isActive ? 'bg-slate-100 text-slate-900' : 'text-slate-600 hover:bg-slate-50'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="flex items-center gap-3 border-t border-slate-200 p-3 text-sm md:mt-4 md:block">
          <p className="truncate text-slate-600" title={user?.email}>{user?.email}</p>
          <Button variant="secondary" onClick={logout} className="shrink-0 md:mt-2 md:w-full">Sign out</Button>
        </div>
      </aside>
      <main className="flex-1 px-4 py-6 md:px-8">
        <div className="mx-auto max-w-5xl">
          <Suspense fallback={<Spinner label="Loading…" />}>
            <Outlet />
          </Suspense>
        </div>
      </main>
    </div>
  )
}
