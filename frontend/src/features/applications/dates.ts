const pad = (n: number) => String(n).padStart(2, '0')

/** ISO instant → value for <input type="datetime-local"> in the user's local time. */
export function toDateTimeLocal(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** datetime-local value (local time) → ISO instant, or null when empty/invalid. */
export function fromDateTimeLocal(value: string): string | null {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}

export function formatDate(value: string | null): string {
  if (!value) return '—'
  // Date-only strings (YYYY-MM-DD) are shown as-is in local calendar terms, avoiding timezone shifts.
  const d = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value)
  return d.toLocaleDateString(undefined, { dateStyle: 'medium' })
}

export function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
