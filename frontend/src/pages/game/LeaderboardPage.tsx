import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Star } from 'lucide-react'
import { gameApi } from '@/api/game'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { LeaderboardChase, type LeaderboardSelf } from '@/components/game/leaderboard/LeaderboardChase'
import { Header } from '@/components/layout/Header'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useTranslation } from '@/i18n'
import { useAuthStore } from '@/store/authStore'
import type { LeaderboardEntry } from '@/types'
import { HomeLegalStrip } from './home/HomeLegalStrip'

const LEADERBOARD_SIZE = 50

export function LeaderboardPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { user } = useAuthStore()

  const { data, isLoading } = useQuery({
    queryKey: ['leaderboard', 0, LEADERBOARD_SIZE],
    queryFn: () => gameApi.getLeaderboard(0, LEADERBOARD_SIZE),
  })

  const { data: profile, isLoading: isProfileLoading } = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: () => gameApi.getProfile(),
    enabled: !!user,
  })

  const entries = (data?.entries ?? []).slice(0, LEADERBOARD_SIZE)
  const self: LeaderboardSelf = {
    isAuthenticated: Boolean(user),
    isLoading: isProfileLoading,
    username: user?.username,
    displayName: profile?.displayName || user?.username || user?.email || 'Player',
    avatarUrl: profile?.avatarUrl,
    rank: profile?.leaderboardRank ?? null,
    totalXp: profile?.totalXp ?? null,
    level: profile?.level ?? null,
    onLogin: () => navigate('/login'),
  }

  const handleEntryClick = (entry: LeaderboardEntry) => {
    if (entry.username) navigate(`/profile/${entry.username}`)
  }

  return (
    <div className="relative min-h-screen bg-background text-white">
      <GameAuroraBackdrop />
      <Header />
      <main className="relative mx-auto w-full max-w-[1500px] space-y-5 px-4 pb-28 pt-5 sm:px-7 sm:pb-28 sm:pt-7 xl:px-10">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-[.22em] text-emerald-200/65">{t.leaderboard.subtitle}</p>
          <h1 className="mt-1 text-2xl font-black tracking-[-.05em] text-white sm:text-3xl">{t.leaderboard.title}</h1>
        </div>
        {isLoading ? (
          <section className="space-y-4" aria-label="Loading leaderboard">
            <Skeleton variant="rect" height={205} className="rounded-[1.6rem]" />
            <Skeleton variant="rect" height={460} className="rounded-2xl" />
          </section>
        ) : !entries.length ? (
          <EmptyState icon={Star} title={t.common.noResults} className="py-16" />
        ) : (
          <LeaderboardChase entries={entries} self={self} onEntryClick={handleEntryClick} />
        )}
      </main>
      <HomeLegalStrip />
    </div>
  )
}
