import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { AuthForm } from '../features/auth/AuthForm'
import { useAuth } from '../features/auth/AuthContext'

function AuthCard({ title, children, footer }: { title: string; children: React.ReactNode; footer: React.ReactNode }) {
  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center px-4">
      <p className="text-center text-lg font-semibold tracking-tight">JobLens</p>
      <div className="mt-4 rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="mb-4 text-xl font-semibold tracking-tight">{title}</h1>
        {children}
      </div>
      <p className="mt-4 text-center text-sm text-slate-600">{footer}</p>
    </main>
  )
}

function useRedirectTarget() {
  const location = useLocation()
  return (location.state as { from?: string } | null)?.from ?? '/dashboard'
}

export function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()
  if (isAuthenticated) return <Navigate to={target} replace />

  return (
    <AuthCard
      title="Sign in"
      footer={<>No account? <Link className="font-medium underline" to="/register">Create one</Link></>}
    >
      <AuthForm
        submitLabel="Sign in"
        autoCompletePassword="current-password"
        onSubmit={async ({ email, password }) => {
          await login(email, password)
          navigate(target, { replace: true })
        }}
      />
    </AuthCard>
  )
}

export function RegisterPage() {
  const { isAuthenticated, register } = useAuth()
  const navigate = useNavigate()
  if (isAuthenticated) return <Navigate to="/dashboard" replace />

  return (
    <AuthCard
      title="Create your account"
      footer={<>Already registered? <Link className="font-medium underline" to="/login">Sign in</Link></>}
    >
      <AuthForm
        submitLabel="Create account"
        autoCompletePassword="new-password"
        onSubmit={async ({ email, password }) => {
          await register(email, password)
          navigate('/dashboard', { replace: true })
        }}
      />
    </AuthCard>
  )
}
