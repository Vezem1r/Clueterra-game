import { useMemo, useState } from 'react'
import { CheckCircle2, ChevronDown, CircleDashed, Plus, Trash2 } from 'lucide-react'
import {
  type BudgetContentCandidate,
  type BudgetContentCostClaimInput,
  type BudgetContentMetricInput,
  type BudgetContentMilestoneInput,
  type BudgetContentSourceCode,
  type BudgetContentUpdateInput,
} from '@/api/budgetContentAdmin'
import {
  BUDGET_COST_KINDS,
  BUDGET_DIFFICULTIES,
  BUDGET_METRIC_TYPES,
  BUDGET_MILESTONE_TYPES,
  BUDGET_NORMALIZED_METRIC_UNITS,
  BUDGET_REGIONS,
  BUDGET_SOURCE_CODES,
  createBudgetCandidateJson,
  diffBudgetCandidateJson,
  parseBudgetCandidateJson,
  type BudgetJsonChange,
} from '@/components/admin/content/budgetCandidateJson'
import {
  BudgetCandidateEditorModeToolbar,
  BudgetCandidateJsonChangeReview,
  BudgetCandidateJsonEditor,
  type BudgetCandidateEditorMode,
} from '@/components/admin/content/BudgetCandidateJsonEditor'
import { Button } from '@/components/ui/Button'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { toast } from '@/store/toastStore'

interface BudgetCandidateEditFormProps {
  candidate: BudgetContentCandidate
  saving: boolean
  onModeChange?: (mode: BudgetCandidateEditorMode) => void
  onSave: (input: BudgetContentUpdateInput) => void
}

export const BUDGET_CANDIDATE_EDIT_FORM_ID = 'budget-candidate-edit-form'

const inputClass = 'h-9 w-full rounded-lg border border-white/[.09] bg-black/25 px-3 text-xs text-white outline-none placeholder:text-white/20 focus:border-emerald-400/40'
const textareaClass = `${inputClass} h-auto min-h-24 py-2 leading-5`
const editorSelectClass = 'w-full [&>button]:w-full [&>button]:justify-between'
const regionOptions = toSelectOptions(BUDGET_REGIONS)
const difficultyOptions = toSelectOptions(BUDGET_DIFFICULTIES)
const costKindOptions = toSelectOptions(BUDGET_COST_KINDS)
const metricTypeOptions = toSelectOptions(BUDGET_METRIC_TYPES)
const milestoneTypeOptions = toSelectOptions(BUDGET_MILESTONE_TYPES)
const sourceCodeOptions = toSelectOptions(BUDGET_SOURCE_CODES)
const datePrecisionOptions = [
  { value: 'YEAR', label: 'Year only' },
  { value: 'DAY', label: 'Exact date' },
]

