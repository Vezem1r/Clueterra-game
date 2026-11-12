import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Banknote, ChevronRight, Info, Trophy } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

import { projectPagesApi } from '@/api/projectPages'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { Header } from '@/components/layout/Header'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import {
  countryPhrase,
  hubHeading,
  hubSubject,
  COUNTRY_RANKINGS_ANCHOR,
  PROJECT_HUB_CATEGORIES,
  PROJECT_HUB_ROOT_PATH,
} from '@/features/projects/projectHubs'
import { getProjectDocumentMeta, PROJECT_DIRECTORY_DESCRIPTION } from '@/features/projects/projectSeo'
import { useProjectSsrData } from '@/features/projects/projectSsrData'
import type { ProjectHubEntry, ProjectHubLink, ProjectHubPage as ProjectHub } from '@/features/projects/projectTypes'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { budgetImageVariantUrl } from '@/pages/budget/budgetImageUrls'
import { cn } from '@/utils/cn'
import { BudgetGameInvitation } from '@/features/projects/BudgetGameInvitation'
import { projectRankingGroups } from '@/features/projects/projectRankingGroups'

const HUB_STALE_TIME = 1000 * 60 * 5
const THUMBNAIL_SIZE = 480

export function ProjectHubPage() {
  const location = useLocation()
  const ssrData = useProjectSsrData()
  const initialHub = ssrData?.kind === 'hub' && ssrData.path === location.pathname ? ssrData.hub : undefined
  const terminalSsrState = ssrData?.path === location.pathname
    && (ssrData.kind === 'not-found' || ssrData.kind === 'error')

  const query = useQuery({
    queryKey: ['project-hub', location.pathname],
    queryFn: () => projectPagesApi.getHub(location.pathname),
    initialData: initialHub,
    enabled: !terminalSsrState,
    staleTime: HUB_STALE_TIME,
    retry: false,
  })

  const documentMeta = useMemo(
    () => (query.data ? getProjectDocumentMeta({ kind: 'hub', path: query.data.path, hub: query.data }) : null),
    [query.data],
  )

  useEffect(() => {
    if (!query.data || !location.hash) return
    document.getElementById(location.hash.slice(1))?.scrollIntoView()
  }, [query.data, location.hash])

  useSeoMeta({
    title: documentMeta?.title || 'The most expensive megaprojects in our catalogue | Clueterra',
    description: documentMeta?.description || PROJECT_DIRECTORY_DESCRIPTION,
    canonicalPath: location.pathname,
    keywords: documentMeta?.keywords,
    structuredData: documentMeta?.structuredData || undefined,
  })

  return (
    <div className="min-h-[100dvh] overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="hero" />
      <Header />

      <main className="relative z-10 px-4 pb-24 pt-8 sm:px-6 sm:pt-12 md:pb-28 lg:px-10">
        <div className="mx-auto max-w-[72rem]">
          {query.data ? (
            <HubContents hub={query.data} />
          ) : terminalSsrState || query.isError ? (
            <HubUnavailable />
          ) : (
            <div className="h-[60vh]" aria-hidden="true" />
          )}
        </div>
      </main>

      <HomeLegalStrip guideMode="budget" />
    </div>
  )
}

