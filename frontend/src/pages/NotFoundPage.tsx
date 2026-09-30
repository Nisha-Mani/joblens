import { Link } from 'react-router-dom'
import { EmptyState } from '../components/ui/EmptyState'

export default function NotFoundPage() {
  return (
    <div className="mx-auto max-w-xl px-4 py-16">
      <EmptyState
        title="Page not found"
        description="The page you are looking for does not exist."
        action={<Link to="/" className="text-sm font-medium underline">Go home</Link>}
      />
    </div>
  )
}
