import { Calculator, ChartNoAxesCombined, Coins, Info, Target } from 'lucide-react'
import type { AppSettings } from '@/types'

const MIN_BASE_XP = 1
const MAX_BASE_XP = 10_000
const FULL_XP_THRESHOLD_MIN = 0
const FULL_XP_THRESHOLD_MAX = 50
const CURVE_POWER_MIN = 0.1
const CURVE_POWER_MAX = 3
const CURVE_POWER_STEP = 0.05
const QUESTIONS_PER_CHALLENGE = 5
const PREVIEW_ERROR = [0, 10, 20, 35, 60, 100]

interface BudgetSettingsTabProps {
  draft: AppSettings
  onChange: (patch: Partial<AppSettings>) => void
}

export function BudgetSettingsTab({ draft, onChange }: BudgetSettingsTabProps) {
  const thresholdFraction = draft.budgetFullXpThresholdPercent / 100
  const preview = [...new Set([...PREVIEW_ERROR, draft.budgetFullXpThresholdPercent])]
    .sort((left, right) => left - right)
    .map(relativeError => {
    const errorFraction = relativeError / 100
    const scoringFraction = errorFraction <= thresholdFraction
      ? 1
      : Math.max(0, 1 - (errorFraction - thresholdFraction) / (1 - thresholdFraction))
    const xpPercent = 100 * scoringFraction ** draft.budgetXpCurvePower
    return {
      relativeError,
      xpPercent: Math.round(xpPercent * 100) / 100,
      xp: Math.round(draft.budgetBaseXpPerQuestion * xpPercent / 100),
    }
  })

  return (
    <>
      <div className="space-y-5">
        <section className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-5">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center shrink-0">
              <Coins size={15} className="text-emerald-400/70" />
            </div>
            <div>
              <h2 className="text-sm font-semibold">Question rewards</h2>
              <p className="text-[10px] text-white/25 mt-0.5">Experience awarded for BudgetGuessr answers</p>
            </div>
          </div>

          <NumberField
            label="Base XP per question"
            description="XP awarded for an exact answer or an estimate inside the full-XP tolerance"
            value={draft.budgetBaseXpPerQuestion}
            min={MIN_BASE_XP}
            max={MAX_BASE_XP}
            onChange={value => onChange({ budgetBaseXpPerQuestion: value })}
          />

          <SliderField
            label="Full XP tolerance"
            description="Maximum relative error that still counts as correct and awards 100% XP"
            value={`±${draft.budgetFullXpThresholdPercent}%`}
            current={draft.budgetFullXpThresholdPercent}
            min={FULL_XP_THRESHOLD_MIN}
            max={FULL_XP_THRESHOLD_MAX}
            step={1}
            rangeMin="0%"
            rangeMax="50%"
            onChange={value => onChange({ budgetFullXpThresholdPercent: value })}
          />

          <div className="rounded-lg bg-emerald-500/[.04] border border-emerald-500/10 p-3">
            <div className="flex items-center justify-between text-xs">
              <span className="text-white/35">Maximum per challenge</span>
              <span className="font-mono font-bold text-emerald-400/80">
                {(draft.budgetBaseXpPerQuestion * QUESTIONS_PER_CHALLENGE).toLocaleString()} XP
              </span>
            </div>
            <p className="text-[10px] text-white/20 mt-1">Five perfect answers</p>
          </div>
        </section>
      </div>

      <div className="space-y-5">
        <section className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-5">
            <div className="w-8 h-8 rounded-lg bg-violet-500/10 flex items-center justify-center shrink-0">
              <ChartNoAxesCombined size={15} className="text-violet-400/70" />
            </div>
            <div>
              <h2 className="text-sm font-semibold">Accuracy curve</h2>
              <p className="text-[10px] text-white/25 mt-0.5">Controls how quickly XP falls as the estimate gets worse</p>
            </div>
          </div>

          <SliderField
            label="Curve power"
            description="Controls how quickly XP falls outside the full-XP tolerance"
            value={draft.budgetXpCurvePower.toFixed(2)}
            current={draft.budgetXpCurvePower}
            min={CURVE_POWER_MIN}
            max={CURVE_POWER_MAX}
            step={CURVE_POWER_STEP}
            rangeMin={CURVE_POWER_MIN.toFixed(1)}
            rangeMax={CURVE_POWER_MAX.toFixed(1)}
            onChange={value => onChange({ budgetXpCurvePower: value })}
          />

          <div className="rounded-lg bg-white/[.02] border border-white/[.06] p-3">
            <div className="flex items-start gap-2">
              <Info size={13} className="text-white/25 shrink-0 mt-0.5" />
              <p className="text-[10px] text-white/25 leading-relaxed">
                Relative error measures the distance from the correct answer. Guesses inside the configured tolerance
                earn full XP; outside it, XP declines smoothly to zero.
              </p>
            </div>
          </div>
        </section>
      </div>

      <div className="space-y-5">
        <section className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-8 h-8 rounded-lg bg-cyan-500/10 flex items-center justify-center shrink-0">
              <Calculator size={15} className="text-cyan-400/70" />
            </div>
            <div>
              <h2 className="text-sm font-semibold">Reward preview</h2>
              <p className="text-[10px] text-white/25 mt-0.5">Uses the same formula as the game session</p>
            </div>
          </div>

          <div className="space-y-1">
            {preview.map(row => (
              <div key={row.relativeError} className="grid grid-cols-[1fr_auto_auto] items-center gap-4 px-3 py-2 rounded-lg bg-white/[.02] border border-white/[.04]">
                <div className="flex items-center gap-2">
                  <Target size={11} className="text-white/25" />
                  <span className="text-xs text-white/45">{row.relativeError}% error</span>
                </div>
                <span className="text-[10px] font-mono text-white/25">{row.xpPercent.toFixed(2)}%</span>
                <span className="w-16 text-right text-xs font-mono font-bold text-white/75">{row.xp} XP</span>
              </div>
            ))}
          </div>

          <div className="mt-4 pt-4 border-t border-white/[.06]">
            <p className="text-[10px] text-white/25 leading-relaxed">
              New values are captured when a player starts a challenge. Sessions already in progress keep the reward
              settings they started with.
            </p>
          </div>
        </section>
      </div>
    </>
  )
}