function HubContents({ hub }: { hub: ProjectHub }) {
  const groups = projectRankingGroups(hub)
  const isAirportRanking = hub.kind === 'CATEGORY' && hub.category === 'AIRPORT'
  const heading = hubHeading(hub)
  const subject = hubSubject(hub)
  const place = hub.kind === 'COUNTRY' && hub.countryName ? ` in ${countryPhrase(hub.countryName, hub.countryCode)}` : ''
  const crumbLabel = hub.kind === 'CATEGORY' && hub.category
    ? PROJECT_HUB_CATEGORIES[hub.category].label
    : hub.kind === 'COUNTRY'
      ? hub.countryName
      : null

  return (
    <>
      <nav aria-label="Breadcrumb" className="flex flex-wrap items-center gap-1.5 text-xs font-medium text-white/40">
        <Link to="/projects" className="transition-colors hover:text-white">Projects</Link>
        <ChevronRight size={12} />
        {crumbLabel ? (
          <>
            <Link to={PROJECT_HUB_ROOT_PATH} className="transition-colors hover:text-white">Most expensive</Link>
            <ChevronRight size={12} />
            <span className="text-white/60">{crumbLabel}</span>
          </>
        ) : (
          <span className="text-white/60">Most expensive</span>
        )}
      </nav>

      <section className="relative mt-6 border-b border-white/[.08] pb-10">
        <div className="pointer-events-none absolute -left-28 -top-36 h-80 w-80 rounded-full bg-amber-300/[.07] blur-[100px]" />
        <p className="relative text-[11px] font-semibold uppercase tracking-[.24em] text-amber-200/65">
          BudgetGuessr rankings
        </p>
        <h1 className="relative mt-4 max-w-4xl text-balance text-4xl font-bold tracking-[-.045em] text-white sm:text-5xl lg:leading-[1.04]">
          {heading}
        </h1>
        <p className="relative mt-5 max-w-4xl text-pretty text-base leading-7 text-white/52 sm:text-lg sm:leading-8">
          {isAirportRanking
            ? `${hub.projects.length} airport projects from our catalogue, grouped by scope and ranked by recorded US-dollar cost.`
            : `The ${hub.projects.length} highest recorded costs among ${hub.totalProjects} ${subject}${place} in the BudgetGuessr catalogue, ordered in US dollars.`}
        </p>
        <p className="relative mt-3 max-w-4xl text-sm leading-6 text-white/60">Original budgets, estimates and reported costs; no inflation adjustment. <a href="#ranking-methodology" className="text-amber-100/80 underline underline-offset-4 hover:text-amber-100">How to compare them</a></p>
        <BudgetGameInvitation />
        {isAirportRanking && (
          <nav aria-label="Airport project scopes" className="relative mt-6 flex flex-wrap gap-x-5 gap-y-3 text-sm text-amber-100/80">
            {groups.map(group => <a key={group.id} href={`#${group.id}`} className="underline underline-offset-4 hover:text-amber-100">{group.id === 'airports' ? 'Airports' : group.id === 'expansions' ? 'Terminals & expansions' : 'Rail links'}</a>)}
          </nav>
        )}
      </section>

      {groups.map(group => (
        <section key={group.id} id={group.id} className="scroll-mt-24 pt-8" aria-labelledby={`${group.id}-heading`}>
          <h2 id={`${group.id}-heading`} className={isAirportRanking ? 'text-2xl font-semibold tracking-tight' : 'sr-only'}>{group.title}</h2>
          {group.description && <p className="mb-5 mt-2 max-w-3xl text-sm leading-6 text-white/60">{group.description}</p>}
          <ol className="grid gap-2.5">
            {group.projects.map((project) => (
              <HubRow key={project.slug} project={project} showCountry={hub.kind !== 'COUNTRY'} />
            ))}
          </ol>
        </section>
      ))}

      <section id="ranking-methodology" className="mt-10 scroll-mt-24 rounded-[1.75rem] border border-white/[.08] bg-white/[.025] p-5 sm:p-6">
        <div className="flex items-start gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-amber-200/[.08] text-amber-200/80">
            <Info size={17} />
          </span>
          <div>
            <h2 className="text-base font-semibold text-white">How this ranking works</h2>
            <p className="mt-2 text-sm leading-6 text-white/50">
              This is a curated catalogue, not an exhaustive list of every megaproject. Projects are ordered by their
              recorded US-dollar equivalent; each detail page shows the source amount and any currency conversion.
              Figures are not adjusted for inflation, and construction bills, estimates, and programme costs can have
              different scopes. Unbuilt proposals and cancelled projects are excluded.
            </p>
          </div>
        </div>
      </section>

      <MoreRankings hub={hub} />

      <section className="mt-10 flex flex-col items-start justify-between gap-4 rounded-[1.75rem] border border-amber-200/[.12] bg-[linear-gradient(135deg,rgba(251,191,36,.06),rgba(8,9,15,.94))] p-5 sm:flex-row sm:items-center sm:p-6">
        <div>
          <h2 className="text-lg font-semibold text-white">Could you have guessed these?</h2>
          <p className="mt-1 text-sm text-white/50">BudgetGuessr shows you a real project every day and asks what it cost.</p>
        </div>
        <Link
          to="/budget"
          className="inline-flex shrink-0 items-center justify-center gap-2 rounded-full bg-amber-200 px-5 py-3 text-sm font-semibold text-[#151006] transition hover:-translate-y-0.5 hover:bg-amber-100"
        >
          <Banknote size={17} />
          Play BudgetGuessr
        </Link>
      </section>
    </>
  )
}

