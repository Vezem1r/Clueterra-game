import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, ArrowUpRight, Banknote, CalendarDays, ExternalLink, MapPin } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'

import { projectPagesApi } from '@/api/projectPages'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { Header } from '@/components/layout/Header'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { useProjectSsrData } from '@/features/projects/projectSsrData'
import { getProjectDocumentMeta } from '@/features/projects/projectSeo'
import { BudgetGameInvitation } from '@/features/projects/BudgetGameInvitation'
import { budgetImageSrcSet, budgetImageVariantUrl } from '@/pages/budget/budgetImageUrls'
import type {
  ProjectCard,
  ProjectCost,
  ProjectDetail,
  ProjectDetailPageData,
  ProjectMetric,
  ProjectMilestone,
} from '@/features/projects/projectTypes'
import { useSeoMeta } from '@/hooks/useSeoMeta'

export function ProjectDetailPage({ preview = false }: { preview?: boolean }) {
  const location = useLocation()
  const { slug, wikidataId } = useParams()
  const identifier = preview ? wikidataId : slug
  const ssrData = useProjectSsrData()
  const initialDetail = ssrData?.kind === 'detail' && ssrData.path === location.pathname
    ? ssrData.detail
    : undefined
  const terminalSsrState = ssrData?.path === location.pathname
    && (ssrData.kind === 'not-found' || ssrData.kind === 'error')

  const query = useQuery({
    queryKey: preview ? ['project-preview', identifier] : ['project-page', identifier],
    queryFn: () => preview
      ? projectPagesApi.getPreview(identifier ?? '')
      : projectPagesApi.getProject(identifier ?? ''),
    initialData: initialDetail,
    enabled: Boolean(identifier) && !terminalSsrState,
    staleTime: preview ? 0 : 1000 * 60 * 5,
  })

  if (query.data) {
    return <ProjectDetailContent data={query.data} preview={preview} />
  }

  return (
    <ProjectPageState
      loading={query.isPending && !terminalSsrState}
      message={ssrData?.kind === 'error' ? 'The project service is temporarily unavailable.' : undefined}
    />
  )
}

