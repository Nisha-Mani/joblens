import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../components/layout/PageHeader'
import { createJob } from '../features/jobs/api'
import { JobForm } from '../features/jobs/JobForm'

export default function NewJobPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const create = useMutation({ mutationFn: createJob })

  return (
    <div>
      <PageHeader title="Add job" description="Save a role and paste its description." />
      <JobForm
        submitLabel="Save job"
        onCancel={() => navigate('/jobs')}
        onSubmit={async (input) => {
          const job = await create.mutateAsync(input)
          await queryClient.invalidateQueries({ queryKey: ['jobs'] })
          navigate(`/jobs/${job.id}`)
        }}
      />
    </div>
  )
}
