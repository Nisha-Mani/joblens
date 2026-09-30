import { apiDelete, apiGet, apiPost, apiPut } from '../../lib/api'

export const EMPLOYMENT_TYPES = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'TEMPORARY', 'OTHER'] as const
export type EmploymentType = (typeof EMPLOYMENT_TYPES)[number]

export const employmentLabel = (type: EmploymentType) =>
  type.split('_').map((w) => w.charAt(0) + w.slice(1).toLowerCase()).join(' ')

export interface JobSummary {
  id: string
  company: string
  title: string
  location: string | null
  employmentType: EmploymentType
  createdAt: string
}

export interface Job extends JobSummary {
  jobDescription: string
  sourceUrl: string | null
  updatedAt: string
}

export interface JobInput {
  company: string
  title: string
  location: string | null
  employmentType: EmploymentType
  jobDescription: string
  sourceUrl: string | null
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type JobSortField = 'createdAt' | 'company' | 'title'

export interface JobSearchParams {
  q: string
  employmentType: EmploymentType | ''
  sortBy: JobSortField
  direction: 'asc' | 'desc'
  page: number
  size?: number
}

export function searchJobs(params: JobSearchParams) {
  const query = new URLSearchParams({
    sortBy: params.sortBy,
    direction: params.direction,
    page: String(params.page),
    size: String(params.size ?? 10),
  })
  if (params.q.trim()) query.set('q', params.q.trim())
  if (params.employmentType) query.set('employmentType', params.employmentType)
  return apiGet<Page<JobSummary>>(`/api/jobs?${query}`)
}

export const getJob = (id: string) => apiGet<Job>(`/api/jobs/${id}`)
export const createJob = (input: JobInput) => apiPost<Job>('/api/jobs', input)
export const updateJob = (id: string, input: JobInput) => apiPut<Job>(`/api/jobs/${id}`, input)
export const deleteJob = (id: string) => apiDelete(`/api/jobs/${id}`)
