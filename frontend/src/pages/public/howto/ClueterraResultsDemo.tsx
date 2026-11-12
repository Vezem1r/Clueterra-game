import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { gameApi } from '@/api/game'
import { BadgeCard } from '@/components/game/BadgeCard'
import { SecretBadgeCard } from '@/components/game/SecretBadgeCard'
import { XpProgressBar } from '@/components/ui/XpProgressBar'
import { useTranslation } from '@/i18n'

const BADGE_SAMPLES_STALE_TIME = 60_000
const DEMO_PROGRESS = {
  totalXp: 2240,
  level: 5,
  xpForCurrentLevel: 1800,
  xpForNextLevel: 2500,
  levelDivisor: 100,
}

export function ClueterraResultsDemo() {
  const { t } = useTranslation()
  const [secretRevealed, setSecretRevealed] = useState(false)
  const { data: samples = [] } = useQuery({
    queryKey: ['guide-badge-samples'],
    queryFn: () => gameApi.getBadgeSamples(),
    staleTime: BADGE_SAMPLES_STALE_TIME,
  })

  const stats = [
    { label: t.guide.step4Xp, value: '240' },
    { label: t.guide.step4Accuracy, value: '83%' },
    { label: t.guide.step4Time, value: '04:18' },
    { label: t.guide.step4Streak, value: '7' },
  ]
  const unlockedBadge = samples[0] ? { ...samples[0], unlocked: true } : null
  const lockedBadge = samples[1] ? { ...samples[1], unlocked: false, grantedAt: null } : null
  const secretBadge = samples[2] ?? null

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-white/[.06] bg-black/20 p-4">
        <XpProgressBar {...DEMO_PROGRESS} showCurrentLevelMarker showNextLevelMarker />
        <div className="mt-4 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          {stats.map((stat) => (
            <div key={stat.label} className="rounded-xl border border-white/[.06] bg-white/[.02] px-3 py-2.5">
              <p className="text-[10px] uppercase tracking-[.18em] text-white/25">{stat.label}</p>
              <p className="mt-1.5 text-lg font-semibold text-white/80">{stat.value}</p>
            </div>
          ))}
        </div>
      </div>

      {unlockedBadge && (
        <div className="grid grid-cols-3 gap-2">
          <BadgeCard badge={unlockedBadge} />
          {lockedBadge && <BadgeCard badge={lockedBadge} />}
          <SecretBadgeCard badge={secretBadge} revealed={secretRevealed} onReveal={() => setSecretRevealed(true)} />
        </div>
      )}
    </div>
  )
}
