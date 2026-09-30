import { apiDelete, apiGet, apiPost, apiPut } from '../../lib/api'

export const PROFICIENCIES = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'] as const
export type Proficiency = (typeof PROFICIENCIES)[number]

export const SKILL_CATEGORIES = [
  'LANGUAGE', 'FRAMEWORK', 'DATABASE', 'CLOUD', 'DEVOPS', 'TESTING', 'TOOL', 'SOFT', 'OTHER',
] as const
export type SkillCategory = (typeof SKILL_CATEGORIES)[number]

export interface Profile {
  name: string | null
  headline: string | null
  location: string | null
  yearsOfExperience: number | null
  summary: string | null
}

export interface UserSkill {
  skillId: string
  name: string
  category: SkillCategory
  proficiency: Proficiency
}

export interface SkillSuggestion {
  name: string
  category: SkillCategory
}

export const getProfile = () => apiGet<Profile>('/api/users/me/profile')
export const updateProfile = (profile: Profile) => apiPut<Profile>('/api/users/me/profile', profile)
export const getSkills = () => apiGet<UserSkill[]>('/api/users/me/skills')
export const addSkill = (skill: { name: string; category?: SkillCategory; proficiency: Proficiency }) =>
  apiPost<UserSkill>('/api/users/me/skills', skill)
export const removeSkill = (skillId: string) => apiDelete(`/api/users/me/skills/${skillId}`)
export const getSuggestions = (q: string) =>
  apiGet<SkillSuggestion[]>(`/api/skills/suggestions?q=${encodeURIComponent(q)}`)
