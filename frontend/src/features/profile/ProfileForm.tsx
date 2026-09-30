import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/TextField'
import { ApiError } from '../../lib/api'
import { updateProfile, type Profile } from './api'

const schema = z.object({
  name: z.string().max(120, 'Name must be at most 120 characters'),
  headline: z.string().max(160, 'Headline must be at most 160 characters'),
  location: z.string().max(120, 'Location must be at most 120 characters'),
  yearsOfExperience: z
    .string()
    .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) <= 60), 'Enter a whole number from 0 to 60'),
  summary: z.string().max(2000, 'Summary must be at most 2000 characters'),
})
type FormValues = z.infer<typeof schema>

function toFormValues(profile: Profile): FormValues {
  return {
    name: profile.name ?? '',
    headline: profile.headline ?? '',
    location: profile.location ?? '',
    yearsOfExperience: profile.yearsOfExperience?.toString() ?? '',
    summary: profile.summary ?? '',
  }
}

export function ProfileForm({ profile }: { profile: Profile }) {
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toFormValues(profile) })

  useEffect(() => reset(toFormValues(profile)), [profile, reset])

  const save = useMutation({
    mutationFn: (values: FormValues) =>
      updateProfile({
        name: values.name,
        headline: values.headline,
        location: values.location,
        yearsOfExperience: values.yearsOfExperience === '' ? null : Number(values.yearsOfExperience),
        summary: values.summary,
      }),
    onSuccess: (saved) => queryClient.setQueryData(['profile'], saved),
  })

  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Name" autoComplete="name" error={errors.name?.message} {...register('name')} />
        <TextField label="Headline" placeholder="e.g. Full-stack engineer" error={errors.headline?.message} {...register('headline')} />
        <TextField label="Location" autoComplete="address-level2" error={errors.location?.message} {...register('location')} />
        <TextField label="Years of experience" inputMode="numeric" error={errors.yearsOfExperience?.message} {...register('yearsOfExperience')} />
      </div>
      <div>
        <label htmlFor="summary" className="block text-sm font-medium text-slate-700">Summary</label>
        <textarea
          id="summary"
          rows={4}
          aria-invalid={errors.summary ? true : undefined}
          className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900"
          {...register('summary')}
        />
        {errors.summary && <p className="mt-1 text-sm text-red-700">{errors.summary.message}</p>}
      </div>

      {save.isError && (
        <p role="alert" className="text-sm text-red-700">
          {save.error instanceof ApiError ? save.error.message : 'Could not save your profile. Try again.'}
        </p>
      )}
      {save.isSuccess && !isDirty && (
        <p role="status" className="text-sm text-green-700">Profile saved.</p>
      )}
      <Button type="submit" disabled={save.isPending}>
        {save.isPending ? 'Saving…' : 'Save profile'}
      </Button>
    </form>
  )
}
