import { useState, type KeyboardEvent, type SyntheticEvent } from 'react'
import { Banknote, CalendarDays, Gauge, Globe2, Landmark, MapPin, Maximize2 } from 'lucide-react'

import type {
  BudgetAnswerChoice,
  BudgetAnsweredQuestion,
  BudgetChallengeQuestion,
  BudgetImage,
  BudgetPromptProject,
} from '@/api/budget'
import { HintImageModal } from '@/components/game/HintImageModal'
import { useTranslation } from '@/i18n'
import { formatUsd, humanize } from './budgetFormatters'
import { budgetImageSrcSet, budgetImageVariantUrl } from './budgetImageUrls'

interface BudgetProjectStageProps {
  question: BudgetChallengeQuestion
  answer?: BudgetAnsweredQuestion | null
  selectedChoice?: BudgetAnswerChoice | null
  onSelectChoice?: (choice: BudgetAnswerChoice) => void
  nameHeadingLevel?: 'h2' | 'h3'
}

export function BudgetProjectStage({
  question,
  answer,
  selectedChoice,
  onSelectChoice,
  nameHeadingLevel = 'h2',
}: BudgetProjectStageProps) {
  if (question.type === 'HIGHER_LOWER') {
    return (
      <div className="relative">
        <div className="grid gap-4 md:grid-cols-2 xl:gap-5">
          <ProjectCard
            project={question.prompt.primaryProject}
            image={question.primaryImage}
            badge="A"
            showCostKind
            compact
            interactive={Boolean(onSelectChoice)}
            selected={selectedChoice === 'PRIMARY'}
            resultState={choiceResultState(answer, 'PRIMARY')}
            revealCost={answer?.reveal.primary?.actualCost?.amountUsd}
            description={answer?.reveal.primary?.project?.description}
            onSelect={() => onSelectChoice?.('PRIMARY')}
          />
          <ProjectCard
            project={question.prompt.comparisonProject}
            image={question.comparisonImage}
            badge="B"
            showCostKind
            compact
            interactive={Boolean(onSelectChoice)}
            selected={selectedChoice === 'COMPARISON'}
            resultState={choiceResultState(answer, 'COMPARISON')}
            revealCost={answer?.reveal.comparison?.actualCost?.amountUsd}
            description={answer?.reveal.comparison?.project?.description}
            onSelect={() => onSelectChoice?.('COMPARISON')}
          />
        </div>
        <span className="pointer-events-none absolute left-1/2 top-1/2 z-20 hidden h-12 w-12 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-2xl border border-white/15 bg-[#090b12]/95 text-[11px] font-black tracking-[.16em] text-white/65 shadow-[0_16px_42px_rgba(0,0,0,.6)] backdrop-blur-xl md:flex">
          VS
        </span>
      </div>
    )
  }

  return (
    <ProjectCard
      project={question.prompt.project}
      image={question.primaryImage}
      metric={question.prompt.metric}
      showCostKind={question.type === 'HOW_MUCH' || question.type === 'COST_PER_UNIT'}
      description={answer?.reveal.project?.description}
      nameHeadingLevel={nameHeadingLevel}
    />
  )
}

interface ProjectCardProps {
  project?: BudgetPromptProject
  image: BudgetImage | null
  badge?: string
  metric?: BudgetChallengeQuestion['prompt']['metric']
  showCostKind?: boolean
  compact?: boolean
  interactive?: boolean
  selected?: boolean
  resultState?: 'correct' | 'incorrect' | 'muted'
  revealCost?: number
  description?: string
  nameHeadingLevel?: 'h2' | 'h3'
  onSelect?: () => void
}

