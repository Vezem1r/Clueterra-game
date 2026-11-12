import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ChevronDown, Layers3, Search, SlidersHorizontal, X } from 'lucide-react'

import {
  PROJECT_CATEGORIES,
  type ProjectCategory,
  type ProjectDirectoryFilters as FilterValues,
} from '@/features/projects/projectTypes'
import { useTranslation } from '@/i18n'
import type { TranslationKeys } from '@/i18n/en'

interface ProjectDirectoryFiltersProps {
  value: FilterValues
  resultCount?: number
  loading: boolean
  onChange: (filters: FilterValues) => void
}

interface FilterDraft {
  search: string
  category: ProjectCategory | null
  minimumCost: string
  maximumCost: string
  minimumYear: string
  maximumYear: string
}

type FilterError = 'costFormat' | 'costRange' | 'costOrder' | 'yearFormat' | 'yearOrder'

interface ParsedFilterDraft {
  filters: FilterValues
  error: FilterError | null
}

const EMPTY_FILTERS: FilterValues = {
  search: '',
  category: null,
  minimumCostUsd: null,
  maximumCostUsd: null,
  minimumYear: null,
  maximumYear: null,
}

export function ProjectDirectoryFilters({
  value,
  resultCount,
  loading,
  onChange,
}: ProjectDirectoryFiltersProps) {
  const { t } = useTranslation()
  const copy = t.projectsPage
  const [draft, setDraft] = useState<FilterDraft>(() => toDraft(value))
  const [observedValue, setObservedValue] = useState(value)
  const [expanded, setExpanded] = useState(() => activeAdditionalFilterCount(value) > 0)
  const parsed = useMemo(() => parseDraft(draft), [draft])

  if (observedValue !== value) {
    setObservedValue(value)
    if (parsed.error || !filtersEqual(parsed.filters, value)) setDraft(toDraft(value))
  }

  useEffect(() => {
    if (parsed.error || filtersEqual(parsed.filters, value)) return
    const timeout = window.setTimeout(() => onChange(parsed.filters), 350)
    return () => window.clearTimeout(timeout)
  }, [onChange, parsed, value])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!parsed.error && !filtersEqual(parsed.filters, value)) onChange(parsed.filters)
  }

  const clearAll = () => {
    setDraft(toDraft(EMPTY_FILTERS))
    onChange(EMPTY_FILTERS)
  }

  const activeCount = activeFilterCount(value)

  return (
    <form
      onSubmit={submit}
      className="mt-7 overflow-hidden rounded-[1.5rem] border border-white/[.09] bg-[#0a0b12]/80 shadow-[0_20px_70px_rgba(0,0,0,.2)] backdrop-blur-xl"
      aria-label={copy.filtersLabel}
    >
      <div className="flex flex-col gap-3 p-3 sm:flex-row sm:items-center sm:p-4">
        <label className="group relative min-w-0 flex-1">
          <span className="sr-only">{copy.searchLabel}</span>
          <Search
            size={18}
            className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-white/32 transition group-focus-within:text-amber-200/80"
          />
          <input
            type="search"
            value={draft.search}
            maxLength={100}
            autoComplete="off"
            placeholder={copy.searchPlaceholder}
            onChange={(event) => setDraft((current) => ({ ...current, search: event.target.value }))}
            className="h-12 w-full rounded-2xl border border-white/[.08] bg-white/[.035] pl-11 pr-11 text-sm text-white outline-none transition placeholder:text-white/28 hover:border-white/[.13] focus:border-amber-200/35 focus:bg-white/[.055] focus:ring-4 focus:ring-amber-200/[.05] [&::-webkit-search-cancel-button]:hidden"
          />
          {draft.search && (
            <button
              type="button"
              onClick={() => setDraft((current) => ({ ...current, search: '' }))}
              className="absolute right-2.5 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-full text-white/35 transition hover:bg-white/[.07] hover:text-white"
              aria-label={copy.clearSearch}
            >
              <X size={15} />
            </button>
          )}
        </label>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setExpanded((current) => !current)}
            className={`inline-flex h-12 flex-1 items-center justify-center gap-2 rounded-2xl border px-4 text-sm font-semibold transition sm:flex-none ${expanded || activeCount > 0
              ? 'border-amber-200/25 bg-amber-200/[.09] text-amber-100'
              : 'border-white/[.08] bg-white/[.035] text-white/55 hover:border-white/[.14] hover:text-white'
            }`}
            aria-expanded={expanded}
            aria-controls="project-range-filters"
          >
            <SlidersHorizontal size={16} />
            {copy.filters}
            {activeCount > 0 && (
              <span className="grid h-5 min-w-5 place-items-center rounded-full bg-amber-200 px-1 text-[10px] font-bold text-[#171107]">
                {activeCount}
              </span>
            )}
            <ChevronDown size={14} className={`transition-transform ${expanded ? 'rotate-180' : ''}`} />
          </button>

          {activeCount > 0 && (
            <button
              type="button"
              onClick={clearAll}
              className="h-12 rounded-2xl px-3 text-xs font-semibold text-white/38 transition hover:bg-white/[.04] hover:text-white/75 sm:px-4"
            >
              {copy.clearAll}
            </button>
          )}
        </div>
      </div>

      {expanded && (
        <div id="project-range-filters" className="border-t border-white/[.07] px-4 pb-4 pt-4 sm:px-5 sm:pb-5">
          <CategoryFilter
            value={draft.category}
            onChange={(category) => setDraft((current) => ({ ...current, category }))}
          />

          <div className="mt-5 grid gap-5 border-t border-white/[.06] pt-5 lg:grid-cols-2 lg:gap-8">
            <FilterRange
              label={copy.costLabel}
              hint={copy.costHint}
              minimumLabel={copy.minCost}
              maximumLabel={copy.maxCost}
              rangeTo={copy.rangeTo}
              minimumPlaceholder="$1M"
              maximumPlaceholder="$10B"
              minimumValue={draft.minimumCost}
              maximumValue={draft.maximumCost}
              inputMode="decimal"
              onMinimumChange={(minimumCost) => setDraft((current) => ({ ...current, minimumCost }))}
              onMaximumChange={(maximumCost) => setDraft((current) => ({ ...current, maximumCost }))}
            />
            <FilterRange
              label={copy.yearLabel}
              hint={copy.yearHint}
              minimumLabel={copy.fromYear}
              maximumLabel={copy.toYear}
              rangeTo={copy.rangeTo}
              minimumPlaceholder="1950"
              maximumPlaceholder="2030"
              minimumValue={draft.minimumYear}
              maximumValue={draft.maximumYear}
              inputMode="numeric"
              onMinimumChange={(minimumYear) => setDraft((current) => ({ ...current, minimumYear }))}
              onMaximumChange={(maximumYear) => setDraft((current) => ({ ...current, maximumYear }))}
            />
          </div>

          <div className="mt-4 flex min-h-6 flex-wrap items-center justify-between gap-3 border-t border-white/[.06] pt-4">
            <p className={`text-xs ${parsed.error ? 'text-red-300/85' : 'text-white/34'}`} role="status">
              {parsed.error ? copy.filterErrors[parsed.error] : resultLabel(resultCount, loading, copy)}
            </p>
            <button
              type="submit"
              disabled={Boolean(parsed.error)}
              className="rounded-full bg-white px-4 py-2 text-xs font-bold text-[#0a0b12] transition hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-35"
            >
              {copy.applyNow}
            </button>
          </div>
        </div>
      )}
    </form>
  )
}

