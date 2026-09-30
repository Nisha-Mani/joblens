import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/TextField'
import { ApiError } from '../../lib/api'

const schema = z.object({
  email: z.email('Enter a valid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters').max(72, 'Password is too long'),
})
export type AuthFormValues = z.infer<typeof schema>

interface AuthFormProps {
  submitLabel: string
  onSubmit: (values: AuthFormValues) => Promise<void>
  autoCompletePassword: 'current-password' | 'new-password'
}

export function AuthForm({ submitLabel, onSubmit, autoCompletePassword }: AuthFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AuthFormValues>({ resolver: zodResolver(schema) })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await onSubmit(values)
    } catch (error) {
      if (error instanceof ApiError) {
        const fields = Object.entries(error.fieldErrors)
        fields.forEach(([field, message]) => {
          if (field === 'email' || field === 'password') setError(field, { message })
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
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {formError}
        </p>
      )}
      <TextField
        label="Email"
        type="email"
        autoComplete="email"
        error={errors.email?.message}
        {...register('email')}
      />
      <TextField
        label="Password"
        type="password"
        autoComplete={autoCompletePassword}
        error={errors.password?.message}
        {...register('password')}
      />
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? 'Please wait…' : submitLabel}
      </Button>
    </form>
  )
}
