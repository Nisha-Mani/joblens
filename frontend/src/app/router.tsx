import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '../components/layout/AppLayout'
import { ProtectedRoute } from '../features/auth/ProtectedRoute'
import { LoginPage, RegisterPage } from '../pages/AuthPages'
import HomePage from '../pages/HomePage'
import NotFoundPage from '../pages/NotFoundPage'
import ProfilePage from '../pages/ProfilePage'
import ResumePage from '../pages/ResumePage'
import PlaceholderPage from '../pages/PlaceholderPage'

const sections = [
  { path: 'dashboard', title: 'Dashboard', description: 'Your job search at a glance will appear here.' },
  { path: 'jobs', title: 'Jobs', description: 'Save job opportunities and analyze them against your resume.' },
  { path: 'applications', title: 'Applications', description: 'Track every application and interview.' },
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
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
