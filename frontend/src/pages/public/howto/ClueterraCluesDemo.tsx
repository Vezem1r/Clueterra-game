import { Eye, RotateCcw } from 'lucide-react'

import { HintCard } from '@/components/game/HintCard'
import { useTranslation } from '@/i18n'
import type { ClueterraDemoRound } from './useClueterraDemoRound'

export function ClueterraCluesDemo({ round }: { round: ClueterraDemoRound }) {
  const { t } = useTranslation()
  const total = round.hints.length
  const allOpen = round.opened >= total
  const xpLeftPercent = Math.round(((total - round.opened + 1) / total) * 100)

  return (
    <div>
      <div className="space-y-3">
        {round.hints.slice(0, round.opened).map((hint, index) => (
          <HintCard
            key={hint.id}
            hint={hint}
            displayNumber={index + 1}
            isNewest={round.opened > 1 && index === round.opened - 1}
          />
        ))}
      </div>

      <div className="mt-5 rounded-2xl border border-white/[.06] bg-black/20 p-4">
        <div className="flex items-center justify-between gap-3 text-[11px] font-semibold uppercase tracking-[.14em] text-white/35">
          <span>{t.howToPage.cluesXpLabel}</span>
          <span className="tabular-nums text-white/55">
            {t.howToPage.cluesCounter.replace('{count}', String(round.opened)).replace('{total}', String(total))}
          </span>
        </div>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-white/[.06]">
          <div
            className="h-full rounded-full bg-gradient-to-r from-violet-400 to-emerald-300 transition-[width] duration-500 ease-out"
            style={{ width: `${xpLeftPercent}%` }}
          />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={round.openNext}
            disabled={allOpen}
            className="inline-flex min-h-[44px] items-center gap-2 rounded-2xl border border-white/[.1] bg-white/[.06] px-4 text-sm font-semibold text-white transition hover:bg-white/[.1] disabled:cursor-not-allowed disabled:opacity-40"
          >
            <Eye size={15} />
            {allOpen ? t.howToPage.cluesAllOpen : t.howToPage.cluesReveal}
          </button>
          {round.opened > 1 && (
            <button
              type="button"
              onClick={round.reset}
              className="inline-flex min-h-[44px] items-center gap-2 rounded-2xl px-3 text-sm font-medium text-white/45 transition-colors hover:text-white/75"
            >
              <RotateCcw size={14} />
              {t.howToPage.cluesReset}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
