import { useMemo } from 'react'
import {
  Globe, Lightbulb, Shield, Zap, Trophy, Crosshair, Target,
  TrendingUp, Calculator, Timer, Map,
} from 'lucide-react'
import { Badge } from '@/components/ui/Badge'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import type { AppSettings, ChallengeDto } from '@/types'

const MIN_COUNTRIES = 1
const MAX_COUNTRIES = 20
const MIN_HINTS = 1
const MAX_HINTS = 10
const MIN_BASE_XP = 10
const MAX_BASE_XP = 1000
const MIN_LEVEL_DIVISOR = 10
const MAX_LEVEL_DIVISOR = 500
const MIN_MAX_ATTEMPTS = 1
const MAX_MAX_ATTEMPTS = 10
const MULTIPLIER_STEP = 0.05
const LEVEL_TABLE_SIZE = 10
const MIN_TIME_THRESHOLD = 5
const MAX_TIME_THRESHOLD = 120

const LEVEL_CONFIG: Record<string, { icon: typeof Zap; color: string; bg: string }> = {
  EASY: { icon: Shield, color: 'text-emerald-400', bg: 'bg-emerald-500/10' },
  MEDIUM: { icon: Zap, color: 'text-amber-400', bg: 'bg-amber-500/10' },
  HARD: { icon: Trophy, color: 'text-red-400', bg: 'bg-red-500/10' },
}

const DIFFICULTY_OPTIONS = ['EASY', 'MEDIUM', 'HARD'] as const
type Difficulty = typeof DIFFICULTY_OPTIONS[number]

function getBaseXpForDifficulty(draft: AppSettings, difficulty: Difficulty): number {
  if (difficulty === 'EASY') return draft.baseXpEasy
  if (difficulty === 'MEDIUM') return draft.baseXpMedium
  return draft.baseXpHard
}

interface GameSettingsTabProps {
  draft: AppSettings
  onChange: (patch: Partial<AppSettings>) => void
  todayChallenges: ChallengeDto[] | undefined
  calcDifficulty: Difficulty
  setCalcDifficulty: (d: Difficulty) => void
  calcHintsRevealed: number
  setCalcHintsRevealed: (v: number) => void
  calcAttemptsUsed: number
  setCalcAttemptsUsed: (v: number) => void
  calcTimeSpent: number
  setCalcTimeSpent: (v: number) => void
}