function CategoryFilter({
  value,
  onChange,
}: {
  value: ProjectCategory | null
  onChange: (category: ProjectCategory | null) => void
}) {
  const { t } = useTranslation()
  return (
    <fieldset>
      <legend className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[.14em] text-white/62">
        <Layers3 size={14} className="text-amber-200/65" />
        {t.projectsPage.category}
      </legend>
      <div className="mt-3 flex flex-wrap gap-2">
        <CategoryButton selected={value === null} onClick={() => onChange(null)}>
          {t.projectsPage.allCategories}
        </CategoryButton>
        {PROJECT_CATEGORIES.map((category) => (
          <CategoryButton
            key={category}
            selected={value === category}
            onClick={() => onChange(category)}
          >
            {t.budget.categories[categoryKey(category)]}
          </CategoryButton>
        ))}
      </div>
    </fieldset>
  )
}

function CategoryButton({
  selected,
  onClick,
  children,
}: {
  selected: boolean
  onClick: () => void
  children: string
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`rounded-full border px-3.5 py-2 text-xs font-semibold transition ${selected
        ? 'border-amber-200/30 bg-amber-200/[.12] text-amber-100 shadow-[0_0_0_3px_rgba(253,230,138,.035)]'
        : 'border-white/[.08] bg-white/[.025] text-white/45 hover:border-white/[.15] hover:bg-white/[.055] hover:text-white/78'
      }`}
    >
      {children}
    </button>
  )
}

interface FilterRangeProps {
  label: string
  hint: string
  minimumLabel: string
  maximumLabel: string
  rangeTo: string
  minimumPlaceholder: string
  maximumPlaceholder: string
  minimumValue: string
  maximumValue: string
  inputMode: 'decimal' | 'numeric'
  onMinimumChange: (value: string) => void
  onMaximumChange: (value: string) => void
}

function FilterRange({
  label,
  hint,
  minimumLabel,
  maximumLabel,
  rangeTo,
  minimumPlaceholder,
  maximumPlaceholder,
  minimumValue,
  maximumValue,
  inputMode,
  onMinimumChange,
  onMaximumChange,
}: FilterRangeProps) {
  return (
    <fieldset>
      <div className="flex items-baseline justify-between gap-3">
        <legend className="text-xs font-semibold uppercase tracking-[.14em] text-white/62">{label}</legend>
        <span className="text-[10px] text-white/27">{hint}</span>
      </div>
      <div className="mt-2.5 grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2">
        <RangeInput
          label={minimumLabel}
          placeholder={minimumPlaceholder}
          value={minimumValue}
          inputMode={inputMode}
          onChange={onMinimumChange}
        />
        <span className="text-xs text-white/22" aria-hidden="true">{rangeTo}</span>
        <RangeInput
          label={maximumLabel}
          placeholder={maximumPlaceholder}
          value={maximumValue}
          inputMode={inputMode}
          onChange={onMaximumChange}
        />
      </div>
    </fieldset>
  )
}

