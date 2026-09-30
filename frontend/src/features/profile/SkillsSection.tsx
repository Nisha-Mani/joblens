import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Spinner } from '../../components/ui/Spinner'
import { ApiError } from '../../lib/api'
import {
  PROFICIENCIES, SKILL_CATEGORIES, addSkill, getSkills, getSuggestions, removeSkill,
  type Proficiency, type SkillCategory,
} from './api'

const label = (value: string) => value.charAt(0) + value.slice(1).toLowerCase()

export function SkillsSection() {
  const queryClient = useQueryClient()
  const skills = useQuery({ queryKey: ['skills'], queryFn: getSkills })

  const [name, setName] = useState('')
  const [proficiency, setProficiency] = useState<Proficiency>('INTERMEDIATE')
  const [category, setCategory] = useState<SkillCategory>('OTHER')

  const suggestions = useQuery({
    queryKey: ['skill-suggestions', name.trim()],
    queryFn: () => getSuggestions(name.trim()),
    enabled: name.trim().length > 0,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['skills'] })
  const add = useMutation({
    mutationFn: addSkill,
    onSuccess: () => {
      setName('')
      return invalidate()
    },
  })
  const remove = useMutation({ mutationFn: removeSkill, onSuccess: invalidate })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) return
    add.mutate({ name: name.trim(), category, proficiency })
  }

  return (
    <section aria-labelledby="skills-heading" className="space-y-4">
      <h2 id="skills-heading" className="text-lg font-semibold">Skills</h2>

      <form onSubmit={submit} className="flex flex-wrap items-end gap-3" aria-label="Add skill">
        <div className="min-w-48 flex-1">
          <label htmlFor="skill-name" className="block text-sm font-medium text-slate-700">Skill</label>
          <input
            id="skill-name"
            list="skill-suggestions"
            value={name}
            maxLength={60}
            onChange={(e) => setName(e.target.value)}
            className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900"
          />
          <datalist id="skill-suggestions">
            {suggestions.data?.map((s) => <option key={s.name} value={s.name} />)}
          </datalist>
        </div>
        <div>
          <label htmlFor="skill-category" className="block text-sm font-medium text-slate-700">Category</label>
          <select id="skill-category" value={category} onChange={(e) => setCategory(e.target.value as SkillCategory)}
            className="mt-1 block rounded-md border border-slate-300 bg-white px-3 py-2 text-sm">
            {SKILL_CATEGORIES.map((c) => <option key={c} value={c}>{label(c)}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="skill-proficiency" className="block text-sm font-medium text-slate-700">Proficiency</label>
          <select id="skill-proficiency" value={proficiency} onChange={(e) => setProficiency(e.target.value as Proficiency)}
            className="mt-1 block rounded-md border border-slate-300 bg-white px-3 py-2 text-sm">
            {PROFICIENCIES.map((p) => <option key={p} value={p}>{label(p)}</option>)}
          </select>
        </div>
        <Button type="submit" disabled={add.isPending || !name.trim()}>Add skill</Button>
      </form>

      {(add.isError || remove.isError) && (
        <p role="alert" className="text-sm text-red-700">
          {(add.error ?? remove.error) instanceof ApiError
            ? (add.error ?? remove.error)?.message
            : 'Something went wrong. Try again.'}
        </p>
      )}

      {skills.isPending && <Spinner label="Loading skills…" />}
      {skills.isError && <ErrorState message="Could not load your skills." onRetry={() => void skills.refetch()} />}
      {skills.isSuccess && skills.data.length === 0 && (
        <EmptyState title="No skills yet" description="Add the technologies you work with. They are used to compare you against job descriptions." />
      )}
      {skills.isSuccess && skills.data.length > 0 && (
        <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
          {skills.data.map((skill) => (
            <li key={skill.skillId} className="flex items-center justify-between gap-3 px-4 py-2 text-sm">
              <span>
                <span className="font-medium">{skill.name}</span>
                <span className="ml-2 text-slate-500">{label(skill.category)} · {label(skill.proficiency)}</span>
              </span>
              <Button variant="secondary" aria-label={`Remove ${skill.name}`}
                disabled={remove.isPending} onClick={() => remove.mutate(skill.skillId)}>
                Remove
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
