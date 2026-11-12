import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Flag } from 'lucide-react'
import { gameApi } from '@/api/game'
import { AchievementsSection } from '@/components/game/AchievementsSection'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { ProfileHeader } from '@/components/game/ProfileHeader'
import { ProfileStatsGrid } from '@/components/game/ProfileStatsGrid'
import { BudgetProfileStatsGrid } from '@/components/game/BudgetProfileStatsGrid'
import { ProfileStreakPanel } from '@/components/game/ProfileStreakPanel'
import { ProfileXpPanel } from '@/components/game/ProfileXpPanel'
import { Header } from '@/components/layout/Header'
import { Skeleton } from '@/components/ui/Skeleton'
import { useTranslation } from '@/i18n'

const CONTENT_REPORT_EMAIL = 'support@clueterra.com'

export function PublicProfilePage() {
  const { username } = useParams<{ username: string }>()
  const navigate = useNavigate()
  const { t } = useTranslation()

  const { data: profile, isLoading, isError } = useQuery({
    queryKey: ['publicProfile', username],
    queryFn: () => gameApi.getPublicProfile(username!),
    enabled: !!username,
  })

  const reportHref = profile
    ? `mailto:${CONTENT_REPORT_EMAIL}?subject=${encodeURIComponent(`Clueterra profile report: @${profile.username}`)}&body=${encodeURIComponent(`Profile URL: ${window.location.href}\nUsername: @${profile.username}\nReason: `)}`
    : `mailto:${CONTENT_REPORT_EMAIL}`

  return (
    <div className="relative min-h-screen overflow-hidden bg-background text-white">
      <GameAuroraBackdrop />

      <Header />

      <main className="relative z-10 mx-auto flex w-full max-w-[1940px] flex-col px-5 py-10 sm:px-8 sm:py-16 xl:px-10 2xl:px-12">
        <div className="mb-6 flex flex-wrap items-center gap-2">
          <button
            onClick={() => navigate(-1)}
            className="inline-flex w-fit items-center gap-2 rounded-2xl border border-white/[.08] bg-white/[.04] px-3 py-2 text-sm text-white/40 transition-colors hover:bg-white/[.08] hover:text-white/72 cursor-pointer"
          >
            <ArrowLeft size={16} />
            {t.common.back}
          </button>
          {profile ? (
            <a
              href={reportHref}
              className="inline-flex w-fit items-center gap-2 rounded-2xl border border-white/[.08] bg-white/[.025] px-3 py-2 text-sm text-white/34 transition-colors hover:bg-white/[.06] hover:text-white/62"
            >
              <Flag size={15} />
              {t.profile.reportProfile}
            </a>
          ) : null}
        </div>

        {isLoading ? (
          <div className="grid gap-6 2xl:grid-cols-2 2xl:items-stretch">
            <div className="space-y-6">
              <Skeleton variant="rect" height={320} />
              <Skeleton variant="rect" height={180} />
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {Array.from({ length: 6 }).map((_, i) => (
                  <Skeleton key={i} variant="rect" height={116} />
                ))}
              </div>
            </div>
            <Skeleton variant="rect" height={760} />
          </div>
        ) : isError || !profile ? (
          <div className="py-20 text-center text-white/25">
            {t.common.noResults}
          </div>
        ) : (
          <div className="grid gap-6 2xl:grid-cols-2 2xl:items-stretch">
            <div className="order-1 space-y-6">
              <ProfileHeader
                profile={profile}
              />

              <ProfileXpPanel
                profile={profile}
              />

              <ProfileStreakPanel profile={profile} isOwnProfile={false} />

              <ProfileStatsGrid
                profile={profile}
              />

              <BudgetProfileStatsGrid profile={profile} />
            </div>

            <AchievementsSection
              mode="public"
              username={profile.username}
              className="order-2 2xl:h-full"
            />
          </div>
        )}
      </main>
    </div>
  )
}