function ProjectDetailContent({ data, preview }: { data: ProjectDetailPageData; preview: boolean }) {
  const project = data.project
  const costs = project.costs ?? []
  const metrics = project.metrics ?? []
  const timeline = project.timeline ?? []
  const documentMeta = useMemo(
    () => getProjectDocumentMeta({
      kind: 'detail',
      path: preview ? `/projects/preview/${project.wikidataId}` : `/projects/${project.slug}`,
      detail: data,
      preview,
    }),
    [data, preview, project.slug, project.wikidataId],
  )

  useSeoMeta({
    title: documentMeta.title,
    description: documentMeta.description,
    canonicalPath: documentMeta.canonicalPath,
    keywords: documentMeta.keywords,
    robots: documentMeta.robots,
    structuredData: documentMeta.structuredData || undefined,
    image: documentMeta.image || undefined,
    imageAlt: documentMeta.imageAlt || undefined,
    ogType: documentMeta.type,
  })

  return (
    <div className="min-h-[100dvh] overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="hero" />
      {preview && (
        <aside className="relative z-50 border-b border-amber-200/20 bg-amber-200/[.1] px-4 py-2 text-center text-xs font-semibold text-amber-100" role="status">
          Private draft preview · blocked from search indexing
        </aside>
      )}
      <Header />

      <main className="relative z-10 px-4 pb-24 pt-7 sm:px-6 md:pb-28 lg:px-10">
        <div className="mx-auto max-w-6xl">
          <nav className="flex min-w-0 items-center gap-2 overflow-hidden text-xs text-white/48" aria-label="Breadcrumb">
            <Link to="/" className="shrink-0 transition-colors hover:text-white/80">Clueterra</Link>
            <span aria-hidden="true">/</span>
            <Link to="/projects" className="shrink-0 transition-colors hover:text-white/80">Projects</Link>
            <span aria-hidden="true">/</span>
            <span className="truncate text-white/68" aria-current="page">{project.name}</span>
          </nav>

          <article className="mt-5">
            <header className="grid overflow-hidden rounded-[2.5rem] border border-white/[.09] bg-[linear-gradient(150deg,rgba(18,22,36,.95),rgba(7,9,16,.98))] shadow-[0_40px_120px_rgba(0,0,0,.38)] lg:grid-cols-[1.04fr_.96fr]">
              <div className="flex flex-col justify-center px-6 py-9 sm:px-10 sm:py-12 lg:px-12 lg:py-14">
                <p className="text-[11px] font-semibold uppercase tracking-[.2em] text-amber-200/75">
                  {project.categoryHubPath ? (
                    <Link to={project.categoryHubPath} className="underline-offset-4 transition-colors hover:text-amber-100 hover:underline">
                      {project.category}
                    </Link>
                  ) : (
                    project.category
                  )}
                  {' · '}
                  {project.countryHubPath ? (
                    <Link to={project.countryHubPath} className="underline-offset-4 transition-colors hover:text-amber-100 hover:underline">
                      {project.country}
                    </Link>
                  ) : (
                    project.country
                  )}
                </p>
                <h1 className="mt-4 text-4xl font-bold tracking-[-.04em] text-white sm:text-5xl lg:text-[3.4rem] lg:leading-[1.04]">
                  {project.costQuestion}
                </h1>
                {project.intro && <p className="mt-5 text-base leading-8 text-white/68">{project.intro}</p>}

                <div className="mt-7 rounded-[1.75rem] border border-amber-200/15 bg-amber-200/[.055] p-5 sm:p-6">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-[11px] font-semibold uppercase tracking-[.2em] text-amber-200/70">{project.costType}</p>
                    {project.status && (
                      <span className="rounded-full border border-sky-200/20 bg-sky-300/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-[.12em] text-sky-100/80">
                        {project.status}
                      </span>
                    )}
                  </div>
                  <p className="mt-2 text-3xl font-bold tracking-[-.03em] text-amber-100 sm:text-4xl">{project.answer}</p>
                  {project.usdEquivalent && <p className="mt-2 text-sm text-white/58">{project.usdEquivalent}</p>}
                  {project.costScope && <p className="mt-2 text-sm font-medium text-amber-100/80">Covers: {project.costScope}</p>}
                  {project.costYear && <p className="mt-3 flex items-center gap-1.5 text-xs text-white/52"><CalendarDays size={13} /> Cost basis: {project.costYear}</p>}
                  <SourceLink name={project.sourceName} url={project.sourceUrl} />
                </div>
                <BudgetGameInvitation />
              </div>

              <figure className="relative min-h-80 overflow-hidden border-t border-white/[.08] bg-white/[.03] lg:min-h-full lg:border-l lg:border-t-0">
                <img src={budgetImageVariantUrl(project.image.url, 960)} srcSet={budgetImageSrcSet(project.image.url, 960)} sizes="(min-width: 1200px) 550px, (min-width: 1024px) 46vw, 100vw" alt={project.image.alt} width={1280} height={800} fetchPriority="high" className="absolute inset-0 h-full w-full object-cover" />
                <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/90 via-black/45 to-transparent px-5 pb-5 pt-16 text-xs text-white/68">
                  {project.image.author && <span>{project.image.author}</span>}
                  {project.image.author && project.image.license && <span aria-hidden="true"> · </span>}
                  {project.image.license && project.image.licenseUrl
                    ? <a href={project.image.licenseUrl} target="_blank" rel="license noopener noreferrer" className="hover:text-white">{project.image.license}</a>
                    : <span>{project.image.license}</span>}
                </div>
              </figure>
            </header>

            <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_21rem]">
              <div className="space-y-4">
                {project.answerDetail && <CopySection eyebrow="The short answer" heading={project.answer} body={project.answerDetail} />}
                <CostBreakdown costs={costs} />
                {project.scopeText && <CopySection eyebrow="What the number includes" heading="Cost scope" body={project.scopeText} />}
                {metrics.length > 0 && <ProjectMetrics metrics={metrics} />}
                {timeline.length > 0 && <ProjectTimeline milestones={timeline} />}
                {project.contextText && <CopySection eyebrow="Project context" heading={`About ${project.name}`} body={project.contextText} />}
                {project.methodologyText && <CopySection eyebrow="Reading the data" heading="How to interpret the figure" body={project.methodologyText} />}
              </div>

              <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start" aria-label="Project facts and sources">
                {project.facts.length > 0 && (
                  <section className="rounded-[1.75rem] border border-white/[.08] bg-white/[.03] p-5 sm:p-6">
                    <h2 className="text-lg font-semibold text-white">Project facts</h2>
                    <dl className="mt-4 divide-y divide-white/[.06]">
                      {project.facts.map((fact) => (
                        <div key={`${fact.label}-${fact.value}`} className="grid gap-1.5 py-3 first:pt-0 last:pb-0">
                          <dt className="text-xs font-medium text-white/48">{fact.label}</dt>
                          <dd className="text-sm font-medium leading-6 text-white/82">
                            <a href={fact.sourceUrl} target="_blank" rel="noopener noreferrer" title={`Source: ${fact.sourceName}`} className="underline decoration-white/15 underline-offset-4 transition-colors hover:text-white">
                              {fact.value}
                            </a>
                          </dd>
                        </div>
                      ))}
                    </dl>
                  </section>
                )}

                <SourcesAndCredits project={project} />
              </aside>
            </div>
          </article>

          <section className="mt-6 flex flex-col gap-5 rounded-[2rem] border border-amber-200/10 bg-amber-200/[.035] p-6 sm:flex-row sm:items-center sm:justify-between sm:p-8">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[.2em] text-amber-200/60">Now make the guess</p>
              <h2 className="mt-2 text-2xl font-semibold tracking-tight text-white">Could you estimate it without seeing the answer?</h2>
              <p className="mt-2 text-sm text-white/60">BudgetGuessr turns real project costs into a daily estimation challenge.</p>
            </div>
            <Link to="/budget" className="inline-flex shrink-0 items-center justify-center gap-2 rounded-2xl bg-amber-200 px-5 py-3 text-sm font-semibold text-[#151006] transition-colors hover:bg-amber-100">
              <Banknote size={17} /> Play BudgetGuessr
            </Link>
          </section>

          {data.relatedProjects.length > 0 && (
            <RelatedProjects projects={data.relatedProjects} rankingPath={project.categoryHubPath} />
          )}
        </div>
      </main>

      <HomeLegalStrip guideMode="budget" />
    </div>
  )
}

