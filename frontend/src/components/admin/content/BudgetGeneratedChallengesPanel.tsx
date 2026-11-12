import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Lightbulb,
  Trash2,
} from 'lucide-react'
import {
  budgetContentAdminApi,
  type BudgetChallengePeriod,
  type BudgetGeneratedChallenge,
  type BudgetGeneratedChallengeQuestion,
} from '@/api/budgetContentAdmin'
import { BudgetGeneratedQuestionPreview } from '@/components/admin/content/BudgetGeneratedQuestionPreview'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { toast } from '@/store/toastStore'
import { extractApiErrorMessage } from '@/utils/errors'

export function BudgetGeneratedChallengesPanel() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [period, setPeriod] = useState<BudgetChallengePeriod>('UPCOMING')
  const [deleteTarget, setDeleteTarget] = useState<BudgetGeneratedChallenge | null>(null)
  const challengesQuery = useQuery({
    queryKey: ['admin', 'budget-content', 'challenges', period, page],
    queryFn: () => budgetContentAdminApi.getChallenges(period, page),
    staleTime: 30_000,
  })
  const result = challengesQuery.data
  const deleteMutation = useMutation({
    mutationFn: budgetContentAdminApi.deleteChallenge,
    onSuccess: () => {
      toast.success('Future challenge deleted')
      setDeleteTarget(null)
      if (result?.content.length === 1 && page > 0) setPage((current) => current - 1)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'budget-content', 'challenges'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'budget-content', 'facets'] })
    },
    onError: (error) => {
      toast.error(extractApiErrorMessage(error, 'Failed to delete the future challenge'))
    },
  })

  const selectPeriod = (nextPeriod: BudgetChallengePeriod) => {
    setPeriod(nextPeriod)
    setPage(0)
  }

  return (
    <div className="space-y-4">
      <section className="flex flex-col gap-4 rounded-2xl border border-white/[.08] bg-white/[.018] p-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-emerald-400/10 p-2.5 text-emerald-300">
            <CalendarDays size={17} />
          </div>
          <div>
            <h2 className="text-sm font-semibold text-white/90">Daily challenge schedule</h2>
            <p className="mt-0.5 text-xs text-white/35">
              Inspect the exact five-question lineup, answers and player hints before its play date.
            </p>
          </div>
        </div>
        <div
          role="tablist"
          aria-label="Daily challenge period"
          className="inline-flex self-start rounded-xl border border-white/[.08] bg-black/10 p-1 lg:self-auto"
        >
          <PeriodTab
            active={period === 'UPCOMING'}
            label="Today & upcoming"
            onClick={() => selectPeriod('UPCOMING')}
          />
          <PeriodTab
            active={period === 'PAST'}
            label="Past"
            onClick={() => selectPeriod('PAST')}
          />
        </div>
      </section>

      {challengesQuery.isLoading && <LoadingList />}
      {challengesQuery.isError && <EmptyState text="Could not load generated daily challenges." />}
      {result && result.content.length === 0 && (
        <EmptyState text={period === 'UPCOMING'
          ? 'No challenge exists for today or an upcoming date.'
          : 'No past daily challenges are available yet.'}
        />
      )}

      {result && result.content.length > 0 && (
        <>
          <div className="space-y-3">
            {result.content.map((challenge) => (
              <ChallengeCard
                key={challenge.id}
                challenge={challenge}
                onDelete={() => setDeleteTarget(challenge)}
                deleting={deleteMutation.isPending && deleteMutation.variables === challenge.id}
              />
            ))}
          </div>
          <div className="flex flex-col gap-2 rounded-xl border border-white/[.07] px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
            <span className="text-xs text-white/35">{result.totalElements.toLocaleString()} challenges</span>
            <div className="flex items-center justify-between gap-2 sm:justify-end">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                <ChevronLeft size={14} /> Previous
              </Button>
              <span className="text-xs text-white/40">
                {page + 1} / {Math.max(result.totalPages, 1)}
              </span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={page + 1 >= result.totalPages}
                onClick={() => setPage(page + 1)}
              >
                Next <ChevronRight size={14} />
              </Button>
            </div>
          </div>
        </>
      )}

      <ConfirmDialog
        isOpen={deleteTarget != null}
        onClose={() => !deleteMutation.isPending && setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
        title="Delete future challenge?"
        message={deleteTarget
          ? `${formatChallengeDate(deleteTarget.challengeDate)} will be removed from the schedule. Its generated questions will remain available and can be selected again when challenges are regenerated.`
          : ''}
        confirmLabel="Delete challenge"
        variant="danger"
        isLoading={deleteMutation.isPending}
      />
    </div>
  )
}

