import type { ReactNode } from 'react'
import { Check, Sparkles, Target, Trophy, X } from 'lucide-react'
import { useTranslation } from '@/i18n'
import { CountryFlag } from '@/components/ui/CountryFlag'
import type { Country } from '@/types'
import { formatDecimal } from '@/utils/format'
import { getCountryName } from '@/utils/country'
import { useSettingsStore } from '@/store/settingsStore'
import { StatCard } from './StatCard'
import {
  CARD_RADIUS_INNER,
  CARD_RADIUS_OUTER,
  LABEL_TEXT,
  LABEL_TRACKING,
  SURFACE_PANEL,
  getToneChipClasses,
  getToneSurfaceClasses,
  type ResultsTone,
} from './constants'
import type { RunInsights } from './useRunInsights'

interface RunBreakdownCardProps {
  insights: RunInsights
}

export function RunBreakdownCard({ insights }: RunBreakdownCardProps) {
  const { t } = useTranslation()

  return (
    <div
      className={`${CARD_RADIUS_OUTER} ${SURFACE_PANEL} backdrop-blur-[2px] p-5 sm:p-6 flex flex-col h-full`}
    >
      <div className="flex items-start gap-3 pb-5 border-b border-white/[.07]">
        <div
          className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${getToneChipClasses('emerald')}`}
        >
          <Sparkles size={17} />
        </div>
        <div className="min-w-0">
          <h2 className="text-lg font-semibold text-white/85">{t.results.runSummaryTitle}</h2>
          <p className="text-sm text-white/32 leading-relaxed">{t.results.runSummaryBody}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 mt-5">
        <StatCard
          icon={<Target size={16} />}
          value={String(insights.totalAttempts)}
          label={t.results.totalAttempts}
          size="sm"
          layout="header"
        />
        <StatCard
          icon={<Check size={16} />}
          value={String(insights.firstTryHits)}
          label={t.results.firstTryHits}
          size="sm"
          layout="header"
        />
        <StatCard
          icon={<X size={16} />}
          value={String(insights.missedCountries)}
          label={t.results.missedCountries}
          size="sm"
          layout="header"
        />
        <StatCard
          icon={<Trophy size={16} />}
          value={formatDecimal(insights.avgAttemptsPerCountry)}
          label={t.results.avgAttempts}
          size="sm"
          layout="header"
        />
      </div>

      <div className="mt-auto pt-5 border-t border-white/[.07] space-y-3">
        <CountryFocusRow
          label={t.results.bestCountry}
          country={insights.bestCountry?.correctCountry ?? null}
          meta={`${insights.bestCountry?.xpEarned ?? 0} XP`}
          tone="emerald"
          icon={<Trophy size={14} />}
        />
        <CountryFocusRow
          label={t.results.stubbornCountry}
          country={insights.toughestCountry?.correctCountry ?? null}
          meta={`${insights.toughestCountry?.hintsSeen ?? 0} ${t.results.cluesSeen.toLowerCase()}`}
          tone="rose"
          icon={<Sparkles size={14} />}
        />
      </div>
    </div>
  )
}

interface CountryFocusRowProps {
  label: string
  country: Country | null
  meta: string
  tone: ResultsTone
  icon?: ReactNode
}

function CountryFocusRow({ label, country, meta, tone }: CountryFocusRowProps) {
  const language = useSettingsStore((state) => state.language)
  return (
    <div
      className={`flex items-center gap-3 border px-4 py-3 min-h-[78px] ${CARD_RADIUS_INNER} ${getToneSurfaceClasses(tone)}`}
    >
      {country ? (
        <CountryFlag country={country} size="xs" />
      ) : (
        <div className="w-9 h-7 rounded-md bg-white/[.05] border border-white/[.08]" />
      )}
      <div className="min-w-0 flex-1">
        <p className={`${LABEL_TEXT} ${LABEL_TRACKING} opacity-65 mb-1`}>{label}</p>
        <p className="text-sm font-semibold truncate">
          {country ? getCountryName(country, language) : '—'}
        </p>
      </div>
      <p className="text-[11px] opacity-70 shrink-0 text-right">{meta}</p>
    </div>
  )
}
