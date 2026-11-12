import { UserRound } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ProfileGlassPanel } from '@/components/game/profile/ProfileGlassPanel'
import { ProfileOverview } from '@/components/game/profile/ProfileOverview'
import { Skeleton } from '@/components/ui/Skeleton'
import { useGuestProfile, useGuestProgress } from '@/hooks/useGameQueries'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'

export function GuestProfileContent() {
  const { t } = useTranslation()
  const { data: progress, isLoading: progressLoading } = useGuestProgress(true)
  const hasProgress = !!progress?.hasGuest && progress.playCount + progress.budgetPlayCount > 0
  const { data: guestProfile, isLoading: profileLoading } = useGuestProfile(hasProgress, progress?.expiresAt)

  useSeoMeta({
    title: `${t.guestProfile.title} | Clueterra`,
    description: t.guestProfile.subtitle,
    canonicalPath: null,
    robots: 'noindex,nofollow',
  })

  if (progressLoading || (hasProgress && profileLoading)) {
    return (
      <div className="grid gap-6 2xl:grid-cols-2 2xl:items-stretch">
        <div className="space-y-6">
          <Skeleton variant="rect" height={320} />
          <Skeleton variant="rect" height={180} />
          <Skeleton variant="rect" height={160} />
        </div>
        <Skeleton variant="rect" height={760} />
      </div>
    )
  }

  if (!hasProgress || !progress) {
    return (
      <ProfileGlassPanel className="mx-auto max-w-2xl px-6 py-14 text-center sm:px-10">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl border border-emerald-300/20 bg-emerald-300/[.08] text-emerald-200">
          <UserRound size={28} />
        </div>
        <h1 className="mt-6 text-3xl font-black tracking-tight">{t.guestProfile.emptyTitle}</h1>
        <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-white/50">{t.guestProfile.emptyBody}</p>
        <div className="mt-7 flex flex-wrap justify-center gap-3">
          <Link to="/" className="rounded-xl bg-emerald-300 px-5 py-3 text-sm font-bold text-emerald-950 transition hover:bg-emerald-200">{t.guestProfile.playCountries}</Link>
          <Link to="/budget" className="rounded-xl border border-white/10 px-5 py-3 text-sm font-semibold text-white/70 transition hover:bg-white/[.06]">{t.guestProfile.playBudget}</Link>
        </div>
      </ProfileGlassPanel>
    )
  }

  if (!guestProfile) {
    return <p className="py-20 text-center text-white/40">{t.common.noResults}</p>
  }

  const level = Math.max(1, progress.currentLevel)
  const divisor = Math.max(1, progress.levelDivisor)
  const profile = {
    ...guestProfile,
    totalXp: progress.totalXp,
    level,
    levelDivisor: divisor,
    xpForCurrentLevel: (level - 1) ** 2 * divisor,
    xpForNextLevel: level ** 2 * divisor,
  }
  return <ProfileOverview profile={profile} guest />
}
