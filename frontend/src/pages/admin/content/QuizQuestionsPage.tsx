import { useDeferredValue, useEffect, useMemo, useRef, useState } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ArrowUpDown, Check, Globe2, Loader2, Search } from 'lucide-react'
import {
  adminApi,
  type QuizContentSummary,
  type QuizQuestionPreview,
  type QuizStopFilters,
  type QuizStopPreview,
} from '@/api/admin'
import type { QuizKind } from '@/api/quiz'
import type { SilhouetteNames } from '@/api/silhouettes'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { QuizShape } from '@/pages/games/countries-quiz/QuizShape'
import { QUIZ_KIND_ICONS } from '@/pages/games/countries-quiz/quizGameConstants'
import { SILHOUETTE_TIER_BADGE, flagUrl } from '@/pages/games/silhouettes/silhouetteGameConstants'
import { cn } from '@/utils/cn'

const QUIZ_STOPS_QUERY_KEY = 'admin-quiz-stops'
const PAGE_SKELETONS = 6
const PERCENT = 100
const STALE_MS = 30_000
const ISO_LENGTH = 2
const ALL = 'ALL'
const TIERS = [1, 2, 3, 4, 5]
const VALUE_SEPARATOR = ' · '

const KIND_LABELS: Record<QuizKind, string> = {
  FLAG: 'Flag',
  CAPITAL: 'Capital',
  NEIGHBOUR: 'Neighbour',
  CURRENCY: 'Currency',
  POPULATION: 'Most populous',
  AREA: 'Largest area',
  LEFT_DRIVING: 'Drives on the left',
  DOMAIN: 'Internet domain',
  LANDLOCKED: 'Landlocked',
  OUTLINE: 'Nearby outline',
  COASTLINE: 'Longest coastline',
  TOURISTS: 'Most tourists',
  PEAK: 'Highest mountain',
  NEIGHBOUR_CAPITAL: 'Neighbour capital',
  HIGHEST_POINT: 'Highest point',
  NOT_NEIGHBOUR: 'Not a neighbour',
  LIFE_EXPECTANCY: 'Longest lives',
  WEALTH: 'GDP per person',
  HERITAGE: 'World Heritage',
  NEIGHBOUR_COUNT: 'Number of neighbours',
  FOREST: 'Most forest',
  MEDIAN_AGE: 'Youngest people',
}

const sortOptions = [
  { value: 'COUNTRY', label: 'Country A–Z' },
  { value: 'EASIEST', label: 'Easiest tier first' },
  { value: 'HARDEST', label: 'Hardest tier first' },
  { value: 'MOST_PLAYED', label: 'Most visited' },
  { value: 'LOWEST_ACCURACY', label: 'Lowest accuracy' },
  { value: 'NEWEST', label: 'Recently generated' },
]

const statusOptions = [
  { value: 'ACTIVE', label: 'Active stops' },
  { value: 'INACTIVE', label: 'Retired' },
  { value: 'ALL', label: 'All stops' },
]

const tierOptions = [
  { value: ALL, label: 'All tiers' },
  ...TIERS.map(tier => ({ value: String(tier), label: `Tier ${tier}` })),
]

const kindOptions = [
  { value: ALL, label: 'Any question kind' },
  ...(Object.keys(KIND_LABELS) as QuizKind[]).map(kind => ({ value: kind, label: KIND_LABELS[kind] })),
]

function percent(part: number, whole: number) {
  return whole > 0 ? `${Math.round((part / whole) * PERCENT)}%` : '—'
}

function optionText(option: Record<string, unknown>) {
  const names = option.names as SilhouetteNames | undefined
  if (names) return option.code ? `${names.en} (${String(option.code)})` : names.en
  if (option.text) return String(option.text)
  if (option.value != null) return String(option.value)
  return String(option.iso ?? '')
}

