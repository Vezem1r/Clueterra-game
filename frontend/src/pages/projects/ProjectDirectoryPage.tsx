import { useCallback, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, ArrowUpRight, Banknote, BookOpenCheck } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'

import { projectPagesApi } from '@/api/projectPages'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { Header } from '@/components/layout/Header'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { budgetImageSrcSet, budgetImageVariantUrl } from '@/pages/budget/budgetImageUrls'
import { getProjectDocumentMeta, PROJECT_DIRECTORY_DESCRIPTION } from '@/features/projects/projectSeo'
import { useProjectSsrData } from '@/features/projects/projectSsrData'
import type {
  ProjectCard,
  ProjectDirectory,
  ProjectDirectoryFilters as ProjectFilterValues,
} from '@/features/projects/projectTypes'
import { isProjectCategory } from '@/features/projects/projectTypes'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { ProjectDirectoryFilters } from './ProjectDirectoryFilters'
import { ProjectRankingIndex } from './ProjectRankingIndex'

export function ProjectDirectoryPage() {
  const { t } = useTranslation()
  const copy = t.projectsPage
  const location = useLocation()
  const navigate = useNavigate()
  const { page: pageParam } = useParams()
  const page = pageParam ? Number(pageParam) : 1
  const filters = useMemo(() => filtersFromSearch(location.search), [location.search])
  const filtersActive = hasActiveFilters(filters)
  const initialData = useProjectSsrData()
  const initialDirectory = !filtersActive
    && initialData?.kind === 'directory'
    && initialData.path === location.pathname
    ? initialData.directory
    : undefined
  const terminalSsrState = !filtersActive
    && initialData?.path === location.pathname
    && (initialData.kind === 'not-found' || initialData.kind === 'error')

  const query = useQuery({
    queryKey: ['project-directory', page, filters],
    queryFn: () => projectPagesApi.getDirectory(page, filters),
    initialData: initialDirectory,
    enabled: Number.isInteger(page) && page > 0 && !terminalSsrState,
    staleTime: 1000 * 60 * 5,
  })

  const updateFilters = useCallback((nextFilters: ProjectFilterValues) => {
    const search = filtersToSearch(nextFilters)
    void navigate({ pathname: '/projects', search }, { replace: true })
  }, [navigate])

  const countryHubs = query.data?.countryHubs ?? []
  const canonicalPath = page === 1 ? '/projects' : `/projects/page/${page}`
  const documentMeta = useMemo(
    () => query.data
      ? getProjectDocumentMeta({ kind: 'directory', path: canonicalPath, directory: query.data })
      : null,
    [canonicalPath, query.data],
  )

  useSeoMeta({
    title: documentMeta?.title || 'Real megaproject costs and sources | Clueterra',
    description: documentMeta?.description || PROJECT_DIRECTORY_DESCRIPTION,
    canonicalPath,
    keywords: documentMeta?.keywords,
    structuredData: documentMeta?.structuredData || undefined,
  })

  return (
    <div className="min-h-[100dvh] overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="hero" />
      <Header />

      <main className="relative z-10 px-4 pb-24 pt-8 sm:px-6 sm:pt-12 md:pb-28 lg:px-10">
        <div className="mx-auto max-w-[88rem]">
          <section className="relative border-b border-white/[.08] pb-10 sm:pb-12 lg:pb-14">
            <div className="pointer-events-none absolute -left-28 -top-36 h-80 w-80 rounded-full bg-amber-300/[.07] blur-[100px]" />
            <div className="relative grid gap-8 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-end">
              <div className="max-w-4xl">
                <p className="text-[11px] font-semibold uppercase tracking-[.24em] text-amber-200/65">
                  {copy.eyebrow}
                </p>
                <h1 className="mt-4 max-w-4xl text-4xl font-bold tracking-[-.045em] text-white sm:text-5xl lg:text-6xl lg:leading-[1.02]">
                  {copy.title}
                </h1>
                <p className="mt-5 max-w-2xl text-base leading-7 text-white/48 sm:text-lg sm:leading-8">
                  {copy.intro}
                </p>
              </div>

              <div className="flex flex-wrap gap-3 lg:justify-end">
                <Link to="/budget" className="inline-flex items-center justify-center gap-2 rounded-full bg-amber-200 px-5 py-3 text-sm font-semibold text-[#151006] transition hover:-translate-y-0.5 hover:bg-amber-100">
                  <Banknote size={17} />
                  {copy.playCta}
                </Link>
                <Link to="/data-sources" className="inline-flex items-center justify-center gap-2 rounded-full border border-white/[.1] bg-white/[.035] px-5 py-3 text-sm font-medium text-white/62 transition hover:-translate-y-0.5 hover:border-white/[.18] hover:bg-white/[.07] hover:text-white">
                  <BookOpenCheck size={16} />
                  {copy.sourcesCta}
                </Link>
              </div>
            </div>
          </section>

          <ProjectRankingIndex countryHubs={countryHubs} />

          <section className="pt-9 sm:pt-11" aria-labelledby="published-projects-heading">
            <div className="flex items-end justify-between gap-5">
              <div>
                <p className="text-[10px] font-semibold uppercase tracking-[.22em] text-emerald-300/60">{copy.collectionEyebrow}</p>
                <h2 id="published-projects-heading" className="mt-2 text-2xl font-semibold tracking-[-.025em] text-white sm:text-3xl">{copy.collectionTitle}</h2>
              </div>
              {query.data && (
                <p className="shrink-0 rounded-full border border-white/[.08] bg-white/[.035] px-3 py-1.5 text-xs font-medium text-white/38">
                  {query.data.totalProjects === 1 ? copy.projectCountOne : copy.projectCount.replace('{count}', String(query.data.totalProjects))}
                </p>
              )}
            </div>

            <ProjectDirectoryFilters
              value={filters}
              resultCount={query.data?.totalProjects}
              loading={query.isFetching}
              onChange={updateFilters}
            />

            {query.isPending && !terminalSsrState && <DirectoryLoading />}
            {(terminalSsrState || query.isError || !Number.isInteger(page) || page < 1) && <DirectoryUnavailable />}
            {query.data && (
              <DirectoryContents
                directory={query.data}
                filters={filters}
                filtered={filtersActive}
                onClearFilters={() => updateFilters(EMPTY_FILTERS)}
              />
            )}
          </section>
        </div>
      </main>

      <HomeLegalStrip guideMode="budget" />
    </div>
  )
}

