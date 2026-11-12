import { UserPlus, Bot, Flame, ShieldCheck } from 'lucide-react'
import { SettingsToggle } from './SettingsToggle'
import { cn } from '@/utils/cn'
import { useTranslation } from '@/i18n'
import type { AppSettings } from '@/types'

const MIN_STREAK_MAX_BONUS = 0
const MAX_STREAK_MAX_BONUS = 100
const MIN_STREAK_MAX_DAYS = 1
const MAX_STREAK_MAX_DAYS = 60
const MIN_STREAK_CURVE_POWER = 0.1
const MAX_STREAK_CURVE_POWER = 5
const STREAK_CURVE_STEP = 0.1
const MIN_STREAK_FREEZE_MAX = 0
const MAX_STREAK_FREEZE_MAX = 20
const MIN_STREAK_FREEZE_INTERVAL = 1
const MAX_STREAK_FREEZE_INTERVAL = 30
const MIN_STREAK_FREEZE_PER_BADGE = 0
const MAX_STREAK_FREEZE_PER_BADGE = 5

interface GlobalSettingsTabProps {
  draft: AppSettings
  onChange: (patch: Partial<AppSettings>) => void
}

export function GlobalSettingsTab({ draft, onChange }: GlobalSettingsTabProps) {
  const { t } = useTranslation()

  return (
    <>
      {/* ─── Col 1: Access Control ─── */}
      <div className="space-y-5">
        {/* Registration */}
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-8 h-8 rounded-lg bg-accent/10 flex items-center justify-center shrink-0">
              <UserPlus size={15} className="text-accent/70" />
            </div>
            <h2 className="text-sm font-semibold">{t.admin.registrationToggle}</h2>
          </div>
          <SettingsToggle
            checked={draft.registrationEnabled}
            onChange={() => onChange({ registrationEnabled: !draft.registrationEnabled })}
            label="Allow new users to register via email"
            description="OAuth login always available"
          />
          <div className={cn(
            'mt-4 flex items-center gap-2 px-3 py-2 rounded-lg text-xs transition-colors',
            draft.registrationEnabled ? 'bg-success/[.06] text-success/70' : 'bg-white/[.03] text-white/25'
          )}>
            <div className={cn('w-1.5 h-1.5 rounded-full', draft.registrationEnabled ? 'bg-success' : 'bg-white/20')} />
            {draft.registrationEnabled ? 'Registration open' : 'Registration closed'}
          </div>
        </div>

        {/* AI Auto-Approve */}
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-8 h-8 rounded-lg bg-violet-500/10 flex items-center justify-center shrink-0">
              <Bot size={15} className="text-violet-400/70" />
            </div>
            <h2 className="text-sm font-semibold">AI Auto-Approve</h2>
          </div>
          <div className="space-y-3">
            <SettingsToggle
              checked={draft.autoApproveGeneration}
              onChange={() => onChange({ autoApproveGeneration: !draft.autoApproveGeneration })}
              label="Generation"
              description="Auto-apply generated hints"
            />
            <SettingsToggle
              checked={draft.autoApproveReview}
              onChange={() => onChange({ autoApproveReview: !draft.autoApproveReview })}
              label="Review"
              description="Auto-apply review changes"
            />
          </div>
        </div>
      </div>

      {/* ─── Col 2: Streak Bonus ─── */}
      <div className="space-y-5">
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-5">
            <div className="w-8 h-8 rounded-lg bg-orange-500/10 flex items-center justify-center shrink-0">
              <Flame size={15} className="text-orange-400/70" />
            </div>
            <div>
              <h2 className="text-sm font-semibold">Streak Bonus</h2>
              <p className="text-[10px] text-white/25 mt-0.5">XP bonus curve for daily streaks</p>
            </div>
          </div>

          <StreakSlider
            label="Max bonus percent"
            value={`${draft.streakMaxBonusPercent}%`}
            min={MIN_STREAK_MAX_BONUS} max={MAX_STREAK_MAX_BONUS} step={1}
            current={draft.streakMaxBonusPercent}
            onChange={v => onChange({ streakMaxBonusPercent: v })}
            rangeMin="0%" rangeMax="100%"
          />

          <StreakSlider
            label="Max streak days"
            value={String(draft.streakMaxDays)}
            min={MIN_STREAK_MAX_DAYS} max={MAX_STREAK_MAX_DAYS} step={1}
            current={draft.streakMaxDays}
            onChange={v => onChange({ streakMaxDays: v })}
            rangeMin={String(MIN_STREAK_MAX_DAYS)} rangeMax={String(MAX_STREAK_MAX_DAYS)}
          />

          <StreakSlider
            label="Curve power"
            value={draft.streakCurvePower.toFixed(1)}
            min={MIN_STREAK_CURVE_POWER} max={MAX_STREAK_CURVE_POWER} step={STREAK_CURVE_STEP}
            current={draft.streakCurvePower}
            onChange={v => onChange({ streakCurvePower: v })}
            last
            rangeMin={String(MIN_STREAK_CURVE_POWER)} rangeMax={String(MAX_STREAK_CURVE_POWER)}
          />

          <div className="rounded-lg bg-white/[.02] border border-white/[.06] p-3 mt-1">
            <p className="text-[10px] text-white/25 font-mono leading-relaxed">
              bonus = {draft.streakMaxBonusPercent}% &times; (streak / {draft.streakMaxDays})^{draft.streakCurvePower.toFixed(1)}
            </p>
          </div>
        </div>
      </div>

      {/* ─── Col 3: Streak Shields ─── */}
      <div className="space-y-5">
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-5">
            <div className="w-8 h-8 rounded-lg bg-sky-500/10 flex items-center justify-center shrink-0">
              <ShieldCheck size={15} className="text-sky-400/70" />
            </div>
            <div>
              <h2 className="text-sm font-semibold">Streak Shields</h2>
              <p className="text-[10px] text-white/25 mt-0.5">Freeze protection for streaks</p>
            </div>
          </div>

          <NumberField
            label="Shield max"
            value={draft.streakFreezeMax}
            min={MIN_STREAK_FREEZE_MAX} max={MAX_STREAK_FREEZE_MAX}
            onChange={v => onChange({ streakFreezeMax: v })}
            description="Maximum shields a player can hold"
          />

          <NumberField
            label="Shield streak interval"
            value={draft.streakFreezeStreakInterval}
            min={MIN_STREAK_FREEZE_INTERVAL} max={MAX_STREAK_FREEZE_INTERVAL}
            onChange={v => onChange({ streakFreezeStreakInterval: v })}
            description="Days of streak needed to earn a shield"
          />

          <NumberField
            label="Shields per badge"
            value={draft.streakFreezePerBadge}
            min={MIN_STREAK_FREEZE_PER_BADGE} max={MAX_STREAK_FREEZE_PER_BADGE}
            onChange={v => onChange({ streakFreezePerBadge: v })}
            description="Shields granted per badge unlock"
            last
          />
        </div>
      </div>
    </>
  )
}

