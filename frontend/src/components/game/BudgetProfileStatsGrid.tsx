import { motion } from 'framer-motion'
import { Banknote, Building2, CircleDollarSign, type LucideIcon } from 'lucide-react'
import { ProfileGlassPanel } from '@/components/game/profile/ProfileGlassPanel'
import type { ProfileDto } from '@/types'
import { useTranslation } from '@/i18n'

interface BudgetProfileStatsGridProps {
  profile: ProfileDto
  animationDelay?: number
}

interface BudgetStatCard {
  icon: LucideIcon
  label: string
  value: string
  accent: string
}

export function BudgetProfileStatsGrid({
  profile,
  animationDelay = 0.42,
}: BudgetProfileStatsGridProps) {
  const { t } = useTranslation()
  const stats: BudgetStatCard[] = [
    {
      icon: Banknote,
      label: t.profile.budgetCompletedChallenges,
      value: profile.budgetStats.completedChallenges.toLocaleString(),
      accent: '#fbbf24',
    },
    {
      icon: Building2,
      label: t.profile.budgetProjectsExplored,
      value: profile.budgetStats.projectsExplored.toLocaleString(),
      accent: '#5eead4',
    },
    {
      icon: CircleDollarSign,
      label: t.profile.budgetProjectsMastered,
      value: formatUsd(profile.budgetStats.projectsMasteredValueUsd),
      accent: '#6ee7b7',
    },
  ]

  return (
    <motion.section
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: animationDelay }}
      aria-label="BudgetGuessr statistics"
    >
      <div className="mb-3 flex items-center gap-2 px-1">
        <span className="h-1.5 w-1.5 rounded-full bg-amber-300 shadow-[0_0_12px_rgba(252,211,77,.65)]" />
        <h2 className="text-[10px] font-bold uppercase tracking-[.18em] text-amber-100/55">
          BudgetGuessr
        </h2>
      </div>
      <div className="grid w-full grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
        {stats.map((stat, index) => (
          <motion.div
            key={stat.label}
            initial={{ opacity: 0, scale: 0.94 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: animationDelay + 0.05 + index * 0.05 }}
          >
            <ProfileGlassPanel className="h-full p-3.5 sm:p-4">
              <div className="flex min-h-[58px] items-center gap-3">
                <div
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border"
                  style={{
                    color: stat.accent,
                    backgroundColor: `${stat.accent}15`,
                    borderColor: `${stat.accent}30`,
                  }}
                >
                  <stat.icon size={16} />
                </div>
                <div className="min-w-0">
                  <p className="truncate text-[1.35rem] font-black tracking-normal text-white">
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
      </div>
    </motion.section>
  )
}

function formatUsd(value: number) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)
}
