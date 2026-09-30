import { zodResolver } from '@hookform/resolvers/zod'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useForm } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'
import { z } from 'zod'
import { Button } from './Button'
import { TextField } from './TextField'

const schema = z.object({ email: z.email('Enter a valid email address') })
type Values = z.infer<typeof schema>

function DemoForm({ onSubmit }: { onSubmit: (v: Values) => void }) {
  const { register, handleSubmit, formState } = useForm<Values>({ resolver: zodResolver(schema) })
  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <TextField label="Email" error={formState.errors.email?.message} {...register('email')} />
      <Button type="submit">Save</Button>
    </form>
  )
}

describe('TextField with React Hook Form + Zod', () => {
  it('shows an accessible validation error and blocks submit', async () => {
    const onSubmit = vi.fn()
    render(<DemoForm onSubmit={onSubmit} />)
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    const input = screen.getByLabelText('Email')
    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Enter a valid email address')
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits valid values', async () => {
    const onSubmit = vi.fn()
    render(<DemoForm onSubmit={onSubmit} />)
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.com')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(onSubmit).toHaveBeenCalledWith({ email: 'a@b.com' }, expect.anything())
  })
})
