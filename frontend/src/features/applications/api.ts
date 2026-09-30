import { apiDelete, apiGet, apiPost, apiPut, apiRequest } from '../../lib/api'
import type { Page } from '../jobs/api'

export const STATUSES = ['SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'] as const
export type ApplicationStatus = (typeof STATUSES)[number]

export const statusLabel = (status: ApplicationStatus) => status.charAt(0) + status.slice(1).toLowerCase()

export interface ApplicationSummary {
  id: string
  jobId: string
  company: string
  title: string
  status: ApplicationStatus
  appliedAt: string | null
  interviewDate: string | null
  updatedAt: string
}

export interface HistoryEntry {
  from: ApplicationStatus | null
  to: ApplicationStatus
  changedAt: string
}

export interface ApplicationDetail {
  id: string
  jobId: string
  company: string
  title: string
  location: string | null
  status: ApplicationStatus
  appliedAt: string | null
  interviewDate: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
  history: HistoryEntry[]
}

export interface ApplicationInput {
  jobId?: string
  status: ApplicationStatus
  appliedAt: string | null
  interviewDate: string | null
  notes: string | null
}

export type ApplicationSortField = 'updatedAt' | 'createdAt' | 'appliedAt' | 'interviewDate' | 'status' | 'company'

export interface ApplicationSearchParams {
  q: string
  status: ApplicationStatus | ''
  jobId?: string
  sortBy: ApplicationSortField
  direction: 'asc' | 'desc'
  page: number
  size?: number
}

export function searchApplications(params: ApplicationSearchParams) {
  const query = new URLSearchParams({
    sortBy: params.sortBy,
    direction: params.direction,
    page: String(params.page),
    size: String(params.size ?? 10),
  })
  if (params.q.trim()) query.set('q', params.q.trim())
  if (params.status) query.set('status', params.status)
  if (params.jobId) query.set('jobId', params.jobId)
  return apiGet<Page<ApplicationSummary>>(`/api/applications?${query}`)
}

export const getApplication = (id: string) => apiGet<ApplicationDetail>(`/api/applications/${id}`)
export const createApplication = (input: ApplicationInput) => apiPost<ApplicationDetail>('/api/applications', input)
export const updateApplication = (id: string, input: ApplicationInput) =>
  apiPut<ApplicationDetail>(`/api/applications/${id}`, input)
export const changeApplicationStatus = (id: string, status: ApplicationStatus) =>
  apiRequest<ApplicationDetail>(`/api/applications/${id}/status`, { method: 'PATCH', body: { status } })
export const deleteApplication = (id: string) => apiDelete(`/api/applications/${id}`)