function factSummary(question: QuizQuestionPreview) {
  const fact = question.fact
  switch (fact.type) {
    case 'POPULATION': return `Population ${fact.value.toLocaleString()}${fact.year ? ` (${fact.year})` : ''}`
    case 'AREA': return `Area ${fact.value.toLocaleString()} km²`
    case 'HIGHEST_POINT': return `${fact.names.en}, ${fact.elevation.toLocaleString()} m`
    case 'CAPITAL': return `Capital ${fact.names.en}`
    case 'CURRENCY': return `${fact.names.en} (${fact.code})`
    case 'DRIVES_LEFT': return `Drives on the left${fact.iso ? ` (${fact.iso})` : ''}`
    case 'LANDLOCKED': return 'Landlocked'
    case 'REGION': return fact.subregion
    case 'NEIGHBOURS': return `Neighbours: ${fact.countries.join(VALUE_SEPARATOR)}`
    case 'VALUES': return fact.values.map(value => value.toLocaleString()).join(VALUE_SEPARATOR)
  }
}

export function QuizQuestionsPage() {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const [countryCode, setCountryCode] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<NonNullable<QuizStopFilters['status']>>('ACTIVE')
  const [tier, setTier] = useState(ALL)
  const [kind, setKind] = useState<NonNullable<QuizStopFilters['kind']>>(ALL)
  const [sort, setSort] = useState<NonNullable<QuizStopFilters['sort']>>('COUNTRY')
  const deferredSearch = useDeferredValue(search.trim())
  const filters = useMemo(() => ({
    countryCode, search: deferredSearch, status, kind, sort, tier: tier === ALL ? undefined : Number(tier),
  }), [countryCode, deferredSearch, status, kind, sort, tier])
  const query = useInfiniteQuery({
    queryKey: [QUIZ_STOPS_QUERY_KEY, filters],
    queryFn: ({ pageParam }) => adminApi.getQuizStops(filters, pageParam),
    initialPageParam: 0,
    getNextPageParam: page => page.hasNext ? page.page + 1 : undefined,
    staleTime: STALE_MS,
  })
  const { fetchNextPage, hasNextPage, isFetchingNextPage } = query
  const stops = useMemo(() => query.data?.pages.flatMap(page => page.content) ?? [], [query.data?.pages])
  const summary = query.data?.pages[0]?.summary

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasNextPage) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && !isFetchingNextPage) void fetchNextPage()
    }, { rootMargin: '600px 0px' })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [fetchNextPage, hasNextPage, isFetchingNextPage, stops.length])

  return <div className="mx-auto w-full max-w-[1800px] px-4 py-6 text-white md:px-6 lg:px-8">
    <header className="mb-5">
      <p className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[.2em] text-orange-200/75"><Globe2 size={14} /> World quiz / Content Studio</p>
      <h1 className="mt-1.5 text-2xl font-semibold tracking-[-.035em] sm:text-3xl">Around the World Quiz</h1>
      <p className="mt-1 text-sm text-white/45">Every stop of the world tour with its generated questions, the right answer and how often players get it.</p>
    </header>

    {summary && <SummaryStrip summary={summary} />}

    <section aria-label="World quiz filters" className="mb-5 grid gap-2 rounded-2xl border border-white/[.08] bg-[#101014] p-3 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1fr)_100px_150px_130px_190px_190px]">
      <label className="relative block">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/30" />
        <span className="sr-only">Search countries</span>
        <input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search countries"
          className="h-9 w-full rounded-lg border border-white/[.09] bg-[#08080c] pl-9 pr-3 text-sm text-white outline-none placeholder:text-white/30 focus:border-orange-200/45" />
      </label>
      <input aria-label="Filter by two-letter country code" value={countryCode}
        onChange={event => setCountryCode(event.target.value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, ISO_LENGTH))}
        placeholder="ISO code" className="h-9 w-full rounded-lg border border-white/[.09] bg-[#08080c] px-3 text-sm text-white outline-none placeholder:text-white/30 focus:border-orange-200/45" />
      <CustomSelect value={status} onChange={value => setStatus(value as NonNullable<QuizStopFilters['status']>)}
        options={statusOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <CustomSelect value={tier} onChange={setTier} options={tierOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <CustomSelect value={kind} onChange={value => setKind(value as NonNullable<QuizStopFilters['kind']>)}
        options={kindOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <div className="relative min-w-0">
        <ArrowUpDown size={13} className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-white/35" />
        <CustomSelect value={sort} onChange={value => setSort(value as NonNullable<QuizStopFilters['sort']>)}
          options={sortOptions} className="[&>button]:w-full [&>button]:justify-between [&>button]:pl-8" />
      </div>
    </section>

    {query.isPending && <div className="grid gap-3 lg:grid-cols-2 2xl:grid-cols-3">{Array.from({ length: PAGE_SKELETONS }, (_, index) => <div key={index} className="h-96 animate-pulse rounded-2xl border border-white/[.06] bg-[#111116]" />)}</div>}
    {query.isError && <div role="alert" className="rounded-2xl border border-rose-300/20 bg-rose-300/[.05] p-8 text-center text-sm text-rose-200">Could not load the world quiz. <button type="button" onClick={() => void query.refetch()} className="underline underline-offset-2">Try again</button></div>}
    {!query.isPending && !query.isError && stops.length === 0 && <div className="rounded-2xl border border-dashed border-white/[.1] bg-[#0e0e12] p-12 text-center text-sm text-white/45">No stops match these filters. Run Operations → Generate world quiz questions if the catalog is empty.</div>}
    {stops.length > 0 && <div className="grid items-start gap-3 lg:grid-cols-2 2xl:grid-cols-3">
      {stops.map(stop => <QuizStopCard key={stop.id} stop={stop} />)}
    </div>}
    {stops.length > 0 && <div ref={sentinelRef} className="flex min-h-20 items-center justify-center py-5 text-xs text-white/40">
      {isFetchingNextPage ? <span className="flex items-center gap-2"><Loader2 size={15} className="animate-spin" /> Loading more stops</span>
        : hasNextPage ? <button type="button" onClick={() => void fetchNextPage()} className="rounded-full border border-white/15 px-4 py-2 text-white/65 hover:border-white/30 hover:text-white">Load more</button> : null}
    </div>}
  </div>
}

function SummaryStrip({ summary }: { summary: QuizContentSummary }) {
  const items: [string, string][] = [
    ['Active stops', summary.activeCountries.toLocaleString()],
    ['Active questions', summary.activeQuestions.toLocaleString()],
    ['Players', summary.players.toLocaleString()],
    ['Countries explored', summary.explored.toLocaleString()],
    ['Answers', summary.answers.toLocaleString()],
    ['Accuracy', percent(summary.correct, summary.answers)],
  ]
  return <section aria-label="World quiz totals" className="mb-5 grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-6">
    {items.map(([label, value]) => <div key={label} className="rounded-xl border border-orange-200/[.08] bg-orange-200/[.025] px-3 py-3">
      <p className="truncate text-base font-bold text-white/85">{value}</p>
      <p className="mt-1 text-[10px] text-white/35">{label}</p>
    </div>)}
  </section>
}

function QuizStopCard({ stop }: { stop: QuizStopPreview }) {
  return <article className="min-w-0 overflow-hidden rounded-2xl border border-white/[.09] bg-[#111115] shadow-[0_14px_35px_rgba(0,0,0,.18)]">
    <div className="flex items-start justify-between gap-3 border-b border-white/[.07] p-4">
      <div className="flex min-w-0 items-center gap-3">
        <img src={flagUrl(stop.countryCode)} alt="" className="h-7 w-10 shrink-0 rounded object-cover ring-1 ring-white/15" />
        <div className="min-w-0">
          <p className="truncate text-[10px] font-bold uppercase tracking-[.13em] text-orange-200/65">{stop.subregion} · {stop.countryCode}</p>
          <h2 className="truncate text-lg font-semibold tracking-tight text-white" title={stop.name}>{stop.name}</h2>
        </div>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1.5">
        <span className={cn('rounded-lg px-2 py-1 text-[10px] font-medium ring-1', SILHOUETTE_TIER_BADGE[stop.tier])}>Tier {stop.tier}</span>
        {!stop.active && <span className="rounded-full border border-white/20 px-2 py-0.5 text-[10px] text-white/60">Retired</span>}
      </div>
    </div>
    <dl className="grid grid-cols-3 gap-1.5 px-4 pt-3 text-center">
      {([
        ['Visitors', stop.visitors.toLocaleString()],
        ['Explored', stop.explored.toLocaleString()],
        ['Neighbours', stop.neighbours.length > 0 ? stop.neighbours.join(' ') : 'Island'],
      ] as [string, string][]).map(([label, value]) => <div key={label} className="min-w-0 rounded-lg border border-white/[.06] bg-black/20 px-1 py-1.5">
        <dt className="text-[9px] uppercase tracking-[.08em] text-white/35">{label}</dt>
        <dd className="truncate text-sm font-semibold text-white/85" title={value}>{value}</dd>
      </div>)}
    </dl>
    <ol className="space-y-2 p-4">
      {stop.questions.map(question => <QuizQuestionRow key={question.id} question={question} />)}
    </ol>
  </article>
}

function QuizQuestionRow({ question }: { question: QuizQuestionPreview }) {
  const Icon = QUIZ_KIND_ICONS[question.kind]
  const visual = question.options.some(option => option.type === 'SHAPE' || option.type === 'FLAG')

  return <li className={cn('rounded-xl border border-white/[.06] bg-black/20 p-3', !question.active && 'opacity-50')}>
    <div className="flex items-center justify-between gap-2 text-[10px] font-bold uppercase tracking-[.1em] text-orange-200/60">
      <span className="flex items-center gap-1.5"><Icon size={13} /> {KIND_LABELS[question.kind]} · Level {question.level}</span>
      <span className="text-white/45">{question.answers.toLocaleString()} answers · {percent(question.correct, question.answers)} right</span>
    </div>
    {question.subject && <p className="mt-1.5 text-xs text-white/55">Subject: <span className="font-semibold text-white/80">{question.subject.names.en}</span></p>}
    <ul className={cn('mt-2 grid gap-1.5', visual ? 'grid-cols-4' : 'grid-cols-2')}>
      {question.options.map((option, index) => {
        const right = index === question.answerIndex
        return <li key={index} className={cn('flex min-w-0 items-center gap-1.5 rounded-lg border px-2 py-1.5 text-xs',
          right ? 'border-emerald-300/40 bg-emerald-300/[.08] text-emerald-50' : 'border-white/[.06] text-white/65')}>
          {option.type === 'SHAPE' && typeof option.shape === 'string'
            ? <QuizShape shapePath={option.shape} tone={right ? 'right' : 'idle'} className="h-12 w-full p-1" />
            : option.type === 'FLAG' || option.type === 'COUNTRY'
              ? <img src={flagUrl(String(option.iso))} alt="" className="h-3.5 w-5 shrink-0 rounded-[2px] object-cover ring-1 ring-white/15" />
              : null}
          {option.type !== 'SHAPE' && option.type !== 'FLAG' && <span className="min-w-0 truncate" title={optionText(option)}>{optionText(option)}</span>}
          {right && <Check size={12} className="ml-auto shrink-0 text-emerald-300" />}
        </li>
      })}
    </ul>
    <p className="mt-2 truncate text-[11px] text-white/40" title={factSummary(question)}>Fact: {factSummary(question)}</p>
  </li>
}
