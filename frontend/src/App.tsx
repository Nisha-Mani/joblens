import { useQuery } from '@tanstack/react-query'
import { apiGet } from './lib/api'

interface PingResponse {
  status: string
  service: string
  time: string
}

export default function App() {
  const ping = useQuery({
    queryKey: ['ping'],
    queryFn: () => apiGet<PingResponse>('/api/ping'),
  })

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-6 px-4">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">JobLens</h1>
        <p className="mt-1 text-slate-600">
          AI-powered job search and resume intelligence platform.
        </p>
      </header>

      <section
        aria-live="polite"
        className="rounded-lg border border-slate-200 bg-white p-4 text-sm"
      >
        <h2 className="font-medium">API status</h2>
        {ping.isPending && <p className="mt-1 text-slate-500">Checking backend…</p>}
        {ping.isError && (
          <p role="alert" className="mt-1 text-red-700">
            Backend unreachable. Is the API running on port 8080?
          </p>
        )}
        {ping.isSuccess && (
          <p className="mt-1 text-green-700">
            Connected to {ping.data.service} ({ping.data.status})
          </p>
        )}
      </section>
    </main>
  )
}
