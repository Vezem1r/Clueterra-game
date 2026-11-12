import { useDeferredValue, useEffect, useMemo, useRef, useState } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ArrowUpDown, Check, ExternalLink, ImageOff, Loader2, Search, Waves } from 'lucide-react'
import { adminApi, type RiverQuestionFilters, type RiverQuestionPreview } from '@/api/admin'
import { CustomSelect } from '@/components/ui/CustomSelect'

const sortOptions = [
  { value: 'COUNTRY', label: 'Country · river rank' },
  { value: 'NAME', label: 'River name A–Z' },
  { value: 'LONGEST', label: 'Longest first' },
  { value: 'SHORTEST', label: 'Shortest first' },
  { value: 'EASIEST', label: 'Easiest first' },
  { value: 'HARDEST', label: 'Hardest first' },
  { value: 'NEWEST', label: 'Recently updated' },
]

const difficultyLabel = (rank: number) => rank === 1 ? 'Easy' : rank <= 3 ? 'Medium' : 'Hard'

export function RiverQuestionsPage() {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const [countryCode, setCountryCode] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<NonNullable<RiverQuestionFilters['status']>>('ACTIVE')
  const [sort, setSort] = useState<NonNullable<RiverQuestionFilters['sort']>>('COUNTRY')
  const [showBorders, setShowBorders] = useState(false)
  const deferredSearch = useDeferredValue(search.trim())
  const filters = useMemo(() => ({ countryCode, search: deferredSearch, status, sort }),
    [countryCode, deferredSearch, status, sort])
  const query = useInfiniteQuery({
    queryKey: ['admin-river-questions', filters],
    queryFn: ({ pageParam }) => adminApi.getRiverQuestions(filters, pageParam),
    initialPageParam: 0,
    getNextPageParam: page => (page.page + 1) * page.size < page.totalElements ? page.page + 1 : undefined,
    staleTime: 30_000,
  })
  const { fetchNextPage, hasNextPage, isFetchingNextPage } = query
  const questions = useMemo(() => query.data?.pages.flatMap(page => page.content) ?? [], [query.data?.pages])
  const total = query.data?.pages[0]?.totalElements ?? 0

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
        <p className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[.2em] text-sky-300/75"><Waves size={14} /> Rivers / Content Studio</p>
        <h1 className="mt-1.5 text-2xl font-semibold tracking-[-.035em] sm:text-3xl">Question atlas</h1>
        <p className="mt-1 text-sm text-white/45">Maps, answer variants, difficulty and source-backed clues in one place.</p>
      </div>
      <div className="rounded-xl border border-white/[.08] bg-[#0e0e13] px-3 py-2 text-xs text-white/50"><span className="font-semibold text-white/85">{total.toLocaleString()}</span> matching questions</div>
    </header>

    <section aria-label="River question filters" className="mb-5 grid gap-2 rounded-2xl border border-white/[.08] bg-[#101014] p-3 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1fr)_100px_160px_190px_auto]">
      <label className="relative block">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/30" />
        <span className="sr-only">Search rivers or countries</span>
        <input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search rivers or countries"
          className="h-9 w-full rounded-lg border border-white/[.09] bg-[#08080c] pl-9 pr-3 text-sm text-white outline-none placeholder:text-white/30 focus:border-sky-300/45" />
      </label>
      <input aria-label="Filter by two-letter country code" value={countryCode}
        onChange={event => setCountryCode(event.target.value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 2))}
        placeholder="ISO code" className="h-9 w-full rounded-lg border border-white/[.09] bg-[#08080c] px-3 text-sm text-white outline-none placeholder:text-white/30 focus:border-sky-300/45" />
      <CustomSelect value={status} onChange={value => setStatus(value as NonNullable<RiverQuestionFilters['status']>)}
        options={[{ value: 'ACTIVE', label: 'Active questions' }, { value: 'INACTIVE', label: 'Inactive history' }, { value: 'ALL', label: 'All questions' }]}
        className="[&>button]:w-full [&>button]:justify-between" />
      <div className="relative min-w-0">
        <ArrowUpDown size={13} className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-white/35" />
        <CustomSelect value={sort} onChange={value => setSort(value as NonNullable<RiverQuestionFilters['sort']>)}
          options={sortOptions} className="[&>button]:w-full [&>button]:justify-between [&>button]:pl-8" />
      </div>
      <label className={`group flex h-9 cursor-pointer select-none items-center gap-2.5 whitespace-nowrap rounded-lg border px-3 text-xs font-medium transition-colors ${showBorders ? 'border-sky-300/40 bg-[#10232d] text-sky-100' : 'border-white/[.09] bg-[#08080c] text-white/70 hover:border-white/20 hover:text-white'}`}>
        <input type="checkbox" checked={showBorders} onChange={event => setShowBorders(event.target.checked)} className="peer sr-only" />
        <span aria-hidden="true" className={`flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-[5px] border transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-sky-200 ${showBorders ? 'border-sky-200 bg-sky-300 text-[#08202a]' : 'border-white/35 bg-[#131b25] group-hover:border-sky-200/65'}`}>
          {showBorders && <Check size={13} strokeWidth={3} />}
        </span>
        <span>Borders map</span>
      </label>
    </section>

    {query.isPending && <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">{Array.from({ length: 8 }, (_, index) => <div key={index} className="h-80 animate-pulse rounded-2xl border border-white/[.06] bg-[#111116]" />)}</div>}
    {query.isError && <div role="alert" className="rounded-2xl border border-rose-300/20 bg-rose-300/[.05] p-8 text-center text-sm text-rose-200">Could not load river questions. <button type="button" onClick={() => void query.refetch()} className="underline underline-offset-2">Try again</button></div>}
    {!query.isPending && !query.isError && questions.length === 0 && <div className="rounded-2xl border border-dashed border-white/[.1] bg-[#0e0e12] p-12 text-center text-sm text-white/45">No questions match these filters. Generate the catalog in Operations → Games if it is empty.</div>}
    {questions.length > 0 && <div className="grid items-start gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {questions.map(question => <RiverQuestionCard key={question.id} question={question} showBorders={showBorders} />)}
    </div>}
    {questions.length > 0 && <div ref={sentinelRef} className="flex min-h-20 items-center justify-center py-5 text-xs text-white/40">
      {isFetchingNextPage ? <span className="flex items-center gap-2"><Loader2 size={15} className="animate-spin" /> Loading more questions</span>
        : hasNextPage ? <button type="button" onClick={() => void fetchNextPage()} className="rounded-full border border-white/15 px-4 py-2 text-white/65 hover:border-white/30 hover:text-white">Load more · {questions.length} of {total}</button>
          : <span>Showing all {questions.length.toLocaleString()} matching questions</span>}
    </div>}
  </div>
}

function RiverQuestionCard({ question, showBorders }: { question: RiverQuestionPreview; showBorders: boolean }) {
  const imageUrl = showBorders ? question.bordersImageUrl : question.plainImageUrl
  const [failedImage, setFailedImage] = useState('')
  return <article className="min-w-0 overflow-hidden rounded-2xl border border-white/[.09] bg-[#111115] shadow-[0_14px_35px_rgba(0,0,0,.18)]">
    <div className="relative aspect-[3/2] overflow-hidden bg-[#07131c]">
      {failedImage === imageUrl ? <div className="flex h-full items-center justify-center text-xs text-white/40">Map unavailable</div>
        : <img src={imageUrl} alt={`Map of ${question.name}`} loading="lazy" onError={() => setFailedImage(imageUrl)} className="h-full w-full object-cover" />}
      <span className={`absolute right-3 top-3 rounded-full border px-2.5 py-1 text-[10px] font-semibold backdrop-blur ${question.active ? 'border-emerald-300/25 bg-[#092419]/85 text-emerald-200' : 'border-white/20 bg-black/70 text-white/65'}`}>{question.active ? 'Active' : 'Inactive'}</span>
    </div>
    <div className="p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0"><p className="truncate text-[10px] font-bold uppercase tracking-[.13em] text-sky-200/65">{question.countryName} · {question.countryCode}</p><h2 className="mt-0.5 truncate text-lg font-semibold tracking-tight text-white" title={question.name}>{question.name}</h2></div>
        <span className="shrink-0 rounded-lg border border-white/[.08] bg-white/[.04] px-2 py-1 text-[10px] font-medium text-white/60">#{question.rank} · {difficultyLabel(question.rank)}</span>
      </div>
      <p className="mt-1 text-xs text-white/40">{question.lengthKm.toLocaleString()} km · {showBorders ? 'Borders shown' : 'River course'}</p>
      <div className="mt-3 border-t border-white/[.07] pt-3">
        {question.photoUrl
          ? <figure>
            <img src={question.photoUrl} alt={`Answer photo of ${question.name}`} loading="lazy" className="aspect-[2/1] w-full rounded-lg object-cover" />
            <figcaption className="mt-1 truncate text-[11px] text-white/45">Answer photo: {question.photoAuthorName ?? 'Wikimedia Commons'}, {question.photoLicenseLabel}</figcaption>
          </figure>
          : <p className="flex items-center gap-2 text-xs text-white/40"><ImageOff size={13} /> No answer photo</p>}
        {question.wikipediaUrl && <a href={question.wikipediaUrl} target="_blank" rel="noopener noreferrer" className="mt-1 inline-flex items-center gap-1 text-[11px] text-sky-200/70 hover:text-sky-100">Wikipedia <ExternalLink size={11} /></a>}
      </div>
      <div className="mt-3 space-y-1.5 border-t border-white/[.07] pt-3">
        {question.hints.map(hint => <div key={hint.position} className="rounded-lg border border-white/[.055] bg-black/20 px-2.5 py-2">
          <div className="flex items-center justify-between gap-2 text-[10px] font-bold uppercase tracking-[.1em] text-sky-200/55"><span>{String(hint.position).padStart(2, '0')} / {hint.kind}</span><a href={hint.sourceUrl} target="_blank" rel="noopener noreferrer" aria-label={`Source for ${hint.kind}`} className="text-white/35 hover:text-sky-200"><ExternalLink size={12} /></a></div>
          <p className="mt-1 text-xs leading-[1.45] text-white/70">{hint.text}</p>
        </div>)}
      </div>
    </div>
  </article>
}
