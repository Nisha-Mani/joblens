import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/TextField'
import { ApiError } from '../../lib/api'
import { updateParsed, type ResumeDetail } from './api'
import { fromFormValues, toFormValues, type ParsedFormValues } from './parsedFormat'

const schema = z.object({
  name: z.string().max(120, 'Name must be at most 120 characters'),
  email: z.string().refine((v) => v === '' || z.email().safeParse(v).success, 'Enter a valid email address'),
  phone: z.string().max(40, 'Phone must be at most 40 characters'),
  summary: z.string().max(3000, 'Summary must be at most 3000 characters'),
  skills: z.string(),
  experience: z.string(),
  education: z.string(),
  projects: z.string(),
  certifications: z.string(),
})

function TextArea({ id, label, hint, rows = 4, register }: {
  id: keyof ParsedFormValues
  label: string
  hint?: string
  rows?: number
  register: ReturnType<typeof useForm<ParsedFormValues>>['register']
}) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">{label}</label>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
      <textarea
        id={id}
        rows={rows}
        className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900"
        {...register(id)}
      />
    </div>
  )
}

export function ParsedResumeForm({ resume }: { resume: ResumeDetail }) {
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ParsedFormValues>({
    resolver: zodResolver(schema),
    defaultValues: toFormValues(resume.parsed),
  })

  // Reset when a different resume (or freshly saved data) arrives.
  useEffect(() => reset(toFormValues(resume.parsed)), [resume, reset])

  const save = useMutation({
    mutationFn: (values: ParsedFormValues) => updateParsed(resume.id, fromFormValues(values)),
    onSuccess: (saved) => queryClient.setQueryData(['resume', saved.id], saved),
  })

  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="space-y-4">
      <p className="text-sm text-slate-600">
        These details were extracted automatically and may contain mistakes. Correct anything that looks wrong.
      </p>
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField label="Name" error={errors.name?.message} {...register('name')} />
        <TextField label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <TextField label="Phone" error={errors.phone?.message} {...register('phone')} />
      </div>
      <TextArea id="summary" label="Summary" register={register} />
      <TextArea id="skills" label="Skills" hint="Separate with commas" rows={3} register={register} />
      <TextArea id="experience" label="Experience" hint="Separate entries with a blank line" rows={6} register={register} />
      <TextArea id="education" label="Education" hint="Separate entries with a blank line" rows={3} register={register} />
      <TextArea id="projects" label="Projects" hint="Separate entries with a blank line" rows={3} register={register} />
      <TextArea id="certifications" label="Certifications" hint="One per line" rows={3} register={register} />

      {save.isError && (
        <p role="alert" className="text-sm text-red-700">
          {save.error instanceof ApiError ? save.error.message : 'Could not save changes. Try again.'}
        </p>
      )}
      {save.isSuccess && !isDirty && <p role="status" className="text-sm text-green-700">Changes saved.</p>}
      <Button type="submit" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save changes'}</Button>
    </form>
  )
}
