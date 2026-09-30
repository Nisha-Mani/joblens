import { apiDelete, apiGet, apiPost, apiPut } from '../../lib/api'
import type { Page } from '../jobs/api'

export const CATEGORIES = ['TECHNICAL', 'BEHAVIORAL', 'PROJECT', 'ROLE_SPECIFIC'] as const
export type InterviewCategory = (typeof CATEGORIES)[number]
export const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'] as const
export type Difficulty = (typeof DIFFICULTIES)[number]
export const PREP_STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'PREPARED'] as const
export type PrepStatus = (typeof PREP_STATUSES)[number]

export const label = (value: string) =>
  value.split('_').map((w) => w.charAt(0) + w.slice(1).toLowerCase()).join(' ')

export interface InterviewQuestion {
  id: string
  jobId: string
  company: string
  jobTitle: string
  question: string
  category: InterviewCategory
  difficulty: Difficulty
  skills: string[]
  notes: string | null
  status: PrepStatus
  generated: boolean
  createdAt: string
  updatedAt: string
}

export interface QuestionSearchParams {
  jobId: string
  category: InterviewCategory | ''
  status: PrepStatus | ''
  page: number
  size?: number
}

export function searchQuestions(params: QuestionSearchParams) {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size ?? 20) })
  if (params.jobId) query.set('jobId', params.jobId)
  if (params.category) query.set('category', params.category)
  if (params.status) query.set('status', params.status)
  return apiGet<Page<InterviewQuestion>>(`/api/interviews/questions?${query}`)
}

export const generateQuestions = (jobId: string) =>
  apiPost<InterviewQuestion[]>(`/api/jobs/${jobId}/interview-questions/generate`)
export const addQuestion = (input: { jobId: string; question: string; category: InterviewCategory; difficulty: Difficulty }) =>
  apiPost<InterviewQuestion>('/api/interviews/questions', input)
export const updatePrep = (id: string, input: { notes: string | null; status: PrepStatus }) =>
  apiPut<InterviewQuestion>(`/api/interviews/questions/${id}`, input)
export const deleteQuestion = (id: string) => apiDelete(`/api/interviews/questions/${id}`)