function HubRow({ project, showCountry }: { project: ProjectHubEntry; showCountry: boolean }) {
  const showSourceAmount = project.answer !== project.costUsd
  const meta = [showCountry ? project.country : null, project.year, project.category].filter(Boolean).join(' · ')

  return (
    <li>
      <Link
        to={`/projects/${project.slug}`}
        className="group grid grid-cols-[2.25rem_4.5rem_minmax(0,1fr)] items-center gap-3 rounded-[1.25rem] border border-white/[.07] bg-white/[.025] p-2.5 pr-4 transition hover:border-white/[.14] hover:bg-white/[.045] sm:grid-cols-[3rem_6rem_minmax(0,1fr)_auto] sm:gap-4"
      >
        <span
          className={cn(
            'text-center font-display text-lg font-black tabular-nums sm:text-2xl',
            project.rank <= 3 ? 'text-amber-200' : 'text-white/30',
          )}
        >
          {project.rank}
        </span>
        <span className="relative aspect-[4/3] overflow-hidden rounded-xl bg-white/[.04]">
          <img
            src={budgetImageVariantUrl(project.imageUrl, THUMBNAIL_SIZE)}
            alt={project.imageAlt}
            width={160}
            height={120}
            loading="lazy"
            className="absolute inset-0 h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        </span>
        <span className="min-w-0">
          <h3 className="text-sm font-semibold leading-snug text-white sm:text-base">{project.name}</h3>
          <span className="mt-0.5 block truncate text-xs text-white/40">{meta}</span>
          <span className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 sm:hidden">
            <span className="text-sm font-bold text-amber-100">{project.costUsd}</span>
            {project.status && <StatusBadge label={project.status} />}
            {project.estimate && <EstimateBadge />}
          </span>
          <span className="mt-1 block text-xs leading-5 text-white/55 sm:hidden">
            {[showSourceAmount ? project.answer : null, project.costType, project.costScope].filter(Boolean).join(' · ')}
          </span>
        </span>
        <span className="hidden max-w-72 text-right sm:block">
          <span className="block text-lg font-bold tabular-nums text-amber-100">{project.costUsd}</span>
          <span className="mt-0.5 flex flex-wrap items-center justify-end gap-2 text-xs leading-5 text-white/55">
            {project.status && <StatusBadge label={project.status} />}
            {project.estimate && <EstimateBadge />}
            {[showSourceAmount ? project.answer : null, project.costType, project.costScope].filter(Boolean).join(' · ')}
          </span>
        </span>
      </Link>
    </li>
  )
}

function StatusBadge({ label }: { label: string }) {
  return (
    <span className="rounded-full border border-sky-200/20 bg-sky-300/10 px-2 py-0.5 text-[9px] font-bold uppercase tracking-[.12em] text-sky-100/80">
      {label}
    </span>
  )
}

function EstimateBadge() {
  return (
    <span className="rounded-full border border-amber-200/20 bg-amber-300/10 px-2 py-0.5 text-[9px] font-bold uppercase tracking-[.12em] text-amber-100/80">
      Estimate
    </span>
  )
}

function MoreRankings({ hub }: { hub: ProjectHub }) {
  const categoryLinks = hub.categoryHubs.filter((link) => link.path !== hub.path)
  const countryLinks = hub.countryHubs.filter((link) => link.path !== hub.path)

  return (
    <section className="mt-10" aria-labelledby="more-rankings-heading">
      <h2 id="more-rankings-heading" className="flex items-center gap-2 text-xl font-semibold text-white">
        <Trophy size={18} className="text-amber-200/70" />
        More rankings
      </h2>
      {hub.kind !== 'ALL' && (
        <Link
          to={PROJECT_HUB_ROOT_PATH}
          className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-amber-100/80 transition-colors hover:text-amber-50"
        >
          The most expensive projects in our catalogue
          <ArrowRight size={14} />
        </Link>
      )}
      <RankingChips title="By type" links={categoryLinks} label={(link) => (link.category ? PROJECT_HUB_CATEGORIES[link.category].label : link.path)} />
      <RankingChips
        id={COUNTRY_RANKINGS_ANCHOR}
        title="By country"
        links={countryLinks}
        label={(link) => link.countryName ?? link.path}
      />
    </section>
  )
}

function RankingChips({
  id,
  title,
  links,
  label,
}: {
  id?: string
  title: string
  links: ProjectHubLink[]
  label: (link: ProjectHubLink) => string
}) {
  if (links.length === 0) return null

  return (
    <div id={id} className="mt-5 scroll-mt-24">
      <h3 className="text-[11px] font-semibold uppercase tracking-[.18em] text-white/35">{title}</h3>
      <ul className="mt-2.5 flex flex-wrap gap-2">
        {links.map((link) => (
          <li key={link.path}>
            <Link
              to={link.path}
              className="inline-flex items-center gap-1.5 rounded-full border border-white/[.08] bg-white/[.03] px-3 py-1.5 text-xs font-medium text-white/62 transition hover:border-white/[.16] hover:bg-white/[.06] hover:text-white"
            >
              {label(link)}
              <span className="tabular-nums text-white/30">{link.projectCount}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

function HubUnavailable() {
  return (
    <div className="mx-auto max-w-xl py-24 text-center">
      <h1 className="text-3xl font-bold tracking-tight">This ranking is not available</h1>
      <p className="mt-3 text-sm leading-6 text-white/50">
        The ranking may have moved or there are not enough projects for it yet.
      </p>
      <Link
        to={PROJECT_HUB_ROOT_PATH}
        className="mt-6 inline-flex items-center gap-2 rounded-full bg-amber-200 px-5 py-3 text-sm font-semibold text-[#151006]"
      >
        See all rankings
        <ArrowRight size={15} />
      </Link>
    </div>
  )
}
