import { useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import {
  Banknote,
  CalendarDays,
  Lightbulb,
  LoaderCircle,
  Percent,
  Ruler,
  Scale,
} from 'lucide-react'

import type {
  BudgetAnswerChoice,
  BudgetAnsweredQuestion,
  BudgetChallengeQuestion,
  BudgetRevealedHint,
} from '@/api/budget'
import { useTranslation } from '@/i18n'
import {
  formatBudgetInput,
  formatAnswer,
  formatNumber,
  formatUsd,
  humanize,
  initialGuess,
  parseBudgetAmount,
  sliderBounds,
} from './budgetFormatters'
import { BudgetRevealStage } from './BudgetRevealStage'

interface BudgetAnswerPanelProps {
  question: BudgetChallengeQuestion
  answer: BudgetAnsweredQuestion | null
  submitting: boolean
  error: string
  completed: boolean
  fullXpThresholdPercent: number
  selectedChoice: BudgetAnswerChoice | null
  onSubmitValue: (value: number) => Promise<void>
  onSubmitChoice: (choice: BudgetAnswerChoice) => Promise<void>
  onContinue: () => void
}

export function BudgetAnswerPanel({
  question,
  answer,
  submitting,
  error,
  completed,
  fullXpThresholdPercent,
  selectedChoice,
  onSubmitValue,
  onSubmitChoice,
  onContinue,
}: BudgetAnswerPanelProps) {
  const { t } = useTranslation()
  const [amount, setAmount] = useState(String(initialGuess(question.type)))
  const bounds = sliderBounds(question.type)
  const numericAmount = parseBudgetAmount(amount)
  const sliderValue = Number.isFinite(numericAmount) && numericAmount > 0
    ? Math.min(bounds.max, Math.max(bounds.min, Math.log10(numericAmount)))
    : bounds.min
  const tolerance = Math.min(0.95, Math.max(0, fullXpThresholdPercent / 100))
  const lowerOffset = Math.log10(1 + tolerance)
  const upperOffset = tolerance === 0 ? 0 : -Math.log10(1 - tolerance)
  const selectionStart = Math.max(bounds.min, sliderValue - lowerOffset)
  const selectionEnd = Math.min(bounds.max, sliderValue + upperOffset)
  const selectionSpan = Math.max(0.02, selectionEnd - selectionStart)
  const lensPadding = Math.max(0.16, selectionSpan * 1.35)
  const lensMin = Math.max(bounds.min, selectionStart - lensPadding)
  const lensMax = Math.min(bounds.max, selectionEnd + lensPadding)
  const lensSpan = Math.max(0.02, lensMax - lensMin)
  const lensStartPercent = Math.max(0, Math.min(100, (selectionStart - lensMin) / lensSpan * 100))
  const lensEndPercent = Math.max(lensStartPercent, Math.min(100, (selectionEnd - lensMin) / lensSpan * 100))
  const rangeTrackRef = useRef<HTMLDivElement>(null)
  const [rangeDrag, setRangeDrag] = useState<{ pointerId: number; startX: number; startSlider: number } | null>(null)
  const allowsZero = question.type === 'BUDGET_OVERRUN' || question.type === 'HOW_LATE'
  const canSubmit = Number.isFinite(numericAmount)
    && (numericAmount > 0 || (allowsZero && numericAmount === 0))
    && !submitting

  const title = useMemo(() => {
    switch (question.type) {
      case 'ORIGINAL_BUDGET': return t.budget.session.originalBudget
      case 'BUDGET_OVERRUN': return t.budget.session.budgetOverrun
      case 'HOW_LATE': return t.budget.session.howLate
      case 'HIGHER_LOWER': return t.budget.session.higherLower
      case 'COST_PER_UNIT': return t.budget.session.costPerUnit
      case 'CURRENT_ESTIMATE': return t.budget.session.currentEstimate
      case 'SPENT_SO_FAR': return t.budget.session.spentSoFar
      case 'HOW_MUCH': return question.prompt.project?.answerCostKind === 'CURRENT_ESTIMATE'
        ? t.budget.session.currentEstimate
        : t.budget.session.howMuch
      default: return t.budget.session.howMuch
    }
  }, [question.type, question.prompt.project?.answerCostKind, t])
  const questionHint = question.type === 'HOW_LATE'
    ? t.budget.session.monthsHint
    : question.type === 'BUDGET_OVERRUN'
      ? t.budget.session.percentHint
      : question.type === 'COST_PER_UNIT'
        ? t.budget.session.costPerUnitHint
        : t.budget.guessHint
  const QuestionIcon = question.type === 'HOW_LATE'
    ? CalendarDays
    : question.type === 'BUDGET_OVERRUN'
      ? Percent
      : question.type === 'COST_PER_UNIT'
        ? Ruler
        : Banknote

  if (answer) {
    return (
      <BudgetRevealStage
        answer={answer}
        completed={completed}
        fullXpThresholdPercent={fullXpThresholdPercent}
        onContinue={onContinue}
      />
    )
  }

  if (question.type === 'HIGHER_LOWER') {
    const selectedProject = selectedChoice === 'PRIMARY'
      ? question.prompt.primaryProject
      : selectedChoice === 'COMPARISON'
        ? question.prompt.comparisonProject
        : null

    return (
      <aside
        id="budget-answer-panel"
        className="sticky bottom-3 z-30 mt-4 rounded-[1.5rem] border border-white/[.1] bg-[#090b12]/[.96] p-3 shadow-[0_22px_70px_rgba(0,0,0,.62)] backdrop-blur-xl sm:p-4 lg:static"
      >
        <div className="grid gap-3 sm:grid-cols-[minmax(0,.8fr)_minmax(0,1.2fr)_auto] sm:items-center">
          <HigherLowerHeading title={t.budget.session.chooseProject} />
          <div className={`min-w-0 rounded-2xl border px-4 py-3 transition-colors ${selectedProject
            ? 'border-emerald-300/20 bg-emerald-300/[.07]'
            : 'border-white/[.07] bg-white/[.025]'}`}>
            <p className={`truncate text-sm font-black tracking-[-.015em] sm:text-base ${selectedProject ? 'text-emerald-100' : 'text-white/35'}`}>
              {selectedProject?.name ?? '—'}
            </p>
            <p className="mt-1 truncate text-[9px] font-semibold uppercase tracking-[.13em] text-white/35">
              {selectedProject?.countryName ?? title}
            </p>
          </div>
          <button
            type="button"
            disabled={!selectedChoice || submitting}
            onClick={() => selectedChoice && void onSubmitChoice(selectedChoice)}
            className="flex min-h-14 w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-emerald-300 to-teal-300 px-6 text-sm font-black text-[#06100d] transition hover:-translate-y-0.5 hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:translate-y-0 sm:w-auto sm:min-w-48"
          >
            {submitting ? <LoaderCircle size={17} className="animate-spin" /> : <Scale size={17} />}
            {submitting ? t.budget.checking : t.budget.submit}
          </button>
        </div>
        {error && <p className="mt-3 px-1 text-xs text-red-300/80">{error}</p>}
      </aside>
    )
  }

  const displayedUnit = question.type === 'BUDGET_OVERRUN'
    ? t.budget.session.answerUnitPercent
    : question.type === 'HOW_LATE'
      ? t.budget.session.answerUnitMonths
      : question.type === 'COST_PER_UNIT'
        ? `USD / ${question.prompt.metric?.unit ?? 'unit'}`
        : t.budget.session.answerUnitUsd
  const acceptsShorthand = question.type === 'HOW_MUCH'
    || question.type === 'ORIGINAL_BUDGET'
    || question.type === 'COST_PER_UNIT'
    || question.type === 'CURRENT_ESTIMATE'
    || question.type === 'SPENT_SO_FAR'
  const inputSuffix = acceptsShorthand
    ? question.type === 'COST_PER_UNIT'
      ? `/ ${humanize(question.prompt.metric?.unit ?? 'unit')}`
      : ''
    : displayedUnit

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (canSubmit) void onSubmitValue(numericAmount)
  }

  const setSliderAmount = (value: number) => {
    const centerMin = Math.min(bounds.max, bounds.min + lowerOffset)
    const centerMax = Math.max(centerMin, bounds.max - upperOffset)
    const nextSliderValue = Math.min(centerMax, Math.max(centerMin, value))
    setAmount(String(Math.max(1, Math.round(10 ** nextSliderValue))))
  }

  const moveRange = (clientX: number, pointerId: number) => {
    if (!rangeDrag || rangeDrag.pointerId !== pointerId || !rangeTrackRef.current) return
    const width = rangeTrackRef.current.getBoundingClientRect().width
    if (width <= 0) return
    setSliderAmount(rangeDrag.startSlider + (clientX - rangeDrag.startX) / width * (bounds.max - bounds.min))
  }

  return (
    <aside
      id="budget-answer-panel"
      className="h-full rounded-[1.75rem] border border-white/[.09] bg-[#0a0b13]/95 p-5 shadow-[0_28px_90px_rgba(0,0,0,.42)] sm:p-6 lg:sticky lg:top-20"
    >
      <form onSubmit={submit} className="flex h-full flex-col">
        <QuestionHeading title={title} hint={questionHint} icon={<QuestionIcon size={20} />} />

        <div className="mt-6 flex-1">
        <label className="block">
          <span className="mb-2.5 block text-[11px] font-bold uppercase tracking-[.16em] text-white/38">{t.budget.yourGuess}</span>
          <div className="flex items-center rounded-2xl border border-white/[.1] bg-black/30 px-4 focus-within:border-emerald-300/35">
            {acceptsShorthand && <span className="text-[1.35rem] font-bold text-emerald-300">$</span>}
            <input
              value={formatBudgetInput(amount)}
              onChange={event => setAmount(sanitizeAmount(event.target.value, acceptsShorthand))}
              inputMode="decimal"
              className="min-w-0 flex-1 bg-transparent px-3 py-[1.125rem] text-[1.625rem] font-black tracking-[-.03em] outline-none"
            />
            {inputSuffix && <span className="max-w-28 truncate text-[10px] font-bold uppercase text-white/45">{inputSuffix}</span>}
          </div>
          {acceptsShorthand && (
            <span className="mt-2.5 block text-xs leading-5 text-white/40">{t.budget.session.inputShorthand}</span>
          )}
        </label>

        <div className="mt-6 rounded-2xl border border-white/[.07] bg-white/[.025] p-4 sm:p-5">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[.16em] text-white/38">{t.budget.session.yourCurrentAnswer}</p>
            <p className="mt-2 text-[2rem] font-black tracking-[-.05em] text-emerald-200 sm:text-[2.125rem]">
              {formatDraftValue(numericAmount || 0, question, t.budget.session.answerUnitMonths)}
            </p>
          </div>
          <div className="mt-7">
            <div
              ref={rangeTrackRef}
              className="relative touch-none"
              onPointerDown={event => {
                if ((event.target as HTMLElement).closest('[data-budget-range-window]')) return
                const rect = event.currentTarget.getBoundingClientRect()
                const ratio = rect.width > 0 ? (event.clientX - rect.left) / rect.width : 0
                const zoomed = (event.target as HTMLElement).closest('[data-budget-zoom-track]')
                const min = zoomed ? lensMin : bounds.min
                const max = zoomed ? lensMax : bounds.max
                setSliderAmount(min + Math.max(0, Math.min(1, ratio)) * (max - min))
              }}
              onPointerMove={event => moveRange(event.clientX, event.pointerId)}
              onPointerUp={() => setRangeDrag(null)}
              onPointerCancel={() => setRangeDrag(null)}
            >
              <div className="pointer-events-none flex items-center justify-between gap-3 text-[9px] font-bold uppercase tracking-[.14em] text-white/28">
                <span>{formatScale(10 ** bounds.min, question.type)}</span>
                <span className="text-emerald-200/65">{t.budget.session.rangeFrom} {formatScale(10 ** selectionStart, question.type)} · {t.budget.session.rangeTo} {formatScale(10 ** selectionEnd, question.type)}</span>
                <span>{formatScale(10 ** bounds.max, question.type)}</span>
              </div>

              <div data-budget-zoom-track className="relative mt-3 rounded-2xl border border-emerald-300/15 bg-emerald-300/[.045] px-3 pb-4 pt-8 sm:px-4">
                <div className="pointer-events-none absolute inset-x-4 top-2 flex items-center justify-between text-[9px] font-bold uppercase tracking-[.12em] text-emerald-100/55 sm:inset-x-5">
                  <span>{t.budget.session.rangeFrom}</span>
                  <span>{t.budget.session.rangeTo}</span>
                </div>
                <div className="relative h-12">
                  <div className="pointer-events-none absolute inset-x-0 top-5 h-3 rounded-full bg-white/[.1]" />
                  <div
                    className="pointer-events-none absolute top-5 h-3 rounded-full bg-gradient-to-r from-emerald-300/45 via-emerald-200 to-teal-300/45 shadow-[0_0_22px_rgba(52,211,153,.22)]"
                    style={{ left: `${lensStartPercent}%`, width: `${Math.max(8, lensEndPercent - lensStartPercent)}%` }}
                  />
                  <div className="pointer-events-none absolute inset-x-0 top-4 z-[1] flex justify-between px-px">
                    {Array.from({ length: 7 }, (_, index) => <span key={index} className="h-5 w-px bg-white/25" />)}
                  </div>
                  <div
                    data-budget-range-window
                    className="absolute top-3 z-20 h-7 cursor-grab rounded-full border border-emerald-50/90 bg-emerald-100 shadow-[0_0_0_4px_rgba(110,231,183,.12),0_5px_18px_rgba(0,0,0,.35)] active:cursor-grabbing"
                    style={{ left: `${lensStartPercent}%`, width: `${Math.max(8, lensEndPercent - lensStartPercent)}%` }}
                    onPointerDown={event => {
                      event.preventDefault()
                      event.stopPropagation()
                      event.currentTarget.setPointerCapture(event.pointerId)
                      setRangeDrag({ pointerId: event.pointerId, startX: event.clientX, startSlider: sliderValue })
                    }}
                    onPointerUp={() => setRangeDrag(null)}
                    aria-hidden="true"
                  >
                    <span className="absolute -left-px -top-1.5 h-10 w-1 rounded-full bg-white shadow-[0_0_8px_rgba(255,255,255,.8)]" />
                    <span className="absolute -right-px -top-1.5 h-10 w-1 rounded-full bg-white shadow-[0_0_8px_rgba(255,255,255,.8)]" />
                  </div>
                  <input
                    type="range"
                    min={bounds.min}
                    max={bounds.max}
                    step="0.01"
                    value={sliderValue}
                    onChange={event => setSliderAmount(Number(event.target.value))}
                    className="budget-range pointer-events-none absolute inset-x-0 top-0 z-10 h-12 w-full bg-transparent opacity-0"
                    aria-label={title}
                    aria-valuetext={`${formatScale(10 ** selectionStart, question.type)} – ${formatScale(10 ** selectionEnd, question.type)}`}
                  />
                </div>
                <div className="pointer-events-none mt-1 flex items-center justify-between gap-2 text-[10px] font-semibold text-white/38">
                  <span>{formatScale(10 ** lensMin, question.type)}</span>
                  <span className="text-center text-emerald-100/75">{formatScale(numericAmount || 0, question.type)}</span>
                  <span>{formatScale(10 ** lensMax, question.type)}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        </div>

        {error && <p className="mt-4 text-xs text-red-300/75">{error}</p>}
        <button
          type="submit"
          disabled={!canSubmit}
          className="mt-auto flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-emerald-300 to-teal-300 px-5 py-[1.125rem] text-[15px] font-black text-[#06100d] transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-30"
        >
          {submitting ? <LoaderCircle size={17} className="animate-spin" /> : <QuestionIcon size={17} />}
          {submitting ? t.budget.checking : t.budget.submit}
        </button>
      </form>
    </aside>
  )
}

