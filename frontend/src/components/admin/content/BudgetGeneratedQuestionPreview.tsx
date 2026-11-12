import { useState } from 'react'
import { CircleDot, ExternalLink, Image as ImageIcon, Lightbulb } from 'lucide-react'
import type {
  BudgetGeneratedChallengeQuestion,
  BudgetGeneratedQuestion,
} from '@/api/budgetContentAdmin'
import type {
  BudgetHintPayload,
  BudgetPromptProject,
  BudgetReveal,
} from '@/api/budget'
import { HintImageModal } from '@/components/game/HintImageModal'
import { cn } from '@/utils/cn'

type GeneratedQuestion = BudgetGeneratedQuestion | BudgetGeneratedChallengeQuestion

interface BudgetGeneratedQuestionPreviewProps {
  question: GeneratedQuestion
  compact?: boolean
  position?: number
}

const usd = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 2,
})
const number = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 })

export function BudgetGeneratedQuestionPreview({
  question,
  compact = false,
  position,
}: BudgetGeneratedQuestionPreviewProps) {
  const [previewImage, setPreviewImage] = useState<string | null>(null)
  const variantId = questionVariantId(question)
  const hints = question.reveal.hints || []
  const primaryImage = adminVariantImageUrl(variantId, 'primary')
  const comparisonImage = question.comparisonProjectName
    ? adminVariantImageUrl(variantId, 'comparison')
    : null

  return (
    <article
      className={cn(
        'overflow-hidden rounded-2xl border border-white/[.08] bg-white/[.018] [content-visibility:auto]',
        compact ? '[contain-intrinsic-size:280px]' : '[contain-intrinsic-size:320px]',
      )}
    >
      <div className={compact ? 'p-3' : 'p-4'}>
        <div className="mb-3 flex flex-wrap items-center gap-2">
          {position != null && (
            <span className="flex h-6 min-w-6 items-center justify-center rounded-md bg-white/[.08] px-1.5 text-[10px] font-semibold text-white/55">
              {position}
            </span>
          )}
          <span className="rounded-md bg-sky-400/[.09] px-2 py-1 text-[9px] font-semibold uppercase tracking-[.12em] text-sky-200/75">
            {humanize(question.questionType)}
          </span>
          <span className="text-[9px] font-semibold uppercase tracking-[.12em] text-white/30">
            {question.difficulty}
          </span>
          <span className="text-white/15">·</span>
          <span className="text-[10px] text-white/25">Variant #{variantId}</span>
          <span
            className={cn(
              'ml-auto inline-flex items-center gap-1 rounded-md px-2 py-1 text-[9px] font-semibold uppercase tracking-[.08em]',
              question.active
                ? 'bg-emerald-400/[.09] text-emerald-300/75'
                : 'bg-amber-400/[.09] text-amber-300/75',
            )}
          >
            <CircleDot size={9} /> {question.active ? 'Active' : 'Retired'}
          </span>
        </div>

        <div className="grid gap-3 sm:grid-cols-[auto_minmax(0,1fr)]">
          <QuestionImages
            compact={compact}
            primary={{ url: primaryImage, name: question.projectName }}
            comparison={comparisonImage && question.comparisonProjectName
              ? { url: comparisonImage, name: question.comparisonProjectName }
              : null}
            onPreview={setPreviewImage}
          />
          <div className="min-w-0">
            <h3 className={cn(
              'font-semibold leading-snug text-white/90',
              compact ? 'text-sm' : 'text-base',
            )}>
              {questionTitle(question)}
            </h3>
            <ProjectSummary question={question} />
          </div>
        </div>

        <div className={cn('mt-3 grid gap-2', hints.length > 0 && 'lg:grid-cols-[minmax(190px,.7fr)_minmax(0,1.3fr)]')}>
          <div className="rounded-xl border border-emerald-300/[.12] bg-emerald-300/[.045] px-3 py-2.5">
            <p className="text-[9px] font-semibold uppercase tracking-[.12em] text-emerald-300/55">
              Correct answer
            </p>
            <p className="mt-1 text-sm font-semibold text-emerald-100/90">{formatAnswer(question)}</p>
          </div>
          {hints.length > 0 && <HintList hints={hints} />}
        </div>
      </div>

      <details className="group border-t border-white/[.06]">
        <summary className="flex cursor-pointer list-none items-center justify-between px-4 py-3 text-xs font-medium text-white/40 transition-colors hover:bg-white/[.02] hover:text-white/70">
          <span>Evidence, facts and sources</span>
          <span className="text-[10px] text-white/20 group-open:hidden">Open</span>
          <span className="hidden text-[10px] text-white/20 group-open:inline">Close</span>
        </summary>
        <div className="border-t border-white/[.06] p-4">
          <RevealDetails reveal={question.reveal} />
        </div>
      </details>

      {previewImage && (
        <HintImageModal
          src={previewImage}
          isOpen
          isPanorama={false}
          onClose={() => setPreviewImage(null)}
        />
      )}
    </article>
  )
}

