import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../components/layout/PageHeader'
import { ErrorState } from '../components/ui/ErrorState'
import { Spinner } from '../components/ui/Spinner'
import { getProfile } from '../features/profile/api'
import { ProfileForm } from '../features/profile/ProfileForm'
import { SkillsSection } from '../features/profile/SkillsSection'

export default function ProfilePage() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: getProfile })

  return (
    <div className="space-y-10">
      <div>
        <PageHeader title="Profile" description="Your professional details and skills, used when analyzing jobs." />
        {profile.isPending && <Spinner label="Loading profile…" />}
        {profile.isError && <ErrorState message="Could not load your profile." onRetry={() => void profile.refetch()} />}
        {profile.isSuccess && <ProfileForm profile={profile.data} />}
      </div>
      <SkillsSection />
    </div>
  )
}
