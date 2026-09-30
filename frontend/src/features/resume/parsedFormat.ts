import type { ParsedResume } from './api'

/** Editable text representations of the parsed lists, and the reverse conversions. */

export const skillsToText = (skills: string[]) => skills.join(', ')
export const textToSkills = (text: string) =>
  text.split(/[,\n]/).map((s) => s.trim()).filter(Boolean)

// Entries that can span several lines are separated by a blank line.
export const blocksToText = (entries: string[]) => entries.join('\n\n')
export const textToBlocks = (text: string) =>
  text.split(/\n\s*\n/).map((s) => s.trim()).filter(Boolean)

// One entry per line.
export const linesToText = (entries: string[]) => entries.join('\n')
export const textToLines = (text: string) =>
  text.split('\n').map((s) => s.trim()).filter(Boolean)

export interface ParsedFormValues {
  name: string
  email: string
  phone: string
  summary: string
  skills: string
  experience: string
  education: string
  projects: string
  certifications: string
}

export function toFormValues(parsed: ParsedResume): ParsedFormValues {
  return {
    name: parsed.name ?? '',
    email: parsed.email ?? '',
    phone: parsed.phone ?? '',
    summary: parsed.summary ?? '',
    skills: skillsToText(parsed.skills),
    experience: blocksToText(parsed.experience),
    education: blocksToText(parsed.education),
    projects: blocksToText(parsed.projects),
    certifications: linesToText(parsed.certifications),
  }
}

export function fromFormValues(values: ParsedFormValues): ParsedResume {
  const orNull = (v: string) => (v.trim() === '' ? null : v.trim())
  return {
    name: orNull(values.name),
    email: orNull(values.email),
    phone: orNull(values.phone),
    summary: orNull(values.summary),
    skills: textToSkills(values.skills),
    experience: textToBlocks(values.experience),
    education: textToBlocks(values.education),
    projects: textToBlocks(values.projects),
    certifications: textToLines(values.certifications),
  }
}