export function BudgetHints({
  question,
  revealing,
  disabled,
  onReveal,
}: {
  question: BudgetChallengeQuestion
  revealing: boolean
  disabled: boolean
  onReveal: (hintIndex: number) => Promise<void>
}) {
  const { t } = useTranslation()
  const revealedHints = question.revealedHints ?? []
  const availableHintCount = question.availableHintCount ?? 0
  const nextHintIndex = revealedHints.length
  const hasNextHint = nextHintIndex < availableHintCount
  const nextMultiplier = nextHintIndex === 0 && availableHintCount > 1 ? 85 : 60
  const penalty = 100 - nextMultiplier

  if (availableHintCount === 0) return null

  return (
    <section className="rounded-[1.5rem] border border-amber-200/[.12] bg-[linear-gradient(135deg,rgba(251,191,36,.055),rgba(8,9,15,.94))] p-4 sm:p-5">
      <div className={`grid gap-4 ${hasNextHint ? 'md:grid-cols-[minmax(0,1fr)_minmax(17rem,.52fr)] md:items-center' : ''}`}>
        <div className="min-w-0">
          <div className="flex items-start gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-amber-200/[.09] text-amber-200">
              <Lightbulb size={17} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-[10px] font-black uppercase tracking-[.15em] text-amber-100/65">
                {t.budget.session.hintsTitle}
              </p>
              <p className="mt-1 text-[11px] leading-4 text-white/40">{t.budget.session.hintsBody}</p>
            </div>
          </div>

          {revealedHints.length > 0 && (
            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              {revealedHints.map(hint => <RevealedHint key={hint.index} hint={hint} />)}
            </div>
          )}
        </div>

        {hasNextHint && (
          <button
            type="button"
            disabled={disabled || revealing}
            onClick={() => void onReveal(nextHintIndex)}
            className="flex min-h-14 w-full items-center justify-between gap-3 rounded-2xl border border-amber-100/[.14] bg-black/25 px-4 text-left shadow-[0_16px_40px_rgba(0,0,0,.2)] transition hover:border-amber-100/28 hover:bg-amber-100/[.055] disabled:cursor-not-allowed disabled:opacity-45"
          >
            <span className="flex min-w-0 items-center gap-2 text-xs font-black text-amber-100/80">
              {revealing ? <LoaderCircle size={15} className="shrink-0 animate-spin" /> : <Lightbulb size={15} className="shrink-0" />}
              {nextHintIndex === 0 && availableHintCount > 1
                ? t.budget.session.revealHint
                : t.budget.session.narrowRange}
            </span>
            <span className="shrink-0 text-right text-[9px] font-bold uppercase tracking-[.1em] text-rose-200/65">
              {t.budget.session.hintPenalty.replace('{percent}', String(penalty))}
            </span>
          </button>
        )}
      </div>
    </section>
  )
}

