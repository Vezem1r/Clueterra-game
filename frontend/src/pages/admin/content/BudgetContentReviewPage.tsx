import { useDeferredValue, useEffect, useMemo, useRef, useState } from 'react'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CheckCheck,
  CalendarDays,
  Database,
  Filter,
  FileSearch,
  Layers3,
  Loader2,
  RefreshCw,
  Search,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react'
import {
  budgetContentAdminApi,
  type BudgetCandidateKey,
  type BudgetContentCandidate,
  type BudgetContentFilters,
  type BudgetContentReviewStatus,
  type BudgetContentTab,
} from '@/api/budgetContentAdmin'
import { BudgetCandidateCard } from '@/components/admin/content/BudgetCandidateCard'
import { BudgetCandidateInfoModal } from '@/components/admin/content/BudgetCandidateInfoModal'
import { BudgetGeneratedChallengesPanel } from '@/components/admin/content/BudgetGeneratedChallengesPanel'
import { BudgetGeneratedQuestionsPanel } from '@/components/admin/content/BudgetGeneratedQuestionsPanel'
import { BudgetProjectSeoPagesPanel } from '@/components/admin/content/BudgetProjectSeoPagesPanel'
import { isBudgetContentApproved } from '@/components/admin/content/budgetContentReviewStatus'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { toast } from '@/store/toastStore'
import { extractApiErrorMessage } from '@/utils/errors'
import { cn } from '@/utils/cn'

