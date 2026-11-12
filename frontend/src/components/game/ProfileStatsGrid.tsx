import { motion } from 'framer-motion'
import { Gamepad2, MapPinned, Sparkles, Target, type LucideIcon } from 'lucide-react'
import { ProfileGlassPanel } from '@/components/game/profile/ProfileGlassPanel'
import type { ProfileDto } from '@/types'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'

interface ProfileStatsGridProps {
  profile: ProfileDto
  animationDelay?: number
  className?: string
}

interface StatCard {
  icon: LucideIcon
  label: string
  value: string
  accent: string
}

export function ProfileStatsGrid({
  profile,
  animationDelay = 0.3,
  className,
}: ProfileStatsGridProps) {
  const { t } = useTranslation()

  const stats: StatCard[] = [
    { icon: Gamepad2, label: t.profile.completedRuns, value: profile.completedRuns.toLocaleString(), accent: '#a78bfa' },
    { icon: MapPinned, label: t.profile.countriesSolved, value: profile.countriesSolved.toLocaleString(), accent: '#60a5fa' },
    { icon: Target, label: t.profile.solveRate, value: `${Math.round(profile.solveRate)}%`, accent: '#38bdf8' },
    { icon: Sparkles, label: t.profile.perfectRuns, value: profile.perfectRuns.toLocaleString(), accent: '#f59e0b' },
  ]

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: animationDelay }}
      className={cn('grid w-full grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-4', className)}
    >
      {stats.map((stat, idx) => (
        <motion.div
          key={stat.label}
          initial={{ opacity: 0, scale: 0.92 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: animationDelay + 0.05 + idx * 0.05 }}
          className="w-full self-stretch"
        >
          <ProfileGlassPanel className="h-full p-3.5 sm:p-4">
            <div className="flex min-h-[58px] items-center gap-3">
              <div
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border"
                style={{ color: stat.accent, backgroundColor: `${stat.accent}15`, borderColor: `${stat.accent}30` }}
              >
                <stat.icon size={16} />
              </div>

              <div className="min-w-0">
                <p className="text-[1.35rem] font-black tracking-normal text-white">
                  {stat.value}
                </p>
                <p className="mt-0.5 text-[9px] font-semibold uppercase tracking-[.13em] text-white/28 sm:text-[10px]">
                  {stat.label}
                </p>
              </div>
            </div>
          </ProfileGlassPanel>
        </motion.div>
      ))}
    </motion.div>
  )
}
