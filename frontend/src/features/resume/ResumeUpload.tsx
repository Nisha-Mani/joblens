import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type ChangeEvent } from 'react'
import { ApiError } from '../../lib/api'
import { uploadResume, validateResumeFile, type ResumeDetail } from './api'

export function ResumeUpload({ onUploaded }: { onUploaded: (resume: ResumeDetail) => void }) {
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const [clientError, setClientError] = useState<string | null>(null)

  const upload = useMutation({
    mutationFn: uploadResume,
    onSuccess: (resume) => {
      queryClient.setQueryData(['resume', resume.id], resume)
      void queryClient.invalidateQueries({ queryKey: ['resumes'] })
      onUploaded(resume)
    },
    onSettled: () => {
      if (inputRef.current) inputRef.current.value = ''
    },
  })

  function onChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    const problem = validateResumeFile(file)
    setClientError(problem)
    if (!problem) upload.mutate(file)
  }

  const error =
    clientError ??
    (upload.isError
      ? upload.error instanceof ApiError
        ? upload.error.message
        : 'Upload failed. Check your connection and try again.'
      : null)

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <label htmlFor="resume-file" className="block text-sm font-medium text-slate-700">
        Upload a resume (PDF, up to 5 MB)
      </label>
      <input
        ref={inputRef}
        id="resume-file"
        type="file"
        accept="application/pdf,.pdf"
        disabled={upload.isPending}
        onChange={onChange}
        className="mt-2 block w-full text-sm file:mr-3 file:rounded-md file:border-0 file:bg-slate-900 file:px-4 file:py-2 file:text-sm file:font-medium file:text-white hover:file:bg-slate-800"
      />
      {upload.isPending && <p role="status" className="mt-2 text-sm text-slate-600">Uploading and analyzing…</p>}
      {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
    </div>
  )
}
