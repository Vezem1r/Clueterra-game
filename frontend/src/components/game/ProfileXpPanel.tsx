import { motion } from 'framer-motion'
import { ArrowUpRight, Zap } from 'lucide-react'
import { ProfileGlassPanel } from '@/components/game/profile/ProfileGlassPanel'
import { XpProgressBar } from '@/components/ui/XpProgressBar'
import { useTranslation } from '@/i18n'
import type { ProfileDto } from '@/types'
import { cn } from '@/utils/cn'

interface ProfileXpPanelProps {
  profile: ProfileDto
  className?: string
}

export function ProfileXpPanel({ profile, className }: ProfileXpPanelProps) {
  const { t } = useTranslation()
  const xpToNextLevel = Math.max(0, profile.xpForNextLevel - profile.totalXp)

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.18 }}
      className={cn('w-full', className)}
    >
      <ProfileGlassPanel className="p-5 sm:p-6">
        <div className="min-w-0">
          <div className="mb-3 flex items-center gap-2 text-white/30">
            <Zap size={14} />
            <p className="text-[10px] font-semibold uppercase tracking-[.16em]">{t.profile.totalXp}</p>
          </div>
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="flex flex-wrap items-end gap-3">
              <p className="text-[2.5rem] font-black tracking-[-0.06em] text-white sm:text-[3rem]">
                {profile.totalXp.toLocaleString()}
              </p>
              <div className="mb-1 rounded-full border border-white/[.08] bg-white/[.05] px-3 py-1 text-xs font-semibold uppercase tracking-[.16em] text-white/46">
                XP
              </div>
            </div>
            <div className="inline-flex items-center gap-2 rounded-full border border-white/[.08] bg-white/[.03] px-3.5 py-2 text-xs text-white/44">
              <ArrowUpRight size={14} />
              <span>{xpToNextLevel.toLocaleString()} XP</span>
            </div>
          </div>
          <div className="mt-4">
            <XpProgressBar
              totalXp={profile.totalXp}
              level={profile.level}
              xpForCurrentLevel={profile.xpForCurrentLevel}
              xpForNextLevel={profile.xpForNextLevel}
              levelDivisor={profile.levelDivisor}
              showCurrentLevelMarker={false}
              showNextLevelMarker={false}
            />
          </div>
        </div>
      </ProfileGlassPanel>
    </motion.div>
  )
}