function PeriodTab({
  active,
  label,
  onClick,
}: {
  active: boolean
  label: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={`rounded-lg px-3 py-2 text-xs font-semibold transition-colors ${active
        ? 'bg-white/[.1] text-white'
        : 'text-white/35 hover:bg-white/[.04] hover:text-white/65'}`}
    >
      {label}
    </button>
  )
}

function ChallengeCard({
  challenge,
  onDelete,
  deleting,
}: {
  challenge: BudgetGeneratedChallenge
  onDelete: () => void
  deleting: boolean
}) {
  const [expanded, setExpanded] = useState(false)
  const hintCount = challenge.questions.reduce(
    (total, question) => total + (question.reveal.hints?.length || 0),
    0,
  )

  return (
    <article className="min-w-0 overflow-hidden rounded-2xl border border-white/[.08] bg-white/[.018]">
      <header className="flex flex-col gap-4 p-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <div className="shrink-0 rounded-xl bg-emerald-400/10 p-2.5 text-emerald-300">
            <CalendarDays size={17} />
          </div>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-base font-semibold text-white/90">
                {formatChallengeDate(challenge.challengeDate)}
              </h3>
              <span className="rounded-md bg-emerald-400/[.08] px-2 py-1 text-[9px] font-semibold uppercase tracking-[.1em] text-emerald-300/70">
                {challengePeriodLabel(challenge.challengeDate)}
              </span>
            </div>
            <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] uppercase tracking-[.1em] text-white/25">
              <span>{challenge.generationTrigger}</span>
              <span>{challenge.status}</span>
              <span>Challenge #{challenge.id}</span>
              <span>{challenge.questions.length} questions</span>
              <span className="inline-flex items-center gap-1"><Lightbulb size={10} /> {hintCount} hints</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2 self-end lg:self-auto">
          {challenge.deletable ? (
            <Button type="button" variant="danger" size="sm" onClick={onDelete} isLoading={deleting}>
              <Trash2 size={13} /> Delete
            </Button>
          ) : (
            <span className="rounded-lg border border-white/[.06] px-2.5 py-2 text-[10px] text-white/25">
              Protected date
            </span>
          )}
          <Button type="button" variant="secondary" size="sm" onClick={() => setExpanded((current) => !current)}>
            {expanded ? 'Hide details' : 'Inspect challenge'}
            <ChevronDown size={14} className={`transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`} />
          </Button>
        </div>
      </header>

      <div className="grid border-t border-white/[.06] sm:grid-cols-2 xl:grid-cols-5">
        {challenge.questions.map((question) => (
          <QuestionSlot key={question.questionId} question={question} />
        ))}
      </div>

      {expanded && (
        <ol className="space-y-2 border-t border-white/[.06] bg-black/10 p-3">
          {challenge.questions.map((question) => (
            <li key={question.questionId}>
              <BudgetGeneratedQuestionPreview
                question={question}
                position={question.position}
                compact
              />
            </li>
          ))}
        </ol>
      )}
    </article>
  )
}

function QuestionSlot({ question }: { question: BudgetGeneratedChallengeQuestion }) {
  const hints = question.reveal.hints?.length || 0
  return (
    <div className="min-w-0 border-b border-white/[.05] p-3 last:border-b-0 sm:[&:nth-last-child(-n+2)]:border-b-0 xl:border-b-0 xl:border-r xl:last:border-r-0">
      <div className="flex items-center justify-between gap-2">
        <span className="flex h-5 w-5 items-center justify-center rounded-md bg-white/[.06] text-[9px] font-semibold text-white/40">
          {question.position}
        </span>
        <span className="text-[9px] uppercase tracking-[.1em] text-white/25">{question.difficulty}</span>
      </div>
      <p className="mt-2 truncate text-xs font-medium text-white/70" title={question.projectName}>
        {question.projectName}
      </p>
      <div className="mt-1 flex items-center justify-between gap-2 text-[9px] uppercase tracking-[.08em] text-white/25">
        <span className="truncate">{humanize(question.questionType)}</span>
        <span className="inline-flex shrink-0 items-center gap-1"><Lightbulb size={9} /> {hints}</span>
      </div>
    </div>
  )
}

function formatChallengeDate(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

function challengePeriodLabel(value: string) {
  const today = new Date().toISOString().slice(0, 10)
  if (value === today) return 'Today'
  return value > today ? 'Upcoming' : 'Past'
}

function LoadingList() {
  return (
    <div className="space-y-3">
      {[0, 1, 2].map((value) => (
        <div key={value} className="h-48 animate-pulse rounded-2xl border border-white/[.06] bg-white/[.025]" />
      ))}
    </div>
  )
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-white/[.1] py-20 text-center text-sm text-white/40">
      {text}
    </div>
  )
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ')
}
