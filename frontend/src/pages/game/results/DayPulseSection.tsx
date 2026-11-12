import { motion } from 'framer-motion'
import { Clock, Sparkles, Star, Target, Trophy } from 'lucide-react'
import { useTranslation } from '@/i18n'
import type { GameResultsCommunityStats } from '@/types'
import { formatDecimal, formatDurationMs, formatPercent } from '@/utils/format'
import { StatCard } from './StatCard'
import { HardestCountryCard } from './HardestCountryCard'
import {
  CARD_RADIUS_OUTER,
  SECTION_DELAY_PULSE,
  SURFACE_PANEL,
  getToneChipClasses,
} from './constants'

interface DayPulseSectionProps {
  stats: GameResultsCommunityStats
}

export function DayPulseSection({ stats }: DayPulseSectionProps) {
  const { t } = useTranslation()

  if (!stats.enoughData) {
    return (
      <motion.section
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: SECTION_DELAY_PULSE }}
        className={`mb-8 sm:mb-10 ${CARD_RADIUS_OUTER} ${SURFACE_PANEL} p-5 sm:p-6`}
      >
        <div className="flex flex-col sm:flex-row sm:items-center gap-4 sm:gap-5">
          <div
            className={`w-12 h-12 rounded-2xl flex items-center justify-center shrink-0 ${getToneChipClasses('amber')}`}
          >
            <Sparkles size={18} />
          </div>
          <div className="flex-1">
            <h2 className="text-lg font-semibold text-white/85 mb-2">
              {t.results.communityPendingTitle}
            </h2>
            <p className="text-sm leading-relaxed text-white/35">{t.results.communityPendingBody}</p>
          </div>
        </div>
      </motion.section>
    )
  }

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: SECTION_DELAY_PULSE }}
      className="mb-8 sm:mb-10"
    >
      <header className="mb-4">
        <h2 className="text-lg sm:text-xl font-semibold text-white/85 mb-1">
          {t.results.communityTitle}
        </h2>
        <p className="text-sm text-white/30">{t.results.communitySubtitle}</p>
      </header>

      <div className="grid xl:grid-cols-[minmax(0,1fr)_minmax(360px,1fr)] gap-4 sm:gap-5 items-stretch">
        <div className="grid grid-cols-2 gap-4 auto-rows-fr">
          <StatCard
            icon={<Star size={18} />}
            value={`${stats.avgXpPerCompletedRun} XP`}
            label={t.results.avgRunXp}
            tone="amber"
            size="lg"
            layout="header"
          />
          <StatCard
            icon={<Target size={18} />}
            value={formatPercent(stats.accuracyRate)}
            label={t.results.accuracy}
            tone="emerald"
            size="lg"
            layout="header"
          />
          <StatCard
            icon={<Clock size={18} />}
            value={formatDurationMs(stats.avgActiveTimePerSessionMs)}
            label={t.results.avgActiveTime}
            tone="sky"
            size="lg"
            layout="header"
          />
          <StatCard
            icon={<Trophy size={18} />}
            value={formatDecimal(stats.avgAttemptsPerCountry)}
            label={t.results.avgAttempts}
            tone="violet"
            size="lg"
            layout="header"
          />
        </div>

        {stats.hardestCountry ? (
          <HardestCountryCard hardest={stats.hardestCountry} />
        ) : (
          <div className={`${CARD_RADIUS_OUTER} ${SURFACE_PANEL} p-5 sm:p-6`}>
            <p className="text-sm text-white/30">{t.results.communityPendingBody}</p>
          </div>
        )}
      </div>
    </motion.section>
  )
}
