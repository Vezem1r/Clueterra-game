import {
  Braces,
  ClipboardCopy,
  ListTree,
  RotateCcw,
} from 'lucide-react'
import type { BudgetJsonChange } from '@/components/admin/content/budgetCandidateJson'
import { Button } from '@/components/ui/Button'

export type BudgetCandidateEditorMode = 'form' | 'json'

export function BudgetCandidateEditorModeToolbar({
  mode,
  onOpenForm,
  onOpenJson,
}: {
  mode: BudgetCandidateEditorMode
  onOpenForm: () => unknown
  onOpenJson: () => void
}) {
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-white/[.08] bg-white/[.025] p-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <p className="text-xs font-semibold text-white/80">Editorial workspace</p>
        <p className="mt-1 text-[11px] leading-4 text-white/35">
          Edit fields directly or round-trip the same project contract through an AI assistant.
        </p>
      </div>
      <div className="inline-flex w-fit rounded-xl border border-white/[.08] bg-black/25 p-1" role="tablist" aria-label="Editor mode">
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'form'}
          onClick={() => {
            if (mode === 'json') onOpenForm()
          }}
          className={`inline-flex h-8 items-center gap-1.5 rounded-lg px-3 text-xs font-medium transition-colors ${mode === 'form'
            ? 'bg-white/[.1] text-white shadow-sm'
            : 'text-white/40 hover:text-white/70'}`}
        >
          <ListTree size={13} /> Form
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'json'}
          onClick={() => {
            if (mode === 'form') onOpenJson()
          }}
          className={`inline-flex h-8 items-center gap-1.5 rounded-lg px-3 text-xs font-medium transition-colors ${mode === 'json'
            ? 'bg-emerald-400/10 text-emerald-200 shadow-sm'
            : 'text-white/40 hover:text-white/70'}`}
        >
          <Braces size={13} /> JSONC
        </button>
      </div>
    </div>
  )
}

export function BudgetCandidateJsonEditor({
  value,
  errors,
  onChange,
  onCopy,
  onReset,
}: {
  value: string
  errors: string[]
  onChange: (value: string) => void
  onCopy: () => void
  onReset: () => void
}) {
  return (
    <section className="overflow-hidden rounded-2xl border border-emerald-400/15 bg-[#080d0d] shadow-[0_24px_70px_rgba(0,0,0,.22)]">
      <div className="flex flex-col gap-3 border-b border-white/[.07] bg-white/[.025] px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-emerald-200/90">
            <Braces size={14} /> AI-ready project JSONC
          </div>
          <p className="mt-1 text-[10px] leading-4 text-white/30">
            Comments explain the contract. Comments and unknown properties are discarded before the validated API request.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={onReset}>
            <RotateCcw size={13} /> Reset from form
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={onCopy}>
            <ClipboardCopy size={13} /> Copy JSONC
          </Button>
        </div>
      </div>

      {errors.length > 0 && (
        <div className="border-b border-red-400/15 bg-red-400/[.06] px-4 py-3" role="alert">
          <p className="text-xs font-semibold text-red-200">JSONC was not applied</p>
          <ul className="mt-2 max-h-36 list-disc space-y-1 overflow-auto pl-4 text-[11px] leading-4 text-red-200/65">
            {errors.map((error, index) => <li key={`${error}-${index}`}>{error}</li>)}
          </ul>
        </div>
      )}

      <textarea
        aria-label="Budget project JSONC"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        spellCheck={false}
        autoCapitalize="off"
        autoCorrect="off"
        className="min-h-[min(68vh,900px)] w-full resize-y bg-transparent px-4 py-4 font-mono text-[12px] leading-5 text-emerald-50/80 outline-none selection:bg-emerald-400/20 sm:px-5"
      />
    </section>
  )
}

