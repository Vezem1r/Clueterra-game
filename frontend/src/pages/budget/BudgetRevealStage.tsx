import {
  ArrowLeft,
  ArrowRight,
  Banknote,
  CalendarDays,
  Calculator,
  CheckCircle2,
  ChevronDown,
  ExternalLink,
  Gauge,
  Layers3,
  Target,
  Trophy,
  XCircle,
} from 'lucide-react'
import type { ReactNode } from 'react'

import type {
  BudgetAnswerExplanation,
  BudgetAnsweredQuestion,
  BudgetFormulaMilestone,
  BudgetReveal,
} from '@/api/budget'
import { useTranslation } from '@/i18n'
import { BudgetProjectStage } from './BudgetProjectStage'
import { formatAnswer, formatNumber, formatUsd, humanize } from './budgetFormatters'

interface BudgetRevealStageProps {
  answer: BudgetAnsweredQuestion
  completed: boolean
  fullXpThresholdPercent: number
  onContinue: () => void
  reviewMode?: boolean
}

interface RevealSourceReference {
  name: string
  href: string
  facts: string[]
  licenseLabel?: string
  licenseUrl?: string
}

type Translation = ReturnType<typeof useTranslation>['t']

export function BudgetRevealStage({
  answer,
  completed,
  fullXpThresholdPercent,
  onContinue,
  reviewMode = false,
}: BudgetRevealStageProps) {
  const { t } = useTranslation()
  const isChoice = answer.question.type === 'HIGHER_LOWER'
  const fullXp = answer.rawXpPercent >= 100
  const tone = resultTone(fullXp, answer.rawXpPercent)
  const ResultIcon = fullXp ? CheckCircle2 : tone === 'warning' ? Target : XCircle
  const difference = answer.guessedValue != null && answer.correctValue != null
    ? Math.abs(answer.guessedValue - answer.correctValue)
    : null
  const relativeError = difference != null && answer.correctValue
    ? difference / answer.correctValue * 100
    : null
  const guessedAnswer = isChoice
    ? choiceProjectName(answer, answer.guessedChoice)
    : formatAnswer(answer.guessedValue, answer.correctUnit, true, t.budget.session.answerUnitMonths)
  const correctAnswer = isChoice
    ? choiceProjectName(answer, answer.correctChoice)
    : formatAnswer(answer.correctValue, answer.correctUnit, true, t.budget.session.answerUnitMonths)
  const resultMessage = revealResultMessage(
    answer,
    difference,
    relativeError,
    fullXpThresholdPercent,
    t,
  )
  const questionLabel = revealQuestionLabel(answer, t)
  const projectReveals = isChoice
    ? [answer.reveal.primary, answer.reveal.comparison].filter((reveal): reveal is BudgetReveal => Boolean(reveal))
    : [answer.reveal]
  const sources = collectRevealSources(projectReveals, t)
  const displayedCostAnswer = answer.question.type === 'HOW_MUCH'
    || answer.question.type === 'ORIGINAL_BUDGET'
    || answer.question.type === 'CURRENT_ESTIMATE'
    || answer.question.type === 'SPENT_SO_FAR'
    ? answer.correctValue
    : undefined

  return (
    <section id="budget-answer-panel" className="space-y-5">
      <header className={`overflow-hidden rounded-[1.75rem] border shadow-[0_28px_90px_rgba(0,0,0,.38)] ${toneClass(tone)}`}>
        <div className="grid gap-4 p-4 sm:p-5 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
          <div className="flex min-w-0 items-start gap-3.5">
            <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl ${iconToneClass(tone)}`}>
              <ResultIcon size={22} />
            </div>
            <div className="min-w-0">
              <h2 className="text-xl font-black leading-tight tracking-[-.025em] text-white sm:text-2xl">
                {resultMessage}
              </h2>
              {!isChoice && !fullXp && (
                <p className="mt-2 text-xs leading-5 text-white/45">
                  {replaceTokens(t.budget.session.outsideFullXp, { threshold: String(fullXpThresholdPercent) })}
                </p>
              )}
              {answer.hintsUsed > 0 && (
                <p className="mt-2 text-[11px] font-semibold text-amber-100/55">
                  {t.budget.session.rawXp}: {answer.rawXpEarned} XP · {t.budget.session.hintMultiplier}: {formatNumber(answer.hintMultiplierPercent, 0)}% · {t.budget.session.finalXp}: {answer.xpEarned} XP
                </p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2 sm:grid-cols-[repeat(2,minmax(8rem,1fr))_auto]">
            <ResultMetric label={t.budget.accuracy} value={`${formatNumber(answer.accuracyPercent, 1)}%`} />
            <ResultMetric label={t.budget.session.xpEarned} value={`+${answer.xpEarned} XP`} accent />
            <button
              type="button"
              onClick={onContinue}
                  className="col-span-2 flex min-h-[3.25rem] items-center justify-center gap-2 rounded-2xl bg-white px-5 text-sm font-black text-black transition hover:-translate-y-0.5 hover:bg-emerald-100 sm:col-span-1 sm:min-w-40"
            >
              {reviewMode && <ArrowLeft size={17} />}
              {reviewMode
                ? t.budget.session.backToResults
                : completed
                  ? t.budget.session.seeResults
                  : t.budget.session.nextQuestion}
              {!reviewMode && (completed ? <Trophy size={17} /> : <ArrowRight size={17} />)}
            </button>
          </div>
        </div>
      </header>

      {isChoice ? (
        <div className="space-y-4">
          <BudgetProjectStage question={answer.question} answer={answer} />
          <div className={`grid gap-4 ${projectReveals.length > 1 ? 'lg:grid-cols-2' : ''}`}>
            {projectReveals.map((reveal, index) => (
              <ProjectFactsDisclosure
                key={`${reveal.project?.name ?? 'project'}-${index}`}
                reveal={reveal}
                displayedCostAnswer={reveal.actualCost?.amountUsd}
              />
            ))}
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1.5fr)_minmax(400px,.72fr)] xl:gap-5">
            <BudgetProjectStage question={answer.question} answer={answer} />
            <article className="overflow-hidden rounded-[1.75rem] border border-white/[.09] bg-[linear-gradient(155deg,rgba(14,17,27,.98),rgba(7,9,14,.98))] shadow-[0_28px_90px_rgba(0,0,0,.4)]">
              <AnswerComparison
                guessedAnswer={guessedAnswer}
                correctAnswer={correctAnswer}
                questionLabel={questionLabel}
                embedded
              />
              {answer.reveal.explanation && (
                <AnswerExplanationCard
                  explanation={answer.reveal.explanation}
                  correctValue={answer.correctValue}
                  correctUnit={answer.correctUnit}
                  embedded
                />
              )}
              <ProjectFactsDisclosure
                reveal={answer.reveal}
                displayedCostAnswer={displayedCostAnswer}
                embedded
              />
            </article>
          </div>
        </div>
      )}

      <SourcesDisclosure sources={sources} />
    </section>
  )
}

function AnswerComparison({
  guessedAnswer,
  correctAnswer,
  questionLabel,
  embedded = false,
}: {
  guessedAnswer: string
  correctAnswer: string
  questionLabel: string
  embedded?: boolean
}) {
  const { t } = useTranslation()

  return (
    <section className={embedded
      ? 'p-5 sm:p-6'
      : 'rounded-[1.75rem] border border-white/[.09] bg-[linear-gradient(155deg,rgba(16,18,29,.98),rgba(7,8,14,.98))] p-5 shadow-[0_28px_90px_rgba(0,0,0,.4)] sm:p-6'}>
      <p className="text-[9px] font-bold uppercase tracking-[.18em] text-white/35">
        {questionLabel}
      </p>
      <div className="mt-4 grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 rounded-2xl border border-white/[.06] bg-white/[.035] p-4">
        <AnswerValue label={t.budget.yours} value={guessedAnswer} muted />
        <ArrowRight size={18} className="text-white/20" />
        <AnswerValue
          label={t.budget.session.correctAnswer}
          value={correctAnswer}
          align="right"
        />
      </div>
    </section>
  )
}

function AnswerExplanationCard({
  explanation,
  correctValue,
  correctUnit,
  embedded = false,
}: {
  explanation: BudgetAnswerExplanation
  correctValue: number | null
  correctUnit: string | null
  embedded?: boolean
}) {
  const { t } = useTranslation()
  const explanationText = formatExplanation(explanation, correctValue, correctUnit, t)

  if (!explanationText) return null

  return (
    <section className={embedded
      ? 'border-t border-emerald-200/[.08] bg-emerald-300/[.018]'
      : 'overflow-hidden rounded-[1.5rem] border border-emerald-200/[.1] bg-[linear-gradient(145deg,rgba(14,34,29,.72),rgba(8,9,15,.96))]'}>
      <div className="flex items-start gap-3 px-5 py-4 sm:px-6 sm:py-5">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-emerald-300/[.09] text-emerald-200">
          <Calculator size={17} />
        </span>
        <div className="min-w-0 pt-0.5">
          <h3 className="text-sm font-black tracking-[-.01em] text-white/88">{t.budget.session.whyThisAnswer}</h3>
          <p className="mt-2 break-words text-sm font-bold leading-6 text-emerald-50/72">
            {explanationText}
          </p>
        </div>
      </div>
    </section>
  )
}

function formatExplanation(
  explanation: BudgetAnswerExplanation,
  correctValue: number | null,
  correctUnit: string | null,
  t: Translation,
) {
  const formula = explanation.formula
  if (formula?.operation === 'DIVIDE') {
    return `${formatAnswer(formula.numerator ?? null, formula.numeratorUnit ?? null, true, t.budget.session.answerUnitMonths)} ÷ ${formatNumber(formula.denominator ?? 0)} ${humanize(formula.denominatorUnit ?? '')} = ${formatAnswer(formula.result ?? correctValue, formula.resultUnit ?? correctUnit, true, t.budget.session.answerUnitMonths)}`
  }
  if (formula?.operation === 'PERCENT_INCREASE') {
    return `(${formatUsd(formula.final ?? 0, true)} − ${formatUsd(formula.original ?? 0, true)}) ÷ ${formatUsd(formula.original ?? 0, true)} × 100 = ${formatAnswer(formula.result ?? correctValue, formula.resultUnit ?? correctUnit ?? 'PERCENT', true, t.budget.session.answerUnitMonths)}`
  }
  if (formula?.operation === 'MONTHS_BETWEEN') {
    return `${formatMilestoneDate(formula.planned)} → ${formatMilestoneDate(formula.actual)} = ${formatAnswer(formula.result ?? correctValue, formula.resultUnit ?? correctUnit ?? 'MONTHS', true, t.budget.session.answerUnitMonths)}`
  }

  const conversion = explanation.conversion
  if (!conversion) return null
  const sourceLow = formatSourceAmount(conversion.sourceAmountLow, conversion.sourceCurrency)
  const sourceHigh = formatSourceAmount(conversion.sourceAmountHigh, conversion.sourceCurrency)
  const sourceAmount = conversion.sourceAmountLow === conversion.sourceAmountHigh
    ? sourceLow
    : `${sourceLow} — ${sourceHigh}`
  const usdAmount = formatUsdRange(conversion.amountUsdLow, conversion.amountUsdHigh)
  return `${sourceAmount}${conversion.costYear ? ` · ${conversion.costYear}` : ''} → ${usdAmount}`
}

function formatSourceAmount(value: number, currency: string) {
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      notation: 'compact',
      maximumFractionDigits: 2,
    }).format(value)
  } catch {
    return `${formatNumber(value, 2)} ${currency}`
  }
}

function formatMilestoneDate(milestone: BudgetFormulaMilestone | undefined) {
  return milestone?.date ?? String(milestone?.year ?? '—')
}

function ProjectFactsDisclosure({
  reveal,
  displayedCostAnswer,
  embedded = false,
}: {
  reveal: BudgetReveal
  displayedCostAnswer: number | null | undefined
  embedded?: boolean
}) {
  const { t } = useTranslation()
  const project = reveal.project
  const costs = reveal.costs ?? []
  const metrics = reveal.metrics ?? []
  const milestones = reveal.milestones ?? []
  const visibleCosts = costs.filter(cost => (
    displayedCostAnswer == null
    || !amountContains(cost.amountUsdLow, cost.amountUsdHigh, displayedCostAnswer)
  ))
  const showActualCost = reveal.actualCost
    && (displayedCostAnswer == null || reveal.actualCost.amountUsd !== displayedCostAnswer)
    && !visibleCosts.some(cost => amountContains(cost.amountUsdLow, cost.amountUsdHigh, reveal.actualCost!.amountUsd))
  const hasCostStory = Boolean(showActualCost || visibleCosts.length || reveal.budgetOverrunPercent != null)
  const hasTimeline = Boolean(milestones.length || reveal.delayMonths != null)

  if (!hasCostStory && !metrics.length && !hasTimeline) return null

  return (
    <details className={`group ${embedded
      ? 'border-t border-white/[.055]'
      : 'overflow-hidden rounded-[1.25rem] border border-white/[.06] bg-[#090a12]/80'}`}>
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-5 py-4 sm:px-6 [&::-webkit-details-marker]:hidden">
        <span className="flex min-w-0 items-center gap-2.5">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-emerald-300/[.07] text-emerald-200/65">
            <Layers3 size={15} />
          </span>
          <span className="min-w-0">
            {!embedded && project?.name && (
              <span className="block truncate text-xs font-bold text-white/68">{project.name}</span>
            )}
            <span className="block text-[9px] font-black uppercase tracking-[.15em] text-white/38">
              {t.budget.session.projectFacts}
            </span>
          </span>
        </span>
        <ChevronDown size={15} className="shrink-0 text-white/28 transition-transform group-open:rotate-180" />
      </summary>

      <div
        className="grid gap-x-5 border-t border-white/[.045] px-5 pb-5 sm:px-6"
        style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 15rem), 1fr))' }}
      >
          {hasCostStory && (
            <RevealSection icon={Banknote} title={t.budget.projectCost} flat>
              {showActualCost && reveal.actualCost && (
                <RevealRow
                  label={t.budget.session.finalCost}
                  value={formatUsd(reveal.actualCost.amountUsd)}
                />
              )}
              {visibleCosts.map((cost, index) => (
                <RevealRow
                  key={`${cost.kind}-${index}`}
                  label={costKindLabel(cost.kind, t)}
                  value={formatUsdRange(cost.amountUsdLow, cost.amountUsdHigh, cost.year)}
                />
              ))}
              {reveal.budgetOverrunPercent != null && (
                <RevealRow label={t.budget.overrun} value={`${formatNumber(reveal.budgetOverrunPercent, 1)}%`} />
              )}
            </RevealSection>
          )}

          {metrics.length > 0 && (
            <RevealSection icon={Gauge} title={t.budget.session.metric} flat>
              {metrics.map((metric, index) => (
                <RevealRow
                  key={`${metric.type}-${index}`}
                  label={humanize(metric.type)}
                  value={`${formatNumber(metric.value)} ${humanize(metric.unit)}`}
                />
              ))}
            </RevealSection>
          )}

          {hasTimeline && (
            <RevealSection icon={CalendarDays} title={t.budget.timeline} flat>
              {milestones.map((milestone, index) => (
                <RevealRow
                  key={`${milestone.type}-${index}`}
                  label={humanize(milestone.type)}
                  value={milestone.date ?? String(milestone.year ?? '—')}
                />
              ))}
              {reveal.delayMonths != null && (
                <RevealRow
                  label={t.budget.delay}
                  value={`${formatNumber(reveal.delayMonths, 0)} ${t.budget.session.answerUnitMonths}`}
                />
              )}
            </RevealSection>
          )}
      </div>
    </details>
  )
}

function RevealSection({
  icon: Icon,
  title,
  children,
  flat = false,
}: {
  icon: typeof Banknote
  title: string
  children: ReactNode
  flat?: boolean
}) {
  return (
    <section className={flat ? 'min-w-0 pt-4' : 'min-w-0 rounded-xl border border-white/[.055] bg-white/[.02] p-3.5'}>
      <div className="mb-2.5 flex items-center gap-2">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-300/[.07] text-emerald-200/65">
          <Icon size={14} />
        </span>
        <h4 className="text-[9px] font-black uppercase tracking-[.14em] text-white/48">{title}</h4>
      </div>
      <div className="grid">{children}</div>
    </section>
  )
}

function RevealRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-white/[.045] py-2 last:border-b-0">
      <p className="min-w-0 text-[10px] font-semibold leading-4 text-white/38">{label}</p>
      <p className="shrink-0 text-right text-xs font-bold leading-4 text-white/75">{value}</p>
    </div>
  )
}

function SourcesDisclosure({ sources }: { sources: RevealSourceReference[] }) {
  const { t } = useTranslation()
  if (sources.length === 0) return null

  return (
    <details className="group rounded-xl border border-white/[.045] bg-black/15 text-white/45">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 [&::-webkit-details-marker]:hidden">
        <span className="flex min-w-0 items-center gap-2">
          <ExternalLink size={13} className="shrink-0 text-white/28" />
          <span className="text-[10px] font-bold uppercase tracking-[.13em]">
            {t.budget.session.evidence}
          </span>
          <span className="rounded-md bg-white/[.035] px-1.5 py-0.5 text-[9px] text-white/28">
            {sources.length}
          </span>
        </span>
        <ChevronDown size={14} className="shrink-0 text-white/25 transition-transform group-open:rotate-180" />
      </summary>

      <div className="grid gap-2 border-t border-white/[.04] p-3 sm:grid-cols-2 xl:grid-cols-3">
        {sources.map(source => (
          <div key={source.href} className="min-w-0 rounded-lg border border-white/[.045] bg-white/[.015] px-3 py-2.5">
            <a
              href={source.href}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-start justify-between gap-2 text-[11px] font-semibold text-white/52 transition hover:text-emerald-200/80"
            >
              <span className="min-w-0 truncate">{source.name}</span>
              <ExternalLink size={11} className="mt-0.5 shrink-0" />
            </a>
            <p className="mt-1 line-clamp-2 text-[9px] leading-4 text-white/27">
              {source.facts.join(' · ')}
            </p>
            <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[9px] text-white/22">
              <span>{sourceDomain(source.href)}</span>
              {source.licenseLabel && source.licenseUrl && (
                <a
                  href={source.licenseUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline decoration-white/10 underline-offset-2 hover:text-white/50"
                >
                  {source.licenseLabel}
                </a>
              )}
            </div>
          </div>
        ))}
      </div>
    </details>
  )
}

function collectRevealSources(reveals: BudgetReveal[], t: Translation): RevealSourceReference[] {
  const references = new Map<string, Omit<RevealSourceReference, 'facts'> & { facts: Set<string> }>()
  const add = (
    name: string | undefined,
    href: string | undefined,
    fact: string,
    licenseLabel?: string,
    licenseUrl?: string,
  ) => {
    if (!href) return
    const existing = references.get(href)
    if (existing) {
      existing.facts.add(fact)
      existing.licenseLabel ||= licenseLabel
      existing.licenseUrl ||= licenseUrl
      return
    }
    references.set(href, {
      name: name || sourceDomain(href),
      href,
      facts: new Set([fact]),
      licenseLabel,
      licenseUrl,
    })
  }

  reveals.forEach(reveal => {
    const descriptionSource = reveal.project?.descriptionAttribution
    add(
      descriptionSource?.sourceName,
      descriptionSource?.sourceUrl,
      t.budget.context,
      descriptionSource?.licenseLabel,
      descriptionSource?.licenseUrl,
    )
    add(
      reveal.actualCost?.sourceName,
      reveal.actualCost?.referenceUrl || reveal.actualCost?.sourceUrl,
      t.budget.session.finalCost,
    )
    reveal.costs?.forEach(cost => add(
      cost.sourceName,
      cost.referenceUrl || cost.sourceUrl,
      costKindLabel(cost.kind, t),
    ))
    reveal.metrics?.forEach(metric => add(
      metric.sourceName,
      metric.referenceUrl || metric.sourceUrl,
      humanize(metric.type),
    ))
    reveal.milestones?.forEach(milestone => add(
      milestone.sourceName,
      milestone.referenceUrl || milestone.sourceUrl,
      humanize(milestone.type),
    ))
  })

  return [...references.values()].map(reference => ({
    ...reference,
    facts: [...reference.facts],
  }))
}

function AnswerValue({
  label,
  value,
  muted = false,
  align = 'left',
}: {
  label: string
  value: string
  muted?: boolean
  align?: 'left' | 'right'
}) {
  return (
    <div className={`min-w-0 ${align === 'right' ? 'text-right' : ''}`}>
      <p className="text-[9px] font-semibold uppercase tracking-[.13em] text-white/35">{label}</p>
      <p className={`mt-1 break-words text-base font-black leading-tight tracking-[-.02em] sm:text-lg ${muted ? 'text-white/65' : 'text-emerald-200'}`}>
        {value}
      </p>
    </div>
  )
}

function ResultMetric({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="rounded-2xl border border-white/[.055] bg-black/20 px-4 py-2.5">
      <p className="text-[8px] font-semibold uppercase tracking-[.13em] text-white/35">{label}</p>
      <p className={`mt-1 text-lg font-black ${accent ? 'text-emerald-300' : 'text-white/85'}`}>{value}</p>
    </div>
  )
}

function choiceProjectName(
  answer: BudgetAnsweredQuestion,
  choice: BudgetAnsweredQuestion['guessedChoice'],
) {
  if (choice === 'PRIMARY') return answer.question.prompt.primaryProject?.name ?? 'Project A'
  if (choice === 'COMPARISON') return answer.question.prompt.comparisonProject?.name ?? 'Project B'
  return '—'
}

function revealResultMessage(
  answer: BudgetAnsweredQuestion,
  difference: number | null,
  relativeError: number | null,
  fullXpThresholdPercent: number,
  t: Translation,
) {
  if (answer.question.type === 'HIGHER_LOWER') {
    return answer.guessedChoice === answer.correctChoice
      ? t.budget.session.choiceCorrect
      : t.budget.session.choiceIncorrect
  }
  if (difference === 0) return t.budget.session.exactAnswer
  if (answer.rawXpPercent >= 100) {
    return replaceTokens(t.budget.session.fullXpHit, { threshold: String(fullXpThresholdPercent) })
  }

  const formattedDifference = formatAnswer(
    difference,
    answer.correctUnit,
    true,
    t.budget.session.answerUnitMonths,
  )
  const differenceText = relativeError == null
    ? formattedDifference
    : `${formattedDifference} (${formatNumber(relativeError, 1)}%)`
  const key = (answer.guessedValue ?? 0) < (answer.correctValue ?? 0)
    ? t.budget.session.guessLow
    : t.budget.session.guessHigh
  return replaceTokens(key, { difference: differenceText })
}

function revealQuestionLabel(answer: BudgetAnsweredQuestion, t: Translation) {
  switch (answer.question.type) {
    case 'ORIGINAL_BUDGET': return t.budget.session.originalBudget
    case 'BUDGET_OVERRUN': return t.budget.session.budgetOverrun
    case 'HOW_LATE': return t.budget.session.howLate
    case 'COST_PER_UNIT': return t.budget.session.costPerUnit
    case 'CURRENT_ESTIMATE': return t.budget.session.currentEstimate
    case 'SPENT_SO_FAR': return t.budget.session.spentSoFar
    case 'HOW_MUCH': return answer.question.prompt.project?.answerCostKind === 'CURRENT_ESTIMATE'
      ? t.budget.session.currentEstimate
      : t.budget.session.howMuch
    default: return t.budget.session.howMuch
  }
}

function formatUsdRange(low: number, high: number, year?: number) {
  const value = low === high
    ? formatUsd(low, true)
    : `${formatUsd(low, true)} – ${formatUsd(high, true)}`
  return year ? `${value} · ${year}` : value
}

function costKindLabel(kind: string, t: Translation) {
  if (kind === 'ORIGINAL_BUDGET') return t.budget.session.originalBudgetValue
  if (kind === 'FINAL_COST') return t.budget.session.finalCost
  return humanize(kind)
}

function amountContains(low: number, high: number, value: number) {
  return value >= Math.min(low, high) && value <= Math.max(low, high)
}

function sourceDomain(href: string) {
  try {
    return new URL(href).hostname.replace(/^www\./, '')
  } catch {
    return href
  }
}

function resultTone(fullXp: boolean, xpPercent: number): 'success' | 'warning' | 'danger' {
  if (fullXp) return 'success'
  if (xpPercent >= 40) return 'warning'
  return 'danger'
}

function toneClass(tone: 'success' | 'warning' | 'danger') {
  if (tone === 'success') return 'border-emerald-300/15 bg-[linear-gradient(135deg,rgba(16,56,47,.92),rgba(8,11,18,.98))]'
  if (tone === 'warning') return 'border-amber-300/15 bg-[linear-gradient(135deg,rgba(58,42,16,.88),rgba(8,10,17,.98))]'
  return 'border-rose-300/15 bg-[linear-gradient(135deg,rgba(55,25,36,.9),rgba(8,9,16,.98))]'
}

function iconToneClass(tone: 'success' | 'warning' | 'danger') {
  if (tone === 'success') return 'bg-emerald-300/15 text-emerald-300'
  if (tone === 'warning') return 'bg-amber-300/15 text-amber-300'
  return 'bg-rose-300/15 text-rose-300'
}

function replaceTokens(template: string, values: Record<string, string>) {
  return Object.entries(values).reduce(
    (result, [key, value]) => result.replace(`{${key}}`, value),
    template,
  )
}
