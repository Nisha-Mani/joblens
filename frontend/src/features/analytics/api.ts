import { apiGet } from '../../lib/api'
import type { ApplicationStatus } from '../applications/api'

export interface Totals {
  tracked: number
  applied: number
  appliedThisMonth: number
  responded: number
  interviews: number
  offers: number
  rejections: number
}

export interface Rates {
  responseRate: number | null
  interviewRate: number | null
  offerRate: number | null
}

export interface Dashboard {
  totals: Totals
  rates: Rates
  statusDistribution: { status: ApplicationStatus; count: number }[]
  applicationsByMonth: { month: string; count: number }[]
  upcomingInterviews: { applicationId: string; company: string; title: string; status: ApplicationStatus; interviewDate: string }[]
  recentApplications: { applicationId: string; company: string; title: string; status: ApplicationStatus; updatedAt: string }[]
  topMissingSkills: { skill: string; count: number }[]
}

export const getDashboard = (months = 12) => apiGet<Dashboard>(`/api/analytics/dashboard?months=${months}`)

/** Fractions become whole percentages; null means there was nothing to divide by. */
export const formatRate = (rate: number | null) => (rate === null ? '—' : `${Math.round(rate * 100)}%`)

export function monthLabel(month: string) {
  const [year, m] = month.split('-').map(Number)
  return new Date(Date.UTC(year, m - 1, 1)).toLocaleDateString(undefined, { month: 'short', timeZone: 'UTC' })
}

export function monthLabelLong(month: string) {
  const [year, m] = month.split('-').map(Number)
  return new Date(Date.UTC(year, m - 1, 1)).toLocaleDateString(undefined, { month: 'long', year: 'numeric', timeZone: 'UTC' })
}
