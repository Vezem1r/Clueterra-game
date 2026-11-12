import { useDeferredValue, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Search, Sparkles } from 'lucide-react'
import { budgetContentAdminApi } from '@/api/budgetContentAdmin'
import { BudgetGeneratedQuestionPreview } from '@/components/admin/content/BudgetGeneratedQuestionPreview'
import { Button } from '@/components/ui/Button'
import { CustomSelect } from '@/components/ui/CustomSelect'

const questionTypes = [
  'HOW_MUCH',
  'ORIGINAL_BUDGET',
  'BUDGET_OVERRUN',
  'HOW_LATE',
  'HIGHER_LOWER',
  'COST_PER_UNIT',
  'CURRENT_ESTIMATE',
  'SPENT_SO_FAR',
]

export function BudgetGeneratedQuestionsPanel() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('ACTIVE')
  const [questionType, setQuestionType] = useState('')
  const [search, setSearch] = useState('')
  const deferredSearch = useDeferredValue(search.trim())
  const questionsQuery = useQuery({
    queryKey: ['admin', 'budget-content', 'questions', status, questionType, deferredSearch, page],
    queryFn: () => budgetContentAdminApi.getQuestions({
      status,
      questionType: questionType || undefined,
      search: deferredSearch || undefined,
      page,
      size: 20,
    }),
    staleTime: 30_000,
  })
  const result = questionsQuery.data

  const changeFilter = (setter: (value: string) => void, value: string) => {
    setter(value)
    setPage(0)
  }

  return (
    <div className="space-y-4">
      <section className="overflow-hidden rounded-2xl border border-white/[.08] bg-white/[.018]">
        <div className="flex flex-col gap-3 border-b border-white/[.06] p-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            <div className="rounded-xl bg-violet-400/10 p-2.5 text-violet-300">
              <Sparkles size={17} />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-white/90">Generated question library</h2>
              <p className="mt-0.5 text-xs text-white/35">
                Review the playable prompt, answer, player hints and source evidence together.
              </p>
            </div>
          </div>
          {result && (
            <div className="flex items-baseline gap-2 rounded-xl border border-white/[.06] bg-black/10 px-3 py-2">
              <span className="text-lg font-semibold text-white/85">{result.totalElements.toLocaleString()}</span>
              <span className="text-[10px] uppercase tracking-[.12em] text-white/30">matching variants</span>
            </div>
          )}
        </div>

        <div className="grid gap-2 p-3 md:grid-cols-[minmax(240px,1fr)_180px_210px]">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              value={search}
              onChange={(event) => changeFilter(setSearch, event.target.value)}
              placeholder="Search projects in generated questions"
              className="h-9 w-full rounded-lg border border-white/[.08] bg-white/[.035] pl-9 pr-3 text-xs text-white outline-none placeholder:text-white/22 focus:border-emerald-400/35"
            />
          </div>
          <CustomSelect
            value={status}
            onChange={(value) => changeFilter(setStatus, value)}
            size="sm"
            options={[
              { value: 'ACTIVE', label: 'Active variants' },
              { value: 'RETIRED', label: 'Retired variants' },
              { value: 'ALL', label: 'All revisions' },
            ]}
          />
          <CustomSelect
            value={questionType}
            onChange={(value) => changeFilter(setQuestionType, value)}
            size="sm"
            options={[
              { value: '', label: 'All question mechanics' },
              ...questionTypes.map((type) => ({ value: type, label: humanize(type) })),
            ]}
          />
        </div>
      </section>

      {questionsQuery.isLoading && <LoadingList />}
      {questionsQuery.isError && <EmptyState text="Could not load generated questions." />}
      {result && result.content.length === 0 && <EmptyState text="No generated questions match these filters." />}

      {result && result.content.length > 0 && (
        <>
          <div className="space-y-2">
            {result.content.map((question) => (
              <BudgetGeneratedQuestionPreview key={question.id} question={question} />
            ))}
          </div>
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            totalElements={result.totalElements}
            onPage={setPage}
          />
        </>
      )}
    </div>
  )
}

function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number
  totalPages: number
  totalElements: number
  onPage: (page: number) => void
}) {
  return (
    <div className="flex flex-col gap-2 rounded-xl border border-white/[.07] px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
      <span className="text-xs text-white/35">{totalElements.toLocaleString()} questions</span>
      <div className="flex items-center justify-between gap-2 sm:justify-end">
        <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
          <ChevronLeft size={14} /> Previous
        </Button>
        <span className="text-xs text-white/40">{page + 1} / {Math.max(totalPages, 1)}</span>
        <Button variant="ghost" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next <ChevronRight size={14} />
        </Button>
      </div>
    </div>
  )
}

function LoadingList() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={index} className="h-52 animate-pulse rounded-2xl border border-white/[.06] bg-white/[.025]" />
      ))}
    </div>
  )
}

function EmptyState({ text }: { text: string }) {
  return <div className="rounded-2xl border border-dashed border-white/[.1] py-20 text-center text-sm text-white/40">{text}</div>
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ')
}
