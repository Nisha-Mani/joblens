import { describe, expect, it } from 'vitest'
import type { ParsedResume } from './api'
import { fromFormValues, textToBlocks, textToLines, textToSkills, toFormValues } from './parsedFormat'

describe('parsedFormat', () => {
  it('splits skills by comma and newline, trimming blanks', () => {
    expect(textToSkills(' Java, , React\nDocker ')).toEqual(['Java', 'React', 'Docker'])
  })

  it('splits blocks on blank lines but keeps multi-line entries together', () => {
    expect(textToBlocks('Role A\nDetails\n\n  \nRole B')).toEqual(['Role A\nDetails', 'Role B'])
  })

  it('splits lines one per entry', () => {
    expect(textToLines('AWS\n\n GCP ')).toEqual(['AWS', 'GCP'])
  })

  it('round-trips parsed data through form values', () => {
    const parsed: ParsedResume = {
      name: 'Jane', email: 'j@x.com', phone: null, summary: null,
      skills: ['Java', 'React'], experience: ['A\nB', 'C'], education: [], projects: [],
      certifications: ['AWS'],
    }
    expect(fromFormValues(toFormValues(parsed))).toEqual(parsed)
  })

  it('converts empty strings to null', () => {
    const values = toFormValues({
      name: null, email: null, phone: null, summary: null,
      skills: [], experience: [], education: [], projects: [], certifications: [],
    })
    expect(fromFormValues({ ...values, name: '   ' }).name).toBeNull()
  })
})
