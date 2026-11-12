import { useDeferredValue, useEffect, useMemo, useRef, useState } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ArrowUpDown, Flag, Loader2, Search } from 'lucide-react'
import {
  adminApi,
  type FlagHintPreview,
  type FlagQuestionFilters,
  type FlagQuestionPreview,
  type FlagQuestionSummary,
} from '@/api/admin'
import type { SilhouetteNames } from '@/api/silhouettes'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { SILHOUETTE_TIER_BADGE } from '@/pages/games/silhouettes/silhouetteGameConstants'

const FLAG_QUESTIONS_QUERY_KEY = 'admin-flag-questions'
const PAGE_SKELETONS = 8
const PERCENT = 100
const STALE_MS = 30_000
const ISO_LENGTH = 2
const ALL_TIERS = 'ALL'
const TIERS = [1, 2, 3, 4, 5]
const NAME_SEPARATOR = ', '
const LETTER_SEPARATOR = ' · '

const sortOptions = [
  { value: 'COUNTRY', label: 'Country A–Z' },
  { value: 'EASIEST', label: 'Easiest tier first' },
  { value: 'HARDEST', label: 'Hardest tier first' },
  { value: 'MOST_PLAYED', label: 'Most played' },
  { value: 'MOST_SKIPPED', label: 'Most skipped' },
  { value: 'MOST_CONFUSED', label: 'Most wrong guesses' },
  { value: 'NEWEST', label: 'Recently generated' },
]

const statusOptions = [
  { value: 'ACTIVE', label: 'Active questions' },
  { value: 'INACTIVE', label: 'Retired' },
  { value: 'ALL', label: 'All questions' },
]

const capitalOptions = [
  { value: 'ALL', label: 'Any capital clue' },
  { value: 'WITH_CAPITAL', label: 'With a verified capital' },
  { value: 'NO_CAPITAL', label: 'Without a capital clue' },
]

const tierOptions = [
  { value: ALL_TIERS, label: 'All tiers' },
  ...TIERS.map(tier => ({ value: String(tier), label: `Tier ${tier}` })),
]

const clueLabel: Record<FlagHintPreview['kind'], string> = {
  REGION: 'Region',
  INITIAL: 'First letter',
  CAPITAL: 'Capital',
}

function clueDetail(hint: FlagHintPreview, question: FlagQuestionPreview) {
  if (hint.kind === 'REGION') return question.subregion
  if (hint.kind === 'INITIAL') {
    return Object.values((hint.payload.letters as Partial<SilhouetteNames> | undefined) ?? {}).join(LETTER_SEPARATOR)
  }
  return ((hint.payload.capitals as SilhouetteNames[] | undefined) ?? []).map(capital => capital.en).join(NAME_SEPARATOR)
}

