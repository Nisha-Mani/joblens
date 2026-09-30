import { apiDelete, apiGet, apiPut, apiUpload } from '../../lib/api'

export interface ParsedResume {
  name: string | null
  email: string | null
  phone: string | null
  summary: string | null
  skills: string[]
  experience: string[]
  education: string[]
  projects: string[]
  certifications: string[]
}

export interface ResumeSummary {
  id: string
  fileName: string
  version: number
  sizeBytes: number
  createdAt: string
}

export interface ResumeDetail extends ResumeSummary {
  extractedTextLength: number
  updatedAt: string
  parsed: ParsedResume
}

export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024

export const listResumes = () => apiGet<ResumeSummary[]>('/api/resumes')
export const getResume = (id: string) => apiGet<ResumeDetail>(`/api/resumes/${id}`)
export const deleteResume = (id: string) => apiDelete(`/api/resumes/${id}`)
export const updateParsed = (id: string, parsed: ParsedResume) =>
  apiPut<ResumeDetail>(`/api/resumes/${id}/parsed`, parsed)

export function uploadResume(file: File) {
  const form = new FormData()
  form.append('file', file)
  return apiUpload<ResumeDetail>('/api/resumes', form)
}

/** Client-side pre-check for fast feedback; the server remains the source of truth. */
export function validateResumeFile(file: File): string | null {
  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
  if (!isPdf) return 'Only PDF files are supported.'
  if (file.size === 0) return 'The selected file is empty.'
  if (file.size > MAX_UPLOAD_BYTES) return 'The file is too large. Maximum size is 5 MB.'
  return null
}
