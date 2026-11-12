import { AlertTriangle } from 'lucide-react'
import { useTranslation } from '@/i18n'
import { CountryFlag } from '@/components/ui/CountryFlag'
import type { HardestCountryInsight } from '@/types'
import { formatDecimal, formatDurationMs, formatPercent, interpolate } from '@/utils/format'
import { getCountryName } from '@/utils/country'
import { useSettingsStore } from '@/store/settingsStore'
import {
  CARD_RADIUS_INNER,
  LABEL_TEXT,
  LABEL_TRACKING,
  SURFACE_INLAY,
  getToneChipClasses,
} from './constants'

interface HardestCountryCardProps {
  hardest: HardestCountryInsight
}

export function HardestCountryCard({ hardest }: HardestCountryCardProps) {
  const { t } = useTranslation()
  const language = useSettingsStore((state) => state.language)

  return (
    <div className="relative h-full overflow-hidden rounded-[1.75rem] bg-[linear-gradient(140deg,rgba(244,63,94,.12),rgba(15,23,42,.55))] border border-rose-300/[.12] p-5 sm:p-6">
      <div className="absolute -top-10 -right-8 w-32 h-32 rounded-full bg-rose-300/10 blur-3xl pointer-events-none hidden sm:block" />

      <div className="relative h-full flex flex-col">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_132px] mb-5">
          <div className="flex items-start gap-4 min-w-0">
            <CountryFlag country={hardest.country} size="lg" />
            <div className="min-w-0 flex-1">
              <div
                className={`inline-flex items-center gap-2 px-3 py-1 rounded-full border mb-3 ${LABEL_TEXT} ${LABEL_TRACKING} ${getToneChipClasses('rose')}`}
              >
                <AlertTriangle size={13} />
                {t.results.hardestCountry}
              </div>
              <h3 className="text-2xl font-bold text-white/90 truncate">
                {getCountryName(hardest.country, language)}
              </h3>
              <p className="text-sm text-rose-50/55 mt-2 leading-relaxed max-w-sm">
                {interpolate(t.results.hardestCountryBody, { rate: formatPercent(hardest.successRate) })}
              </p>
            </div>
          </div>

          <div
            className={`${CARD_RADIUS_INNER} ${SURFACE_INLAY} px-4 py-3 min-h-[108px] flex flex-col justify-between`}
          >
            <p className={`${LABEL_TEXT} ${LABEL_TRACKING} text-white/30`}>{t.results.accuracy}</p>
            <p className="text-3xl font-bold text-white/90 text-right">
              {formatPercent(hardest.successRate)}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-3 mb-4">
          <MiniStat value={formatDecimal(hardest.avgAttemptsNeeded)} label={t.results.avgAttempts} />
          <MiniStat value={formatDecimal(hardest.avgHintsNeeded)} label={t.results.cluesSeen} />
          <MiniStat value={formatDurationMs(hardest.avgActiveTimeMs)} label={t.results.avgActiveTime} />
        </div>

        {hardest.mostConfusedWith ? (
          <div className="mt-auto flex items-center gap-3 rounded-2xl bg-black/15 border border-white/[.06] p-3">
            <CountryFlag country={hardest.mostConfusedWith} size="xs" />
            <div className="min-w-0">
              <p className={`${LABEL_TEXT} ${LABEL_TRACKING} text-white/25 mb-1`}>
                {t.results.oftenConfusedWith}
              </p>
              <p className="text-sm text-white/75 truncate">
                {getCountryName(hardest.mostConfusedWith, language)}
              </p>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}

function MiniStat({ value, label }: { value: string; label: string }) {
  return (
    <div
      className={`${CARD_RADIUS_INNER} ${SURFACE_INLAY} p-3 min-h-[86px] flex flex-col justify-between`}
    >
      <p className="text-lg font-bold text-white/85">{value}</p>
      <p className={`${LABEL_TEXT} ${LABEL_TRACKING} text-white/30 mt-2`}>{label}</p>
    </div>
  )
}
