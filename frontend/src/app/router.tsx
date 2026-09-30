import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '../components/layout/AppLayout'
import { ProtectedRoute } from '../features/auth/ProtectedRoute'
import { LoginPage, RegisterPage } from '../pages/AuthPages'
import ApplicationDetailPage from '../pages/ApplicationDetailPage'
import ApplicationsPage from '../pages/ApplicationsPage'
import HomePage from '../pages/HomePage'
import JobDetailPage from '../pages/JobDetailPage'
import JobsPage from '../pages/JobsPage'
import NewApplicationPage from '../pages/NewApplicationPage'
import NewJobPage from '../pages/NewJobPage'
import NotFoundPage from '../pages/NotFoundPage'
import ProfilePage from '../pages/ProfilePage'
import ResumePage from '../pages/ResumePage'
import PlaceholderPage from '../pages/PlaceholderPage'

const sections = [
  { path: 'dashboard', title: 'Dashboard', description: 'Your job search at a glance will appear here.' },
  { path: 'analytics', title: 'Analytics', description: 'Understand your job search progress over time.' },
]

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          {sections.map((s) => (
            <Route
              key={s.path}
              path={s.path}
              element={<PlaceholderPage title={s.title} description={s.description} />}
            />
          ))}
          <Route path="resume" element={<ResumePage />} />
          <Route path="applications" element={<ApplicationsPage />} />
          <Route path="applications/new" element={<NewApplicationPage />} />
          <Route path="applications/:id" element={<ApplicationDetailPage />} />
          <Route path="jobs" element={<JobsPage />} />
          <Route path="jobs/new" element={<NewJobPage />} />
          <Route path="jobs/:id" element={<JobDetailPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
