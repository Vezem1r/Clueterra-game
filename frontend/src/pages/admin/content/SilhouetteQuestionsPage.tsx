import { useDeferredValue, useEffect, useMemo, useRef, useState } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ArrowUpDown, Camera, Globe2, ImageOff, Loader2, Lock, Search } from 'lucide-react'
import {
  adminApi,
  type SilhouetteHintPreview,
  type SilhouetteQuestionFilters,
  type SilhouetteQuestionPreview,
  type SilhouetteQuestionSummary,
} from '@/api/admin'
import type { SilhouetteNames } from '@/api/silhouettes'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { flagUrl, SILHOUETTE_TIER_BADGE, SILHOUETTE_VIEWBOX } from '@/pages/games/silhouettes/silhouetteGameConstants'
import { SILHOUETTE_QUESTIONS_QUERY_KEY, SilhouettePhotoModal } from './SilhouettePhotoModal'

const PAGE_SKELETONS = 8
const PERCENT = 100
const STALE_MS = 30_000
const ISO_LENGTH = 2
const ALL_TIERS = 'ALL'
const TIERS = [1, 2, 3, 4, 5]
const NAME_SEPARATOR = ', '

const sortOptions = [
  { value: 'COUNTRY', label: 'Country A–Z' },
  { value: 'EASIEST', label: 'Easiest tier first' },
  { value: 'HARDEST', label: 'Hardest tier first' },
  { value: 'MOST_PLAYED', label: 'Most played' },
  { value: 'MOST_SKIPPED', label: 'Most skipped' },
  { value: 'NEWEST', label: 'Recently generated' },
]

const statusOptions = [
  { value: 'ACTIVE', label: 'Active questions' },
  { value: 'INACTIVE', label: 'Retired' },
  { value: 'ALL', label: 'All questions' },
]

const photoOptions = [
  { value: 'ALL', label: 'Any photo' },
  { value: 'WITH_PHOTO', label: 'With a photo' },
  { value: 'MISSING', label: 'Without a photo' },
  { value: 'MANUAL', label: 'Photo chosen by hand' },
]

const tierOptions = [
  { value: ALL_TIERS, label: 'All tiers' },
  ...TIERS.map(tier => ({ value: String(tier), label: `Tier ${tier}` })),
]

const clueLabel: Record<SilhouetteHintPreview['kind'], string> = {
  REGION: 'Region',
  LOCATION: 'Country on the globe',
  CAPITAL: 'Capital',
  FLAG_COLORS: 'Flag colours (no single capital)',
  LANDMARK: 'Landmark photo',
}

function clueDetail(hint: SilhouetteHintPreview, question: SilhouetteQuestionPreview) {
  if (hint.kind === 'REGION') return question.subregion
  if (hint.kind === 'CAPITAL') {
    return ((hint.payload.capitals as SilhouetteNames[] | undefined) ?? []).map(capital => capital.en).join(NAME_SEPARATOR)
  }
  if (hint.kind === 'LANDMARK') return question.landmarkName ?? 'Photo without a named landmark'
  if (hint.kind === 'FLAG_COLORS') return `${((hint.payload.colors as unknown[] | undefined) ?? []).length} colours`
  return 'Outline and camera fit'
}

