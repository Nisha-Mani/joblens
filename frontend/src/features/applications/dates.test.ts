import { describe, expect, it } from 'vitest'
import { formatDate, fromDateTimeLocal, toDateTimeLocal } from './dates'

describe('dates', () => {
  it('round-trips a local datetime through ISO', () => {
    const local = '2026-10-15T14:30'
    const iso = fromDateTimeLocal(local)
    expect(iso).not.toBeNull()
    expect(toDateTimeLocal(iso)).toBe(local)
  })

  it('returns null/empty for missing or invalid values', () => {
    expect(fromDateTimeLocal('')).toBeNull()
    expect(fromDateTimeLocal('not a date')).toBeNull()
    expect(toDateTimeLocal(null)).toBe('')
    expect(toDateTimeLocal('garbage')).toBe('')
  })

  it('formats date-only values without shifting the calendar day', () => {
    expect(formatDate('2026-03-01')).toContain('1')
    expect(formatDate('2026-03-01')).not.toContain('Feb')
    expect(formatDate(null)).toBe('—')
  })
})
