import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/TextField'
import { ApiError } from '../../lib/api'
import { EMPLOYMENT_TYPES, employmentLabel, type Job, type JobInput } from './api'

const schema = z.object({
  company: z.string().trim().min(1, 'Company is required').max(150, 'Company must be at most 150 characters'),
  title: z.string().trim().min(1, 'Title is required').max(150, 'Title must be at most 150 characters'),
  location: z.string().max(150, 'Location must be at most 150 characters'),
  employmentType: z.enum(EMPLOYMENT_TYPES),
  jobDescription: z
    .string()
    .trim()
    .min(1, 'Job description is required')
    .max(20000, 'Job description must be at most 20000 characters'),
  sourceUrl: z
    .string()
    .max(2048, 'Source URL is too long')
    .refine((v) => v === '' || /^https?:\/\/\S+$/.test(v), 'Source URL must start with http:// or https://'),
})
type FormValues = z.infer<typeof schema>

interface JobFormProps {
  job?: Job
  submitLabel: string
  onSubmit: (input: JobInput) => Promise<void>
  onCancel?: () => void
}

export function JobForm({ job, submitLabel, onSubmit, onCancel }: JobFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      company: job?.company ?? '',
      title: job?.title ?? '',
      location: job?.location ?? '',
      employmentType: job?.employmentType ?? 'FULL_TIME',
      jobDescription: job?.jobDescription ?? '',
      sourceUrl: job?.sourceUrl ?? '',
    },
  })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await onSubmit({
        ...values,
        location: values.location.trim() || null,
        sourceUrl: values.sourceUrl.trim() || null,
      })
    } catch (error) {
      if (error instanceof ApiError) {
        const fields = Object.entries(error.fieldErrors)
        fields.forEach(([field, message]) => {
          if (field in schema.shape) setError(field as keyof FormValues, { message })
        })
        if (fields.length === 0) setFormError(error.message)
      } else {
        setFormError('Could not reach the server. Check your connection and try again.')
      }
    }
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {formError && (
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">{formError}</p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Company" error={errors.company?.message} {...register('company')} />
        <TextField label="Job title" error={errors.title?.message} {...register('title')} />
        <TextField label="Location" placeholder="e.g. Remote, Berlin" error={errors.location?.message} {...register('location')} />
        <div>
          <label htmlFor="employmentType" className="block text-sm font-medium text-slate-700">Employment type</label>
          <select id="employmentType" className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" {...register('employmentType')}>
            {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{employmentLabel(t)}</option>)}
          </select>
        </div>
      </div>
      <TextField label="Source URL (optional)" type="url" placeholder="https://" error={errors.sourceUrl?.message} {...register('sourceUrl')} />
      <div>
        <label htmlFor="jobDescription" className="block text-sm font-medium text-slate-700">Job description</label>
        <p className="text-xs text-slate-500">Paste the full posting. It is used to compare the role against your resume.</p>
        <textarea
          id="jobDescription"
          rows={10}
          aria-invalid={errors.jobDescription ? true : undefined}
          aria-describedby={errors.jobDescription ? 'jobDescription-error' : undefined}
          className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900 aria-[invalid=true]:border-red-500"
          {...register('jobDescription')}
        />
        {errors.jobDescription && (
          <p id="jobDescription-error" className="mt-1 text-sm text-red-700">{errors.jobDescription.message}</p>
        )}
      </div>
      <div className="flex gap-3">
        <Button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Saving…' : submitLabel}</Button>
        {onCancel && <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  )
}