interface RangeInputProps {
  label: string
  placeholder: string
  value: string
  inputMode: 'decimal' | 'numeric'
  onChange: (value: string) => void
}

function RangeInput({ label, placeholder, value, inputMode, onChange }: RangeInputProps) {
  return (
    <label className="min-w-0">
      <span className="sr-only">{label}</span>
      <input
        type="text"
        inputMode={inputMode}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        className="h-11 w-full min-w-0 rounded-xl border border-white/[.08] bg-black/20 px-3 text-sm tabular-nums text-white outline-none transition placeholder:text-white/22 hover:border-white/[.13] focus:border-emerald-300/30 focus:bg-black/30 focus:ring-4 focus:ring-emerald-300/[.04]"
      />
    </label>
  )
}

function parseDraft(draft: FilterDraft): ParsedFilterDraft {
  const minimumCost = parseMoney(draft.minimumCost)
  const maximumCost = parseMoney(draft.maximumCost)
  const minimumYear = parseYear(draft.minimumYear)
  const maximumYear = parseYear(draft.maximumYear)

  const parseError = minimumCost.error || maximumCost.error || minimumYear.error || maximumYear.error
  if (parseError) return { filters: EMPTY_FILTERS, error: parseError }
  if (minimumCost.value !== null && maximumCost.value !== null && minimumCost.value > maximumCost.value) {
    return { filters: EMPTY_FILTERS, error: 'costOrder' }
  }
  if (minimumYear.value !== null && maximumYear.value !== null && minimumYear.value > maximumYear.value) {
    return { filters: EMPTY_FILTERS, error: 'yearOrder' }
  }

  return {
    filters: {
      search: draft.search.trim().slice(0, 100),
      category: draft.category,
      minimumCostUsd: minimumCost.value,
      maximumCostUsd: maximumCost.value,
      minimumYear: minimumYear.value,
      maximumYear: maximumYear.value,
    },
    error: null,
  }
}

function parseMoney(raw: string): { value: number | null; error: FilterError | null } {
  const input = raw.trim().replaceAll(',', '').replace(/^\$/, '')
  if (!input) return { value: null, error: null }
  const match = input.match(/^(\d+(?:\.\d+)?)\s*([kmbt])?$/i)
  if (!match) return { value: null, error: 'costFormat' }
  const multipliers: Record<string, number> = { k: 1e3, m: 1e6, b: 1e9, t: 1e12 }
  const value = Number(match[1]) * (match[2] ? multipliers[match[2].toLowerCase()] : 1)
  if (!Number.isFinite(value) || value < 0 || value > Number.MAX_SAFE_INTEGER) {
    return { value: null, error: 'costRange' }
  }
  return { value, error: null }
}

function parseYear(raw: string): { value: number | null; error: FilterError | null } {
  const input = raw.trim()
  if (!input) return { value: null, error: null }
  if (!/^\d{1,4}$/.test(input)) return { value: null, error: 'yearFormat' }
  const value = Number(input)
  return value >= 1 && value <= 9999
    ? { value, error: null }
    : { value: null, error: 'yearFormat' }
}

function toDraft(filters: FilterValues): FilterDraft {
  return {
    search: filters.search,
    category: filters.category,
    minimumCost: filters.minimumCostUsd?.toString() ?? '',
    maximumCost: filters.maximumCostUsd?.toString() ?? '',
    minimumYear: filters.minimumYear?.toString() ?? '',
    maximumYear: filters.maximumYear?.toString() ?? '',
  }
}

function filtersEqual(left: FilterValues, right: FilterValues) {
  return left.search === right.search
    && left.category === right.category
    && left.minimumCostUsd === right.minimumCostUsd
    && left.maximumCostUsd === right.maximumCostUsd
    && left.minimumYear === right.minimumYear
    && left.maximumYear === right.maximumYear
}

function activeRangeCount(filters: FilterValues) {
  return [filters.minimumCostUsd, filters.maximumCostUsd, filters.minimumYear, filters.maximumYear]
    .filter((value) => value !== null).length
}

function activeAdditionalFilterCount(filters: FilterValues) {
  return activeRangeCount(filters) + (filters.category ? 1 : 0)
}

function activeFilterCount(filters: FilterValues) {
  return activeAdditionalFilterCount(filters) + (filters.search ? 1 : 0)
}

function resultLabel(resultCount: number | undefined, loading: boolean, copy: TranslationKeys['projectsPage']) {
  if (loading) return copy.resultsUpdating
  if (resultCount === undefined) return copy.resultsAuto
  return resultCount === 1 ? copy.resultsOne : copy.resultsMany.replace('{count}', resultCount.toLocaleString())
}

function categoryKey(category: ProjectCategory) {
  return category.toLowerCase() as Lowercase<ProjectCategory>
}