function ProjectCard({
  project,
  image,
  badge,
  metric,
  showCostKind = false,
  compact = false,
  interactive = false,
  selected = false,
  resultState,
  revealCost,
  description,
  nameHeadingLevel = 'h2',
  onSelect,
}: ProjectCardProps) {
  const { t } = useTranslation()
  const NameHeading = nameHeadingLevel
  const [imageOpen, setImageOpen] = useState(false)
  const [failedImageUrl, setFailedImageUrl] = useState<string | null>(null)

  if (!project) return null

  const categoryKey = project.category.toLocaleLowerCase() as keyof typeof t.budget.categories
  const lifecycleKey = project.lifecycleStatus?.toLocaleLowerCase() as keyof typeof t.budget.lifecycle
  const costKindKey = project.answerCostKind?.toLocaleLowerCase() as keyof typeof t.budget.costKinds
  const costKind = project.answerCostKind
    ? t.budget.costKinds[costKindKey] ?? humanize(project.answerCostKind)
    : null
  const yearLabel = project.completionYear
    ? (project.projectYear != null || project.openingYear != null
      ? t.budget.completionYear
      : t.budget.projectYear).replace('{year}', String(project.completionYear))
    : project.openingYear
      ? t.budget.openingYear.replace('{year}', String(project.openingYear))
      : project.expectedCompletionYear
        ? t.budget.expectedCompletionYear.replace('{year}', String(project.expectedCompletionYear))
        : project.projectYear
          ? t.budget.projectYear.replace('{year}', String(project.projectYear))
          : null
  const imageAvailable = Boolean(image && image.url !== failedImageUrl)
  const retryImageOnce = (event: SyntheticEvent<HTMLImageElement>) => {
    const element = event.currentTarget
    if (element.dataset.retried === 'true') {
      setFailedImageUrl(image?.url ?? '')
      return
    }
    element.dataset.retried = 'true'
    element.removeAttribute('srcset')
    element.removeAttribute('sizes')
    const originalUrl = image?.url ?? element.src
    const separator = originalUrl.includes('?') ? '&' : '?'
    element.src = `${originalUrl}${separator}retry=${Date.now()}`
  }
  const selectOnKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (!interactive || !onSelect || (event.key !== 'Enter' && event.key !== ' ')) return
    event.preventDefault()
    onSelect()
  }
  const borderClass = resultState === 'correct'
    ? 'border-emerald-300/70 ring-2 ring-emerald-300/20'
    : resultState === 'incorrect'
      ? 'border-rose-300/55 ring-2 ring-rose-300/10'
      : selected
        ? 'border-emerald-300/70 ring-2 ring-emerald-300/20'
        : 'border-white/[.09]'

  return (
    <article
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-pressed={interactive ? selected : undefined}
      onClick={interactive ? onSelect : undefined}
      onKeyDown={interactive ? selectOnKeyDown : undefined}
      className={`group overflow-hidden rounded-[1.75rem] border bg-[#070810] shadow-[0_28px_90px_rgba(0,0,0,.42)] transition ${interactive ? 'cursor-pointer hover:-translate-y-0.5 hover:border-emerald-300/35' : ''} ${compact ? '' : 'lg:flex lg:flex-col'} ${borderClass} ${resultState === 'muted' ? 'opacity-75' : ''}`}
    >
      <div className={`relative ${compact ? '' : 'lg:min-h-[420px] lg:flex-1'}`}>
        <div className={`relative isolate overflow-hidden bg-[#070810] ${compact
          ? 'aspect-[4/3] xl:aspect-[16/10]'
          : 'aspect-[16/10] lg:absolute lg:inset-0 lg:aspect-auto'}`}>
          {imageAvailable ? (
            <>
              <img
                src={budgetImageVariantUrl(image!.url, 960)}
                srcSet={budgetImageSrcSet(image!.url)}
                sizes={compact
                  ? '(min-width: 768px) 50vw, 100vw'
                  : '(min-width: 1024px) 62vw, 100vw'}
                alt={image!.altText || project.name}
                loading="eager"
                decoding="async"
                fetchPriority="high"
                onError={retryImageOnce}
                className="absolute -inset-px block h-[calc(100%+2px)] w-[calc(100%+2px)] max-w-none transform-gpu object-cover transition-transform duration-500 [backface-visibility:hidden] will-change-transform group-hover:scale-[1.025]"
              />
              <button
                type="button"
                onClick={event => {
                  event.stopPropagation()
                  setImageOpen(true)
                }}
                aria-label={t.budget.openImage}
                className="absolute left-4 top-4 z-10 flex h-9 w-9 cursor-zoom-in items-center justify-center rounded-xl border border-white/15 bg-black/65 text-white/85 backdrop-blur-md transition hover:bg-black/75 lg:opacity-0 lg:group-hover:opacity-100 lg:focus-visible:opacity-100"
              >
                <Maximize2 size={16} />
              </button>
            </>
          ) : (
            <div className="absolute inset-0 flex items-center justify-center bg-[radial-gradient(circle_at_30%_25%,rgba(52,211,153,.14),transparent_36%),radial-gradient(circle_at_75%_70%,rgba(251,191,36,.11),transparent_40%),linear-gradient(145deg,#151824,#090a11)]">
              <Landmark size={compact ? 44 : 58} strokeWidth={1.2} className="text-white/15" />
            </div>
          )}
          <div className="pointer-events-none absolute -inset-px hidden bg-gradient-to-t from-[#070810] via-[#070810]/75 to-transparent lg:block" />
          {revealCost != null && (
            <div className="pointer-events-none absolute right-5 top-5 hidden rounded-2xl border border-emerald-200/20 bg-[#07130f]/90 px-3.5 py-2.5 text-right shadow-[0_12px_35px_rgba(0,0,0,.45)] backdrop-blur-xl lg:block">
              <p className="text-[8px] font-bold uppercase tracking-[.16em] text-emerald-100/55">{t.budget.actual}</p>
              <p className="mt-1 text-2xl font-black tracking-[-.04em] text-emerald-200">{formatUsd(revealCost, true)}</p>
            </div>
          )}
        </div>

        <div className={`${interactive ? 'pointer-events-auto' : 'lg:pointer-events-none'} relative flex flex-col px-4 pb-5 pt-4 sm:px-6 sm:pb-6 sm:pt-5 lg:absolute lg:inset-x-0 lg:bottom-0 lg:p-6`}>
          <div className="flex items-center gap-2">
            {badge && (
              <span className={`flex h-8 w-8 items-center justify-center rounded-lg text-xs font-black shadow-lg ${selected
                ? 'bg-emerald-300 text-[#06100d]'
                : 'bg-white text-black'}`}>
                {badge}
              </span>
            )}
            <span className="rounded-full border border-amber-200/15 bg-amber-300/10 px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.14em] text-amber-100/80 backdrop-blur-md">
              {t.budget.categories[categoryKey]}
            </span>
          </div>

          <NameHeading className={`mt-3 max-w-4xl font-black leading-[1.05] tracking-[-.04em] text-white ${
            compact ? 'text-xl sm:text-2xl' : 'text-2xl sm:text-4xl'
          }`}>
            {project.name}
          </NameHeading>

          {description && (
            <p className={`order-2 mt-4 max-w-4xl border-t border-white/[.08] pt-4 text-white/70 lg:order-none lg:mt-2 lg:border-t-0 lg:pt-0 lg:text-white/78 lg:[text-shadow:0_2px_12px_rgba(0,0,0,.95)] ${compact
              ? 'text-xs leading-relaxed lg:text-[11px] lg:leading-[1.45] xl:text-xs'
              : 'text-sm leading-relaxed lg:text-xs lg:leading-5 xl:text-sm xl:leading-6'}`}>
              {description}
            </p>
          )}

          <div className="order-1 mt-3 flex flex-wrap gap-2 lg:order-none">
            <ProjectFact icon={MapPin} value={project.countryName} />
            {project.regionName && project.regionName !== project.countryName && (
              <ProjectFact icon={Globe2} value={project.regionName} />
            )}
            {yearLabel && <ProjectFact icon={CalendarDays} value={yearLabel} />}
            {project.lifecycleStatus && project.lifecycleStatus !== 'UNKNOWN' && (
              <ProjectFact icon={CalendarDays} value={t.budget.lifecycle[lifecycleKey] ?? humanize(project.lifecycleStatus)} />
            )}
            {showCostKind && costKind && <ProjectFact icon={Banknote} value={`${t.budget.costBasis}: ${costKind}`} />}
            {metric && (
              <ProjectFact
                icon={Gauge}
                value={`${metric.value.toLocaleString('en-US')} ${humanize(metric.unit)}`}
              />
            )}
          </div>

          {revealCost != null && (
            <div className="order-1 mt-4 inline-flex items-baseline gap-2.5 self-start rounded-xl border border-emerald-200/15 bg-emerald-300/[.06] px-3.5 py-2.5 lg:hidden">
              <span className="text-[10px] font-bold uppercase tracking-[.12em] text-emerald-100/55">{t.budget.revealedCost}</span>
              <span className="text-lg font-black tracking-[-.03em] text-emerald-200">{formatUsd(revealCost, true)}</span>
            </div>
          )}
        </div>
      </div>

      {imageAvailable && <BudgetImageCredit image={image!} stopSelection={interactive} />}

      {imageAvailable && (
        <HintImageModal
          src={image!.url}
          isOpen={imageOpen}
          isPanorama={false}
          onClose={() => setImageOpen(false)}
        />
      )}
    </article>
  )
}

