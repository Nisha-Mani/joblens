import { lazy } from 'react'
import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '../components/layout/AppLayout'
import { ProtectedRoute } from '../features/auth/ProtectedRoute'
import { LoginPage, RegisterPage } from '../pages/AuthPages'
import HomePage from '../pages/HomePage'
import NotFoundPage from '../pages/NotFoundPage'

// The public pages load eagerly; the authenticated app is split into one chunk per page so a first visit
// to the login screen does not download the dashboard, charts, analysis views and so on.
const AnalyticsPage = lazy(() => import('../pages/AnalyticsPage'))
const ApplicationDetailPage = lazy(() => import('../pages/ApplicationDetailPage'))
const ApplicationsPage = lazy(() => import('../pages/ApplicationsPage'))
const DashboardPage = lazy(() => import('../pages/DashboardPage'))
const InterviewPrepPage = lazy(() => import('../pages/InterviewPrepPage'))
const JobDetailPage = lazy(() => import('../pages/JobDetailPage'))
const JobsPage = lazy(() => import('../pages/JobsPage'))
const NewApplicationPage = lazy(() => import('../pages/NewApplicationPage'))
const NewJobPage = lazy(() => import('../pages/NewJobPage'))
const ProfilePage = lazy(() => import('../pages/ProfilePage'))
const ResumePage = lazy(() => import('../pages/ResumePage'))

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="resume" element={<ResumePage />} />
          <Route path="applications" element={<ApplicationsPage />} />
          <Route path="applications/new" element={<NewApplicationPage />} />
          <Route path="applications/:id" element={<ApplicationDetailPage />} />
          <Route path="interview-prep" element={<InterviewPrepPage />} />
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
