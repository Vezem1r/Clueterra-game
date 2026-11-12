import { Globe, Layers3, Sparkles, Zap } from 'lucide-react'
import { useTranslation } from '@/i18n'
import type { HomeDifficultyOption } from './types'

const MISSING_VALUE = '—'

function formatMetric(value: number | null) {
  return value == null ? MISSING_VALUE : String(value)
}

interface HomeModeStatsStripProps {
  option: HomeDifficultyOption
}

export function HomeModeStatsStrip({ option }: HomeModeStatsStripProps) {
  const { t } = useTranslation()

  const metrics = [
    { label: t.home.modeStatContinents, value: formatMetric(option.continentCount), Icon: Globe },
    { label: t.home.modeStatCategories, value: formatMetric(option.categoryCount), Icon: Layers3 },
    { label: t.home.modeStatTotalClues, value: String(option.totalHints), Icon: Sparkles },
    { label: t.home.modeStatAvgXp, value: formatMetric(option.avgXp), Icon: Zap },
  ]

  return (
    <div className="relative overflow-hidden rounded-[1.15rem] border border-white/[.08] bg-[linear-gradient(180deg,rgba(255,255,255,.03),rgba(8,11,18,.68))] shadow-[inset_0_1px_0_rgba(255,255,255,.04),0_18px_40px_rgba(0,0,0,.14)]">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(52,211,153,.08),transparent_34%),radial-gradient(circle_at_bottom_right,rgba(167,139,250,.06),transparent_38%)]" />
      <div className="pointer-events-none absolute inset-x-8 top-0 h-px bg-[linear-gradient(90deg,transparent,rgba(255,255,255,.18),transparent)]" />
      <div className="animate-glass-sweep pointer-events-none absolute inset-y-0 left-[-35%] w-[32%] bg-[linear-gradient(90deg,transparent,rgba(255,255,255,.06),transparent)] opacity-30" />
      <div className="relative grid grid-cols-4 divide-x divide-white/[.06] px-1 py-1.5">
        {metrics.map(({ label, value, Icon }) => (
          <div key={label} className="min-w-0 px-2 py-2 text-center first:pl-1 last:pr-1 sm:px-3">
            <div className="flex items-center justify-center gap-1.5 text-white/28">
              <Icon size={10.5} className="shrink-0 text-emerald-300/70" />
              <p className="truncate text-[0.46rem] font-bold uppercase tracking-[.2em] text-white/28 sm:text-[0.5rem]">
                {label}
              </p>
            </div>
            <p className="mt-1.5 text-[1.04rem] font-black leading-none tracking-[-0.04em] text-white/88 sm:text-[1.1rem]">
              {value}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}
