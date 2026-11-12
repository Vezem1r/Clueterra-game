import { AchievementsSection } from '@/components/game/AchievementsSection'
import { BudgetProfileStatsGrid } from '@/components/game/BudgetProfileStatsGrid'
import { ProfileHeader } from '@/components/game/ProfileHeader'
import { ProfileStatsGrid } from '@/components/game/ProfileStatsGrid'
import { ProfileStreakPanel } from '@/components/game/ProfileStreakPanel'
import { ProfileXpPanel } from '@/components/game/ProfileXpPanel'
import type { ProfileDto } from '@/types'

interface ProfileOverviewProps {
  profile: ProfileDto
  guest?: boolean
  email?: string
  showSettings?: boolean
  onSettingsClick?: () => void
}

export function ProfileOverview({
  profile,
  guest = false,
  email,
  showSettings,
  onSettingsClick,
}: ProfileOverviewProps) {
  return (
    <div className="grid gap-6 2xl:grid-cols-2 2xl:items-stretch">
      <div className="order-1 space-y-6">
        <ProfileHeader
          profile={profile}
          guest={guest}
          email={email}
          showSettings={showSettings}
          onSettingsClick={onSettingsClick}
        />
        <ProfileXpPanel profile={profile} />
        <ProfileStreakPanel profile={profile} isOwnProfile />
        <ProfileStatsGrid profile={profile} />
        <BudgetProfileStatsGrid profile={profile} />
      </div>
      <AchievementsSection mode={guest ? 'guest' : 'own'} className="order-2 2xl:h-full" />
    </div>
  )
}