const tabs: Array<{ value: BudgetContentTab; label: string }> = [
  { value: 'REVIEW', label: 'Review queue' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
]

const tabStatuses: Record<BudgetContentTab, string[]> = {
  REVIEW: ['NEEDS_REVIEW', 'NEEDS_IMAGE', 'DISCOVERED'],
  APPROVED: ['EDITORIAL_APPROVED', 'APPROVED'],
  REJECTED: ['REJECTED'],
}

interface ReviewCommand {
  candidates: BudgetCandidateKey[]
  status: BudgetContentReviewStatus
}

type StudioTab = 'PROJECTS' | 'QUESTIONS' | 'CHALLENGES' | 'SEO'

function candidateKey(candidate: BudgetCandidateKey) {
  return `${candidate.wikidataId}::${candidate.category}`
}

function decodeCandidateKey(key: string): BudgetCandidateKey {
  const [wikidataId, category] = key.split('::')
  return { wikidataId, category }
}

export function BudgetContentReviewPage() {
  const queryClient = useQueryClient()
  const sentinelRef = useRef<HTMLDivElement>(null)
  const [studioTab, setStudioTab] = useState<StudioTab>('PROJECTS')
  const [tab, setTab] = useState<BudgetContentTab>('REVIEW')
  const [category, setCategory] = useState('')
  const [countryCode, setCountryCode] = useState('')
  const [difficulty, setDifficulty] = useState('')
  const [issue, setIssue] = useState('')
  const [minimumQuality, setMinimumQuality] = useState('')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [infoCandidate, setInfoCandidate] = useState<BudgetCandidateKey | null>(null)
  const [rejectConfirmationOpen, setRejectConfirmationOpen] = useState(false)
  const deferredSearch = useDeferredValue(search.trim())

  const seoPagesQuery = useQuery({
    queryKey: ['admin-budget-project-seo-pages'],
    queryFn: () => budgetContentAdminApi.getSeoPages({ size: 1 }),
  })

  const filters: BudgetContentFilters = useMemo(
    () => ({
      tab,
      category: category || undefined,
      countryCode: countryCode || undefined,
      difficulty: difficulty || undefined,
      issue: (issue || undefined) as BudgetContentFilters['issue'],
      minimumQuality: minimumQuality ? Number(minimumQuality) : undefined,
      search: deferredSearch || undefined,
    }),
    [category, countryCode, deferredSearch, difficulty, issue, minimumQuality, tab],
  )

  const facetsQuery = useQuery({
    queryKey: ['admin', 'budget-content', 'facets', tab],
    queryFn: () => budgetContentAdminApi.getFacets(tab),
    staleTime: 30_000,
  })

  const candidatesQuery = useInfiniteQuery({
    queryKey: ['admin', 'budget-content', 'candidates', filters],
    queryFn: ({ pageParam }) => budgetContentAdminApi.getCandidates(filters, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor || undefined,
    enabled: studioTab === 'PROJECTS',
    staleTime: 30_000,
  })

  const candidates = useMemo(
    () => candidatesQuery.data?.pages.flatMap((page) => page.content) || [],
    [candidatesQuery.data?.pages],
  )
  const { fetchNextPage, hasNextPage, isFetchingNextPage } = candidatesQuery

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && hasNextPage && !isFetchingNextPage) {
          void fetchNextPage()
        }
      },
      { rootMargin: '700px 0px' },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [fetchNextPage, hasNextPage, isFetchingNextPage])

  const reviewMutation = useMutation({
    mutationFn: (command: ReviewCommand) =>
      budgetContentAdminApi.reviewSelected(command.candidates, command.status),
    onSuccess: (result) => {
      const updated = result.updatedCount
      toast.success(`${updated} candidate${updated === 1 ? '' : 's'} updated`)
      setSelected(new Set())
      setInfoCandidate(null)
      setRejectConfirmationOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'budget-content'] })
    },
    onError: (error) => {
      toast.error(extractApiErrorMessage(error, 'Failed to update candidates'))
    },
  })

  const selectedKeys = useMemo(() => [...selected].map(decodeCandidateKey), [selected])
  const approvableSelectedKeys = useMemo(
    () => candidates
      .filter((candidate) => selected.has(candidateKey(candidate)))
      .filter((candidate) => !isBudgetContentApproved(candidate.reviewStatus))
      .map(({ wikidataId, category }) => ({ wikidataId, category })),
    [candidates, selected],
  )
  const facets = facetsQuery.data
  const tabCount = (value: BudgetContentTab) => facets?.statuses
    .filter((status) => tabStatuses[value].includes(status.key))
    .reduce((sum, status) => sum + status.count, 0) || 0
  const facetCount = (values: Array<{ key: string; count: number }> | undefined, key: string) =>
    values?.find((value) => value.key === key)?.count || 0
  const activeQuestionCount = facetCount(facets?.questionStatuses, 'ACTIVE')
  const challengeCount = facets?.challengeStatuses.reduce((sum, value) => sum + value.count, 0) || 0

  const toggleCandidate = (candidate: BudgetContentCandidate) => {
    const key = candidateKey(candidate)
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const reviewOne = (candidate: BudgetCandidateKey, status: BudgetContentReviewStatus) => {
    reviewMutation.mutate({ candidates: [candidate], status })
  }

  const reviewSelected = (status: BudgetContentReviewStatus) => {
    const candidatesToReview = status === 'EDITORIAL_APPROVED'
      ? approvableSelectedKeys
      : selectedKeys
    if (candidatesToReview.length === 0) return
    reviewMutation.mutate({ candidates: candidatesToReview, status })
  }

  const resetFilters = () => {
    setCategory('')
    setCountryCode('')
    setDifficulty('')
    setIssue('')
    setMinimumQuality('')
    setSearch('')
    setSelected(new Set())
  }

  const updateFilter = <T extends string,>(setter: (value: T) => void, value: T) => {
    setter(value)
    setSelected(new Set())
  }

  const hasFilters = Boolean(category || countryCode || difficulty || issue || minimumQuality || search)
  const isLoading = candidatesQuery.isLoading
  const isError = candidatesQuery.isError

  return (
    <div className="mx-auto w-full max-w-[1800px] px-4 py-6 md:px-6 lg:px-8">
        <div className="mb-6 grid gap-2 rounded-2xl border border-white/[.08] bg-white/[.018] p-2 sm:grid-cols-2 xl:grid-cols-4">
        {([
          { value: 'PROJECTS' as const, label: 'Projects', count: facets?.total || 0, icon: Database },
          { value: 'QUESTIONS' as const, label: 'Generated questions', count: activeQuestionCount, icon: Sparkles },
          { value: 'CHALLENGES' as const, label: 'Daily challenges', count: challengeCount, icon: CalendarDays },
          { value: 'SEO' as const, label: 'SEO pages', count: seoPagesQuery.data?.totalElements || 0, icon: FileSearch },
        ]).map((item) => {
          const Icon = item.icon
          return (
            <button
              key={item.value}
              type="button"
              onClick={() => setStudioTab(item.value)}
              className={cn(
                'flex items-center justify-between rounded-xl px-4 py-3 text-left transition-colors',
                studioTab === item.value ? 'bg-white text-black' : 'text-white/50 hover:bg-white/[.04] hover:text-white/80',
              )}
            >
              <span className="flex items-center gap-2 text-sm font-semibold"><Icon size={15} /> {item.label}</span>
              <span className={cn('rounded-lg px-2 py-1 text-xs', studioTab === item.value ? 'bg-black/10' : 'bg-white/[.05]')}>{item.count}</span>
            </button>
          )
        })}
      </div>

      {studioTab === 'QUESTIONS' && <BudgetGeneratedQuestionsPanel />}
      {studioTab === 'CHALLENGES' && <BudgetGeneratedChallengesPanel />}
      {studioTab === 'SEO' && <BudgetProjectSeoPagesPanel />}

      {studioTab === 'PROJECTS' && <>

      {facets && (
        <div className="mb-5 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {[
            { label: 'All projects', value: facets.total, tab: null },
            { label: 'Needs review', value: tabCount('REVIEW'), tab: 'REVIEW' as const },
            { label: 'Reviewed', value: tabCount('APPROVED'), tab: 'APPROVED' as const },
            { label: 'Rejected', value: tabCount('REJECTED'), tab: 'REJECTED' as const },
          ].map((summary) => (
            <button
              type="button"
              key={summary.label}
              disabled={!summary.tab}
              onClick={() => summary.tab && updateFilter(setTab, summary.tab)}
              className={cn(
                'rounded-xl border px-3 py-3 text-left transition-colors',
                summary.tab === tab ? 'border-emerald-400/25 bg-emerald-400/[.06]' : 'border-white/[.07] bg-white/[.022] hover:bg-white/[.04]',
              )}
            >
              <p className="truncate text-[9px] font-semibold uppercase tracking-[.11em] text-white/28">{summary.label}</p>
              <p className="mt-1 text-xl font-semibold text-white/80">{summary.value}</p>
            </button>
          ))}
        </div>
      )}

      <div className="sticky top-0 z-30 -mx-2 mb-5 space-y-3 rounded-2xl border border-white/[.08] bg-[#090b10]/95 p-2 shadow-2xl shadow-black/20 backdrop-blur-xl">
        <div className="flex gap-1 overflow-x-auto">
          {tabs.map((item) => (
            <button
              key={item.value}
              type="button"
              onClick={() => updateFilter(setTab, item.value)}
              className={cn(
                'flex h-9 shrink-0 items-center gap-2 rounded-xl px-3 text-xs font-medium transition-colors',
                tab === item.value
                  ? 'bg-white text-black'
                  : 'text-white/45 hover:bg-white/[.05] hover:text-white/75',
              )}
            >
              {item.label}
              <span className={cn('rounded-md px-1.5 py-0.5 text-[9px]', tab === item.value ? 'bg-black/10' : 'bg-white/[.05]')}>
                {tabCount(item.value)}
              </span>
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-2 border-t border-white/[.06] pt-2">
          <div className="relative min-w-[220px] flex-1 xl:max-w-sm">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              value={search}
              onChange={(event) => updateFilter(setSearch, event.target.value)}
              placeholder="Search project or description"
              className="h-9 w-full rounded-lg border border-white/[.08] bg-white/[.035] pl-9 pr-3 text-xs text-white outline-none placeholder:text-white/22 focus:border-emerald-400/35"
            />
          </div>
          <CustomSelect
            value={category}
            onChange={(value) => updateFilter(setCategory, value)}
            size="sm"
            options={[
              { value: '', label: 'All categories' },
              ...(facets?.categories.map((item) => ({ value: item.key, label: `${item.label.replaceAll('_', ' ')} · ${item.count}` })) || []),
            ]}
          />
          <CustomSelect
            value={countryCode}
            onChange={(value) => updateFilter(setCountryCode, value)}
            size="sm"
            options={[
              { value: '', label: 'All countries' },
              ...(facets?.countries.map((item) => ({ value: item.key, label: `${item.label} · ${item.count}` })) || []),
            ]}
          />
          <CustomSelect
            value={difficulty}
            onChange={(value) => updateFilter(setDifficulty, value)}
            size="sm"
            options={[
              { value: '', label: 'All difficulties' },
              ...(facets?.difficulties.map((item) => ({ value: item.key, label: `${item.label} · ${item.count}` })) || []),
            ]}
          />
          <CustomSelect
            value={issue}
            onChange={(value) => updateFilter(setIssue, value)}
            size="sm"
            options={[
              { value: '', label: 'All quality flags' },
              ...(facets?.issues.map((item) => ({ value: item.key, label: `${item.label} · ${item.count}` })) || []),
            ]}
          />
          <CustomSelect
            value={minimumQuality}
            onChange={(value) => updateFilter(setMinimumQuality, value)}
            size="sm"
            options={[
              { value: '', label: 'Any quality' },
              { value: '60', label: 'Quality 60+' },
              { value: '75', label: 'Quality 75+' },
              { value: '90', label: 'Quality 90+' },
            ]}
          />
          {hasFilters && (
            <Button variant="ghost" size="sm" onClick={resetFilters}>
              <X size={13} /> Clear
            </Button>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2 border-t border-white/[.06] pt-2">
          <span className="mr-1 flex items-center gap-1.5 text-xs text-white/38">
            <Layers3 size={13} /> {selected.size} selected
          </span>
          <Button
            variant="ghost"
            size="sm"
            disabled={candidates.length === 0}
            onClick={() => setSelected(new Set(candidates.map(candidateKey)))}
          >
            Select loaded ({candidates.length})
          </Button>
          {selected.size > 0 && (
            <Button variant="ghost" size="sm" onClick={() => setSelected(new Set())}>
              Clear selection
            </Button>
          )}
          <div className="hidden h-5 w-px bg-white/[.08] sm:block" />
          {tab !== 'APPROVED' && (
            <Button
              variant="primary"
              size="sm"
              disabled={approvableSelectedKeys.length === 0 || reviewMutation.isPending}
              onClick={() => reviewSelected('EDITORIAL_APPROVED')}
            >
              <CheckCheck size={14} /> Approve selected
            </Button>
          )}
          <Button
            variant="danger"
            size="sm"
            disabled={selected.size === 0 || reviewMutation.isPending}
            onClick={() => setRejectConfirmationOpen(true)}
          >
            <Trash2 size={14} /> Reject selected
          </Button>
        </div>
      </div>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {Array.from({ length: 12 }).map((_, index) => (
            <div key={index} className="aspect-[.82] animate-pulse rounded-2xl border border-white/[.06] bg-white/[.025]" />
          ))}
        </div>
      )}

      {isError && (
        <div className="rounded-2xl border border-red-400/20 bg-red-400/[.06] p-8 text-center">
          <p className="text-sm text-red-300">Could not load the content review gallery.</p>
          <Button
            variant="ghost"
            size="sm"
            className="mt-3"
            onClick={() => candidatesQuery.refetch()}
          >
            <RefreshCw size={14} /> Retry
          </Button>
        </div>
      )}

      {!isLoading && !isError && candidates.length === 0 && (
        <div className="rounded-2xl border border-dashed border-white/[.1] py-20 text-center">
          <Filter size={24} className="mx-auto mb-3 text-white/20" />
          <p className="text-sm text-white/45">No candidates match this view.</p>
        </div>
      )}

      {candidates.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {candidates.map((candidate) => (
            <BudgetCandidateCard
              key={candidateKey(candidate)}
              candidate={candidate}
              selected={selected.has(candidateKey(candidate))}
              busy={reviewMutation.isPending}
              onToggle={() => toggleCandidate(candidate)}
              onInfo={() => setInfoCandidate(candidate)}
              onReview={(status) => reviewOne(candidate, status)}
            />
          ))}
        </div>
      )}

      <div ref={sentinelRef} className="flex h-28 items-center justify-center">
        {candidatesQuery.isFetchingNextPage && <Loader2 size={20} className="animate-spin text-white/30" />}
        {!candidatesQuery.hasNextPage && candidates.length > 0 && (
          <span className="text-xs text-white/22">All matching candidates loaded</span>
        )}
      </div>

      <BudgetCandidateInfoModal
        key={infoCandidate ? candidateKey(infoCandidate) : 'closed'}
        candidate={infoCandidate}
        onClose={() => setInfoCandidate(null)}
        busy={reviewMutation.isPending}
        onReview={(status) => infoCandidate && reviewOne(infoCandidate, status)}
      />

      <ConfirmDialog
        isOpen={rejectConfirmationOpen}
        onClose={() => setRejectConfirmationOpen(false)}
        onConfirm={() => reviewSelected('REJECTED')}
        title={`Reject ${selected.size} selected candidates?`}
        message="They will leave the active generator pool and remain available in the Rejected tab for recovery."
        confirmLabel="Reject selected"
        variant="danger"
        isLoading={reviewMutation.isPending}
      />
      </>}
    </div>
  )
}
