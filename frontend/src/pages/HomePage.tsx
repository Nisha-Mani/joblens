import { Link } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

const FEATURES = [
  {
    title: 'Resume match analysis',
    body: 'Compare your resume with any job description and see matching skills, gaps, keyword misses and concrete edits, scored and explained.',
  },
  {
    title: 'Application tracker',
    body: 'Follow every role from saved to offer with statuses, interview dates, notes and a complete history. Search, filter and sort server-side.',
  },
  {
    title: 'Interview preparation',
    body: 'Get technical, behavioral and role-specific questions tailored to each job, then keep your notes and preparation progress alongside them.',
  },
  {
    title: 'Progress you can trust',
    body: 'Response and interview rates, a funnel and monthly activity, all computed from your real application history.',
  },
]

const linkBase = 'rounded-md px-4 py-2 text-sm font-medium focus:outline-2 focus:outline-offset-2 focus:outline-slate-900'

export default function HomePage() {
  const { isAuthenticated } = useAuth()

  return (
    <div className="min-h-screen bg-white">
      <header className="border-b border-slate-200">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <span className="text-lg font-semibold tracking-tight">JobLens</span>
          <nav aria-label="Account" className="flex items-center gap-2">
            {isAuthenticated ? (
              <Link to="/dashboard" className={`${linkBase} bg-slate-900 text-white hover:bg-slate-800`}>Open dashboard</Link>
            ) : (
              <>
                <Link to="/login" className={`${linkBase} text-slate-700 hover:bg-slate-100`}>Sign in</Link>
                <Link to="/register" className={`${linkBase} bg-slate-900 text-white hover:bg-slate-800`}>Get started</Link>
              </>
            )}
          </nav>
        </div>
      </header>

      <main>
        <section className="mx-auto max-w-5xl px-4 py-16 sm:py-24">
          <h1 className="max-w-2xl text-4xl font-semibold tracking-tight sm:text-5xl">
            Know how your experience fits every job you apply to.
          </h1>
          <p className="mt-5 max-w-2xl text-lg leading-relaxed text-slate-600">
            JobLens helps software engineers understand how their resume lines up with a job description, close the
            gaps, prepare for interviews and keep every application organised in one place.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to={isAuthenticated ? '/dashboard' : '/register'} className={`${linkBase} bg-slate-900 text-white hover:bg-slate-800`}>
              {isAuthenticated ? 'Open dashboard' : 'Create a free account'}
            </Link>
            {!isAuthenticated && (
              <Link to="/login" className={`${linkBase} border border-slate-300 text-slate-900 hover:bg-slate-50`}>Sign in</Link>
            )}
          </div>
        </section>

        <section aria-labelledby="features-heading" className="border-t border-slate-200 bg-slate-50">
          <div className="mx-auto max-w-5xl px-4 py-14">
            <h2 id="features-heading" className="text-xl font-semibold tracking-tight">Everything for the search, in one place</h2>
            <ul className="mt-8 grid gap-6 sm:grid-cols-2">
              {FEATURES.map((f) => (
                <li key={f.title} className="rounded-lg border border-slate-200 bg-white p-5">
                  <h3 className="font-medium">{f.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-slate-600">{f.body}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section aria-labelledby="privacy-heading" className="mx-auto max-w-5xl px-4 py-14">
          <h2 id="privacy-heading" className="text-xl font-semibold tracking-tight">Your data, handled carefully</h2>
          <p className="mt-3 max-w-2xl text-sm leading-relaxed text-slate-600">
            Your resume is parsed on the server without any AI involved. Only skills and experience text go to an AI
            model for analysis, never your name, email or phone number, and AI output is validated before it is ever
            stored. You can delete any resume at any time.
          </p>
        </section>
      </main>

      <footer className="border-t border-slate-200">
        <p className="mx-auto max-w-5xl px-4 py-6 text-sm text-slate-500">JobLens is a portfolio project.</p>
      </footer>
    </div>
  )
}