function RevealedHint({ hint }: { hint: BudgetRevealedHint }) {
  const { t } = useTranslation()
  const payload = hint.payload

  if (payload.type === 'COMPARABLE_PROJECT') {
    const metric = payload.metricValue != null && payload.metricUnit
      ? `${formatNumber(payload.metricValue)} ${humanize(payload.metricUnit)}`
      : null
    return (
      <div className="rounded-xl border border-white/[.07] bg-black/20 px-3.5 py-3">
        <p className="text-[9px] font-black uppercase tracking-[.14em] text-amber-100/45">
          {t.budget.session.comparableProject}
        </p>
        <p className="mt-1.5 text-sm font-black text-white/82">{payload.projectName}</p>
        <p className="mt-1 text-xs text-white/52">
          {formatAnswer(payload.value, payload.unit, true, t.budget.session.answerUnitMonths)}
          {metric ? ` · ${metric}` : ''}
        </p>
      </div>
    )
  }

  return (
    <div className="rounded-xl border border-white/[.07] bg-black/20 px-3.5 py-3">
      <p className="text-[9px] font-black uppercase tracking-[.14em] text-amber-100/45">
        {t.budget.session.answerRange}
      </p>
      <p className="mt-1.5 text-sm font-black text-white/82">
        {formatAnswer(payload.minimum, payload.unit, true, t.budget.session.answerUnitMonths)}
        {' — '}
        {formatAnswer(payload.maximum, payload.unit, true, t.budget.session.answerUnitMonths)}
      </p>
    </div>
  )
}