export function BudgetCandidateEditForm({
  candidate,
  saving,
  onModeChange,
  onSave,
}: BudgetCandidateEditFormProps) {
  const [form, setForm] = useState<BudgetContentUpdateInput>(() => candidateToInput(candidate))
  const [mode, setMode] = useState<BudgetCandidateEditorMode>('form')
  const [jsonText, setJsonText] = useState('')
  const [jsonErrors, setJsonErrors] = useState<string[]>([])
  const [jsonBaseline, setJsonBaseline] = useState<BudgetContentUpdateInput | null>(null)
  const [comparisonBaseline, setComparisonBaseline] = useState<BudgetContentUpdateInput | null>(null)
  const changes = useMemo(
    () => comparisonBaseline ? diffBudgetCandidateJson(comparisonBaseline, form) : [],
    [comparisonBaseline, form],
  )
  const update = <K extends keyof BudgetContentUpdateInput>(key: K, value: BudgetContentUpdateInput[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
  }
  const openJson = () => {
    const baseline = structuredClone(form)
    setJsonBaseline(baseline)
    setJsonText(createBudgetCandidateJson(candidate, baseline))
    setJsonErrors([])
    setMode('json')
    onModeChange?.('json')
  }
  const applyJson = () => {
    const result = parseBudgetCandidateJson(jsonText)
    setJsonErrors(result.errors)
    if (!result.value) return null

    const baseline = jsonBaseline ?? structuredClone(form)
    const importedChanges = diffBudgetCandidateJson(baseline, result.value)
    setForm(result.value)
    setComparisonBaseline(baseline)
    setMode('form')
    onModeChange?.('form')
    toast.success(importedChanges.length === 0
      ? 'JSON is valid; no project changes detected'
      : `JSON applied: ${importedChanges.length} change${importedChanges.length === 1 ? '' : 's'} ready for review`)
    return result.value
  }
  const copyJson = async () => {
    try {
      await navigator.clipboard.writeText(jsonText)
      toast.success('Project JSONC copied')
    } catch {
      toast.error('Could not copy JSONC to the clipboard')
    }
  }
  const resetJson = () => {
    const baseline = structuredClone(form)
    setJsonBaseline(baseline)
    setJsonText(createBudgetCandidateJson(candidate, baseline))
    setJsonErrors([])
  }

  return (
    <form
      id={BUDGET_CANDIDATE_EDIT_FORM_ID}
      aria-busy={saving}
      className="space-y-8"
      onSubmit={(event) => {
        event.preventDefault()
        if (mode === 'json') {
          applyJson()
          return
        }
        onSave(form)
      }}
    >
      <BudgetCandidateEditorModeToolbar mode={mode} onOpenForm={applyJson} onOpenJson={openJson} />

      {mode === 'json' ? (
        <BudgetCandidateJsonEditor
          value={jsonText}
          errors={jsonErrors}
          onChange={(value) => {
            setJsonText(value)
            setJsonErrors([])
          }}
          onCopy={copyJson}
          onReset={resetJson}
        />
      ) : (
        <>
      {comparisonBaseline && (
        <BudgetCandidateJsonChangeReview
          changes={changes}
          onClear={() => setComparisonBaseline(null)}
        />
      )}

      <EditorSection
        title="Project"
        description="Core facts shown before the player answers."
        changeCount={countProjectChanges(changes)}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Name"><input required maxLength={300} className={inputClass} value={form.name} onChange={(event) => update('name', event.target.value)} /></Field>
          <Field label="Image file on Commons"><input required maxLength={500} className={inputClass} value={form.imageFileTitle} onChange={(event) => update('imageFileTitle', event.target.value)} /></Field>
          <div className="sm:col-span-2"><Field label="Description"><textarea required maxLength={5000} className={textareaClass} value={form.description} onChange={(event) => update('description', event.target.value)} /></Field></div>
          <Field label="Country name"><input required className={inputClass} value={form.countryName} onChange={(event) => update('countryName', event.target.value)} /></Field>
          <Field label="Country code"><input required pattern="[A-Z]{2}" maxLength={2} className={inputClass} value={form.countryCode} onChange={(event) => update('countryCode', event.target.value.toUpperCase())} /></Field>
          <Field label="Region">
            <CustomSelect
              className={editorSelectClass}
              value={form.regionCode}
              onChange={(value) => update('regionCode', value)}
              options={regionOptions}
            />
          </Field>
          <Field label="Completion year"><input type="number" min={1850} max={2200} className={inputClass} value={form.projectYear ?? ''} onChange={(event) => update('projectYear', nullableNumber(event.target.value))} /></Field>
          <Field label="Difficulty">
            <CustomSelect
              className={editorSelectClass}
              value={form.difficulty}
              onChange={(value) => update('difficulty', value)}
              options={difficultyOptions}
            />
          </Field>
        </div>
      </EditorSection>

      <MechanicReadiness form={form} category={candidate.category} />

      <EditorSection
        title="External description source"
        description="Only required when the description is copied or adapted from another source."
        changeCount={countChanges(changes, 'descriptionSource')}
        action={!form.descriptionSource
          ? <AddButton label="Add source" onClick={() => update('descriptionSource', {
            name: '', url: '', licenseLabel: '', licenseUrl: '',
          })} />
          : undefined}
      >
        {form.descriptionSource ? (
          <FactCard title="Reusable description source" onRemove={() => update('descriptionSource', null)}>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Source name"><input required className={inputClass} value={form.descriptionSource.name} onChange={(event) => update('descriptionSource', { ...form.descriptionSource!, name: event.target.value })} /></Field>
              <Field label="Source URL"><input required type="url" className={inputClass} value={form.descriptionSource.url} onChange={(event) => update('descriptionSource', { ...form.descriptionSource!, url: event.target.value })} /></Field>
              <Field label="License"><input required className={inputClass} value={form.descriptionSource.licenseLabel} onChange={(event) => update('descriptionSource', { ...form.descriptionSource!, licenseLabel: event.target.value })} /></Field>
              <Field label="License URL"><input required type="url" className={inputClass} value={form.descriptionSource.licenseUrl} onChange={(event) => update('descriptionSource', { ...form.descriptionSource!, licenseUrl: event.target.value })} /></Field>
            </div>
          </FactCard>
        ) : <EmptyFacts text="Not required for original wording. Add a source only when the description is copied or adapted from reusable licensed text." />}
      </EditorSection>

      <EditorSection
        title="Cost claims"
        description="Original and final costs unlock extra mechanics; one claim can be the playable answer."
        changeCount={countChanges(changes, 'costClaims')}
        action={(
          <AddButton
            label="Add cost"
            onClick={() => update('costClaims', [
              ...form.costClaims,
              {
                ...newCost(candidate, false),
                primary: !form.costClaims.some((claim) => (
                  claim.costKind === 'ORIGINAL_BUDGET' && claim.primary
                )),
              },
            ])}
          />
        )}
      >
        <div className="space-y-3">
          {form.costClaims.map((claim, index) => (
            <CostEditor
              key={`${claim.sourceKey}-${index}`}
              claim={claim}
              removable={form.costClaims.length > 1}
              onChange={(next) => update('costClaims', replaceCost(form.costClaims, index, next))}
              onRemove={() => removeAt(form.costClaims, index, (value) => update('costClaims', value))}
            />
          ))}
        </div>
      </EditorSection>

      <EditorSection
        title="Project metrics"
        description="Length, height, capacity and power enable cost-per-unit questions and richer reveals."
        changeCount={countChanges(changes, 'metrics')}
        action={<AddButton label="Add metric" onClick={() => update('metrics', [...form.metrics, newMetric()])} />}
      >
        {form.metrics.length === 0 ? <EmptyFacts text="No metrics. Add length, height, capacity or another reveal fact." /> : (
          <div className="space-y-3">
            {form.metrics.map((metric, index) => (
              <MetricEditor
                key={`${metric.metricType}-${index}`}
                metric={metric}
                onChange={(next) => update('metrics', replaceMetric(form.metrics, index, next))}
                onRemove={() => removeAt(form.metrics, index, (value) => update('metrics', value))}
              />
            ))}
          </div>
        )}
      </EditorSection>

      <EditorSection
        title="Schedule milestones"
        description="Planned and actual completion dates together enable How Late questions."
        changeCount={countChanges(changes, 'milestones')}
        action={<AddButton label="Add milestone" onClick={() => update('milestones', [...form.milestones, newMilestone()])} />}
      >
        {form.milestones.length === 0 ? <EmptyFacts text="No milestones. Add construction, planned or actual completion dates." /> : (
          <div className="space-y-3">
            {form.milestones.map((milestone, index) => (
              <MilestoneEditor
                key={`${milestone.milestoneType}-${index}`}
                milestone={milestone}
                onChange={(next) => update('milestones', replaceMilestone(form.milestones, index, next))}
                onRemove={() => removeAt(form.milestones, index, (value) => update('milestones', value))}
              />
            ))}
          </div>
        )}
      </EditorSection>
        </>
      )}
    </form>
  )
}

function CostEditor({
  claim,
  removable,
  onChange,
  onRemove,
}: {
  claim: BudgetContentCostClaimInput
  removable: boolean
  onChange: (claim: BudgetContentCostClaimInput) => void
  onRemove: () => void
}) {
  const set = <K extends keyof BudgetContentCostClaimInput>(key: K, value: BudgetContentCostClaimInput[K]) => onChange({ ...claim, [key]: value })

  return (
    <FactCard title={claim.costKind.replaceAll('_', ' ')} onRemove={removable ? onRemove : undefined}>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <Field label="Kind">
          <CustomSelect
            className={editorSelectClass}
            value={claim.costKind}
            onChange={(value) => set('costKind', value)}
            options={costKindOptions}
          />
        </Field>
        <Field label="Source amount">
          <input
            required
            type="number"
            min="0.01"
            step="any"
            className={inputClass}
            value={claim.sourceAmountLow || ''}
            onChange={(event) => {
              const value = Number(event.target.value)
              onChange({
                ...claim,
                sourceAmountLow: value,
                sourceAmountHigh: value,
                unitsPerUsd: rate(value, claim.amountUsdLow),
              })
            }}
          />
        </Field>
        <Field label="Currency">
          <input
            required
            pattern="[A-Z]{3}"
            maxLength={3}
            placeholder="USD"
            className={inputClass}
            value={claim.sourceCurrency}
            onChange={(event) => {
              const sourceCurrency = event.target.value.toUpperCase()
              onChange({
                ...claim,
                sourceCurrency,
                unitsPerUsd: sourceCurrency === 'USD' ? 1 : claim.unitsPerUsd,
                exchangeRateProvider: sourceCurrency === 'USD' ? 'NONE' : 'ECB',
              })
            }}
          />
        </Field>
        <Field label="Amount USD">
          <input
            required
            type="number"
            min="0.01"
            step="any"
            className={inputClass}
            value={claim.amountUsdLow || ''}
            onChange={(event) => {
              const value = Number(event.target.value)
              onChange({
                ...claim,
                amountUsdLow: value,
                amountUsdHigh: value,
                unitsPerUsd: rate(claim.sourceAmountLow, value),
              })
            }}
          />
        </Field>
        <Field label="Cost year">
          <input
            type="number"
            min={1850}
            max={2200}
            className={inputClass}
            value={claim.costYear ?? ''}
            onChange={(event) => set('costYear', nullableNumber(event.target.value))}
          />
        </Field>
      </div>
      <p className="mt-2 text-[11px] leading-4 text-white/30">{costKindHelp(claim.costKind)}</p>
      <div className="mt-3 flex flex-wrap gap-5 text-xs text-white/55">
        <Check label="Primary for this kind" checked={claim.primary} onChange={(value) => set('primary', value)} />
        <Check label="Playable answer" checked={claim.answer} onChange={(value) => set('answer', value)} />
      </div>
      <SourceDetails initiallyOpen={!claim.sourceName}>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Field label="Source registry">
            <CustomSelect
              className={editorSelectClass}
              value={claim.sourceCode}
              onChange={(value) => set('sourceCode', value as BudgetContentCostClaimInput['sourceCode'])}
              options={sourceCodeOptions}
              placeholder="Select source"
            />
          </Field>
          <Field label="Source name">
            <input
              required
              placeholder="Published source title"
              className={inputClass}
              value={claim.sourceName}
              onChange={(event) => set('sourceName', event.target.value)}
            />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Source URL">
              <input
                required
                type="url"
                placeholder="https://…"
                className={inputClass}
                value={claim.sourceUrl}
                onChange={(event) => set('sourceUrl', event.target.value)}
              />
            </Field>
          </div>
          <div className="sm:col-span-2">
            <Field label="Reference URL">
              <input
                type="url"
                placeholder="Optional direct citation"
                className={inputClass}
                value={claim.referenceUrl}
                onChange={(event) => set('referenceUrl', event.target.value)}
              />
            </Field>
          </div>
          <div className="sm:col-span-2">
            <Field label="Normalization note">
              <input
                required
                placeholder="Explain currency conversion and what this amount represents"
                className={inputClass}
                value={claim.normalizationNote}
                onChange={(event) => set('normalizationNote', event.target.value)}
              />
            </Field>
          </div>
        </div>
      </SourceDetails>
    </FactCard>
  )
}

function MetricEditor({
  metric,
  onChange,
  onRemove,
}: {
  metric: BudgetContentMetricInput
  onChange: (metric: BudgetContentMetricInput) => void
  onRemove: () => void
}) {
  const set = <K extends keyof BudgetContentMetricInput>(key: K, value: BudgetContentMetricInput[K]) => onChange({ ...metric, [key]: value })

  return (
    <FactCard title={metric.metricType.replaceAll('_', ' ')} onRemove={onRemove}>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <Field label="Metric">
          <CustomSelect
            className={editorSelectClass}
            value={metric.metricType}
            onChange={(metricType) => {
              onChange({ ...metric, metricType, normalizedUnit: normalizedUnit(metricType) })
            }}
            options={metricTypeOptions}
          />
        </Field>
        <Field label="Normalized value">
          <input required type="number" min="0.01" step="any" className={inputClass} value={metric.normalizedValue || ''} onChange={(event) => set('normalizedValue', Number(event.target.value))} />
        </Field>
        <Field label="Normalized unit">
          <input required className={inputClass} value={metric.normalizedUnit} onChange={(event) => set('normalizedUnit', event.target.value)} />
        </Field>
        <Field label="Source value">
          <input required type="number" min="0.01" step="any" className={inputClass} value={metric.sourceValue || ''} onChange={(event) => set('sourceValue', Number(event.target.value))} />
        </Field>
        <Field label="Source unit">
          <input required className={inputClass} value={metric.sourceUnit} onChange={(event) => set('sourceUnit', event.target.value)} />
        </Field>
      </div>
      <div className="mt-3"><Check label="Primary metric" checked={metric.primary} onChange={(value) => set('primary', value)} /></div>
      <SourceDetails initiallyOpen={!metric.sourceName}>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Field label="Source registry">
            <CustomSelect
              className={editorSelectClass}
              value={metric.sourceCode}
              onChange={(value) => set('sourceCode', value as BudgetContentMetricInput['sourceCode'])}
              options={sourceCodeOptions}
              placeholder="Select source"
            />
          </Field>
          <Field label="Source name"><input required placeholder="Published source title" className={inputClass} value={metric.sourceName} onChange={(event) => set('sourceName', event.target.value)} /></Field>
          <div className="sm:col-span-2"><Field label="Source URL"><input required type="url" placeholder="https://…" className={inputClass} value={metric.sourceUrl} onChange={(event) => set('sourceUrl', event.target.value)} /></Field></div>
        </div>
      </SourceDetails>
    </FactCard>
  )
}

function MilestoneEditor({
  milestone,
  onChange,
  onRemove,
}: {
  milestone: BudgetContentMilestoneInput
  onChange: (milestone: BudgetContentMilestoneInput) => void
  onRemove: () => void
}) {
  const set = <K extends keyof BudgetContentMilestoneInput>(key: K, value: BudgetContentMilestoneInput[K]) => onChange({ ...milestone, [key]: value })

  return (
    <FactCard title={milestone.milestoneType.replaceAll('_', ' ')} onRemove={onRemove}>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Field label="Milestone">
          <CustomSelect
            className={editorSelectClass}
            value={milestone.milestoneType}
            onChange={(value) => set('milestoneType', value)}
            options={milestoneTypeOptions}
          />
        </Field>
        <Field label="Precision">
          <CustomSelect
            className={editorSelectClass}
            value={milestone.datePrecision}
            onChange={(value) => {
              const precision = value as 'DAY' | 'YEAR'
              onChange({
                ...milestone,
                datePrecision: precision,
                milestoneDate: precision === 'DAY' ? milestone.milestoneDate : null,
                milestoneYear: precision === 'YEAR' ? milestone.milestoneYear : null,
              })
            }}
            options={datePrecisionOptions}
          />
        </Field>
        <Field label={milestone.datePrecision === 'DAY' ? 'Date' : 'Year'}>
          {milestone.datePrecision === 'DAY' ? (
            <input required type="date" className={inputClass} value={milestone.milestoneDate || ''} onChange={(event) => set('milestoneDate', event.target.value)} />
          ) : (
            <input required type="number" min={1850} max={2200} className={inputClass} value={milestone.milestoneYear ?? ''} onChange={(event) => set('milestoneYear', nullableNumber(event.target.value))} />
          )}
        </Field>
        <div className="flex items-end pb-2"><Check label="Primary milestone" checked={milestone.primary} onChange={(value) => set('primary', value)} /></div>
      </div>
      <SourceDetails initiallyOpen={!milestone.sourceName}>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Field label="Source registry">
            <CustomSelect
              className={editorSelectClass}
              value={milestone.sourceCode}
              onChange={(value) => set('sourceCode', value as BudgetContentMilestoneInput['sourceCode'])}
              options={sourceCodeOptions}
              placeholder="Select source"
            />
          </Field>
          <Field label="Source name"><input required placeholder="Published source title" className={inputClass} value={milestone.sourceName} onChange={(event) => set('sourceName', event.target.value)} /></Field>
          <div className="sm:col-span-2"><Field label="Source URL"><input required type="url" placeholder="https://…" className={inputClass} value={milestone.sourceUrl} onChange={(event) => set('sourceUrl', event.target.value)} /></Field></div>
        </div>
      </SourceDetails>
    </FactCard>
  )
}

function EditorSection({
  title,
  description,
  action,
  changeCount,
  children,
}: {
  title: string
  description?: string
  action?: React.ReactNode
  changeCount?: number
  children: React.ReactNode
}) {
  return (
    <section>
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold text-white/85">{title}</h3>
            {Boolean(changeCount) && (
              <span className="rounded-full border border-amber-300/15 bg-amber-300/[.05] px-2 py-0.5 text-[8px] font-semibold uppercase tracking-[.08em] text-amber-100/70">
                {changeCount} JSON change{changeCount === 1 ? '' : 's'}
              </span>
            )}
          </div>
          {description && <p className="mt-1 text-[11px] leading-4 text-white/30">{description}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  )
}

function FactCard({ title, onRemove, children }: { title: string; onRemove?: () => void; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-white/[.08] bg-white/[.02] p-4 sm:p-5">
      <div className="mb-4 flex items-center justify-between">
        <p className="text-[10px] font-semibold uppercase tracking-[.12em] text-emerald-300/65">{title}</p>
        {onRemove && (
          <button type="button" aria-label={`Remove ${title}`} onClick={onRemove} className="text-white/25 hover:text-red-300">
            <Trash2 size={14} />
          </button>
        )}
      </div>
      {children}
    </div>
  )
}

function SourceDetails({ initiallyOpen, children }: { initiallyOpen: boolean; children: React.ReactNode }) {
  const [open, setOpen] = useState(initiallyOpen)

  return (
    <details
      open={open}
      onToggle={(event) => setOpen(event.currentTarget.open)}
      onInvalidCapture={(event) => {
        event.currentTarget.open = true
        setOpen(true)
      }}
      className="mt-4 rounded-xl border border-white/[.06] bg-black/15"
    >
      <summary className="flex cursor-pointer list-none items-center justify-between px-3 py-2.5 text-xs font-medium text-white/45 hover:text-white/70">
        Source and normalization
        <ChevronDown size={14} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
      </summary>
      <div className="border-t border-white/[.06] p-3 sm:p-4">{children}</div>
    </details>
  )
}

function MechanicReadiness({
  form,
  category,
}: {
  form: BudgetContentUpdateInput
  category: string
}) {
  const playableCost = form.costClaims.find((claim) => claim.answer && claim.amountUsdLow > 0)
  const originalBudget = form.costClaims.find((claim) => (
    claim.costKind === 'ORIGINAL_BUDGET' && claim.amountUsdLow > 0
  ))
  const planned = form.milestones.find((milestone) => milestone.milestoneType === 'PLANNED_COMPLETION')
  const actual = form.milestones.find((milestone) => [
    'ACTUAL_COMPLETION',
    'OPENING',
    'CURRENT_COMPLETION_ESTIMATE',
  ].includes(milestone.milestoneType))
  const supportedMetrics = costPerUnitMetrics(category)
  const delayed = isLater(actual, planned)
  const hasSupportedMetric = form.metrics.some((metric) => (
    supportedMetrics.includes(metric.metricType) && metric.normalizedValue > 0
  ))
  const overrun = playableCost && originalBudget
    ? ((midpoint(playableCost) - midpoint(originalBudget)) / midpoint(originalBudget)) * 100
    : null
  const mechanics = [
    {
      label: 'How Much',
      ready: Boolean(playableCost),
      note: playableCost ? 'Playable answer selected' : 'Select one cost as the playable answer',
    },
    {
      label: 'Original Budget',
      ready: Boolean(originalBudget),
      note: originalBudget ? 'Original budget is available' : 'Add an Original Budget cost',
    },
    {
      label: 'Budget Overrun',
      ready: overrun != null && overrun >= 10,
      note: overrun == null
        ? 'Needs an original budget and playable final cost'
        : overrun >= 10
          ? `${Math.round(overrun)}% over the original budget`
          : 'The overrun must be at least 10%',
    },
    {
      label: 'How Late',
      ready: delayed,
      note: planned && actual
        ? delayed
          ? 'The actual date is later than planned'
          : 'The actual date is not later than planned'
        : 'Add planned and actual completion milestones',
    },
    {
      label: 'Higher / Lower',
      ready: Boolean(playableCost),
      note: playableCost ? 'Eligible for category pairing' : 'Needs a playable answer cost',
    },
    {
      label: 'Cost per Unit',
      ready: Boolean(playableCost) && hasSupportedMetric,
      note: hasSupportedMetric
        ? 'A category-compatible metric is available'
        : `Add ${supportedMetrics.map(humanize).join(' or ')}`,
    },
  ]

  return (
    <EditorSection
      title="Question mechanics"
      description="A saved editorial version is picked up the next time question generation runs."
    >
      <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
        {mechanics.map((mechanic) => (
          <div
            key={mechanic.label}
            className={`rounded-xl border p-3 ${mechanic.ready
              ? 'border-emerald-400/15 bg-emerald-400/[.045]'
              : 'border-white/[.07] bg-white/[.018]'}`}
          >
            <div className="flex items-center gap-2">
              {mechanic.ready
                ? <CheckCircle2 size={14} className="shrink-0 text-emerald-300" />
                : <CircleDashed size={14} className="shrink-0 text-white/25" />}
              <p className="text-xs font-semibold text-white/75">{mechanic.label}</p>
            </div>
            <p className="mt-1.5 text-[10px] leading-4 text-white/32">{mechanic.note}</p>
          </div>
        ))}
      </div>
    </EditorSection>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="mb-1.5 block text-[9px] font-semibold uppercase tracking-[.11em] text-white/28">{label}</span>{children}</label>
}

function Check({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <label className="flex items-center gap-2"><input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} className="accent-emerald-400" />{label}</label>
}

function AddButton({ label, onClick }: { label: string; onClick: () => void }) {
  return <Button type="button" variant="ghost" size="sm" onClick={onClick}><Plus size={13} /> {label}</Button>
}

function EmptyFacts({ text }: { text: string }) {
  return <div className="rounded-xl border border-dashed border-white/[.09] p-5 text-center text-xs text-white/30">{text}</div>
}

function removeAt<T>(values: T[], index: number, update: (values: T[]) => void) {
  update(values.filter((_, currentIndex) => currentIndex !== index))
}

function replaceCost(
  claims: BudgetContentCostClaimInput[],
  index: number,
  next: BudgetContentCostClaimInput,
) {
  return claims.map((claim, currentIndex) => {
    if (currentIndex === index) return next
    return {
      ...claim,
      primary: next.primary && claim.costKind === next.costKind ? false : claim.primary,
      answer: next.answer ? false : claim.answer,
    }
  })
}

function replaceMetric(
  metrics: BudgetContentMetricInput[],
  index: number,
  next: BudgetContentMetricInput,
) {
  return metrics.map((metric, currentIndex) => currentIndex === index ? next : {
    ...metric,
    primary: next.primary && metric.metricType === next.metricType ? false : metric.primary,
  })
}

function replaceMilestone(
  milestones: BudgetContentMilestoneInput[],
  index: number,
  next: BudgetContentMilestoneInput,
) {
  return milestones.map((milestone, currentIndex) => currentIndex === index ? next : {
    ...milestone,
    primary: next.primary && milestone.milestoneType === next.milestoneType
      ? false
      : milestone.primary,
  })
}

function nullableNumber(value: string) {
  return value === '' ? null : Number(value)
}

function rate(sourceAmount: number, amountUsd: number) {
  return sourceAmount > 0 && amountUsd > 0 ? sourceAmount / amountUsd : 1
}

function normalizedUnit(metricType: string) {
  return BUDGET_NORMALIZED_METRIC_UNITS[metricType] || 'M'
}

function candidateToInput(candidate: BudgetContentCandidate): BudgetContentUpdateInput {
  return {
    name: candidate.name,
    description: candidate.description || '',
    countryCode: candidate.countryCode,
    countryName: candidate.countryName,
    regionCode: candidate.regionCode,
    projectYear: candidate.projectYear,
    difficulty: candidate.difficulty,
    imageFileTitle: candidate.image.fileTitle,
    descriptionSource: candidate.descriptionSource,
    costClaims: candidate.costClaims.length > 0
      ? candidate.costClaims.map(normalizeCost)
      : [newCost(candidate, true)],
    metrics: candidate.metrics.map(normalizeMetric),
    milestones: candidate.milestones.map(normalizeMilestone),
  }
}

function newCost(candidate: BudgetContentCandidate, playable: boolean): BudgetContentCostClaimInput {
  const cost = candidate.cost
  if (!playable) {
    return {
      costKind: 'ORIGINAL_BUDGET',
      sourceAmountLow: 0,
      sourceAmountHigh: 0,
      sourceCurrency: '',
      amountUsdLow: 0,
      amountUsdHigh: 0,
      costYear: null,
      unitsPerUsd: 1,
      exchangeRateDate: new Date().toISOString().slice(0, 10),
      exchangeRateBasis: 'LATEST_REFERENCE_RATE',
      exchangeRateProvider: 'NONE',
      sourceCode: '',
      sourceName: '',
      sourceUrl: '',
      referenceUrl: '',
      normalizationNote: '',
      scopeNote: '',
      sourceKey: 'EDITORIAL_COST',
      evidenceText: '',
      verificationStatus: 'SOURCE_BACKED',
      primary: true,
      answer: false,
    }
  }

  const sourceAmount = cost?.sourceAmount || 0
  const amountUsd = cost?.answerCostUsd || 0
  return {
    costKind: 'REPORTED_COST',
    sourceAmountLow: sourceAmount,
    sourceAmountHigh: sourceAmount,
    sourceCurrency: cost?.sourceCurrency || 'USD',
    amountUsdLow: amountUsd,
    amountUsdHigh: amountUsd,
    costYear: cost?.costYear || candidate.projectYear,
    unitsPerUsd: 1,
    exchangeRateDate: cost?.snapshotAt || new Date().toISOString().slice(0, 10),
    exchangeRateBasis: cost?.costYear ? 'ANNUAL_AVERAGE' : 'LATEST_REFERENCE_RATE',
    exchangeRateProvider: cost?.sourceCurrency === 'USD' ? 'NONE' : 'ECB',
    sourceCode: 'WIKIDATA',
    sourceName: cost?.sourceName || 'Wikidata capital cost (P2130)',
    sourceUrl: cost?.sourceUrl || candidate.sourceUrl,
    referenceUrl: cost?.referenceUrl || '',
    normalizationNote: cost?.normalizationNote || 'Editorially reviewed cost.',
    scopeNote: 'Whole project unless the source specifies otherwise',
    sourceKey: 'P2130',
    evidenceText: '',
    verificationStatus: 'SOURCE_BACKED',
    primary: true,
    answer: true,
  }
}

function normalizeCost(claim: BudgetContentCostClaimInput): BudgetContentCostClaimInput {
  return {
    ...claim,
    sourceCode: claim.sourceCode || inferSourceCode(claim.sourceKey),
    referenceUrl: claim.referenceUrl || '',
    scopeNote: claim.scopeNote || '',
    sourceKey: claim.sourceKey || 'EDITORIAL_COST',
    evidenceText: claim.evidenceText || '',
    verificationStatus: claim.verificationStatus || 'SOURCE_BACKED',
  }
}

function newMetric(): BudgetContentMetricInput {
  return {
    metricType: 'LENGTH',
    normalizedValue: 0,
    normalizedUnit: 'M',
    sourceValue: 0,
    sourceUnit: 'M',
    sourceCode: '',
    sourceName: '',
    sourceUrl: '',
    referenceUrl: '',
    sourceKey: 'EDITORIAL_METRIC',
    evidenceText: '',
    primary: true,
  }
}

function normalizeMetric(metric: BudgetContentMetricInput): BudgetContentMetricInput {
  return {
    ...metric,
    sourceCode: metric.sourceCode || inferSourceCode(metric.sourceKey),
    referenceUrl: metric.referenceUrl || '',
    sourceKey: metric.sourceKey || 'EDITORIAL_METRIC',
    evidenceText: metric.evidenceText || '',
  }
}

function newMilestone(): BudgetContentMilestoneInput {
  return {
    milestoneType: 'ACTUAL_COMPLETION',
    milestoneDate: null,
    milestoneYear: null,
    datePrecision: 'YEAR',
    sourceCode: '',
    sourceName: '',
    sourceUrl: '',
    referenceUrl: '',
    sourceKey: 'EDITORIAL_MILESTONE',
    evidenceText: '',
    primary: true,
  }
}

function normalizeMilestone(milestone: BudgetContentMilestoneInput): BudgetContentMilestoneInput {
  return {
    ...milestone,
    sourceCode: milestone.sourceCode || inferSourceCode(milestone.sourceKey),
    milestoneDate: milestone.milestoneDate || null,
    milestoneYear: milestone.milestoneYear || null,
    referenceUrl: milestone.referenceUrl || '',
    sourceKey: milestone.sourceKey || 'EDITORIAL_MILESTONE',
    evidenceText: milestone.evidenceText || '',
  }
}

function inferSourceCode(sourceKey: string | undefined): BudgetContentSourceCode {
  if (sourceKey?.startsWith('WIKIPEDIA_')) return 'WIKIPEDIA'
  if (sourceKey?.startsWith('NASA_')) return 'NASA'
  if (sourceKey?.startsWith('FTA_')) return 'FTA'
  if (sourceKey?.startsWith('GAO_')) return 'GAO'
  if (sourceKey?.startsWith('TRANSIT_COSTS:')) return 'CLUETERRA_RESEARCH'
  return 'WIKIDATA'
}

function costKindHelp(costKind: string) {
  return {
    REPORTED_COST: 'General reported construction cost; useful as the main How Much answer.',
    ORIGINAL_BUDGET: 'Unlocks Original Budget and, with a final cost, Budget Overrun.',
    APPROVED_BUDGET: 'An officially approved budget revision; shown in the reveal timeline.',
    FINAL_COST: 'Actual completed-project cost; pair it with Original Budget for overrun questions.',
    CURRENT_ESTIMATE: 'Latest estimate for an unfinished project; do not label it as final cost.',
    FINANCING_AMOUNT: 'Funding or loan amount only; it is not automatically a construction cost.',
    CONTRACT_VALUE: 'Value of a specific contract; clarify its scope in the normalization note.',
  }[costKind] || ''
}

function midpoint(claim: BudgetContentCostClaimInput) {
  return (claim.amountUsdLow + claim.amountUsdHigh) / 2
}

function isLater(
  actual: BudgetContentMilestoneInput | undefined,
  planned: BudgetContentMilestoneInput | undefined,
) {
  const actualMonth = milestoneMonth(actual)
  const plannedMonth = milestoneMonth(planned)
  return actualMonth != null && plannedMonth != null && actualMonth > plannedMonth
}

function milestoneMonth(milestone: BudgetContentMilestoneInput | undefined) {
  if (!milestone) return null
  if (milestone.milestoneDate) {
    const [year, month] = milestone.milestoneDate.split('-').map(Number)
    return year * 12 + month
  }
  return milestone.milestoneYear ? milestone.milestoneYear * 12 + 1 : null
}

function costPerUnitMetrics(category: string) {
  return {
    BRIDGE: ['LENGTH'],
    METRO_RAIL: ['LENGTH'],
    STADIUM: ['CAPACITY'],
    SKYSCRAPER: ['FLOOR_AREA', 'HEIGHT'],
    AIRPORT: ['ANNUAL_PASSENGERS'],
    TUNNEL: ['LENGTH'],
    DAM: ['POWER', 'STORAGE_VOLUME'],
    ENERGY: ['POWER'],
    SPACE: ['MASS'],
    ROAD_HIGHWAY: ['LENGTH'],
    PORT: ['CONTAINER_CAPACITY', 'ANNUAL_CARGO_TONNES'],
  }[category] || []
}

function toSelectOptions(values: readonly string[]) {
  return values.map((value) => ({ value, label: selectOptionLabel(value) }))
}

function selectOptionLabel(value: string) {
  return {
    WIKIDATA: 'Wikidata',
    WIKIPEDIA: 'Wikipedia',
    NASA: 'NASA',
  }[value] || value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/^./, (letter) => letter.toUpperCase())
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ')
}

function countChanges(changes: BudgetJsonChange[], prefix: string) {
  return changes.filter((change) => (
    change.path === prefix
    || change.path.startsWith(`${prefix}.`)
    || change.path.startsWith(`${prefix}[`)
  )).length
}

function countProjectChanges(changes: BudgetJsonChange[]) {
  return changes.length
    - countChanges(changes, 'descriptionSource')
    - countChanges(changes, 'costClaims')
    - countChanges(changes, 'metrics')
    - countChanges(changes, 'milestones')
}