export function BudgetCandidateJsonChangeReview({
  changes,
  onClear,
}: {
  changes: BudgetJsonChange[]
  onClear: () => void
}) {
  const counts = {
    added: changes.filter((change) => change.kind === 'added').length,
    changed: changes.filter((change) => change.kind === 'changed').length,
    removed: changes.filter((change) => change.kind === 'removed').length,
  }

  return (
    <section className="overflow-hidden rounded-2xl border border-amber-300/15 bg-amber-300/[.025]">
      <div className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-xs font-semibold text-white/80">JSON import comparison</p>
            <ChangeCount kind="added" count={counts.added} />
            <ChangeCount kind="changed" count={counts.changed} />
            <ChangeCount kind="removed" count={counts.removed} />
          </div>
          <p className="mt-1 text-[10px] leading-4 text-white/30">
            Compared with the form at the moment JSONC mode was opened. Review before saving.
          </p>
        </div>
        <Button type="button" variant="ghost" size="sm" onClick={onClear}>Clear highlights</Button>
      </div>
      {changes.length === 0 ? (
        <div className="border-t border-white/[.06] px-4 py-4 text-xs text-emerald-200/65">
          No differences detected.
        </div>
      ) : (
        <div className="max-h-72 space-y-1.5 overflow-auto border-t border-white/[.06] p-3 sm:p-4">
          {changes.map((change, index) => {
            const description = describeChange(change.path)
            return (
              <div
                key={`${change.kind}-${change.path}-${index}`}
                className={`rounded-xl border px-3 py-3 ${change.kind === 'added'
                  ? 'border-emerald-400/15 bg-emerald-400/[.045]'
                  : change.kind === 'removed'
                    ? 'border-red-400/15 bg-red-400/[.045]'
                    : 'border-amber-300/15 bg-amber-300/[.04]'}`}
              >
                <div className="flex items-start gap-2">
                  <span className={`mt-0.5 shrink-0 rounded px-1.5 py-0.5 text-[8px] font-bold uppercase tracking-[.1em] ${change.kind === 'added'
                    ? 'bg-emerald-400/10 text-emerald-200'
                    : change.kind === 'removed'
                      ? 'bg-red-400/10 text-red-200'
                      : 'bg-amber-300/10 text-amber-100'}`}
                  >
                    {change.kind}
                  </span>
                  <div className="min-w-0">
                    <p className="text-[11px] font-semibold text-white/70">{description.context}</p>
                    <p className="mt-0.5 text-[10px] text-white/35">{description.field}</p>
                  </div>
                </div>
                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                  {change.kind !== 'added' && (
                    <ChangeValue title="Previous value" value={change.before} />
                  )}
                  {change.kind !== 'removed' && (
                    <ChangeValue title="New value" value={change.after} />
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}

function ChangeCount({ kind, count }: { kind: BudgetJsonChange['kind']; count: number }) {
  if (count === 0) return null
  return (
    <span className={`rounded-full border px-2 py-0.5 text-[9px] font-semibold uppercase tracking-[.08em] ${kind === 'added'
      ? 'border-emerald-400/15 text-emerald-200/80'
      : kind === 'removed'
        ? 'border-red-400/15 text-red-200/80'
        : 'border-amber-300/15 text-amber-100/80'}`}
    >
      {count} {kind}
    </span>
  )
}

function ChangeValue({ title, value }: { title: string; value: unknown }) {
  return (
    <div className="min-w-0 rounded-lg border border-white/[.05] bg-black/15 px-2.5 py-2">
      <p className="mb-1.5 text-[8px] font-semibold uppercase tracking-[.1em] text-white/25">{title}</p>
      {isRecord(value) ? (
        <dl className="grid gap-x-3 gap-y-2 sm:grid-cols-2">
          {Object.entries(value).map(([key, entry]) => (
            <div key={key} className={isLongValue(entry) ? 'sm:col-span-2' : undefined}>
              <dt className="text-[8px] font-semibold uppercase tracking-[.08em] text-white/25">
                {fieldLabel(key)}
              </dt>
              <dd className="mt-0.5 break-words text-[10px] leading-4 text-white/55">
                <ScalarValue value={entry} field={key} />
              </dd>
            </div>
          ))}
        </dl>
      ) : (
        <div className="break-words text-[11px] leading-5 text-white/60">
          <ScalarValue value={value} />
        </div>
      )}
    </div>
  )
}

function ScalarValue({ value, field }: { value: unknown; field?: string }) {
  if (value === null || value === undefined || value === '') return <span className="text-white/25">Not set</span>
  if (typeof value === 'boolean') return <>{value ? 'Yes' : 'No'}</>
  if (typeof value === 'number') return <>{new Intl.NumberFormat('en-US').format(value)}</>
  if (typeof value === 'string' && /^https?:\/\//i.test(value)) {
    return (
      <a href={value} target="_blank" rel="noreferrer" className="text-sky-300/75 underline decoration-sky-300/20 underline-offset-2 hover:text-sky-200">
        {value}
      </a>
    )
  }
  if (typeof value === 'string' && field !== 'sourceKey' && /^[A-Z][A-Z0-9_]*$/.test(value)) {
    return <>{titleCase(value)}</>
  }
  return <>{String(value)}</>
}

function describeChange(path: string) {
  const collection = /^(costClaims|metrics|milestones)\[([^\]]+)](?:\.(.+))?$/.exec(path)
  if (collection) {
    const context = {
      costClaims: 'Cost claim',
      metrics: 'Project metric',
      milestones: 'Schedule milestone',
    }[collection[1]]
    return {
      context: `${context} · ${titleCase(collection[2])}`,
      field: collection[3] ? fieldLabel(collection[3]) : 'Entire entry',
    }
  }
  if (path === 'descriptionSource' || path.startsWith('descriptionSource.')) {
    return {
      context: 'External description source',
      field: path.includes('.') ? fieldLabel(path.split('.').at(-1)!) : 'Entire entry',
    }
  }
  return { context: 'Project', field: fieldLabel(path) }
}

function fieldLabel(field: string) {
  return {
    name: 'Name',
    description: 'Description',
    countryCode: 'Country code',
    countryName: 'Country name',
    regionCode: 'Region',
    projectYear: 'Completion year',
    difficulty: 'Difficulty',
    imageFileTitle: 'Commons image file',
    licenseLabel: 'License',
    licenseUrl: 'License URL',
    costKind: 'Cost kind',
    sourceAmountLow: 'Source amount (minimum)',
    sourceAmountHigh: 'Source amount (maximum)',
    sourceCurrency: 'Currency',
    amountUsdLow: 'USD amount (minimum)',
    amountUsdHigh: 'USD amount (maximum)',
    costYear: 'Cost year',
    unitsPerUsd: 'Currency units per USD',
    exchangeRateDate: 'Exchange-rate date',
    exchangeRateBasis: 'Exchange-rate basis',
    exchangeRateProvider: 'Exchange-rate provider',
    sourceCode: 'Source registry',
    sourceName: 'Source name',
    sourceUrl: 'Source URL',
    referenceUrl: 'Reference URL',
    normalizationNote: 'Normalization note',
    scopeNote: 'Scope note',
    sourceKey: 'Source key',
    evidenceText: 'Evidence',
    verificationStatus: 'Verification status',
    primary: 'Primary',
    answer: 'Playable answer',
    metricType: 'Metric type',
    normalizedValue: 'Normalized value',
    normalizedUnit: 'Normalized unit',
    sourceValue: 'Source value',
    sourceUnit: 'Source unit',
    milestoneType: 'Milestone type',
    milestoneDate: 'Date',
    milestoneYear: 'Year',
    datePrecision: 'Date precision',
  }[field] || titleCase(field.replace(/([a-z])([A-Z])/g, '$1_$2'))
}

function titleCase(value: string) {
  return value
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/(^|\s|#)([a-z])/g, (_, prefix: string, letter: string) => `${prefix}${letter.toUpperCase()}`)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isLongValue(value: unknown) {
  return typeof value === 'string' && (value.length > 70 || /^https?:\/\//i.test(value))
}