interface DirectoryContentsProps {
  directory: ProjectDirectory
  filters: ProjectFilterValues
  filtered: boolean
  onClearFilters: () => void
}

function DirectoryContents({ directory, filters, filtered, onClearFilters }: DirectoryContentsProps) {
  const { t } = useTranslation()
  const copy = t.projectsPage
  if (directory.projects.length === 0) {
    return (
      <div className="mt-7 rounded-[1.75rem] border border-dashed border-white/[.1] bg-black/15 px-6 py-12 text-center">
        <h3 className="text-lg font-semibold text-white">
          {filtered ? copy.emptyFilteredTitle : copy.emptyTitle}
        </h3>
        <p className="mt-2 text-sm text-white/42">
          {filtered ? copy.emptyFilteredBody : copy.emptyBody}
        </p>
        {filtered && (
          <button
            type="button"
            onClick={onClearFilters}
            className="mt-5 rounded-full border border-white/[.1] bg-white/[.04] px-4 py-2 text-sm font-semibold text-white/60 transition hover:border-white/[.18] hover:text-white"
          >
            {copy.clearAllFilters}
          </button>
        )}
      </div>
    )
  }

  return (
    <>
      <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {directory.projects.map((project) => <ProjectCardView key={project.slug} project={project} />)}
      </div>
      {directory.totalPages > 1 && <DirectoryPagination directory={directory} filters={filters} />}
    </>
  )
}