function choiceResultState(
  answer: BudgetAnsweredQuestion | null | undefined,
  choice: BudgetAnswerChoice,
): 'correct' | 'incorrect' | 'muted' | undefined {
  if (!answer) return undefined
  if (answer.correctChoice === choice) return 'correct'
  if (answer.guessedChoice === choice) return 'incorrect'
  return 'muted'
}

function ProjectFact({ icon: Icon, value }: { icon: typeof MapPin; value: string }) {
  return (
    <span className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-white/10 bg-black/45 px-2.5 py-1.5 text-[10px] font-semibold text-white/75 backdrop-blur-md sm:text-xs">
      <Icon size={12} className="shrink-0 text-amber-200/70" />
      <span className="min-w-0 break-words">{value}</span>
    </span>
  )
}

function BudgetImageCredit({ image, stopSelection = false }: { image: BudgetImage; stopSelection?: boolean }) {
  const { t } = useTranslation()
  const authorName = image.authorName?.trim()
  const attributionText = image.attributionText?.trim()
  const hasDistinctAttribution = attributionText
    && attributionText.toLocaleLowerCase() !== authorName?.toLocaleLowerCase()

  return (
    <div
      onClick={stopSelection ? event => event.stopPropagation() : undefined}
      className="relative z-10 -mt-px flex flex-wrap items-center gap-x-3 gap-y-1 bg-[#070810] px-4 py-2 text-[9px] text-white/40 sm:px-6 sm:text-[10px]"
    >
      {authorName && <span>{authorName}</span>}
      {hasDistinctAttribution && <span>{attributionText}</span>}
      {image.licenseLabel && (image.licenseUrl
        ? <a href={image.licenseUrl} target="_blank" rel="noopener noreferrer" className="underline decoration-white/15 underline-offset-2">{image.licenseLabel}</a>
        : <span>{image.licenseLabel}</span>)}
      {image.sourcePageUrl && (
        <a href={image.sourcePageUrl} target="_blank" rel="noopener noreferrer" className="underline decoration-white/15 underline-offset-2">
          {t.play.originalImage}
        </a>
      )}
    </div>
  )
}
