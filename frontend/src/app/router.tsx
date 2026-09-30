import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '../components/layout/AppLayout'
import { ProtectedRoute } from '../features/auth/ProtectedRoute'
import { LoginPage, RegisterPage } from '../pages/AuthPages'
import AnalyticsPage from '../pages/AnalyticsPage'
import ApplicationDetailPage from '../pages/ApplicationDetailPage'
import ApplicationsPage from '../pages/ApplicationsPage'
import DashboardPage from '../pages/DashboardPage'
import HomePage from '../pages/HomePage'
import InterviewPrepPage from '../pages/InterviewPrepPage'
import JobDetailPage from '../pages/JobDetailPage'
import JobsPage from '../pages/JobsPage'
import NewApplicationPage from '../pages/NewApplicationPage'
import NewJobPage from '../pages/NewJobPage'
import NotFoundPage from '../pages/NotFoundPage'
import ProfilePage from '../pages/ProfilePage'
import ResumePage from '../pages/ResumePage'

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