function CopySection({ eyebrow, heading, body }: { eyebrow: string; heading: string; body: string }) {
  return (
    <section className="border-b border-white/[.08] px-1 py-6 sm:px-2 sm:py-8">
      <p className="text-[10px] font-semibold uppercase tracking-[.2em] text-emerald-300/72">{eyebrow}</p>
      <h2 className="mt-3 text-2xl font-semibold tracking-tight text-white">{heading}</h2>
      <p className="mt-4 text-sm leading-7 text-white/68 sm:text-base sm:leading-8">{body}</p>
    </section>
  )
}

function CostBreakdown({ costs }: { costs: ProjectCost[] }) {
  if (costs.length === 0) return null

  if (costs.length === 1) {
    const cost = costs[0]
    return (
      <section className="rounded-[1.55rem] border border-amber-200/10 bg-amber-200/[.035] p-5 sm:p-6" aria-labelledby="project-cost-basis-heading">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <p className="text-[10px] font-semibold uppercase tracking-[.2em] text-amber-200/72">Cost evidence</p>
            <h2 id="project-cost-basis-heading" className="mt-2 text-xl font-semibold tracking-tight text-white">Basis of the published figure</h2>
          </div>
          <div className="flex shrink-0 flex-wrap items-center gap-2 sm:max-w-48 sm:justify-end">
            <span className="rounded-full border border-white/[.09] bg-black/15 px-3 py-1.5 text-xs font-medium text-white/65">{cost.label}</span>
            {cost.scope && <span className="rounded-full border border-white/[.09] bg-black/15 px-3 py-1.5 text-xs text-white/58">{cost.scope}</span>}
            {cost.year && <span className="rounded-full border border-white/[.09] bg-black/15 px-3 py-1.5 text-xs text-white/58">{cost.year}</span>}
          </div>
        </div>
        <SourceLink name={cost.sourceName} url={cost.sourceUrl} />
      </section>
    )
  }

  const orderedCosts = [...costs].sort((left, right) => Number(right.featured) - Number(left.featured))
  const featureFirstCard = orderedCosts.length % 2 === 1

  return (
    <section className="rounded-[1.9rem] border border-white/[.08] bg-[linear-gradient(145deg,rgba(251,191,36,.055),rgba(9,12,20,.82)_42%)] p-6 shadow-[0_20px_60px_rgba(0,0,0,.16)] sm:p-8" aria-labelledby="project-costs-heading">
      <p className="text-[10px] font-semibold uppercase tracking-[.2em] text-amber-200/60">Cost record</p>
      <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <h2 id="project-costs-heading" className="text-2xl font-semibold tracking-tight text-white">How the cost changed</h2>
        <p className="max-w-sm text-xs leading-5 text-white/52">Budgets, contracts and final costs may cover different stages. Each figure keeps its own year and source.</p>
      </div>

      <div className="mt-6 grid gap-3 sm:grid-cols-2">
        {orderedCosts.map((cost, index) => (
          <article
            key={`${cost.label}-${cost.scope ?? 'whole'}-${cost.amount}-${cost.year ?? 'undated'}`}
            className={`rounded-[1.4rem] border p-5 ${featureFirstCard && index === 0 ? 'sm:col-span-2' : ''} ${cost.featured ? 'border-amber-200/20 bg-amber-200/[.06]' : 'border-white/[.07] bg-black/15'}`}
          >
            <div className="flex items-start justify-between gap-3">
              <p className={`text-[10px] font-semibold uppercase tracking-[.16em] ${cost.featured ? 'text-amber-200/72' : 'text-white/52'}`}>{cost.label}</p>
              {cost.year && <span className="shrink-0 rounded-full border border-white/[.08] px-2 py-1 text-[10px] text-white/55">{cost.year}</span>}
            </div>
            {cost.scope && <p className="mt-1 text-xs font-medium text-white/62">{cost.scope}</p>}
            <p className="mt-3 text-2xl font-bold tracking-[-.025em] text-white">{cost.amount}</p>
            {cost.usdEquivalent && <p className="mt-1 text-xs text-white/55">{cost.usdEquivalent}</p>}
            <SourceLink name={cost.sourceName} url={cost.sourceUrl} />
          </article>
        ))}
      </div>
    </section>
  )
}