export function FlagQuestionsPage() {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const [countryCode, setCountryCode] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<NonNullable<FlagQuestionFilters['status']>>('ACTIVE')
  const [tier, setTier] = useState(ALL_TIERS)
  const [sort, setSort] = useState<NonNullable<FlagQuestionFilters['sort']>>('COUNTRY')
  const [capital, setCapital] = useState<NonNullable<FlagQuestionFilters['capital']>>('ALL')
  const deferredSearch = useDeferredValue(search.trim())
  const filters = useMemo(() => ({
    countryCode, search: deferredSearch, status, sort, capital, tier: tier === ALL_TIERS ? undefined : Number(tier),
  }), [countryCode, deferredSearch, status, sort, capital, tier])
  const query = useInfiniteQuery({
    queryKey: [FLAG_QUESTIONS_QUERY_KEY, filters],
    queryFn: ({ pageParam }) => adminApi.getFlagQuestions(filters, pageParam),
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
        <p className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[.2em] text-rose-300/75"><Flag size={14} /> Flags / Content Studio</p>
        <h1 className="mt-1.5 text-2xl font-semibold tracking-[-.035em] sm:text-3xl">Guess the flag</h1>
        <p className="mt-1 text-sm text-white/45">Generated flag questions and clues, with how players do on each flag and which ones they mix up.</p>
      </div>
    </header>

    {summary && <SummaryStrip summary={summary} />}

    <section aria-label="Flag question filters" className="mb-5 grid gap-2 rounded-2xl border border-white/[.08] bg-[#101014] p-3 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1fr)_100px_170px_140px_210px_190px]">
      <label className="relative block">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/30" />
        <span className="sr-only">Search countries</span>
        <input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search countries"
          className="h-9 w-full rounded-lg border border-white/[.09] bg-[#08080c] pl-9 pr-3 text-sm text-white outline-none placeholder:text-white/30 focus:border-rose-300/45" />
      </label>
      <input aria-label="Filter by two-letter country code" value={countryCode}
        onChange={event => setCountryCode(event.target.value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, ISO_LENGTH))}
        placeholder="ISO code" className="h-9 w-full rounded-lg border border-white/[.09] bg-[#08080c] px-3 text-sm text-white outline-none placeholder:text-white/30 focus:border-rose-300/45" />
      <CustomSelect value={status} onChange={value => setStatus(value as NonNullable<FlagQuestionFilters['status']>)}
        options={statusOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <CustomSelect value={tier} onChange={setTier} options={tierOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <CustomSelect value={capital} onChange={value => setCapital(value as NonNullable<FlagQuestionFilters['capital']>)}
        options={capitalOptions} className="[&>button]:w-full [&>button]:justify-between" />
      <div className="relative min-w-0">
        <ArrowUpDown size={13} className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-white/35" />
        <CustomSelect value={sort} onChange={value => setSort(value as NonNullable<FlagQuestionFilters['sort']>)}
          options={sortOptions} className="[&>button]:w-full [&>button]:justify-between [&>button]:pl-8" />
      </div>
    </section>

    {query.isPending && <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">{Array.from({ length: PAGE_SKELETONS }, (_, index) => <div key={index} className="h-72 animate-pulse rounded-2xl border border-white/[.06] bg-[#111116]" />)}</div>}
    {query.isError && <div role="alert" className="rounded-2xl border border-rose-300/20 bg-rose-300/[.05] p-8 text-center text-sm text-rose-200">Could not load flag questions. <button type="button" onClick={() => void query.refetch()} className="underline underline-offset-2">Try again</button></div>}
    {!query.isPending && !query.isError && questions.length === 0 && <div className="rounded-2xl border border-dashed border-white/[.1] bg-[#0e0e12] p-12 text-center text-sm text-white/45">No questions match these filters. Run Operations → Generate flag questions if the catalog is empty.</div>}
    {questions.length > 0 && <div className="grid items-start gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {questions.map(question => <FlagQuestionCard key={question.id} question={question} />)}
    </div>}
    {questions.length > 0 && <div ref={sentinelRef} className="flex min-h-20 items-center justify-center py-5 text-xs text-white/40">
      {isFetchingNextPage ? <span className="flex items-center gap-2"><Loader2 size={15} className="animate-spin" /> Loading more questions</span>
        : hasNextPage ? <button type="button" onClick={() => void fetchNextPage()} className="rounded-full border border-white/15 px-4 py-2 text-white/65 hover:border-white/30 hover:text-white">Load more</button> : null}
    </div>}
  </div>
}

function SummaryStrip({ summary }: { summary: FlagQuestionSummary }) {
  const items: [string, string][] = [
    ['Active questions', summary.activeQuestions.toLocaleString()],
    ['With capital clue', summary.capitalClues.toLocaleString()],
    ['Players', summary.players.toLocaleString()],
    ['Flags raised', summary.solves.toLocaleString()],
    ['Guesses', summary.guesses.toLocaleString()],
  ]
  return <section aria-label="Flag game totals" className="mb-5 grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-5">
    {items.map(([label, value]) => <div key={label} className="rounded-xl border border-rose-200/[.08] bg-rose-200/[.025] px-3 py-3">
      <p className="truncate text-base font-bold text-white/85">{value}</p>
      <p className="mt-1 text-[10px] text-white/35">{label}</p>
    </div>)}
  </section>
}

function FlagQuestionCard({ question }: { question: FlagQuestionPreview }) {
  const solveRate = question.players > 0 ? Math.round((question.solved / question.players) * PERCENT) : null
  return <article className="min-w-0 overflow-hidden rounded-2xl border border-white/[.09] bg-[#111115] shadow-[0_14px_35px_rgba(0,0,0,.18)]">
    <div className="relative flex aspect-[3/2] items-center justify-center bg-[#090b14]">
      <img src={question.flagUrl} alt={`Flag of ${question.name}`} loading="lazy" className="h-[70%] w-auto rounded-md object-contain shadow-[0_12px_30px_rgba(0,0,0,.45)] ring-1 ring-white/15" />
      <span className={`absolute right-3 top-3 rounded-full border px-2.5 py-1 text-[10px] font-semibold backdrop-blur ${question.active ? 'border-emerald-300/25 bg-[#092419]/85 text-emerald-200' : 'border-white/20 bg-black/70 text-white/65'}`}>{question.active ? 'Active' : 'Retired'}</span>
    </div>
    <div className="p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-[10px] font-bold uppercase tracking-[.13em] text-rose-200/65">{question.subregion} · {question.countryCode}</p>
          <h2 className="truncate text-lg font-semibold tracking-tight text-white" title={question.name}>{question.name}</h2>
        </div>
        <span className={`shrink-0 rounded-lg px-2 py-1 text-[10px] font-medium ring-1 ${SILHOUETTE_TIER_BADGE[question.tier]}`}>Tier {question.tier}</span>
      </div>

      <dl className="mt-3 grid grid-cols-4 gap-1.5 text-center">
        {([
          ['Players', question.players.toLocaleString()],
          ['Raised', solveRate == null ? '—' : `${solveRate}%`],
          ['Wrong', question.wrongGuesses.toLocaleString()],
          ['Avg clues', question.averageHints == null ? '—' : question.averageHints.toFixed(1)],
        ] as [string, string][]).map(([label, value]) => <div key={label} className="rounded-lg border border-white/[.06] bg-black/20 px-1 py-1.5">
          <dt className="text-[9px] uppercase tracking-[.08em] text-white/35">{label}</dt>
          <dd className="text-sm font-semibold text-white/85">{value}</dd>
        </div>)}
      </dl>

      <ol className="mt-3 space-y-1.5 border-t border-white/[.07] pt-3">
        {question.hints.map(hint => <li key={hint.position} className="rounded-lg border border-white/[.055] bg-black/20 px-2.5 py-2">
          <div className="flex items-center justify-between gap-2 text-[10px] font-bold uppercase tracking-[.1em] text-rose-200/55">
            <span>{String(hint.position).padStart(ISO_LENGTH, '0')} / {clueLabel[hint.kind]}</span>
            <span className="text-amber-200/60">{hint.costUnits}×</span>
          </div>
          <p className="mt-1 truncate text-xs text-white/70">{clueDetail(hint, question)}</p>
        </li>)}
      </ol>
    </div>
  </article>
}
