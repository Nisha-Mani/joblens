import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ApiError } from '../../lib/api'
import { PREP_STATUSES, deleteQuestion, label, updatePrep, type InterviewQuestion, type PrepStatus } from './api'

const difficultyStyle = {
  EASY: 'bg-green-50 text-green-800',
  MEDIUM: 'bg-amber-50 text-amber-800',
  HARD: 'bg-red-50 text-red-800',
} as const

export function QuestionCard({ question, showJob }: { question: InterviewQuestion; showJob: boolean }) {
  const queryClient = useQueryClient()
  const [notes, setNotes] = useState(question.notes ?? '')
  const [status, setStatus] = useState<PrepStatus>(question.status)
  const dirty = notes.trim() !== (question.notes ?? '') || status !== question.status

  const save = useMutation({
    mutationFn: (next: { notes: string; status: PrepStatus }) =>
      updatePrep(question.id, { notes: next.notes.trim() || null, status: next.status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['questions'] }),
  })
  const remove = useMutation({
    mutationFn: () => deleteQuestion(question.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['questions'] }),
  })

  const error = save.error ?? remove.error
  const notesId = `notes-${question.id}`
  const statusId = `status-${question.id}`

  return (
    <li className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="rounded-full bg-slate-100 px-2.5 py-0.5 font-medium text-slate-700">{label(question.category)}</span>
        <span className={`rounded-full px-2.5 py-0.5 font-medium ${difficultyStyle[question.difficulty]}`}>{label(question.difficulty)}</span>
        {question.skills.map((skill) => (
          <span key={skill} className="rounded-full border border-slate-200 px-2.5 py-0.5 text-slate-600">{skill}</span>
        ))}
        {showJob && <span className="text-slate-500">· {question.jobTitle} at {question.company}</span>}
      </div>

      <p className="text-sm font-medium leading-relaxed">{question.question}</p>

      <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
        <div>
          <label htmlFor={notesId} className="block text-xs font-medium text-slate-600">Your notes</label>
          <textarea id={notesId} rows={3} maxLength={5000} value={notes} onChange={(e) => setNotes(e.target.value)}
            placeholder="Outline your answer, key examples, numbers to mention…"
            className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" />
        </div>
        <div>
          <label htmlFor={statusId} className="block text-xs font-medium text-slate-600">Preparation</label>
          <select id={statusId} value={status} onChange={(e) => setStatus(e.target.value as PrepStatus)}
            className="mt-1 block rounded-md border border-slate-300 bg-white px-3 py-2 text-sm">
            {PREP_STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
        </div>
      </div>

      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error instanceof ApiError ? error.message : 'Something went wrong. Try again.'}
        </p>
      )}
      <div className="flex items-center gap-2">
        <Button disabled={!dirty || save.isPending} onClick={() => save.mutate({ notes, status })}
          aria-label={`Save notes for: ${question.question}`}>
          {save.isPending ? 'Saving…' : 'Save'}
        </Button>
        {save.isSuccess && !dirty && <span role="status" className="text-sm text-green-700">Saved</span>}
        <Button variant="secondary" disabled={remove.isPending} aria-label={`Delete question: ${question.question}`}
          onClick={() => { if (window.confirm('Delete this question and your notes?')) remove.mutate() }}>
          Delete
        </Button>
      </div>
    </li>
  )
}
