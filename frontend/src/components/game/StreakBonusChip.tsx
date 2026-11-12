import { motion } from 'framer-motion'
import { Flame } from 'lucide-react'

import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { STREAK_BONUS_REVEAL } from './streakBonusReveal'

type StreakBonusChipLayout = 'inline' | 'stacked'

interface StreakBonusChipProps {
  bonusXp: number
  layout?: StreakBonusChipLayout
  className?: string
}

export function StreakBonusChip({ bonusXp, layout = 'inline', className }: StreakBonusChipProps) {
  const { t } = useTranslation()
  if (bonusXp <= 0) return null
  const stacked = layout === 'stacked'
  return (
    <motion.div
      className={cn(
        'relative inline-flex shrink-0 items-center overflow-hidden border border-amber-200/15 bg-[linear-gradient(135deg,rgba(251,191,36,.14),rgba(249,115,22,.045))] shadow-[inset_0_1px_0_rgba(255,255,255,.08),0_0_34px_rgba(251,191,36,.1)] will-change-transform',
        stacked ? 'gap-2.5 rounded-[1.15rem] px-3.5 py-2' : 'gap-2 rounded-[1rem] px-3 py-2',
        className,
      )}
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={STREAK_BONUS_REVEAL}
    >
      <span className="pointer-events-none absolute inset-x-3 top-0 h-px bg-gradient-to-r from-transparent via-amber-100/35 to-transparent" />
      <span className={cn(
        'relative flex shrink-0 items-center justify-center rounded-xl border border-amber-200/15 bg-amber-200/10',
        stacked ? 'h-8 w-8' : 'h-7 w-7',
      )}>
        <span className="absolute inset-0 rounded-xl bg-amber-300/10 blur-md" />
        <Flame size={stacked ? 16 : 15} className="relative text-amber-300" />
      </span>
      {stacked ? (
        <span className="flex flex-col leading-none">
          <span className="flex items-baseline gap-1">
            <span className="bg-gradient-to-b from-amber-50 to-amber-300 bg-clip-text text-[1.7rem] font-black tabular-nums text-transparent">
              +{bonusXp}
            </span>
            <span className="text-[10px] font-black uppercase text-amber-200/45">XP</span>
          </span>
          <span className="mt-0.5 text-[8px] font-bold uppercase tracking-[.18em] text-amber-200/42">
            {t.results.streakBonus}
          </span>
        </span>
      ) : (
        <span className="flex items-baseline gap-1.5">
          <span className="bg-gradient-to-b from-amber-50 to-amber-300 bg-clip-text text-lg font-black tabular-nums text-transparent">
            +{bonusXp}
          </span>
          <span className="text-[9px] font-black uppercase text-amber-200/45">XP</span>
          <span className="text-[8px] font-bold uppercase tracking-[.14em] text-amber-200/42">
            {t.results.streakBonus}
          </span>
        </span>
      )}
    </motion.div>
  )
}