function ProjectMetrics({ metrics }: { metrics: ProjectMetric[] }) {
  const singleMetric = metrics.length === 1
  const gridClass = metrics.length === 2
    ? 'sm:grid-cols-2'
    : 'sm:grid-cols-2 xl:grid-cols-3'

  return (
    <section className="rounded-[1.9rem] border border-white/[.08] bg-[linear-gradient(180deg,rgba(255,255,255,.035),rgba(9,12,20,.74))] p-6 shadow-[0_20px_60px_rgba(0,0,0,.16)] sm:p-8" aria-labelledby="project-scale-heading">
      <p className="text-[10px] font-semibold uppercase tracking-[.2em] text-violet-300/72">Project metrics</p>
      <h2 id="project-scale-heading" className="mt-3 text-2xl font-semibold tracking-tight text-white">Key dimensions</h2>
      <dl className={`mt-6 grid gap-3 ${singleMetric ? '' : gridClass}`}>
        {metrics.map((metric) => (
          <div key={`${metric.label}-${metric.value}`} className={`rounded-[1.35rem] border border-white/[.07] bg-black/15 p-5 ${singleMetric ? 'sm:flex sm:items-center sm:justify-between sm:gap-8' : ''}`}>
            <dt className="text-[10px] font-semibold uppercase tracking-[.16em] text-white/52">
              <span className="block">{metric.label}</span>
              <SourceLink name={metric.sourceName} url={metric.sourceUrl} />
            </dt>
            <dd className={`mt-2 font-semibold tracking-tight text-white/88 ${singleMetric ? 'text-2xl sm:mt-0 sm:text-right sm:text-3xl' : 'text-xl'}`}>{metric.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function ProjectTimeline({ milestones }: { milestones: ProjectMilestone[] }) {
  if (milestones.length === 1) {
    const milestone = milestones[0]
    return (
      <section className="rounded-[1.55rem] border border-sky-200/10 bg-sky-200/[.025] p-5 sm:p-6" aria-labelledby="project-timeline-heading">
        <p className="text-[10px] font-semibold uppercase tracking-[.2em] text-sky-300/72">Project record</p>
        <div className="mt-3 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex min-w-0 items-start gap-3">
            <span className="mt-2 h-3 w-3 shrink-0 rounded-full border-2 border-sky-200/65 bg-[#0b0e17] shadow-[0_0_0_4px_rgba(125,211,252,.06)]" aria-hidden="true" />
            <div>
              <h2 id="project-timeline-heading" className="text-lg font-semibold text-white/88">{milestone.label}</h2>
              <SourceLink name={milestone.sourceName} url={milestone.sourceUrl} />
            </div>
          </div>
          <p className="text-2xl font-semibold tracking-tight text-white sm:text-right">{milestone.value}</p>
        </div>
      </section>
    )
  }

  return (
    <section className="rounded-[1.9rem] border border-white/[.08] bg-[linear-gradient(180deg,rgba(255,255,255,.035),rgba(9,12,20,.74))] p-6 shadow-[0_20px_60px_rgba(0,0,0,.16)] sm:p-8" aria-labelledby="project-timeline-heading">
      <p className="text-[10px] font-semibold uppercase tracking-[.2em] text-sky-300/72">Project record</p>
      <h2 id="project-timeline-heading" className="mt-3 text-2xl font-semibold tracking-tight text-white">Timeline</h2>
      <ol className="mt-6 space-y-0">
        {milestones.map((milestone, index) => (
          <li key={`${milestone.label}-${milestone.value}`} className="relative grid grid-cols-[1rem_minmax(0,1fr)] gap-4 pb-6 last:pb-0">
            {index < milestones.length - 1 && <span className="absolute bottom-0 left-[.4375rem] top-4 w-px bg-white/[.09]" aria-hidden="true" />}
            <span className="relative z-10 mt-1.5 h-3.5 w-3.5 rounded-full border-2 border-sky-200/55 bg-[#0b0e17] shadow-[0_0_0_4px_rgba(125,211,252,.06)]" aria-hidden="true" />
            <div className="min-w-0">
              <p className="text-lg font-semibold text-white/88">{milestone.value}</p>
              <p className="mt-0.5 text-sm text-white/60">{milestone.label}</p>
              <SourceLink name={milestone.sourceName} url={milestone.sourceUrl} />
            </div>
          </li>
        ))}
      </ol>
    </section>
  )
}

function SourceLink({ name, url }: { name: string; url: string }) {
  return (
    <a href={url} target="_blank" rel="noopener noreferrer" className="mt-3 inline-flex items-center gap-1.5 text-xs leading-5 text-emerald-300/72 transition-colors hover:text-emerald-200" title={`Source: ${name}`}>
      {name} <ExternalLink size={12} className="shrink-0" />
    </a>
  )
}

function SourcesAndCredits({ project }: { project: ProjectDetail }) {
  const attributionUrl = extractImageCreditUrl(project.image.attribution)
  const hasImageCredits = Boolean(
    project.image.author
    || project.image.license
    || project.image.attribution
    || project.image.sourcePageUrl,
  )

  return (
    <section className="rounded-[1.75rem] border border-white/[.08] bg-white/[.03] p-5 sm:p-6">
      <h2 className="text-lg font-semibold text-white">Sources &amp; credits</h2>
      {project.sources.length > 0 && (
        <ul className="mt-4 space-y-4">
          {project.sources.map((source) => (
            <li key={`${source.url}-${source.claim}`}>
              <a href={source.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-start gap-2 text-sm font-medium leading-6 text-emerald-300/82 transition-colors hover:text-emerald-200">
                {source.name} <ExternalLink size={14} className="mt-1 shrink-0" />
              </a>
              <p className="mt-1 text-xs leading-5 text-white/55">{source.claim}</p>
            </li>
          ))}
        </ul>
      )}

      {hasImageCredits && (
        <div className="mt-5 border-t border-white/[.07] pt-5">
          <h3 className="text-[10px] font-semibold uppercase tracking-[.18em] text-violet-300/65">Image credit</h3>
          {(project.image.author || project.image.license) && (
            <p className="mt-2 text-xs leading-5 text-white/58">
              {project.image.author && <span>{project.image.author}</span>}
              {project.image.author && project.image.license && <span aria-hidden="true"> · </span>}
              {project.image.license && project.image.licenseUrl
                ? <a href={project.image.licenseUrl} target="_blank" rel="license noopener noreferrer" className="underline decoration-white/15 underline-offset-4 transition-colors hover:text-white">{project.image.license}</a>
                : <span>{project.image.license}</span>}
            </p>
          )}
          {project.image.attribution && !attributionUrl && (
            <p className="mt-2 break-words text-xs leading-5 text-white/58 [overflow-wrap:anywhere]">{project.image.attribution}</p>
          )}
          <div className="mt-3 flex flex-col items-start gap-2">
            {attributionUrl && (
              <ImageCreditLink href={attributionUrl} label={imageCreditLinkLabel(attributionUrl)} />
            )}
            {project.image.sourcePageUrl && project.image.sourcePageUrl !== attributionUrl && (
              <ImageCreditLink href={project.image.sourcePageUrl} label={imageRecordLinkLabel(project.image.sourcePageUrl)} />
            )}
          </div>
        </div>
      )}

      <p className="mt-5 border-t border-white/[.07] pt-4 text-xs leading-5 text-white/50">
        Data last updated <time dateTime={project.updatedDate}>{formatDate(project.updatedDate)}</time>.
      </p>
    </section>
  )
}

function ImageCreditLink({ href, label }: { href: string; label: string }) {
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" className="inline-flex max-w-full items-center gap-1.5 text-xs leading-5 text-violet-300/80 transition-colors hover:text-violet-200">
      <span>{label}</span>
      <ExternalLink size={13} className="shrink-0" />
    </a>
  )
}

function extractImageCreditUrl(value: string | null) {
  if (!value) return null

  const candidate = value.trim().replace(/\s+\((?:image\s+)?link\)$/i, '')
  try {
    const url = new URL(candidate)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

function imageCreditLinkLabel(value: string) {
  const host = urlHostname(value)
  if (host === 'flickr.com' || host.endsWith('.flickr.com')) return 'Original photo on Flickr'
  if (host === 'nasa.gov' || host.endsWith('.nasa.gov')) return 'NASA image source'
  return 'Original image source'
}

function imageRecordLinkLabel(value: string) {
  const host = urlHostname(value)
  return host === 'commons.wikimedia.org' ? 'Wikimedia Commons record' : 'Image source record'
}

function urlHostname(value: string) {
  try {
    return new URL(value).hostname.replace(/^www\./, '')
  } catch {
    return ''
  }
}

function RelatedProjects({ projects, rankingPath }: { projects: ProjectCard[]; rankingPath: string | null }) {
  return (
    <section className="mt-10 border-t border-white/[.08] pt-9" aria-labelledby="related-heading">
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-[.2em] text-violet-300/60">Keep exploring</p>
          <h2 id="related-heading" className="mt-2 text-2xl font-semibold tracking-[-.025em] text-white sm:text-3xl">Related project costs</h2>
        </div>
        <div className="hidden items-center gap-2 sm:flex">
          {rankingPath && (
            <Link to={rankingPath} className="inline-flex items-center gap-1.5 rounded-full border border-amber-200/15 px-3.5 py-2 text-sm text-amber-100/70 transition hover:border-amber-200/30 hover:bg-amber-200/[.05] hover:text-amber-50">Ranking <ArrowRight size={15} /></Link>
          )}
          <Link to="/projects" className="inline-flex items-center gap-1.5 rounded-full border border-white/[.08] px-3.5 py-2 text-sm text-white/45 transition hover:border-white/[.15] hover:bg-white/[.04] hover:text-white">View all <ArrowRight size={15} /></Link>
        </div>
      </div>
      <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {projects.map((project) => (
          <article key={project.slug} className="group min-w-0">
            <Link to={`/projects/${project.slug}`} className="block h-full overflow-hidden rounded-[1.5rem] border border-white/[.08] bg-[#080a11] transition duration-300 hover:-translate-y-1 hover:border-white/[.15] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-200/70">
              <div className="relative isolate aspect-[16/10] overflow-hidden bg-[#080a11]">
                <img src={budgetImageVariantUrl(project.imageUrl, 480)} srcSet={budgetImageSrcSet(project.imageUrl, 960)} sizes="(min-width: 1024px) 360px, (min-width: 640px) 45vw, 100vw" alt={project.imageAlt} width={640} height={400} loading="lazy" className="absolute -inset-px block h-[calc(100%+2px)] w-[calc(100%+2px)] max-w-none transform-gpu object-cover transition-transform duration-700 ease-out [backface-visibility:hidden] will-change-transform group-hover:scale-[1.045]" />
                <div className="absolute -inset-px bg-gradient-to-t from-[#080a11] via-black/5 to-transparent" />
                <p className="absolute bottom-4 left-4 text-xl font-bold tracking-tight text-amber-100">{project.answer}</p>
                <span className="absolute bottom-4 right-4 grid h-8 w-8 place-items-center rounded-full border border-white/[.12] bg-black/30 text-white/60 backdrop-blur-md transition group-hover:bg-amber-100 group-hover:text-[#151006]">
                  <ArrowUpRight size={14} />
                </span>
              </div>
              <div className="relative z-10 -mt-px bg-[#080a11] p-4 sm:p-5">
                <p className="text-[9px] font-semibold uppercase tracking-[.18em] text-white/32">{project.category} <span className="px-1 text-white/20">&middot;</span> {project.country}</p>
                <h3 className="mt-2 text-lg font-semibold leading-snug tracking-tight text-white transition-colors group-hover:text-amber-100">{project.name}</h3>
              </div>
            </Link>
          </article>
        ))}
      </div>
      <Link to="/projects" className="mt-5 inline-flex items-center gap-1.5 text-sm font-medium text-white/45 hover:text-white sm:hidden">View all projects <ArrowRight size={15} /></Link>
    </section>
  )
}

function ProjectPageState({ loading, message }: { loading: boolean; message?: string }) {
  return (
    <div className="flex min-h-[100dvh] flex-col overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="hero" />
      <Header />
      <main className="relative z-10 flex-1 px-4 pb-24 pt-16 sm:px-6 md:pb-28 lg:px-10">
        <div className="mx-auto flex min-h-[24rem] max-w-3xl items-center justify-center rounded-[2.5rem] border border-white/[.08] bg-white/[.03] p-8 text-center">
          {loading
            ? <div className="h-6 w-6 animate-spin rounded-full border-2 border-white/20 border-t-white/70" />
            : (
              <div>
                <MapPin size={28} className="mx-auto text-white/30" />
                <h1 className="mt-5 text-3xl font-bold tracking-tight">This project is not on our map</h1>
                <p className="mt-3 text-sm leading-6 text-white/60">{message || 'The address may be incorrect, or the project has not been published yet.'}</p>
                <Link to="/projects" className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-emerald-300/70 hover:text-emerald-200"><ArrowLeft size={15} /> Browse projects</Link>
              </div>
            )}
        </div>
      </main>
      <HomeLegalStrip guideMode="budget" />
    </div>
  )
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`))
}
