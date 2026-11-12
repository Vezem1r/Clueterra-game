import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Snowflake, X } from 'lucide-react'
import { gameApi } from '@/api/game'
import { Tooltip } from '@/components/ui/Tooltip'
import { useTranslation } from '@/i18n'
import type { ProfileDto } from '@/types'
import { cn } from '@/utils/cn'

function getUsedFreezeCount(profile: ProfileDto): number {
  return Math.max(
    0,
    profile.freezesUsedSinceLastCompletion ?? (profile.freezeUsedToday ? 1 : 0)
  )
}

interface StreakFreezeBannerProps {
  profile: ProfileDto
  className?: string
}

export function StreakFreezeBanner({ profile, className }: StreakFreezeBannerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [dismissedUsedCount, setDismissedUsedCount] = useState<number | null>(null)
  const usedCount = getUsedFreezeCount(profile)

  const acknowledgeMutation = useMutation({
    mutationFn: gameApi.acknowledgeStreakFreezeNotice,
    onSuccess: () => {
      queryClient.setQueriesData<ProfileDto>({ queryKey: ['profile'] }, oldProfile => oldProfile
        ? { ...oldProfile, freezeNoticeAcknowledged: true }
        : oldProfile)
      queryClient.invalidateQueries({ queryKey: ['profile'] })
    },
    onError: () => {
      setDismissedUsedCount(null)
    },
  })

  const dismissed = dismissedUsedCount === usedCount

  if (profile.currentStreak <= 0 || usedCount <= 0 || profile.freezeNoticeAcknowledged || dismissed) {
    return null
  }

  const remainingCount = Math.max(0, profile.freezesAvailable ?? 0)
  const message = usedCount > 1
    ? t.home.shieldActivatedCount
        .replace('{count}', String(usedCount))
        .replace('{n}', String(remainingCount))
    : t.home.shieldActivated
        .replace('{n}', String(remainingCount))
  const savedLabel = t.home.shieldSavedShort
  const usedLabel = t.home.shieldUsedShort.replace('{count}', String(usedCount))
  const remainingLabel = t.home.shieldRemainingShort.replace(
    '{count}',
    String(remainingCount)
  )

  const handleDismiss = () => {
    setDismissedUsedCount(usedCount)
    acknowledgeMutation.mutate()
  }

  return (
    <Tooltip
      content={message}
      offset={12}
      placement="bottom"
      maxWidth={260}
      disableTouch
      className="shrink-0"
      contentClassName="border-sky-100/[.14] text-sky-50/90"
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.94, y: 6 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ duration: 0.28, ease: 'easeOut' }}
        aria-label={message}
        className={cn(
          'relative isolate flex h-[4.5rem] w-[11rem] shrink-0 items-center gap-2 overflow-hidden rounded-[1.15rem] border border-sky-100/24 bg-[radial-gradient(circle_at_20%_22%,rgba(224,242,254,.26),transparent_27%),linear-gradient(135deg,rgba(14,165,233,.17),rgba(8,16,30,.86)_54%,rgba(34,211,238,.09))] px-2.5 text-sky-50 shadow-[0_18px_42px_rgba(8,47,73,.22),0_18px_42px_rgba(0,0,0,.22),inset_0_1px_0_rgba(255,255,255,.15)]',
          className
        )}
      >
        <span className="pointer-events-none absolute inset-0 rounded-[inherit] bg-[linear-gradient(135deg,rgba(255,255,255,.18),transparent_34%,rgba(125,211,252,.07)_72%,transparent)]" />
        <span className="pointer-events-none absolute inset-x-2 top-0 h-px bg-gradient-to-r from-transparent via-sky-100/55 to-transparent" />
        <span className="pointer-events-none absolute inset-x-2 bottom-0 h-px bg-gradient-to-r from-transparent via-cyan-100/28 to-transparent" />
        <span className="pointer-events-none absolute left-[58%] top-2 h-1 w-1 rounded-full bg-white/52 shadow-[0_0_10px_rgba(255,255,255,.48)]" />
        <span className="pointer-events-none absolute bottom-2 right-5 h-0.5 w-0.5 rounded-full bg-cyan-100/60 shadow-[0_0_8px_rgba(186,230,253,.5)]" />

        <div className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-sky-100/30 bg-sky-100/[.11] shadow-[inset_0_1px_0_rgba(255,255,255,.18)]">
          <Snowflake size={17} className="text-sky-100 drop-shadow-[0_0_8px_rgba(186,230,253,.55)]" />
        </div>

        <div className="relative min-w-0 flex-1 pr-3">
          <div className="truncate text-[0.48rem] font-black uppercase leading-none tracking-[0.06em] text-sky-100/60">
            {savedLabel}
          </div>
          <div className="mt-1 truncate text-[0.84rem] font-black leading-none tracking-normal text-white">
            {usedLabel}
          </div>
          <div className="mt-1 truncate text-[0.62rem] font-bold leading-none text-sky-100/68">
            {remainingLabel}
          </div>
        </div>

        <button
          type="button"
          onClick={handleDismiss}
          aria-label={t.common.close}
          title={t.common.close}
          disabled={acknowledgeMutation.isPending}
          className="absolute right-1.5 top-1.5 inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-sky-50/38 transition-colors hover:bg-sky-50/[.11] hover:text-sky-50/80 disabled:pointer-events-none disabled:opacity-55"
        >
          <X size={11} />
        </button>
      </motion.div>
    </Tooltip>
  )
}