function NumberField({ label, description, value, min, max, onChange }: {
  label: string
  description: string
  value: number
  min: number
  max: number
  onChange: (value: number) => void
}) {
  return (
    <div className="mb-5">
      <div className="flex items-center justify-between mb-1.5">
        <p className="text-xs text-white/40">{label}</p>
        <span className="text-xs font-mono font-bold text-white/70">{value}</span>
      </div>
      <p className="text-[10px] text-white/20 mb-2">{description}</p>
      <input
        type="number"
        min={min}
        max={max}
        value={value}
        onChange={event => onChange(Math.max(min, Math.min(max, Number(event.target.value) || min)))}
        className="w-full h-9 px-3 rounded-lg bg-white/[.04] border border-white/[.08] text-sm font-mono tabular-nums text-white/80 outline-none focus:border-white/20 transition-colors"
      />
    </div>
  )
}

function SliderField({ label, description, value, current, min, max, step, rangeMin, rangeMax, onChange }: {
  label: string
  description: string
  value: string
  current: number
  min: number
  max: number
  step: number
  rangeMin: string
  rangeMax: string
  onChange: (value: number) => void
}) {
  return (
    <div className="mb-5">
      <div className="flex items-center justify-between mb-1.5">
        <p className="text-xs text-white/40">{label}</p>
        <span className="text-xs font-mono font-bold text-white/70">{value}</span>
      </div>
      <p className="text-[10px] text-white/20 mb-2">{description}</p>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={current}
        onChange={event => onChange(Number(event.target.value))}
        className="w-full accent-accent h-1.5 cursor-pointer"
      />
      <div className="flex justify-between mt-1 text-[10px] text-white/15 tabular-nums px-0.5">
        <span>{rangeMin}</span>
        <span>{rangeMax}</span>
      </div>
    </div>
  )
}
