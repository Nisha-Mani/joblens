import { apiGet, apiPost } from '../../lib/api'

export interface Analysis {
  id: string
  jobId: string
  resumeId: string | null
  resumeVersion: number
  overallScore: number
  matchingSkills: string[]
  missingSkills: string[]
  keywordGaps: string[]
  experienceAssessment: string
  suggestions: string[]
  interviewTopics: string[]
  model: string
  createdAt: string
}

export interface AnalysisSummary {
  id: string
  resumeVersion: number
  overallScore: number
  model: string
  createdAt: string
}

export const analyzeJob = (jobId: string, resumeId?: string) =>
  apiPost<Analysis>(`/api/jobs/${jobId}/analyze`, resumeId ? { resumeId } : undefined)
export const listAnalyses = (jobId: string) => apiGet<AnalysisSummary[]>(`/api/jobs/${jobId}/analyses`)
export const getAnalysis = (id: string) => apiGet<Analysis>(`/api/analyses/${id}`)

export function scoreLevel(score: number): { label: string; classes: string } {
  if (score >= 90) return { label: 'Excellent match', classes: 'bg-green-50 text-green-800 border-green-200' }
  if (score >= 70) return { label: 'Strong match', classes: 'bg-green-50 text-green-800 border-green-200' }
  if (score >= 40) return { label: 'Partial match', classes: 'bg-amber-50 text-amber-800 border-amber-200' }
  return { label: 'Weak match', classes: 'bg-red-50 text-red-800 border-red-200' }
}