export function GameSettingsTab({
  draft, onChange, todayChallenges,
  calcDifficulty, setCalcDifficulty,
  calcHintsRevealed, setCalcHintsRevealed,
  calcAttemptsUsed, setCalcAttemptsUsed,
  calcTimeSpent, setCalcTimeSpent,
}: GameSettingsTabProps) {
  const { t } = useTranslation()

  const xpCalc = useMemo(() => {
    const baseXp = getBaseXpForDifficulty(draft, calcDifficulty)
    const maxHints = draft.hintsPerCountry
    const maxAttempts = draft.maxAttempts
    const hintsBonusPct = (maxHints - calcHintsRevealed) / maxHints * draft.hintsBonusMultiplier
    const attemptsBonusPct = (maxAttempts - calcAttemptsUsed) / maxAttempts * draft.attemptsBonusMultiplier
    const timeBonusPct = Math.max(0, (draft.timeBonusThreshold - calcTimeSpent) / draft.timeBonusThreshold) * draft.timeBonusMultiplier
    const total = Math.floor(baseXp * (1 + hintsBonusPct + attemptsBonusPct + timeBonusPct))
    return { baseXp, hintsBonusPct, attemptsBonusPct, timeBonusPct, total }
  }, [draft, calcDifficulty, calcHintsRevealed, calcAttemptsUsed, calcTimeSpent])

  const levelTable = useMemo(() => {
    return Array.from({ length: LEVEL_TABLE_SIZE }, (_, i) => {
      const level = i + 1
      const xpRequired = (level - 1) ** 2 * draft.levelDivisor
      return { level, xpRequired }
    })
  }, [draft.levelDivisor])

  return (
    <>
      {/* ─── Col 1: Challenge Config ─── */}
      <div className="space-y-5">
        {/* Countries per Challenge */}
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-1">
            <div className="w-8 h-8 rounded-lg bg-blue-500/10 flex items-center justify-center shrink-0">
              <Globe size={15} className="text-blue-400/70" />
            </div>
            <div className="flex-1 min-w-0">
              <h2 className="text-sm font-semibold">{t.admin.countriesPerChallenge}</h2>
            </div>
            <span className="text-3xl font-bold tabular-nums text-white/90">{draft.countriesPerChallenge}</span>
          </div>
          <p className="text-xs text-white/25 mb-4 ml-11">How many countries per daily challenge</p>
          <input type="range" min={MIN_COUNTRIES} max={MAX_COUNTRIES} value={draft.countriesPerChallenge}
            onChange={e => onChange({ countriesPerChallenge: Number(e.target.value) })}
            className="w-full accent-accent h-1.5 cursor-pointer" />
          <div className="flex justify-between mt-2 text-[10px] text-white/15 tabular-nums px-0.5">
            <span>{MIN_COUNTRIES}</span><span>{MAX_COUNTRIES}</span>
          </div>
        </div>

        {/* Hints per Country */}
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-1">
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center shrink-0">
              <Lightbulb size={15} className="text-amber-400/70" />
            </div>
            <div className="flex-1 min-w-0">
              <h2 className="text-sm font-semibold">{t.admin.hintsPerCountry}</h2>
            </div>
            <span className="text-3xl font-bold tabular-nums text-white/90">{draft.hintsPerCountry}</span>
          </div>
          <p className="text-xs text-white/25 mb-4 ml-11">Clues revealed progressively for each country</p>
          <input type="range" min={MIN_HINTS} max={MAX_HINTS} value={draft.hintsPerCountry}
            onChange={e => onChange({ hintsPerCountry: Number(e.target.value) })}
            className="w-full accent-accent h-1.5 cursor-pointer" />
          <div className="flex justify-between mt-2 text-[10px] text-white/15 tabular-nums px-0.5">
            <span>{MIN_HINTS}</span><span>{MAX_HINTS}</span>
          </div>
        </div>

        {/* Today's Challenges */}
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-8 h-8 rounded-lg bg-violet-500/10 flex items-center justify-center shrink-0">
              <Map size={15} className="text-violet-400/70" />
            </div>
            <h2 className="text-sm font-semibold">Today's Challenges</h2>
          </div>
          {!todayChallenges?.length ? (
            <div className="text-center py-6">
              <p className="text-xs text-white/20">No challenges generated for today</p>
            </div>
          ) : (
            <div className="space-y-2.5">
              {todayChallenges.map(ch => {
                const cfg = LEVEL_CONFIG[ch.level] ?? LEVEL_CONFIG.MEDIUM
                const hintsPerCountry = ch.totalCountries > 0 ? Math.round(ch.totalHints / ch.totalCountries) : 0
                return (
                  <div key={ch.id} className="flex items-center gap-3 px-3 py-3 rounded-lg bg-white/[.02] border border-white/[.04]">
                    <div className={cn('w-7 h-7 rounded-md flex items-center justify-center shrink-0', cfg.bg)}>
                      <cfg.icon size={14} className={cfg.color} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium">{ch.level}</p>
                      <p className="text-[11px] text-white/30 mt-0.5">{ch.totalCountries} countries &middot; {hintsPerCountry} hints each</p>
                    </div>
                    <Badge variant={ch.available ? 'success' : 'neutral'} className="text-[10px]">
                      {ch.available ? 'Available' : 'Played'}
                    </Badge>
                  </div>
                )
              })}
            </div>
          )}
        </div>
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <h2 className="text-sm font-semibold mb-1">Rivers · infinite game</h2>
          <p className="text-xs text-white/30 mb-4">XP is awarded once per river; revealed clues reduce the maximum.</p>
          <SliderRow label="Base XP per river" value={`${draft.riverGameBaseXp} XP`}
            min={10} max={1000} step={10} current={draft.riverGameBaseXp}
            onChange={value => onChange({ riverGameBaseXp: value })} />
          <SliderRow label="Penalty per clue" value={`${draft.riverGameHintPenaltyPercent}%`}
            min={0} max={33} step={1} current={draft.riverGameHintPenaltyPercent}
            onChange={value => onChange({ riverGameHintPenaltyPercent: value })} last />
        </div>
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <h2 className="text-sm font-semibold mb-1">Country silhouettes · globe game</h2>
          <p className="text-xs text-white/30 mb-4">XP is awarded once per country. Region and country clues cost one unit each, the capital and the landmark photo two.</p>
          <SliderRow label="Base XP per country" value={`${draft.silhouetteGameBaseXp} XP`}
            min={10} max={1000} step={10} current={draft.silhouetteGameBaseXp}
            onChange={value => onChange({ silhouetteGameBaseXp: value })} />
          <SliderRow label="Penalty per clue unit" value={`${draft.silhouetteGameHintPenaltyPercent}%`}
            min={0} max={16} step={1} current={draft.silhouetteGameHintPenaltyPercent}
            onChange={value => onChange({ silhouetteGameHintPenaltyPercent: value })} last />
        </div>
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <h2 className="text-sm font-semibold mb-1">Guess the flag · flag plaza</h2>
          <p className="text-xs text-white/30 mb-4">XP is awarded once per flag. Region and first letter cost one unit each, the capital two.</p>
          <SliderRow label="Base XP per flag" value={`${draft.flagGameBaseXp} XP`}
            min={10} max={1000} step={10} current={draft.flagGameBaseXp}
            onChange={value => onChange({ flagGameBaseXp: value })} />
          <SliderRow label="Penalty per clue unit" value={`${draft.flagGameHintPenaltyPercent}%`}
            min={0} max={25} step={1} current={draft.flagGameHintPenaltyPercent}
            onChange={value => onChange({ flagGameHintPenaltyPercent: value })} last />
        </div>
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <h2 className="text-sm font-semibold mb-1">Around the World Quiz · world tour</h2>
          <p className="text-xs text-white/30 mb-4">XP is awarded once per question for a correct answer and multiplied by the level of the visit (1–3). Every stop has two questions.</p>
          <SliderRow label="Base XP per correct answer (× level)" value={`${draft.quizGameAnswerXp} XP`}
            min={5} max={500} step={5} current={draft.quizGameAnswerXp}
            onChange={value => onChange({ quizGameAnswerXp: value })} last />
        </div>
      </div>

      {/* ─── Col 2: XP & Leveling ─── */}
      <div className="space-y-5">
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-5">
            <div className="w-8 h-8 rounded-lg bg-violet-500/10 flex items-center justify-center shrink-0">
              <TrendingUp size={15} className="text-violet-400/70" />
            </div>
            <h2 className="text-sm font-semibold">XP & Leveling</h2>
          </div>

          {/* Base XP per difficulty */}
          <div className="mb-5">
            <p className="text-xs text-white/40 mb-3">Base XP per difficulty</p>
            <div className="grid grid-cols-3 gap-3">
              {DIFFICULTY_OPTIONS.map(level => {
                const cfg = LEVEL_CONFIG[level]
                const key = `baseXp${level.charAt(0)}${level.slice(1).toLowerCase()}` as keyof AppSettings
                return (
                  <div key={level} className="space-y-1.5">
                    <div className="flex items-center gap-1.5">
                      <cfg.icon size={12} className={cfg.color} />
                      <span className={cn('text-[11px] font-semibold', cfg.color)}>{level}</span>
                    </div>
                    <input type="number" min={MIN_BASE_XP} max={MAX_BASE_XP} value={draft[key] as number}
                      onChange={e => onChange({ [key]: Math.max(MIN_BASE_XP, Math.min(MAX_BASE_XP, Number(e.target.value) || MIN_BASE_XP)) })}
                      className="w-full h-9 px-3 rounded-lg bg-white/[.04] border border-white/[.08] text-sm font-mono tabular-nums text-white/80 outline-none focus:border-white/20 transition-colors" />
                  </div>
                )
              })}
            </div>
          </div>

          <SliderRow label="Hints bonus (max)" value={`${Math.round(draft.hintsBonusMultiplier * 100)}%`}
            min={0} max={1} step={MULTIPLIER_STEP} current={draft.hintsBonusMultiplier}
            onChange={v => onChange({ hintsBonusMultiplier: v })} />

          <SliderRow label="Attempts bonus (max)" value={`${Math.round(draft.attemptsBonusMultiplier * 100)}%`}
            min={0} max={1} step={MULTIPLIER_STEP} current={draft.attemptsBonusMultiplier}
            onChange={v => onChange({ attemptsBonusMultiplier: v })} />

          <SliderRow label="Time bonus (max)" value={`${Math.round(draft.timeBonusMultiplier * 100)}%`}
            min={0} max={1} step={MULTIPLIER_STEP} current={draft.timeBonusMultiplier}
            onChange={v => onChange({ timeBonusMultiplier: v })} />

          <SliderRow label="Time threshold" value={`${draft.timeBonusThreshold}s`}
            min={MIN_TIME_THRESHOLD} max={MAX_TIME_THRESHOLD} step={5} current={draft.timeBonusThreshold}
            onChange={v => onChange({ timeBonusThreshold: v })} showRange
            rangeMin={`${MIN_TIME_THRESHOLD}s`} rangeMax={`${MAX_TIME_THRESHOLD}s`} />

          <SliderRow label="Participation XP" value={`${Math.round(draft.participationMultiplier * 100)}%`}
            min={0} max={0.5} step={MULTIPLIER_STEP} current={draft.participationMultiplier}
            onChange={v => onChange({ participationMultiplier: v })} />

          <SliderRow label="Level divisor" value={String(draft.levelDivisor)}
            min={MIN_LEVEL_DIVISOR} max={MAX_LEVEL_DIVISOR} step={10} current={draft.levelDivisor}
            onChange={v => onChange({ levelDivisor: v })} showRange
            rangeMin={String(MIN_LEVEL_DIVISOR)} rangeMax={String(MAX_LEVEL_DIVISOR)} />

          <SliderRow label="Max attempts" value={String(draft.maxAttempts)}
            min={MIN_MAX_ATTEMPTS} max={MAX_MAX_ATTEMPTS} step={1} current={draft.maxAttempts}
            onChange={v => onChange({ maxAttempts: v })} showRange last
            rangeMin={String(MIN_MAX_ATTEMPTS)} rangeMax={String(MAX_MAX_ATTEMPTS)} />
        </div>
      </div>

      {/* ─── Col 3: Live Preview ─── */}
      <div className="space-y-5">
        {/* XP Calculator */}
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-8 h-8 rounded-lg bg-cyan-500/10 flex items-center justify-center shrink-0">
              <Calculator size={15} className="text-cyan-400/70" />
            </div>
            <h2 className="text-sm font-semibold">XP Calculator</h2>
          </div>

          <div className="flex gap-2 mb-4">
            {DIFFICULTY_OPTIONS.map(level => {
              const cfg = LEVEL_CONFIG[level]
              return (
                <button key={level} onClick={() => setCalcDifficulty(level)}
                  className={cn(
                    'flex-1 flex items-center justify-center gap-1.5 h-8 rounded-lg text-xs font-semibold transition-all cursor-pointer',
                    calcDifficulty === level
                      ? cn(cfg.bg, cfg.color, 'border border-current/20')
                      : 'bg-white/[.03] text-white/30 border border-white/[.06] hover:bg-white/[.05]'
                  )}>
                  <cfg.icon size={12} />{level}
                </button>
              )
            })}
          </div>

          <CalcSlider icon={Lightbulb} label="Hints revealed"
            value={calcHintsRevealed} max={draft.hintsPerCountry} min={0}
            display={`${calcHintsRevealed}/${draft.hintsPerCountry}`}
            onChange={setCalcHintsRevealed} />

          <CalcSlider icon={Crosshair} label="Attempts used"
            value={calcAttemptsUsed} max={draft.maxAttempts} min={1}
            display={`${calcAttemptsUsed}/${draft.maxAttempts}`}
            onChange={setCalcAttemptsUsed} />

          <CalcSlider icon={Timer} label="Time per country"
            value={calcTimeSpent} max={draft.timeBonusThreshold} min={1}
            display={`${calcTimeSpent}s / ${draft.timeBonusThreshold}s`}
            onChange={setCalcTimeSpent} last />

          <div className="rounded-lg bg-white/[.02] border border-white/[.06] p-4 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="text-white/40">Base XP</span>
              <span className="font-mono text-white/70">{xpCalc.baseXp}</span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-white/40">Hints bonus</span>
              <span className="font-mono text-emerald-400/70">
                +{Math.round(xpCalc.hintsBonusPct * 100)}%
                <span className="text-white/20 ml-1.5">({draft.hintsPerCountry - calcHintsRevealed}/{draft.hintsPerCountry})</span>
              </span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-white/40">Attempts bonus</span>
              <span className="font-mono text-emerald-400/70">
                +{Math.round(xpCalc.attemptsBonusPct * 100)}%
                <span className="text-white/20 ml-1.5">({draft.maxAttempts - calcAttemptsUsed}/{draft.maxAttempts})</span>
              </span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-white/40">Time bonus</span>
              <span className="font-mono text-emerald-400/70">
                +{Math.round(xpCalc.timeBonusPct * 100)}%
                <span className="text-white/20 ml-1.5">({draft.timeBonusThreshold - calcTimeSpent}s saved)</span>
              </span>
            </div>
            <div className="border-t border-white/[.06] pt-2 mt-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-white/60">Total</span>
                <span className="text-lg font-bold tabular-nums text-white/90">{xpCalc.total} XP</span>
              </div>
            </div>
          </div>
        </div>

        {/* Level Progression */}
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center shrink-0">
              <Target size={15} className="text-amber-400/70" />
            </div>
            <div>
              <h2 className="text-sm font-semibold">Level Progression</h2>
              <p className="text-[10px] text-white/25 mt-0.5 font-mono">
                level = floor(sqrt(xp / {draft.levelDivisor})) + 1
              </p>
            </div>
          </div>
          <div className="space-y-1">
            {levelTable.map(({ level, xpRequired }) => (
              <div key={level} className="flex items-center justify-between px-3 py-1.5 rounded-md hover:bg-white/[.02] transition-colors">
                <div className="flex items-center gap-2">
                  <span className="w-6 text-center text-xs font-bold text-white/50">{level}</span>
                  <div className="h-1 rounded-full bg-white/10"
                    style={{ width: `${Math.min(100, (xpRequired / (levelTable[levelTable.length - 1]?.xpRequired || 1)) * 100)}px` }}>
                    <div className="h-full rounded-full bg-gradient-to-r from-violet-500/40 to-violet-400/60 w-full" />
                  </div>
                </div>
                <span className="text-xs font-mono tabular-nums text-white/40">{xpRequired.toLocaleString()} XP</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </>
  )
}

function SliderRow({ label, value, min, max, step, current, onChange, showRange, rangeMin, rangeMax, last }: {
  label: string; value: string; min: number; max: number; step: number; current: number
  onChange: (v: number) => void; showRange?: boolean; rangeMin?: string; rangeMax?: string; last?: boolean
}) {
  return (
    <div className={last ? '' : 'mb-4'}>
      <div className="flex items-center justify-between mb-2">
        <p className="text-xs text-white/40">{label}</p>
        <span className="text-xs font-mono font-bold text-white/70">{value}</span>
      </div>
      <input type="range" min={min} max={max} step={step} value={current}
        onChange={e => onChange(Number(e.target.value))} className="w-full accent-accent h-1.5 cursor-pointer" />
      {showRange && (
        <div className="flex justify-between mt-1 text-[10px] text-white/15 tabular-nums px-0.5">
          <span>{rangeMin}</span><span>{rangeMax}</span>
        </div>
      )}
    </div>
  )
}

function CalcSlider({ icon: Icon, label, value, min, max, display, onChange, last }: {
  icon: typeof Lightbulb; label: string; value: number; min: number; max: number
  display: string; onChange: (v: number) => void; last?: boolean
}) {
  return (
    <div className={last ? 'mb-4' : 'mb-3'}>
      <div className="flex items-center justify-between mb-1.5">
        <div className="flex items-center gap-1.5">
          <Icon size={11} className="text-white/30" />
          <span className="text-[11px] text-white/40">{label}</span>
        </div>
        <span className="text-xs font-mono text-white/60">{display}</span>
      </div>
      <input type="range" min={min} max={max} value={value}
        onChange={e => onChange(Number(e.target.value))} className="w-full accent-accent h-1 cursor-pointer" />
    </div>
  )
}