function QuestionHeading({ title, hint, icon }: { title: string; hint: string; icon: ReactNode }) {
  return (
    <div className="flex items-start gap-3.5">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-400/[.1] text-emerald-300">
        {icon}
      </div>
      <div className="min-w-0">
        <h3 className="text-[1.35rem] font-black leading-tight tracking-[-.025em] sm:text-[1.45rem]">{title}</h3>
        <p className="mt-2 text-[13px] leading-[1.35rem] text-white/50">{hint}</p>
      </div>
    </div>
  )
}

function HigherLowerHeading({ title }: { title: string }) {
  return (
    <div className="flex items-center gap-3.5">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-400/[.1] text-emerald-300">
        <Scale size={20} />
      </div>
      <div className="min-w-0">
        <h3 className="text-xl font-black leading-tight tracking-[-.025em]">{title}</h3>
      </div>
    </div>
  )
}

function sanitizeAmount(value: string, allowSuffix: boolean) {
  const cleaned = value.replaceAll(',', '').replaceAll(' ', '')
  const suffix = allowSuffix ? cleaned.match(/[kmbt]/i)?.[0]?.toLocaleUpperCase() ?? '' : ''
  const sanitized = cleaned.replace(/[^0-9.]/g, '').replace(/(\..*)\./g, '$1')
  const [integer = '', fraction] = sanitized.split('.')
  const compactInteger = integer.replace(/^0+(?=\d)/, '').slice(0, 18)
  const numeric = fraction == null ? compactInteger : `${compactInteger}.${fraction.slice(0, 6)}`
  return `${numeric}${suffix}`
}

function formatScale(value: number, type: BudgetChallengeQuestion['type']) {
  if (type === 'HOW_MUCH'
    || type === 'ORIGINAL_BUDGET'
    || type === 'COST_PER_UNIT'
    || type === 'CURRENT_ESTIMATE'
    || type === 'SPENT_SO_FAR') {
    return formatUsd(value, true)
  }
  return formatNumber(value, 0)
}

function formatDraftValue(value: number, question: BudgetChallengeQuestion, monthsLabel: string) {
  if (question.type === 'BUDGET_OVERRUN') return `${formatNumber(value, 1)}%`
  if (question.type === 'HOW_LATE') return `${formatNumber(value, 0)} ${monthsLabel}`
  if (question.type === 'COST_PER_UNIT') {
    return `${formatUsd(value, true)} / ${humanize(question.prompt.metric?.unit ?? 'unit')}`
  }
  return formatUsd(value, true)
}