export function SilhouetteQuestionsPage() {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const [countryCode, setCountryCode] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<NonNullable<SilhouetteQuestionFilters['status']>>('ACTIVE')
  const [tier, setTier] = useState(ALL_TIERS)
  const [sort, setSort] = useState<NonNullable<SilhouetteQuestionFilters['sort']>>('COUNTRY')
  const [photo, setPhoto] = useState<NonNullable<SilhouetteQuestionFilters['photo']>>('ALL')
  const [editing, setEditing] = useState<SilhouetteQuestionPreview | null>(null)
  const deferredSearch = useDeferredValue(search.trim())
  const filters = useMemo(() => ({
    countryCode, search: deferredSearch, status, sort, photo, tier: tier === ALL_TIERS ? undefined : Number(tier),
  }), [countryCode, deferredSearch, status, sort, photo, tier])
  const query = useInfiniteQuery({
    queryKey: [SILHOUETTE_QUESTIONS_QUERY_KEY, filters],
    queryFn: ({ pageParam }) => adminApi.getSilhouetteQuestions(filters, pageParam),
    initialPageParam: 0,
    getNextPageParam: page => page.hasNext ? page.page + 1 : undefined,
    staleTime: STALE_MS,
  })
  const { fetchNextPage, hasNextPage, isFetchingNextPage } = query
  const questions = useMemo(() => query.data?.pages.flatMap(page => page.content) ?? [], [query.data?.pages])
  const summary = query.data?.pages[0]?.summary

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasNextPage) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && !isFetchingNextPage) void fetchNextPage()
    }, { rootMargin: '600px 0px' })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [fetchNextPage, hasNextPage, isFetchingNextPage, questions.length])

  return <div className="mx-auto w-full max-w-[1800px] px-4 py-6 text-white md:px-6 lg:px-8">
    <header className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <p className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[.2em] text-violet-300/75"><Globe2 size={14} /> Silhouettes / Content Studio</p>
        <h1 className="mt-1.5 text-2xl font-semibold tracking-[-.035em] sm:text-3xl">Country silhouettes</h1>
        <p className="mt-1 text-sm text-white/45">Generated shapes, clues and verified facts, with how players do on each country.</p>
      </div>
    </header>

    {summary && <SummaryStrip summary={summary} />}

    <section aria-label="Silhouette question filters" className="mb-5 grid gap-2 rounded-2xl border border-white/[.08] bg-[#101014] p-3 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1fr)_100px_170px_140px_190px_190px]">
      <label className="relative block">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/30" />
        <span className="sr-only">Search countries</span>
        <input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search countries"
          className="h-9 w-full rounded-lg border border-white/[.09] bg-[#08080c] pl-9 pr-3 text-sm text-white outline-none placeholder:text-white/30 focus:border-violet-300/45" />
      </label>
      <input aria-label="Filter by two-letter country code" value={countryCode}
        onChange={event => setCountryCode(event.target.value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, ISO_LENGTH))}
        placeholder="ISO code" className="h-9 w-full rounded-lg border border-white/[.09] bg-[#08080c] px-3 text-sm text-white outline-none placeholder:text-white/30 focus:border-violet-300/45" />
      <CustomSelect value={status} onChange={value => setStatus(value as NonNullable<SilhouetteQuestionFilters['status']>)}
        options={statusOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <CustomSelect value={tier} onChange={setTier} options={tierOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <CustomSelect value={photo} onChange={value => setPhoto(value as NonNullable<SilhouetteQuestionFilters['photo']>)}
        options={photoOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <div className="relative min-w-0">
        <ArrowUpDown size={13} className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-white/35" />
        <CustomSelect value={sort} onChange={value => setSort(value as NonNullable<SilhouetteQuestionFilters['sort']>)}
          options={sortOptions} className="[&>button]:w-full [&>button]:justify-between [&>button]:pl-8" />
      </div>
    </section>

    {query.isPending && <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">{Array.from({ length: PAGE_SKELETONS }, (_, index) => <div key={index} className="h-80 animate-pulse rounded-2xl border border-white/[.06] bg-[#111116]" />)}</div>}
    {query.isError && <div role="alert" className="rounded-2xl border border-rose-300/20 bg-rose-300/[.05] p-8 text-center text-sm text-rose-200">Could not load silhouette questions. <button type="button" onClick={() => void query.refetch()} className="underline underline-offset-2">Try again</button></div>}
    {!query.isPending && !query.isError && questions.length === 0 && <div className="rounded-2xl border border-dashed border-white/[.1] bg-[#0e0e12] p-12 text-center text-sm text-white/45">No questions match these filters. Run Operations → Generate silhouette questions if the catalog is empty.</div>}
    {questions.length > 0 && <div className="grid items-start gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {questions.map(question => <SilhouetteQuestionCard key={question.id} question={question} onEditPhoto={() => setEditing(question)} />)}
    </div>}
    {questions.length > 0 && <div ref={sentinelRef} className="flex min-h-20 items-center justify-center py-5 text-xs text-white/40">
      {isFetchingNextPage ? <span className="flex items-center gap-2"><Loader2 size={15} className="animate-spin" /> Loading more questions</span>
        : hasNextPage ? <button type="button" onClick={() => void fetchNextPage()} className="rounded-full border border-white/15 px-4 py-2 text-white/65 hover:border-white/30 hover:text-white">Load more</button> : null}
    </div>}
    {editing && <SilhouettePhotoModal key={editing.id} question={editing} onClose={() => setEditing(null)} />}
  </div>
}

function SummaryStrip({ summary }: { summary: SilhouetteQuestionSummary }) {
  const items: [string, string][] = [
    ['Active questions', summary.activeQuestions.toLocaleString()],
    ['With landmark clue', summary.landmarkClues.toLocaleString()],
    ['Players', summary.players.toLocaleString()],
    ['Countries painted', summary.solves.toLocaleString()],
    ['Guesses', summary.guesses.toLocaleString()],
  ]
  return <section aria-label="Silhouette game totals" className="mb-5 grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-5">
    {items.map(([label, value]) => <div key={label} className="rounded-xl border border-violet-200/[.08] bg-violet-200/[.025] px-3 py-3">
      <p className="truncate text-base font-bold text-white/85">{value}</p>
      <p className="mt-1 text-[10px] text-white/35">{label}</p>
    </div>)}
  </section>
}

function SilhouetteQuestionCard({ question, onEditPhoto }: { question: SilhouetteQuestionPreview, onEditPhoto: () => void }) {
  const solveRate = question.players > 0 ? Math.round((question.solved / question.players) * PERCENT) : null
  const facts = [
    question.facts.capital && ['Capital', question.facts.capital.en],
    question.facts.population && ['Population', question.facts.population.value.toLocaleString()],
    question.facts.areaKm2 && ['Area', `${question.facts.areaKm2.value.toLocaleString()} km²`],
    question.facts.highestPoint && ['Highest point', `${question.facts.highestPoint.name.en}, ${question.facts.highestPoint.elevationM.toLocaleString()} m`],
  ].filter((fact): fact is [string, string] => Boolean(fact))
  return <article className="min-w-0 overflow-hidden rounded-2xl border border-white/[.09] bg-[#111115] shadow-[0_14px_35px_rgba(0,0,0,.18)]">
    <div className="relative flex aspect-[3/2] items-center justify-center bg-[#090b14]">
      <svg viewBox={SILHOUETTE_VIEWBOX} role="img" aria-label={`Silhouette of ${question.name}`} className="h-[82%] w-[82%]">
        <path d={question.shapePath} fill="#f1f5f9" fillRule="evenodd" />
      </svg>
      <span className={`absolute right-3 top-3 rounded-full border px-2.5 py-1 text-[10px] font-semibold backdrop-blur ${question.active ? 'border-emerald-300/25 bg-[#092419]/85 text-emerald-200' : 'border-white/20 bg-black/70 text-white/65'}`}>{question.active ? 'Active' : 'Retired'}</span>
    </div>
    <div className="p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2.5">
          <img src={flagUrl(question.countryCode)} alt="" aria-hidden="true" className="h-6 w-9 shrink-0 rounded-[3px] object-cover ring-1 ring-white/20" />
          <div className="min-w-0">
            <p className="truncate text-[10px] font-bold uppercase tracking-[.13em] text-violet-200/65">{question.subregion} · {question.countryCode}</p>
            <h2 className="truncate text-lg font-semibold tracking-tight text-white" title={question.name}>{question.name}</h2>
          </div>
        </div>
        <span className={`shrink-0 rounded-lg px-2 py-1 text-[10px] font-medium ring-1 ${SILHOUETTE_TIER_BADGE[question.tier]}`}>Tier {question.tier}</span>
      </div>

      <dl className="mt-3 grid grid-cols-4 gap-1.5 text-center">
        {([
          ['Players', question.players.toLocaleString()],
          ['Solved', solveRate == null ? '—' : `${solveRate}%`],
          ['Skips', question.skips.toLocaleString()],
          ['Avg clues', question.averageHints == null ? '—' : question.averageHints.toFixed(1)],
        ] as [string, string][]).map(([label, value]) => <div key={label} className="rounded-lg border border-white/[.06] bg-black/20 px-1 py-1.5">
          <dt className="text-[9px] uppercase tracking-[.08em] text-white/35">{label}</dt>
          <dd className="text-sm font-semibold text-white/85">{value}</dd>
        </div>)}
      </dl>

      <div className="mt-3 border-t border-white/[.07] pt-3">
        {question.photoUrl
          ? <figure>
            <img src={question.photoUrl} alt={`Landmark photo for ${question.name}`} loading="lazy" className="aspect-[2/1] w-full rounded-lg object-cover" />
            <figcaption className="mt-1 flex items-center gap-1 truncate text-[11px] text-white/45">
              {question.photoSelection === 'MANUAL' && <Lock size={10} className="shrink-0 text-amber-200/70" aria-label="Chosen by hand" />}
              <span className="truncate">{question.landmarkName ?? 'Landmark'} · {question.photoAuthorName ?? 'Wikimedia Commons'}</span>
            </figcaption>
          </figure>
          : <p className="flex items-center gap-2 text-xs text-white/40"><ImageOff size={13} /> No landmark photo yet</p>}
        <button type="button" onClick={onEditPhoto}
          className="mt-2 flex items-center gap-1 rounded-lg border border-violet-300/25 bg-violet-300/[.06] px-2.5 py-1.5 text-[11px] font-medium text-violet-100 hover:border-violet-300/45">
          <Camera size={12} aria-hidden="true" /> {question.photoUrl ? 'Edit photo' : 'Add photo'}
        </button>
      </div>

      <ol className="mt-3 space-y-1.5 border-t border-white/[.07] pt-3">
        {question.hints.map(hint => <li key={hint.position} className="rounded-lg border border-white/[.055] bg-black/20 px-2.5 py-2">
          <div className="flex items-center justify-between gap-2 text-[10px] font-bold uppercase tracking-[.1em] text-violet-200/55">
            <span>{String(hint.position).padStart(ISO_LENGTH, '0')} / {clueLabel[hint.kind]}</span>
            <span className="text-amber-200/60">{hint.costUnits}×</span>
          </div>
          <p className="mt-1 truncate text-xs text-white/70">{clueDetail(hint, question)}</p>
        </li>)}
      </ol>

      {facts.length > 0 && <dl className="mt-3 grid gap-1 border-t border-white/[.07] pt-3 text-xs">
        {facts.map(([label, value]) => <div key={label} className="flex justify-between gap-3">
          <dt className="text-white/40">{label}</dt>
          <dd className="truncate text-right text-white/75">{value}</dd>
        </div>)}
      </dl>}
    </div>
  </article>
}