function StreakSlider({ label, value, min, max, step, current, onChange, last, rangeMin, rangeMax }: {
  label: string; value: string; min: number; max: number; step: number; current: number
  onChange: (v: number) => void; last?: boolean; rangeMin?: string; rangeMax?: string
}) {
  return (
    <div className={last ? '' : 'mb-4'}>
      <div className="flex items-center justify-between mb-2">
        <p className="text-xs text-white/40">{label}</p>
        <span className="text-xs font-mono font-bold text-white/70">{value}</span>
      </div>
      <input type="range" min={min} max={max} step={step} value={current}
        onChange={e => onChange(Number(e.target.value))} className="w-full accent-accent h-1.5 cursor-pointer" />
      {rangeMin && rangeMax && (
        <div className="flex justify-between mt-1 text-[10px] text-white/15 tabular-nums px-0.5">
          <span>{rangeMin}</span><span>{rangeMax}</span>
        </div>
      )}
    </div>
  )
}

function NumberField({ label, value, min, max, onChange, description, last }: {
  label: string; value: number; min: number; max: number
  onChange: (v: number) => void; description?: string; last?: boolean
}) {
  return (
    <div className={last ? '' : 'mb-4'}>
      <div className="flex items-center justify-between mb-1.5">
        <p className="text-xs text-white/40">{label}</p>
        <span className="text-xs font-mono font-bold text-white/70">{value}</span>
      </div>
      {description && (
        <p className="text-[10px] text-white/20 mb-2">{description}</p>
      )}
      <input type="number" min={min} max={max}
        value={value}
        onChange={e => onChange(Math.max(min, Math.min(max, Number(e.target.value) || min)))}
        className="w-full h-9 px-3 rounded-lg bg-white/[.04] border border-white/[.08] text-sm font-mono tabular-nums text-white/80 outline-none focus:border-white/20 transition-colors"
      />
    </div>
  )
}
