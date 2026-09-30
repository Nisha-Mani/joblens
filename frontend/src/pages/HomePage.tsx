import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ErrorState } from '../components/ui/ErrorState'
import { Spinner } from '../components/ui/Spinner'
import { apiGet } from '../lib/api'

interface PingResponse {
  status: string
  service: string
}

export default function HomePage() {
  const ping = useQuery({ queryKey: ['ping'], queryFn: () => apiGet<PingResponse>('/api/ping') })

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-6 px-4">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">JobLens</h1>
        <p className="mt-1 text-slate-600">
          AI-powered job search and resume intelligence platform.
        </p>
      </header>

      <section aria-live="polite" className="rounded-lg border border-slate-200 bg-white p-4 text-sm">
        <h2 className="font-medium">API status</h2>
        <div className="mt-2">
          {ping.isPending && <Spinner label="Checking backend…" />}
          {ping.isError && (
            <ErrorState
              title="Backend unreachable"
              message="Is the API running on port 8080?"
              onRetry={() => void ping.refetch()}
            />
          )}
          {ping.isSuccess && (
            <p className="text-green-700">
              Connected to {ping.data.service} ({ping.data.status})
            </p>
          )}
        </div>
      </section>

      <Link to="/dashboard" className="text-sm font-medium text-slate-900 underline">
        Open the app
      </Link>
    </main>
  )
}