function HintList({ hints }: { hints: BudgetHintPayload[] }) {
  return (
    <section className="rounded-xl border border-violet-300/[.12] bg-violet-300/[.035] p-2.5">
      <div className="mb-2 flex items-center justify-between gap-2 px-0.5">
        <span className="inline-flex items-center gap-1.5 text-[9px] font-semibold uppercase tracking-[.12em] text-violet-200/65">
          <Lightbulb size={11} /> Player hints
        </span>
        <span className="text-[10px] text-white/25">{hints.length} available</span>
      </div>
      <div className="grid gap-1.5 sm:grid-cols-2">
        {hints.map((hint, index) => (
          <div key={`${hint.type}:${index}`} className="rounded-lg border border-white/[.06] bg-black/10 px-2.5 py-2">
            <div className="flex items-center justify-between gap-2">
              <span className="text-[9px] font-semibold uppercase tracking-[.1em] text-white/30">
                Hint {index + 1}
              </span>
              <span className="text-[9px] text-violet-200/55">{hint.multiplierPercent}% XP</span>
            </div>
            <p className="mt-1 text-[11px] leading-4 text-white/60">{formatHint(hint)}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

function formatHint(hint: BudgetHintPayload) {
  if (hint.type === 'COMPARABLE_PROJECT') {
    const comparison = formatValue(hint.value, hint.unit)
    const metric = hint.metricType && hint.metricValue != null && hint.metricUnit
      ? ` · ${humanize(hint.metricType)} ${formatMetric(hint.metricValue, hint.metricUnit)}`
      : ''
    return `${hint.projectName}: ${comparison}${metric}`
  }
  return `Answer range: ${formatValue(hint.minimum, hint.unit)}–${formatValue(hint.maximum, hint.unit)}`
}

function QuestionImages({
  compact,
  primary,
  comparison,
  onPreview,
}: {
  compact: boolean
  primary: { url: string; name: string }
  comparison: { url: string; name: string } | null
  onPreview: (url: string) => void
}) {
  return (
    <div className={cn('grid shrink-0 gap-1', comparison ? 'grid-cols-2' : 'grid-cols-1')}>
      <QuestionImage {...primary} compact={compact} onPreview={onPreview} />
      {comparison && <QuestionImage {...comparison} compact={compact} onPreview={onPreview} />}
    </div>
  )
}

function QuestionImage({
  url,
  name,
  compact,
  onPreview,
}: {
  url: string
  name: string
  compact: boolean
  onPreview: (url: string) => void
}) {
  const [failed, setFailed] = useState(false)
  const size = compact ? 'h-16 w-20' : 'h-20 w-24 sm:h-24 sm:w-32'

  if (failed) {
    return (
      <div className={cn('flex items-center justify-center rounded-xl border border-white/[.06] bg-black/20 text-white/15', size)}>
        <ImageIcon size={18} />
      </div>
    )
  }

  return (
    <button
      type="button"
      aria-label={`Open ${name} image`}
      title={`Open ${name} image`}
      onClick={() => onPreview(url)}
      className={cn('group/image relative isolate cursor-zoom-in overflow-hidden rounded-xl border border-white/[.08] bg-black/25', size)}
    >
      <img
        src={`${url}?thumbnail=true`}
        alt={name}
        loading="lazy"
        decoding="async"
        onError={() => setFailed(true)}
        className="absolute -inset-px block h-[calc(100%+2px)] w-[calc(100%+2px)] max-w-none transform-gpu object-cover transition-transform duration-300 [backface-visibility:hidden] will-change-transform group-hover/image:scale-105"
      />
    </button>
  )
}

function ProjectSummary({ question }: { question: GeneratedQuestion }) {
  const prompt = question.prompt
  const projects = prompt.project
    ? [prompt.project]
    : [prompt.primaryProject, prompt.comparisonProject].filter(Boolean) as BudgetPromptProject[]

  return (
    <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-white/35">
      {projects.map((project) => (
        <span key={`${project.name}:${project.countryCode}`}>
          {project.countryName}
          {project.completionYear ? ` · ${project.completionYear}` : ''}
          {` · ${humanize(project.category)}`}
        </span>
      ))}
      {prompt.metric && <span>{formatMetric(prompt.metric.value, prompt.metric.unit)}</span>}
    </div>
  )
}

function RevealDetails({ reveal }: { reveal: BudgetReveal }) {
  if (reveal.primary && reveal.comparison) {
    return (
      <div className="grid gap-3 lg:grid-cols-2">
        <ProjectReveal title="Primary project" reveal={reveal.primary} />
        <ProjectReveal title="Comparison project" reveal={reveal.comparison} />
      </div>
    )
  }

  return <ProjectReveal reveal={reveal} />
}

function ProjectReveal({ title, reveal }: { title?: string; reveal: BudgetReveal }) {
  return (
    <div className="min-w-0 space-y-3">
      {title && <p className="text-[9px] font-semibold uppercase tracking-[.12em] text-white/30">{title}</p>}
      {reveal.project?.description && (
        <div>
          <p className="text-xs leading-5 text-white/50">{reveal.project.description}</p>
          {reveal.project.descriptionAttribution?.sourceUrl && (
            <a
              href={reveal.project.descriptionAttribution.sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-1.5 inline-flex items-center gap-1 text-[10px] text-emerald-300/55 hover:text-emerald-200"
            >
              {reveal.project.descriptionAttribution.sourceName || 'Description source'}
              {reveal.project.descriptionAttribution.licenseLabel
                ? ` · ${reveal.project.descriptionAttribution.licenseLabel}`
                : ''}
              <ExternalLink size={10} />
            </a>
          )}
        </div>
      )}
      <div className="grid gap-2 sm:grid-cols-2">
        {reveal.actualCost && <RevealValue label="Actual cost" value={usd.format(reveal.actualCost.amountUsd)} />}
        {reveal.budgetOverrunPercent != null && (
          <RevealValue label="Budget overrun" value={`${number.format(reveal.budgetOverrunPercent)}%`} />
        )}
        {reveal.delayMonths != null && (
          <RevealValue label="Delay" value={`${number.format(reveal.delayMonths)} months`} />
        )}
      </div>

      {reveal.costs && reveal.costs.length > 0 && (
        <FactList title="Costs">
          {reveal.costs.map((cost, index) => (
            <FactRow
              key={`${cost.kind}:${cost.sourceUrl}:${index}`}
              label={`${humanize(cost.kind)}${cost.year ? ` · ${cost.year}` : ''}`}
              value={formatUsdRange(cost.amountUsdLow, cost.amountUsdHigh)}
              sourceName={cost.sourceName}
              sourceUrl={cost.referenceUrl || cost.sourceUrl}
            />
          ))}
        </FactList>
      )}

      {reveal.metrics && reveal.metrics.length > 0 && (
        <FactList title="Project metrics">
          {reveal.metrics.map((metric, index) => (
            <FactRow
              key={`${metric.type}:${metric.sourceUrl}:${index}`}
              label={humanize(metric.type)}
              value={formatMetric(metric.value, metric.unit)}
              sourceName={metric.sourceName}
              sourceUrl={metric.referenceUrl || metric.sourceUrl}
            />
          ))}
        </FactList>
      )}

      {reveal.milestones && reveal.milestones.length > 0 && (
        <FactList title="Schedule">
          {reveal.milestones.map((milestone, index) => (
            <FactRow
              key={`${milestone.type}:${milestone.sourceUrl}:${index}`}
              label={humanize(milestone.type)}
              value={milestone.date || String(milestone.year || '—')}
              sourceName={milestone.sourceName}
              sourceUrl={milestone.referenceUrl || milestone.sourceUrl}
            />
          ))}
        </FactList>
      )}
    </div>
  )
}

function FactList({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <p className="mb-1.5 text-[9px] font-semibold uppercase tracking-[.12em] text-white/25">{title}</p>
      <div className="divide-y divide-white/[.05] rounded-lg border border-white/[.06]">{children}</div>
    </section>
  )
}

function FactRow({
  label,
  value,
  sourceName,
  sourceUrl,
}: {
  label: string
  value: string
  sourceName: string
  sourceUrl: string
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 px-2.5 py-2 text-[11px]">
      <span className="text-white/40">{label}</span>
      <div className="flex items-center gap-2">
        <span className="font-medium text-white/70">{value}</span>
        <a
          href={sourceUrl}
          target="_blank"
          rel="noreferrer"
          title={sourceName}
          aria-label={`Open source: ${sourceName}`}
          className="text-emerald-300/55 hover:text-emerald-200"
        >
          <ExternalLink size={11} />
        </a>
      </div>
    </div>
  )
}

function RevealValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-white/[.06] bg-white/[.025] p-2.5">
      <p className="text-[9px] uppercase tracking-[.1em] text-white/25">{label}</p>
      <p className="mt-1 text-sm font-semibold text-white/75">{value}</p>
    </div>
  )
}

function questionTitle(question: GeneratedQuestion) {
  const name = question.projectName
  return {
    HOW_MUCH: `How much did ${name} cost?`,
    ORIGINAL_BUDGET: `What was the original budget for ${name}?`,
    BUDGET_OVERRUN: `How far did ${name} exceed its original budget?`,
    HOW_LATE: `How late was ${name}?`,
    HIGHER_LOWER: `Which cost more: ${name} or ${question.comparisonProjectName || 'the comparison project'}?`,
    COST_PER_UNIT: `What was the normalized cost of ${name}?`,
    CURRENT_ESTIMATE: `What is the current cost estimate for ${name}?`,
    SPENT_SO_FAR: `How much has been spent on ${name} so far?`,
  }[question.questionType]
}

function formatAnswer(question: GeneratedQuestion) {
  if (question.answerChoice) {
    return question.answerChoice === 'PRIMARY'
      ? question.projectName
      : question.comparisonProjectName || question.answerChoice
  }
  if (question.answerValue == null) return '—'
  return formatValue(question.answerValue, question.answerUnit || '')
}

function formatValue(value: number, unit: string) {
  if (unit === 'USD') return usd.format(value)
  if (unit === 'PERCENT') return `${number.format(value)}%`
  if (unit === 'MONTHS') return `${number.format(value)} months`
  if (unit.startsWith('USD_PER_')) return `${usd.format(value)} / ${unitLabel(unit.slice(8))}`
  return `${number.format(value)} ${unitLabel(unit)}`.trim()
}

function formatUsdRange(low: number, high: number) {
  return low === high ? usd.format(low) : `${usd.format(low)}–${usd.format(high)}`
}

function formatMetric(value: number, unit: string) {
  return `${number.format(value)} ${unitLabel(unit)}`.trim()
}

function unitLabel(unit: string) {
  return {
    M: 'm',
    KM: 'km',
    M2: 'm²',
    M3: 'm³',
    MW: 'MW',
    KG: 'kg',
    SEAT: 'seat',
    SQUARE_METER: 'm²',
    METER: 'm',
    ANNUAL_PASSENGER: 'annual passenger',
    MILLION_M3: 'million m³',
    PASSENGERS_PER_YEAR: 'passengers/year',
    TONNES_PER_YEAR: 'tonnes/year',
    TEU_PER_YEAR: 'TEU/year',
  }[unit] || unit.toLowerCase().replaceAll('_', ' ')
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ')
}

function adminVariantImageUrl(id: number, role: 'primary' | 'comparison') {
  return `/api/assets/budget/admin/variants/${id}/${role}`
}

function questionVariantId(question: GeneratedQuestion) {
  return 'questionId' in question ? question.questionId : question.id
}