function ProjectCardView({ project }: { project: ProjectCard }) {
  return (
    <article className="group min-w-0">
      <Link
        to={`/projects/${project.slug}`}
        className="block h-full overflow-hidden rounded-[1.65rem] border border-white/[.08] bg-[#080a11] shadow-[0_18px_60px_rgba(0,0,0,.16)] transition duration-300 hover:-translate-y-1 hover:border-white/[.16] hover:shadow-[0_26px_80px_rgba(0,0,0,.28)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-200/70"
      >
        <div className="relative isolate aspect-[4/3] overflow-hidden bg-[#080a11]">
          <img
            src={budgetImageVariantUrl(project.imageUrl, 480)}
            srcSet={budgetImageSrcSet(project.imageUrl, 960)}
            sizes="(min-width: 1488px) 337px, (min-width: 1280px) 23vw, (min-width: 1024px) 30vw, (min-width: 640px) 45vw, 100vw"
            alt={project.imageAlt}
            width={720}
            height={540}
            loading="lazy"
            className="absolute -inset-px block h-[calc(100%+2px)] w-[calc(100%+2px)] max-w-none transform-gpu object-cover transition-transform duration-700 ease-out [backface-visibility:hidden] will-change-transform group-hover:scale-[1.045]"
          />
          <div className="absolute -inset-px bg-gradient-to-t from-[#080a11] via-transparent to-black/5" />
          <div className="absolute inset-x-0 bottom-0 flex items-end justify-between gap-3 p-4 sm:p-5">
            <div className="min-w-0">
              <p className="truncate text-[10px] font-semibold uppercase tracking-[.18em] text-white/55">
                {project.category} <span className="px-1 text-white/25">&middot;</span> {project.country}
              </p>
              <p className="mt-1.5 text-xl font-bold tracking-[-.025em] text-amber-100 sm:text-2xl">{project.answer}</p>
            </div>
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full border border-white/[.12] bg-black/30 text-white/65 backdrop-blur-md transition group-hover:border-amber-100/25 group-hover:bg-amber-100 group-hover:text-[#151006]">
              <ArrowUpRight size={16} />
            </span>
          </div>
        </div>

        <div className="relative z-10 -mt-px bg-[#080a11] p-4 sm:p-5">
          <div className="flex items-start justify-between gap-4">
            <h3 className="min-w-0 text-lg font-semibold leading-snug tracking-[-.02em] text-white transition-colors group-hover:text-amber-100 sm:text-xl">
              {project.name}
            </h3>
            {project.year && <span className="shrink-0 pt-1 text-xs tabular-nums text-white/28">{project.year}</span>}
          </div>
          {project.usdEquivalent && <p className="mt-2 truncate text-xs text-white/34">{project.usdEquivalent}</p>}
        </div>
      </Link>
    </article>
  )
}

function DirectoryPagination({
  directory,
  filters,
}: {
  directory: ProjectDirectory
  filters: ProjectFilterValues
}) {
  const search = filtersToSearch(filters)
  const previousBase = directory.pageNumber === 2 ? '/projects' : `/projects/page/${directory.pageNumber - 1}`
  const nextBase = `/projects/page/${directory.pageNumber + 1}`
  const previousPath = `${previousBase}${search}`
  const nextPath = `${nextBase}${search}`
  const { t } = useTranslation()
  const copy = t.projectsPage

  return (
    <nav className="mt-10 grid grid-cols-3 items-center border-t border-white/[.07] pt-6" aria-label={copy.paginationLabel}>
      {directory.pageNumber > 1
        ? <Link to={previousPath} rel="prev" className="inline-flex w-fit items-center gap-1.5 rounded-full border border-white/[.08] px-3.5 py-2 text-sm text-white/50 transition hover:border-white/[.15] hover:bg-white/[.04] hover:text-white"><ArrowLeft size={15} /> {copy.previous}</Link>
        : <span />}
      <p className="text-center text-xs text-white/35">{copy.pageLabel} <strong className="text-white/65">{directory.pageNumber}</strong> {copy.pageOf} <strong className="text-white/65">{directory.totalPages}</strong></p>
      {directory.pageNumber < directory.totalPages
        ? <Link to={nextPath} rel="next" className="ml-auto inline-flex w-fit items-center gap-1.5 rounded-full border border-white/[.08] px-3.5 py-2 text-sm text-white/50 transition hover:border-white/[.15] hover:bg-white/[.04] hover:text-white">{copy.next} <ArrowRight size={15} /></Link>
        : <span />}
    </nav>
  )
}

const EMPTY_FILTERS: ProjectFilterValues = {
  search: '',
  category: null,
  minimumCostUsd: null,
  maximumCostUsd: null,
  minimumYear: null,
  maximumYear: null,
}

function filtersFromSearch(search: string): ProjectFilterValues {
  const params = new URLSearchParams(search)
  const category = params.get('category')
  return {
    search: (params.get('q') || '').trim().slice(0, 100),
    category: isProjectCategory(category) ? category : null,
    minimumCostUsd: numericParam(params.get('minCost')),
    maximumCostUsd: numericParam(params.get('maxCost')),
    minimumYear: yearParam(params.get('yearFrom')),
    maximumYear: yearParam(params.get('yearTo')),
  }
}

function filtersToSearch(filters: ProjectFilterValues) {
  const params = new URLSearchParams()
  if (filters.search) params.set('q', filters.search)
  if (filters.category) params.set('category', filters.category)
  if (filters.minimumCostUsd !== null) params.set('minCost', String(filters.minimumCostUsd))
  if (filters.maximumCostUsd !== null) params.set('maxCost', String(filters.maximumCostUsd))
  if (filters.minimumYear !== null) params.set('yearFrom', String(filters.minimumYear))
  if (filters.maximumYear !== null) params.set('yearTo', String(filters.maximumYear))
  const value = params.toString()
  return value ? `?${value}` : ''
}

function numericParam(value: string | null) {
  if (!value || !/^\d+(?:\.\d+)?$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) || (Number.isFinite(parsed) && parsed >= 0)
    ? parsed
    : null
}

function yearParam(value: string | null) {
  if (!value || !/^\d{1,4}$/.test(value)) return null
  const parsed = Number(value)
  return parsed >= 1 && parsed <= 9999 ? parsed : null
}

function hasActiveFilters(filters: ProjectFilterValues) {
  return Boolean(filters.search)
    || filters.category !== null
    || filters.minimumCostUsd !== null
    || filters.maximumCostUsd !== null
    || filters.minimumYear !== null
    || filters.maximumYear !== null
}

function DirectoryLoading() {
  return (
    <div className="mt-8 flex min-h-48 items-center justify-center">
      <div className="h-5 w-5 animate-spin rounded-full border-2 border-white/20 border-t-white/70" />
    </div>
  )
}

function DirectoryUnavailable() {
  return (
    <div className="mt-7 rounded-[1.75rem] border border-red-300/10 bg-red-300/[.035] px-6 py-10 text-center">
      <h3 className="text-lg font-semibold text-white">This project page is unavailable</h3>
      <p className="mt-2 text-sm text-white/42">The address may be incorrect, or this directory page has not been published.</p>
      <Link to="/projects" className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-emerald-300/70 hover:text-emerald-200"><ArrowLeft size={15} /> Back to projects</Link>
    </div>
  )
}
