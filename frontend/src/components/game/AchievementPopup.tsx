import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { gameApi } from '@/api/game'
import { useAuthStore } from '@/store/authStore'
import { resolveBadgeColorAccent } from '@/constants/badgeColors'
import { useTranslation } from '@/i18n'
import { Button } from '@/components/ui/Button'
import type { UnacknowledgedBadgeDto } from '@/types'

const STAR_ICON = (
  <svg viewBox="0 0 24 24" fill="none" className="w-10 h-10 text-amber-400">
    <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
      fill="currentColor" stroke="currentColor" strokeWidth="1" />
  </svg>
)

export function AchievementPopupView({ badge, onDismiss }: { badge: UnacknowledgedBadgeDto; onDismiss: () => void }) {
  const { t } = useTranslation()
  const accent = resolveBadgeColorAccent(badge.accentColorKey, 'accent')
  const effect = resolveBadgeColorAccent(badge.effectColorKey, 'effect') ?? accent

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="absolute inset-0 bg-black/80 backdrop-blur-sm"
        onClick={onDismiss}
      />
      <motion.div
        initial={{ opacity: 0, scale: 0.8, y: 30 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.8, y: 30 }}
        transition={{ type: 'spring', stiffness: 400, damping: 25 }}
        className="relative z-10 mx-4 w-full max-w-sm overflow-hidden rounded-2xl border p-8 text-center shadow-2xl"
        style={{
          background: accent
            ? `linear-gradient(180deg, rgba(11,14,23,.94), ${accent.surface} 42%, rgba(7,10,18,.98))`
            : undefined,
          borderColor: accent?.border ?? 'rgba(255,255,255,.1)',
          boxShadow: effect ? `0 26px 80px rgba(0,0,0,.5), 0 0 42px ${effect.softGlow}` : undefined,
        }}
      >
        <div
          className="pointer-events-none absolute inset-x-[18%] top-6 h-24 rounded-full blur-3xl"
          style={{ background: effect ? `radial-gradient(circle, ${effect.glow}, transparent 68%)` : undefined }}
        />
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ delay: 0.2, type: 'spring', stiffness: 500, damping: 20 }}
          className="relative z-10 mb-5"
        >
          {badge.imageUrl ? (
            <img
              src={badge.imageUrl}
              alt={badge.name}
              decoding="async"
              className="mx-auto h-20 w-20 object-contain"
              style={effect ? { filter: `drop-shadow(0 0 24px ${effect.glow})` } : undefined}
            />
          ) : (
            <div className="w-20 h-20 mx-auto flex items-center justify-center">
              {STAR_ICON}
            </div>
          )}
        </motion.div>

        <motion.p
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="relative z-10 mb-2 text-[10px] uppercase tracking-[0.2em]"
          style={{ color: effect?.solid ?? undefined }}
        >
          {t.achievements.newAchievement}
        </motion.p>

        <motion.h3
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.35 }}
          className="relative z-10 mb-2 text-xl font-bold text-white"
        >
          {badge.name}
        </motion.h3>

        <motion.p
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="relative z-10 mb-6 text-sm text-white/40"
        >
          {badge.description}
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
        >
          <Button onClick={onDismiss} className="px-8">
            {t.achievements.awesome}
          </Button>
        </motion.div>
      </motion.div>
    </div>
  )
}

export function AchievementPopup() {
  const { isAuthenticated } = useAuthStore()
  const queryClient = useQueryClient()
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set())

  const { data: unacknowledged } = useQuery({
    queryKey: ['unacknowledged-badges'],
    queryFn: gameApi.getUnacknowledgedBadges,
    enabled: isAuthenticated,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  })

  const remaining = (unacknowledged ?? []).filter(b => !dismissedIds.has(b.badgeId))
  const currentBadge: UnacknowledgedBadgeDto | null = remaining[0] ?? null

  const acknowledgeMutation = useMutation({
    mutationFn: (badgeIds: string[]) => gameApi.acknowledgeBadges(badgeIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['unacknowledged-badges'] })
      queryClient.invalidateQueries({ queryKey: ['profile'] })
    },
  })

  const handleDismiss = () => {
    if (currentBadge) {
      acknowledgeMutation.mutate([currentBadge.badgeId])
      setDismissedIds(prev => new Set(prev).add(currentBadge.badgeId))
    }
  }

  return (
    <AnimatePresence>
      {currentBadge && (
        <AchievementPopupView badge={currentBadge} onDismiss={handleDismiss} />
      )}
    </AnimatePresence>
  )
}
