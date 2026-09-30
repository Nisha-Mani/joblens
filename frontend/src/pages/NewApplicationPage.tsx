import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../components/layout/PageHeader'
import { createApplication } from '../features/applications/api'
import { ApplicationForm } from '../features/applications/ApplicationForm'

export default function NewApplicationPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const create = useMutation({ mutationFn: createApplication })

  return (
    <div>
      <PageHeader title="Add application" description="Choose a saved job to track." />
      <ApplicationForm
        submitLabel="Save application"
        initialJobId={searchParams.get('jobId') ?? undefined}
        onCancel={() => navigate('/applications')}
        onSubmit={async (input) => {
          const application = await create.mutateAsync(input)
          await queryClient.invalidateQueries({ queryKey: ['applications'] })
          navigate(`/applications/${application.id}`)
        }}
      />
    </div>
  )
}
