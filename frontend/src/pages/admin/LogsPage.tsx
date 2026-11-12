import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Clock,
  CheckCircle, XCircle, Loader2, AlertTriangle,
  Eye, AlertOctagon,
  ChevronRight,
} from 'lucide-react'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { Tabs } from '@/components/ui/Tabs'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { SourceSelect } from '@/components/admin/SourceSelect'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { DatePicker } from '@/components/ui/DatePicker'
import { adminApi } from '@/api/admin'
import { useTranslation } from '@/i18n'
import { useAdminTable } from '@/hooks/useAdminTable'
import type { ColumnDef } from '@/hooks/useAdminTable'
import { AdminTableHeader } from '@/components/admin/AdminTableHeader'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { TableToolbarButtons } from '@/components/admin/TableToolbarButtons'
import type { OperationBatchLogDto, DataSyncLogDto, HintGenerationLogDto, GroupedError, FetchFailureEntry } from '@/types'
import { cn } from '@/utils/cn'

const PAGE_SIZE = 20
const LOG_STALE_TIME = 3_600_000
const FILTER_STALE_TIME = 300_000
const GROUPED_ERRORS_LIMIT = 20

type LogTab = 'operations' | 'sync' | 'hints'

const OPS_COLUMNS: ColumnDef[] = [
  { key: 'status',        label: 'Status',    sortKey: 'status',        defaultWidth: 90,  defaultVisible: true },
  { key: 'operationType', label: 'Operation', sortKey: 'operationType', defaultWidth: 160, defaultVisible: true },
  { key: 'triggerType',   label: 'Trigger',   sortKey: 'triggerType',   defaultWidth: 100, defaultVisible: true },
  { key: 'progress',      label: 'Progress',                            defaultWidth: 120, defaultVisible: true },
  { key: 'failedItems',   label: 'Failed',    sortKey: 'failedItems',   defaultWidth: 60,  defaultVisible: true },
  { key: 'durationMs',    label: 'Duration',  sortKey: 'durationMs',    defaultWidth: 80,  defaultVisible: true },
  { key: 'errorMessage',  label: 'Error',                               defaultWidth: 200, defaultVisible: true },
  { key: 'startedAt',     label: 'Started',   sortKey: 'startedAt',     defaultWidth: 140, defaultVisible: true },
  { key: 'actions',       label: '',                                     defaultWidth: 40,  defaultVisible: true },
]

const SYNC_COLUMNS: ColumnDef[] = [
  { key: 'status',             label: 'Status',    sortKey: 'status',             defaultWidth: 90,  defaultVisible: true },
  { key: 'source',             label: 'Source',    sortKey: 'source',             defaultWidth: 130, defaultVisible: true },
  { key: 'syncType',           label: 'Type',      sortKey: 'syncType',           defaultWidth: 90,  defaultVisible: true },
  { key: 'countryCode',        label: 'Country',                                  defaultWidth: 70,  defaultVisible: true },
  { key: 'countriesProcessed', label: 'Countries', sortKey: 'countriesProcessed', defaultWidth: 80,  defaultVisible: true },
  { key: 'statisticsSaved',    label: 'Stats',     sortKey: 'statisticsSaved',    defaultWidth: 60,  defaultVisible: true },
  { key: 'hintsGenerated',     label: 'Hints',     sortKey: 'hintsGenerated',     defaultWidth: 60,  defaultVisible: true },
  { key: 'hintsDeactivated',   label: 'Deactivated',                              defaultWidth: 80,  defaultVisible: false },
  { key: 'fetchErrors',        label: 'Fetch Err',                                defaultWidth: 70,  defaultVisible: true },
  { key: 'durationMs',         label: 'Duration',  sortKey: 'durationMs',         defaultWidth: 80,  defaultVisible: true },
  { key: 'errorMessage',       label: 'Error',                                    defaultWidth: 180, defaultVisible: true },
  { key: 'startedAt',          label: 'Started',   sortKey: 'startedAt',          defaultWidth: 140, defaultVisible: true },
  { key: 'actions',            label: '',                                          defaultWidth: 40,  defaultVisible: true },
]

const HINT_COLUMNS: ColumnDef[] = [
  { key: 'countryCode',       label: 'Country',  sortKey: 'countryCode',     defaultWidth: 80,  defaultVisible: true },
  { key: 'triggerType',       label: 'Trigger',  sortKey: 'triggerType',     defaultWidth: 100, defaultVisible: true },
  { key: 'hintsCreated',      label: 'Created',  sortKey: 'hintsCreated',    defaultWidth: 70,  defaultVisible: true },
  { key: 'hintsDuplicates',   label: 'Dupes',    sortKey: 'hintsDuplicates', defaultWidth: 60,  defaultVisible: true },
  { key: 'hintsFailed',       label: 'Failed',   sortKey: 'hintsFailed',     defaultWidth: 60,  defaultVisible: true },
  { key: 'avgDifficulty',     label: 'Avg Diff', sortKey: 'avgDifficulty',   defaultWidth: 70,  defaultVisible: true },
  { key: 'avgQualityScore',   label: 'Quality',                              defaultWidth: 70,  defaultVisible: false },
  { key: 'categoriesCovered', label: 'Cats',                                 defaultWidth: 50,  defaultVisible: false },
  { key: 'hintTypesCovered',  label: 'Types',                                defaultWidth: 50,  defaultVisible: false },
  { key: 'durationMs',        label: 'Duration', sortKey: 'durationMs',      defaultWidth: 80,  defaultVisible: true },
  { key: 'errorMessage',      label: 'Error',                                defaultWidth: 180, defaultVisible: true },
  { key: 'startedAt',         label: 'Started',  sortKey: 'startedAt',       defaultWidth: 140, defaultVisible: true },
  { key: 'actions',           label: '',                                      defaultWidth: 40,  defaultVisible: true },
]

const TAB_COLUMNS: Record<LogTab, ColumnDef[]> = {
  operations: OPS_COLUMNS, sync: SYNC_COLUMNS, hints: HINT_COLUMNS,
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const m = Math.floor(ms / 60000); const s = Math.round((ms % 60000) / 1000)
  return `${m}m ${s}s`
}

function formatDate(d: string): string {
  const date = new Date(d)
  const mo = date.toLocaleString('en', { month: 'short' })
  const day = date.getDate()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${mo} ${day}, ${hh}:${mm}`
}

function dateToParam(val: string, end?: boolean): string | undefined {
  if (!val) return undefined
  return end ? `${val}T23:59:59` : `${val}T00:00:00`
}

function parseFetchDetails(raw: string | null): FetchFailureEntry[] {
  if (!raw) return []
  try { return JSON.parse(raw) } catch { return [] }
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { variant: 'success' | 'error' | 'info' | 'warning' | 'neutral'; icon: typeof Clock }> = {
    COMPLETED: { variant: 'success', icon: CheckCircle },
    STARTED:   { variant: 'info',    icon: Loader2 },
    FAILED:    { variant: 'error',   icon: XCircle },
    PARTIAL:   { variant: 'warning', icon: AlertTriangle },
  }
  const cfg = map[status] || { variant: 'neutral' as const, icon: Clock }
  return (
    <Badge variant={cfg.variant} className="text-[10px]">
      <cfg.icon size={10} className={cn('mr-0.5', status === 'STARTED' && 'animate-spin')} />{status}
    </Badge>
  )
}

function EyeBtn({ onClick }: { onClick: () => void }) {
  return (
    <button onClick={onClick} className="p-1 text-white/20 hover:text-white/60 transition-colors cursor-pointer">
      <Eye size={14} />
    </button>
  )
}

function OpsCellValue({ col, log, onDetail }: { col: ColumnDef; log: OperationBatchLogDto; onDetail: () => void }) {
  switch (col.key) {
    case 'status': return <StatusBadge status={log.status} />
    case 'operationType': return <span className="text-xs text-white/70 truncate">{log.operationType}</span>
    case 'triggerType': return <Badge variant="neutral" className="text-[10px]">{log.triggerType}</Badge>
    case 'progress':
      return (
        <div className="flex items-center gap-1.5">
          <div className="flex-1 h-1.5 bg-white/[.06] rounded-full overflow-hidden max-w-[60px]">
            <div className="h-full bg-blue-500 rounded-full transition-all" style={{ width: `${log.totalItems > 0 ? (log.successfulItems / log.totalItems) * 100 : 0}%` }} />
          </div>
          <span className="text-[11px] text-white/35 font-mono">{log.successfulItems}/{log.totalItems}</span>
        </div>
      )
    case 'failedItems':
      return <span className={cn('text-xs font-mono', log.failedItems > 0 ? 'text-red-400' : 'text-white/20')}>{log.failedItems > 0 ? log.failedItems : '—'}</span>
    case 'durationMs': return <span className="text-xs text-white/35 font-mono">{formatDuration(log.durationMs)}</span>
    case 'errorMessage':
      return log.errorMessage ? <span className="text-xs text-red-400/70 truncate" title={log.errorMessage}>{log.errorMessage}</span> : <span className="text-xs text-white/15">—</span>
    case 'startedAt': return <span className="text-xs text-white/30">{formatDate(log.startedAt)}</span>
    case 'actions': return <EyeBtn onClick={onDetail} />
    default: return null
  }
}

function SyncCellValue({ col, log, onDetail }: { col: ColumnDef; log: DataSyncLogDto; onDetail: () => void }) {
  switch (col.key) {
    case 'status': return <StatusBadge status={log.status} />
    case 'source': return <span className="text-xs text-white/70 truncate">{log.source}</span>
    case 'syncType': return <Badge variant="neutral" className="text-[10px]">{log.syncType}</Badge>
    case 'countryCode': return <span className="text-xs text-white/50 font-mono">{log.countryCode || '—'}</span>
    case 'countriesProcessed': return <span className="text-xs text-white/50 font-mono">{log.countriesProcessed}</span>
    case 'statisticsSaved': return <span className="text-xs text-white/50 font-mono">{log.statisticsSaved}</span>
    case 'hintsGenerated': return <span className="text-xs text-white/50 font-mono">{log.hintsGenerated}</span>
    case 'hintsDeactivated': return <span className={cn('text-xs font-mono', log.hintsDeactivated > 0 ? 'text-amber-400/70' : 'text-white/20')}>{log.hintsDeactivated > 0 ? log.hintsDeactivated : '—'}</span>
    case 'fetchErrors': {
      const count = parseFetchDetails(log.fetchDetails).length
      return <span className={cn('text-xs font-mono', count > 0 ? 'text-red-400' : 'text-white/20')}>{count > 0 ? count : '—'}</span>
    }
    case 'durationMs': return <span className="text-xs text-white/35 font-mono">{formatDuration(log.durationMs)}</span>
    case 'errorMessage':
      return log.errorMessage ? <span className="text-xs text-red-400/70 truncate" title={log.errorMessage}>{log.errorMessage}</span> : <span className="text-xs text-white/15">—</span>
    case 'startedAt': return <span className="text-xs text-white/30">{formatDate(log.startedAt)}</span>
    case 'actions': return log.errorStack || log.errorMessage || log.fetchDetails ? <EyeBtn onClick={onDetail} /> : null
    default: return null
  }
}

function HintCellValue({ col, log, onDetail }: { col: ColumnDef; log: HintGenerationLogDto; onDetail: () => void }) {
  switch (col.key) {
    case 'countryCode': return <span className="text-xs text-white/60 font-mono">{log.countryCode || '—'}</span>
    case 'triggerType': return <Badge variant="neutral" className="text-[10px]">{log.triggerType}</Badge>
    case 'hintsCreated':
      return <span className="text-xs font-mono"><span className="text-emerald-400/80">{log.hintsCreated}</span><span className="text-white/20">/{log.hintsAttempted}</span></span>
    case 'hintsDuplicates':
      return <span className={cn('text-xs font-mono', log.hintsDuplicates > 0 ? 'text-amber-400/70' : 'text-white/20')}>{log.hintsDuplicates > 0 ? log.hintsDuplicates : '—'}</span>
    case 'hintsFailed':
      return <span className={cn('text-xs font-mono', log.hintsFailed > 0 ? 'text-red-400' : 'text-white/20')}>{log.hintsFailed > 0 ? log.hintsFailed : '—'}</span>
    case 'avgDifficulty':
      return <span className="text-xs text-white/40 font-mono">{log.avgDifficulty != null ? log.avgDifficulty.toFixed(1) : '—'}</span>
    case 'avgQualityScore':
      return <span className="text-xs text-white/40 font-mono">{log.avgQualityScore != null ? log.avgQualityScore.toFixed(2) : '—'}</span>
    case 'categoriesCovered': return <span className="text-xs text-white/40 font-mono">{log.categoriesCovered}</span>
    case 'hintTypesCovered': return <span className="text-xs text-white/40 font-mono">{log.hintTypesCovered}</span>
    case 'durationMs': return <span className="text-xs text-white/35 font-mono">{formatDuration(log.durationMs)}</span>
    case 'errorMessage':
      return log.errorMessage ? <span className="text-xs text-red-400/70 truncate" title={log.errorMessage}>{log.errorMessage}</span> : <span className="text-xs text-white/15">—</span>
    case 'startedAt': return <span className="text-xs text-white/30">{formatDate(log.startedAt)}</span>
    case 'actions': return (log.details && Object.keys(log.details).length > 0) || log.lensBreakdown || log.errorMessage ? <EyeBtn onClick={onDetail} /> : null
    default: return null
  }
}

function LensBreakdownChart({ data }: { data: Record<string, unknown> }) {
  const entries = Object.entries(data)
    .map(([k, v]) => ({ lens: k.replace(/-/g, ' ').replace(/_/g, ' '), count: Number(v) || 0 }))
    .sort((a, b) => b.count - a.count)
  const max = Math.max(...entries.map(e => e.count), 1)

  return (
    <div className="space-y-2">
      <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">Lens Breakdown</h4>
      <div className="bg-white/[.03] border border-white/[.06] rounded-lg p-3 space-y-1.5">
        {entries.map(e => (
          <div key={e.lens} className="flex items-center gap-2.5">
            <span className="text-[11px] text-white/50 w-[120px] shrink-0 truncate capitalize">{e.lens}</span>
            <div className="flex-1 h-1.5 bg-white/[.04] rounded-full overflow-hidden">
              <div
                className={cn('h-full rounded-full transition-all', e.count > 0 ? 'bg-violet-500/70' : 'bg-transparent')}
                style={{ width: `${(e.count / max) * 100}%` }}
              />
            </div>
            <span className={cn('text-[11px] font-mono w-[40px] text-right shrink-0', e.count > 0 ? 'text-white/60' : 'text-white/15')}>{e.count}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function SummarySection({ data }: { data: Record<string, unknown> }) {
  const scalars: [string, string | number][] = []
  const maps: [string, Record<string, unknown>][] = []
  const arrays: [string, unknown[]][] = []

  for (const [k, v] of Object.entries(data)) {
    if (Array.isArray(v)) arrays.push([k, v])
    else if (v && typeof v === 'object') maps.push([k, v as Record<string, unknown>])
    else scalars.push([k, v as string | number])
  }

  const formatKey = (k: string) => k.replace(/([A-Z])/g, ' $1').replace(/[_-]/g, ' ').trim()

  return (
    <div className="space-y-3">
      {scalars.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          {scalars.map(([k, v]) => (
            <div key={k} className="bg-white/[.03] border border-white/[.06] rounded-lg px-3 py-2">
              <div className="text-sm font-mono font-bold text-white/70">{typeof v === 'number' ? v.toLocaleString() : String(v ?? '—')}</div>
              <div className="text-[10px] text-white/30 uppercase tracking-widest mt-0.5 capitalize">{formatKey(k)}</div>
            </div>
          ))}
        </div>
      )}
      {arrays.map(([k, arr]) => (
        <div key={k} className="space-y-1.5">
          <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium capitalize">{formatKey(k)}</h4>
          {arr.length === 0 ? (
            <p className="text-xs text-white/20">None</p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {arr.map((item, i) => (
                <Badge key={i} variant="neutral" className="text-[10px]">{String(item)}</Badge>
              ))}
            </div>
          )}
        </div>
      ))}
      {maps.map(([k, obj]) => {
        const isLensLike = Object.values(obj).every(v => typeof v === 'number')
        if (isLensLike) return <LensBreakdownChart key={k} data={obj} />
        return (
          <div key={k} className="space-y-1.5">
            <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium capitalize">{formatKey(k)}</h4>
            <div className="bg-white/[.03] border border-white/[.06] rounded-lg p-3 space-y-1">
              {Object.entries(obj).map(([sk, sv]) => (
                <div key={sk} className="flex items-start gap-2 text-xs">
                  <span className="text-white/40 shrink-0 font-mono capitalize">{formatKey(sk)}:</span>
                  <span className="text-white/70 break-all font-mono">{typeof sv === 'object' ? JSON.stringify(sv) : String(sv ?? '—')}</span>
                </div>
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}

interface RejectionEntry {
  lens?: string
  reason?: string
  details?: string
  indicator?: string
  blocked_by?: string
  similarity?: number
  rejected_text?: string
}

const REASON_COLORS: Record<string, string> = {
  HIGH_SIMILARITY: 'text-amber-400 bg-amber-500/10',
  EXACT_DUPLICATE: 'text-red-400 bg-red-500/10',
  LOW_QUALITY: 'text-orange-400 bg-orange-500/10',
}

const VISIBLE_PAGE = 200

function extractRejections(data: Record<string, unknown>): RejectionEntry[] | null {
  if (Array.isArray(data)) return data as unknown as RejectionEntry[]

  for (const val of Object.values(data)) {
    if (Array.isArray(val) && val.length > 0) {
      const first = val[0]
      if (typeof first === 'object' && first !== null && ('lens' in first || 'reason' in first || 'rejected_text' in first)) {
        return val as RejectionEntry[]
      }
    }
  }

  const vals = Object.values(data)
  if (vals.every(v => typeof v === 'object' && v !== null && !Array.isArray(v) &&
    ('lens' in (v as Record<string, unknown>) || 'reason' in (v as Record<string, unknown>)))) {
    return vals as unknown as RejectionEntry[]
  }

  return null
}

function RejectedHintsSection({ data }: { data: Record<string, unknown> }) {
  const [expanded, setExpanded] = useState<number | null>(null)
  const [reasonFilter, setReasonFilter] = useState('')
  const [lensFilter, setLensFilter] = useState('')
  const [search, setSearch] = useState('')
  const [visibleCount, setVisibleCount] = useState(VISIBLE_PAGE)

  const entries = extractRejections(data)
  if (!entries) return <SummarySection data={data} />

  const scalarFields: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(data)) {
    if (!Array.isArray(v)) scalarFields[k] = v
  }

  const reasons = [...new Set(entries.map(e => e.reason).filter(Boolean))] as string[]
  const lenses = [...new Set(entries.map(e => e.lens).filter(Boolean))].sort() as string[]

  const filtered = entries.filter(e => {
    if (reasonFilter && e.reason !== reasonFilter) return false
    if (lensFilter && e.lens !== lensFilter) return false
    if (search) {
      const q = search.toLowerCase()
      return (e.rejected_text?.toLowerCase().includes(q) || e.indicator?.toLowerCase().includes(q) || e.blocked_by?.toLowerCase().includes(q))
    }
    return true
  })

  const visible = filtered.slice(0, visibleCount)

  return (
    <div className="space-y-2.5">
      {Object.keys(scalarFields).length > 0 && <SummarySection data={scalarFields} />}
      <div className="flex items-center justify-between gap-2">
        <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">
          Rejected Hints <span className="text-white/20">({entries.length.toLocaleString()}{filtered.length !== entries.length ? ` / ${filtered.length.toLocaleString()} shown` : ''})</span>
        </h4>
      </div>
      <div className="flex items-center gap-2 flex-wrap">
        <Input
          value={search}
          onChange={e => { setSearch(e.target.value); setVisibleCount(VISIBLE_PAGE) }}
          placeholder="Search text, indicator..."
          className="h-8 text-xs !w-[200px] shrink-0"
        />
        <div className="flex gap-1 flex-wrap">
          <button
            onClick={() => { setReasonFilter(''); setVisibleCount(VISIBLE_PAGE) }}
            className={cn('px-2 py-0.5 rounded text-[10px] transition-colors cursor-pointer', !reasonFilter ? 'bg-white/[.08] text-white/60' : 'text-white/25 hover:text-white/50')}
          >All</button>
          {reasons.map(r => (
            <button
              key={r}
              onClick={() => { setReasonFilter(reasonFilter === r ? '' : r); setVisibleCount(VISIBLE_PAGE) }}
              className={cn('px-2 py-0.5 rounded text-[10px] transition-colors cursor-pointer', reasonFilter === r ? 'bg-white/[.08] text-white/60' : 'text-white/25 hover:text-white/50')}
            >{r.replace(/_/g, ' ')}</button>
          ))}
        </div>
        {lenses.length > 1 && (
          <CustomSelect
            value={lensFilter}
            onChange={v => { setLensFilter(v); setVisibleCount(VISIBLE_PAGE) }}
            options={[{ value: '', label: 'All lenses' }, ...lenses.map(l => ({ value: l, label: l.replace(/-/g, ' ') }))]}
            placeholder="All lenses"
            size="sm"
          />
        )}
      </div>
      <div className="bg-white/[.03] border border-white/[.06] rounded-lg max-h-[55vh] overflow-auto divide-y divide-white/[.04]">
        {visible.length === 0 ? (
          <div className="px-3 py-6 text-[11px] text-white/20 text-center">No matches</div>
        ) : (
          <>
            {visible.map((entry, i) => (
              <button
                key={i}
                onClick={() => setExpanded(expanded === i ? null : i)}
                className="w-full text-left px-3 py-2 hover:bg-white/[.02] transition-colors cursor-pointer"
              >
                <div className="flex items-center gap-2">
                  <ChevronRight size={10} className={cn('text-white/20 shrink-0 transition-transform', expanded === i && 'rotate-90')} />
                  <span className={cn('text-[10px] px-1.5 py-0.5 rounded font-medium shrink-0', REASON_COLORS[entry.reason || ''] || 'text-white/40 bg-white/[.05]')}>
                    {(entry.reason || '').replace(/_/g, ' ')}
                  </span>
                  <span className="text-[11px] text-violet-400/50 font-mono shrink-0">{entry.lens?.replace(/-/g, ' ')}</span>
                  <span className="text-[11px] text-white/30 font-mono shrink-0">{entry.indicator}</span>
                  <span className="text-[11px] text-white/50 truncate flex-1">{entry.rejected_text}</span>
                  {entry.similarity != null && (
                    <span className="text-[10px] text-amber-400/60 font-mono shrink-0">{(entry.similarity * 100).toFixed(0)}%</span>
                  )}
                </div>
                {expanded === i && (
                  <div className="mt-2 ml-5 space-y-1 text-[11px] bg-white/[.02] rounded-lg p-2.5">
                    {entry.details && <div className="flex gap-2"><span className="text-white/30 shrink-0 w-[70px]">Details:</span><span className="text-white/50">{entry.details}</span></div>}
                    {entry.blocked_by && <div className="flex gap-2"><span className="text-white/30 shrink-0 w-[70px]">Blocked by:</span><span className="text-amber-400/50 italic">{entry.blocked_by}</span></div>}
                    {entry.rejected_text && <div className="flex gap-2"><span className="text-white/30 shrink-0 w-[70px]">Full text:</span><span className="text-white/60">{entry.rejected_text}</span></div>}
                  </div>
                )}
              </button>
            ))}
            {filtered.length > visibleCount && (
              <button
                onClick={() => setVisibleCount(v => v + VISIBLE_PAGE)}
                className="w-full px-3 py-2.5 text-[11px] text-accent/60 hover:text-accent hover:bg-white/[.02] transition-colors text-center cursor-pointer"
              >
                Show more ({Math.min(VISIBLE_PAGE, filtered.length - visibleCount).toLocaleString()} of {(filtered.length - visibleCount).toLocaleString()} remaining)
              </button>
            )}
          </>
        )}
      </div>
    </div>
  )
}

interface ImageMaintenanceAnomaly {
  ownerType?: string
  ownerId?: string
  label?: string
  countryCode?: string
  source?: string
  sourceIndicator?: string
  objectPath?: string
  validationStatus?: string
  reason?: string
  metadataChanges?: string[]
  storageMissing?: boolean
  restored?: boolean
  metadataBackfilled?: boolean
  repairFailed?: boolean
  storedMetadata?: Record<string, unknown>
  currentMetadata?: Record<string, unknown>
}

type ImageFindingFilter =
  | 'ALL'
  | 'METADATA_BACKFILLED'
  | 'DOWNLOADED'
  | 'DOWNLOAD_FAILED'
  | 'SOURCE_MISSING'
  | 'RESTRICTED_LICENSE'
  | 'LICENSE_CHANGED'
  | 'ATTRIBUTION_CHANGED'
  | 'SOURCE_URL_CHANGED'
  | 'VALIDATION_FAILED'

type ImageFindingSort = 'SEVERITY' | 'COUNTRY' | 'SOURCE' | 'OWNER'

const IMAGE_FINDING_FILTERS: { value: ImageFindingFilter; label: string }[] = [
  { value: 'ALL', label: 'All findings' },
  { value: 'METADATA_BACKFILLED', label: 'Metadata backfilled' },
  { value: 'DOWNLOADED', label: 'Downloaded' },
  { value: 'DOWNLOAD_FAILED', label: 'Download failed' },
  { value: 'SOURCE_MISSING', label: 'Source missing' },
  { value: 'RESTRICTED_LICENSE', label: 'Restricted license' },
  { value: 'LICENSE_CHANGED', label: 'License changed' },
  { value: 'ATTRIBUTION_CHANGED', label: 'Attribution changed' },
  { value: 'SOURCE_URL_CHANGED', label: 'Source URL changed' },
  { value: 'VALIDATION_FAILED', label: 'Validation failed' },
]

const IMAGE_FINDING_SORTS: { value: ImageFindingSort; label: string }[] = [
  { value: 'SEVERITY', label: 'Severity' },
  { value: 'COUNTRY', label: 'Country A-Z' },
  { value: 'SOURCE', label: 'Source A-Z' },
  { value: 'OWNER', label: 'Owner ID' },
]

function imageFindingSeverity(item: ImageMaintenanceAnomaly): number {
  if (item.repairFailed || item.validationStatus === 'FETCH_FAILED') return 0
  if (item.validationStatus === 'SOURCE_MISSING' || item.validationStatus === 'RESTRICTED_LICENSE') return 1
  if (item.metadataChanges?.length) return 2
  if (item.restored) return 3
  if (item.metadataBackfilled) return 4
  return 5
}

function imageFindingLabel(item: ImageMaintenanceAnomaly): string {
  if (item.repairFailed) return 'DOWNLOAD FAILED'
  if (item.validationStatus === 'SOURCE_MISSING') return 'SOURCE MISSING'
  if (item.validationStatus === 'RESTRICTED_LICENSE') return 'RESTRICTED LICENSE'
  if (item.validationStatus === 'FETCH_FAILED') return 'VALIDATION FAILED'
  if (item.metadataChanges?.length) return 'METADATA CHANGED'
  if (item.restored) return 'DOWNLOADED'
  if (item.metadataBackfilled) return 'METADATA BACKFILLED'
  return item.validationStatus?.replace(/_/g, ' ') ?? 'AUDIT RESULT'
}

function imageFindingBadgeVariant(item: ImageMaintenanceAnomaly): 'success' | 'warning' | 'error' | 'info' | 'neutral' {
  if (item.repairFailed
    || item.validationStatus === 'SOURCE_MISSING'
    || item.validationStatus === 'RESTRICTED_LICENSE'
    || item.validationStatus === 'FETCH_FAILED') return 'error'
  if (item.metadataChanges?.length) return 'warning'
  if (item.restored) return 'success'
  if (item.metadataBackfilled) return 'info'
  return 'neutral'
}

function matchesImageFindingFilter(item: ImageMaintenanceAnomaly, filter: ImageFindingFilter): boolean {
  switch (filter) {
    case 'ALL': return true
    case 'METADATA_BACKFILLED': return item.metadataBackfilled === true
    case 'DOWNLOADED': return item.restored === true
    case 'DOWNLOAD_FAILED': return item.repairFailed === true
    case 'SOURCE_MISSING': return item.validationStatus === 'SOURCE_MISSING'
    case 'RESTRICTED_LICENSE': return item.validationStatus === 'RESTRICTED_LICENSE'
    case 'LICENSE_CHANGED': return item.metadataChanges?.includes('LICENSE') === true
    case 'ATTRIBUTION_CHANGED': return item.metadataChanges?.includes('ATTRIBUTION') === true
    case 'SOURCE_URL_CHANGED': return item.metadataChanges?.includes('SOURCE_URL') === true
    case 'VALIDATION_FAILED': return item.validationStatus === 'FETCH_FAILED'
  }
}

function summaryNumber(summary: Record<string, unknown>, key: string): number {
  const value = summary[key]
  return typeof value === 'number' ? value : Number(value) || 0
}

function ImageMaintenanceDetailModal({ log, onClose }: { log: OperationBatchLogDto; onClose: () => void }) {
  const summary = log.summary ?? {}
  const mode = String(summary.mode ?? '')
  const anomalies = Array.isArray(summary.anomalies)
    ? summary.anomalies as ImageMaintenanceAnomaly[]
    : []
  const findings = Array.isArray(summary.findings)
    ? summary.findings as ImageMaintenanceAnomaly[]
    : anomalies
  const [findingFilter, setFindingFilter] = useState<ImageFindingFilter>('ALL')
  const [findingSort, setFindingSort] = useState<ImageFindingSort>('SEVERITY')
  const [findingSource, setFindingSource] = useState('')
  const [findingSearch, setFindingSearch] = useState('')
  const findingSources = [...new Set(findings.map(item => item.source).filter((value): value is string => !!value))].sort()
  const findingQuery = findingSearch.trim().toLowerCase()
  const visibleFindings = findings
    .filter(item => {
      if (!matchesImageFindingFilter(item, findingFilter)) return false
      if (findingSource && item.source !== findingSource) return false
      if (!findingQuery) return true
      return [item.countryCode, item.ownerType, item.ownerId, item.label, item.sourceIndicator, item.objectPath]
        .some(value => value?.toLowerCase().includes(findingQuery))
    })
    .sort((left, right) => {
      if (findingSort === 'COUNTRY') return (left.countryCode ?? '').localeCompare(right.countryCode ?? '')
      if (findingSort === 'SOURCE') return (left.source ?? '').localeCompare(right.source ?? '')
      if (findingSort === 'OWNER') return (left.ownerId ?? '').localeCompare(right.ownerId ?? '', undefined, { numeric: true })
      return imageFindingSeverity(left) - imageFindingSeverity(right)
        || (left.countryCode ?? '').localeCompare(right.countryCode ?? '')
    })

  if (mode !== 'IMAGE_INTEGRITY_AUDIT') {
    const deletionFailures = Array.isArray(summary.deletionFailures)
      ? summary.deletionFailures.map(String)
      : []
    const cleanupMetrics = [
      ['Scanned', 'objectsScanned', 'text-white/70'],
      ['Retained', 'retained', 'text-sky-400'],
      ['Deleted', 'deleted', 'text-emerald-400'],
      ['Grace protected', 'protectedByGracePeriod', 'text-amber-400'],
    ] as const
    return (
      <Modal isOpen onClose={onClose} title="Orphaned Image Cleanup" className="max-w-2xl">
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <StatusBadge status={log.status} />
            <Badge variant="neutral" className="text-[10px]">{log.triggerType}</Badge>
            <span className="text-xs text-white/30 ml-auto">{formatDate(log.startedAt)}</span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {cleanupMetrics.map(([label, key, color]) => (
              <div key={key} className="rounded-lg border border-white/[.06] bg-white/[.03] px-3 py-2">
                <div className={cn('text-lg font-mono font-bold', color)}>{summaryNumber(summary, key).toLocaleString()}</div>
                <div className="mt-0.5 text-[10px] uppercase tracking-widest text-white/30">{label}</div>
              </div>
            ))}
          </div>
          <p className="text-xs text-white/35">
            Unreferenced objects younger than {summaryNumber(summary, 'orphanGraceHours')} hours are retained for the next run.
          </p>
          {deletionFailures.length > 0 && (
            <div className="space-y-2">
              <h4 className="text-xs text-red-300/60 uppercase tracking-widest font-medium">Deletion failures</h4>
              <div className="max-h-64 space-y-1 overflow-y-auto rounded-lg border border-red-500/10 bg-red-500/[.03] p-3">
                {deletionFailures.map(path => <div key={path} className="break-all font-mono text-xs text-red-200/60">{path}</div>)}
              </div>
            </div>
          )}
          {log.errorMessage && <p className="break-words text-xs text-red-300/70">{log.errorMessage}</p>}
        </div>
      </Modal>
    )
  }

  const metricGroups = [
    {
      title: 'Storage repair',
      items: [
        ['Present', 'storagePresent', 'text-white/70'],
        ['Missing', 'storageMissing', 'text-amber-400'],
        ['Downloaded', 'downloaded', 'text-emerald-400'],
        ['Download failed', 'downloadFailed', 'text-red-400'],
      ],
    },
    {
      title: 'Source & rights validation',
      items: [
        ['Sources checked', 'uniqueExternalSourcesChecked', 'text-white/70'],
        ['Validated OK', 'validatedOk', 'text-emerald-400'],
        ['Source missing', 'sourceMissing', 'text-red-400'],
        ['Restricted license', 'restrictedLicense', 'text-red-400'],
        ['License changed', 'licenseChanged', 'text-amber-400'],
        ['Attribution changed', 'attributionChanged', 'text-amber-400'],
        ['Source URL changed', 'sourceUrlChanged', 'text-amber-400'],
        ['Validation failed', 'validationFailed', 'text-red-400'],
        ['Unsupported', 'validationUnsupported', 'text-white/35'],
        ['Metadata backfilled', 'metadataBackfilled', 'text-sky-400'],
      ],
    },
  ] as const

  return (
    <Modal isOpen onClose={onClose} title="Image Integrity Audit" className="max-w-4xl">
      <div className="space-y-5">
        <div className="flex items-center gap-3">
          <StatusBadge status={log.status} />
          <Badge variant="neutral" className="text-[10px]">{log.triggerType}</Badge>
          <span className="text-xs text-white/30 ml-auto">{formatDate(log.startedAt)} · {formatDuration(log.durationMs)}</span>
        </div>

        {metricGroups.map(group => (
          <div key={group.title} className="space-y-2">
            <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">{group.title}</h4>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {group.items.map(([label, key, color]) => (
                <div key={key} className="rounded-lg border border-white/[.06] bg-white/[.03] px-3 py-2">
                  <div className={cn('text-lg font-mono font-bold', color)}>{summaryNumber(summary, key).toLocaleString()}</div>
                  <div className="mt-0.5 text-[10px] uppercase tracking-widest text-white/30">{label}</div>
                </div>
              ))}
            </div>
          </div>
        ))}

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">Findings and repairs</h4>
            <span className="text-[11px] text-white/30">
              {visibleFindings.length.toLocaleString()} of {findings.length.toLocaleString()} items
            </span>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Input
              value={findingSearch}
              onChange={event => setFindingSearch(event.target.value)}
              placeholder="Country, hint, object path..."
              className="h-8 text-xs !w-[220px] shrink-0"
            />
            <CustomSelect
              value={findingFilter}
              onChange={value => setFindingFilter(value as ImageFindingFilter)}
              options={IMAGE_FINDING_FILTERS}
              size="sm"
            />
            <CustomSelect
              value={findingSource}
              onChange={setFindingSource}
              options={[{ value: '', label: 'All sources' }, ...findingSources.map(source => ({ value: source, label: source }))]}
              size="sm"
            />
            <CustomSelect
              value={findingSort}
              onChange={value => setFindingSort(value as ImageFindingSort)}
              options={IMAGE_FINDING_SORTS}
              size="sm"
            />
          </div>
          {findings.length === 0 ? (
            <div className="rounded-lg border border-emerald-500/10 bg-emerald-500/[.04] px-4 py-3 text-sm text-emerald-300/70">
              No missing sources, rights changes, or repair failures were found.
            </div>
          ) : visibleFindings.length === 0 ? (
            <div className="rounded-lg border border-white/[.06] bg-white/[.025] px-4 py-6 text-center text-sm text-white/35">
              No findings match the selected filters.
            </div>
          ) : (
            <div className="max-h-[42vh] space-y-2 overflow-y-auto pr-1">
              {visibleFindings.map((item, index) => (
                <div key={`${item.ownerType}-${item.ownerId}-${index}`} className="rounded-lg border border-white/[.07] bg-white/[.025] p-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={imageFindingBadgeVariant(item)} className="text-[10px]">
                      {imageFindingLabel(item)}
                    </Badge>
                    {item.metadataBackfilled && imageFindingLabel(item) !== 'METADATA BACKFILLED' && (
                      <Badge variant="info" className="text-[9px]">METADATA BACKFILLED</Badge>
                    )}
                    <span className="text-xs font-mono text-white/60">{item.countryCode}</span>
                    <span className="text-xs text-white/55">{item.ownerType} #{item.ownerId}</span>
                    <span className="text-xs text-white/30">{item.source}</span>
                  </div>
                  <p className="mt-2 text-sm text-white/70">{item.label || item.sourceIndicator || 'Image asset'}</p>
                  {item.reason && <p className="mt-1 text-xs text-red-300/65 break-words">{item.reason}</p>}
                  {!!item.metadataChanges?.length && (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {item.metadataChanges.map(change => <Badge key={change} variant="warning" className="text-[9px]">{change.replace(/_/g, ' ')}</Badge>)}
                    </div>
                  )}
                  {(item.storedMetadata || item.currentMetadata) && (
                    <details className="mt-2">
                      <summary className="cursor-pointer text-[11px] text-white/35 hover:text-white/55">Compare stored and current metadata</summary>
                      <div className="mt-2 grid gap-2 sm:grid-cols-2">
                        <pre className="overflow-auto rounded bg-black/20 p-2 text-[10px] text-white/45 whitespace-pre-wrap break-all">{JSON.stringify(item.storedMetadata ?? {}, null, 2)}</pre>
                        <pre className="overflow-auto rounded bg-black/20 p-2 text-[10px] text-white/45 whitespace-pre-wrap break-all">{JSON.stringify(item.currentMetadata ?? {}, null, 2)}</pre>
                      </div>
                    </details>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  )
}

function OpsDetailModal({ log, onClose }: { log: OperationBatchLogDto | null; onClose: () => void }) {
  if (log?.operationType === 'IMAGE_MAINTENANCE') {
    return <ImageMaintenanceDetailModal log={log} onClose={onClose} />
  }
  return (
    <Modal isOpen={!!log} onClose={onClose} title="Operation Details" className="max-w-2xl">
      {log && (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <StatusBadge status={log.status} />
            <span className="text-sm text-white/70">{log.operationType}</span>
            <span className="text-xs text-white/30 ml-auto">{formatDate(log.startedAt)}</span>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {[
              { label: 'Total', value: log.totalItems, color: 'text-white/60' },
              { label: 'Successful', value: log.successfulItems, color: 'text-emerald-400' },
              { label: 'Failed', value: log.failedItems, color: log.failedItems > 0 ? 'text-red-400' : 'text-white/20' },
            ].map(s => (
              <div key={s.label} className="bg-white/[.03] border border-white/[.06] rounded-lg px-3 py-2 text-center">
                <div className={cn('text-lg font-mono font-bold', s.color)}>{s.value.toLocaleString()}</div>
                <div className="text-[10px] text-white/30 uppercase tracking-widest mt-0.5">{s.label}</div>
              </div>
            ))}
          </div>
          {log.summary && Object.keys(log.summary).length > 0 && (
            <div className="space-y-2">
              <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">Summary</h4>
              <SummarySection data={log.summary} />
            </div>
          )}
          {log.errorMessage && (
            <div className="space-y-2">
              <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">Error</h4>
              <pre className="text-xs text-red-400/80 bg-red-500/[.05] border border-red-500/10 rounded-lg p-3 whitespace-pre-wrap break-all">{log.errorMessage}</pre>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

function FetchDetailsSection({ raw }: { raw: string }) {
  const entries = parseFetchDetails(raw)
  if (entries.length === 0) return null

  const byFeed = entries.reduce<Record<string, FetchFailureEntry[]>>((acc, e) => {
    const key = e.feedType || 'UNKNOWN'
    ;(acc[key] ??= []).push(e)
    return acc
  }, {})

  const feeds = Object.entries(byFeed).sort((a, b) => b[1].length - a[1].length)

  return (
    <div className="space-y-2">
      <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">
        Fetch Errors <span className="text-white/20">({entries.length})</span>
      </h4>
      <div className="space-y-2">
        {feeds.map(([feed, items]) => (
          <div key={feed} className="bg-white/[.03] border border-white/[.06] rounded-lg overflow-hidden">
            <div className="px-3 py-2 border-b border-white/[.04] flex items-center justify-between">
              <span className="text-[11px] text-white/50 font-medium">{feed}</span>
              <span className="text-[10px] text-red-400/60 font-mono">{items.length} failed</span>
            </div>
            <div className="divide-y divide-white/[.03] max-h-[200px] overflow-auto">
              {items.map((item, i) => (
                <div key={i} className="px-3 py-1.5 flex items-center gap-3 hover:bg-white/[.02]">
                  <span className="text-[11px] text-white/50 font-mono w-[32px] shrink-0">{item.countryCode}</span>
                  {item.statusCode != null && (
                    <span className={cn(
                      'text-[10px] font-mono px-1.5 py-0.5 rounded shrink-0',
                      item.statusCode >= 500 ? 'text-red-400 bg-red-500/10'
                        : item.statusCode >= 400 ? 'text-amber-400 bg-amber-500/10'
                        : 'text-white/30 bg-white/[.05]'
                    )}>{item.statusCode}</span>
                  )}
                  <span className="text-[11px] text-red-400/60 truncate flex-1" title={item.error}>{item.error}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function SyncDetailModal({ log, onClose }: { log: DataSyncLogDto | null; onClose: () => void }) {
  const fetchEntries = log ? parseFetchDetails(log.fetchDetails) : []
  const hasFetchErrors = fetchEntries.length > 0

  return (
    <Modal isOpen={!!log} onClose={onClose} title="Sync Log Details" className={cn(hasFetchErrors ? 'max-w-2xl' : 'max-w-xl')}>
      {log && (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <StatusBadge status={log.status} />
            <span className="text-sm text-white/70">{log.source}</span>
            {log.countryCode && <span className="text-xs text-white/50 font-mono">{log.countryCode}</span>}
            <span className="text-xs text-white/30 ml-auto">{formatDate(log.startedAt)}</span>
          </div>
          <div className="grid grid-cols-3 sm:grid-cols-5 gap-2">
            {[
              { label: 'Countries', value: log.countriesProcessed, color: 'text-white/60' },
              { label: 'Stats saved', value: log.statisticsSaved, color: 'text-white/60' },
              { label: 'Hints', value: log.hintsGenerated, color: log.hintsGenerated > 0 ? 'text-emerald-400' : 'text-white/60' },
              { label: 'Deactivated', value: log.hintsDeactivated, color: log.hintsDeactivated > 0 ? 'text-amber-400' : 'text-white/30' },
              { label: 'Fetch errors', value: fetchEntries.length, color: fetchEntries.length > 0 ? 'text-red-400' : 'text-white/30' },
            ].map(s => (
              <div key={s.label} className="bg-white/[.03] border border-white/[.06] rounded-lg px-3 py-2 text-center">
                <div className={cn('text-sm font-mono font-bold', s.color)}>{s.value.toLocaleString()}</div>
                <div className="text-[10px] text-white/30 uppercase tracking-widest mt-0.5">{s.label}</div>
              </div>
            ))}
          </div>
          {log.fetchDetails && <FetchDetailsSection raw={log.fetchDetails} />}
          {log.errorMessage && (
            <div className="space-y-2">
              <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">Error Message</h4>
              <pre className="text-xs text-red-400/80 bg-red-500/[.05] border border-red-500/10 rounded-lg p-3 whitespace-pre-wrap break-all">{log.errorMessage}</pre>
            </div>
          )}
          {log.errorStack && (
            <div className="space-y-2">
              <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">Stack Trace</h4>
              <pre className="text-[10px] text-white/40 bg-white/[.02] border border-white/[.06] rounded-lg p-3 whitespace-pre-wrap break-all max-h-[300px] overflow-auto">{log.errorStack}</pre>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

function HintDetailModal({ log, onClose }: { log: HintGenerationLogDto | null; onClose: () => void }) {
  return (
    <Modal isOpen={!!log} onClose={onClose} title="Hint Generation Details" className="max-w-6xl max-h-[90vh] flex flex-col">
      {log && (
        <div className="space-y-4 overflow-y-auto min-h-0">
          <div className="flex items-center gap-3 flex-wrap">
            <span className="text-sm text-white/60 font-mono">{log.countryCode || '—'}</span>
            <Badge variant="neutral" className="text-[10px]">{log.triggerType}</Badge>
            {log.avgQualityScore != null && (
              <span className="text-xs text-white/40">Quality: <span className="text-white/70 font-mono">{log.avgQualityScore.toFixed(2)}</span></span>
            )}
            {log.durationMs != null && (
              <span className="text-xs text-white/30">{formatDuration(log.durationMs)}</span>
            )}
            <span className="text-xs text-white/30 ml-auto">{formatDate(log.startedAt)}</span>
          </div>
          <div className="grid grid-cols-4 sm:grid-cols-6 gap-2">
            {[
              { label: 'Created', value: log.hintsCreated, color: 'text-emerald-400' },
              { label: 'Attempted', value: log.hintsAttempted, color: 'text-white/60' },
              { label: 'Duplicates', value: log.hintsDuplicates, color: 'text-amber-400' },
              { label: 'Failed', value: log.hintsFailed, color: 'text-red-400' },
              { label: 'Categories', value: log.categoriesCovered, color: 'text-white/40' },
              { label: 'Types', value: log.hintTypesCovered, color: 'text-white/40' },
            ].map(s => (
              <div key={s.label} className="bg-white/[.03] border border-white/[.06] rounded-lg p-2.5 text-center">
                <div className={cn('text-base font-mono font-bold', s.color)}>{s.value}</div>
                <div className="text-[9px] text-white/30 uppercase tracking-widest mt-0.5">{s.label}</div>
              </div>
            ))}
          </div>
          {log.lensBreakdown && Object.keys(log.lensBreakdown).length > 0 && (
            <LensBreakdownChart data={log.lensBreakdown} />
          )}
          {log.details && Object.keys(log.details).length > 0 && (
            <RejectedHintsSection data={log.details} />
          )}
          {log.errorMessage && (
            <div className="space-y-2">
              <h4 className="text-xs text-white/40 uppercase tracking-widest font-medium">Error</h4>
              <pre className="text-xs text-red-400/80 bg-red-500/[.05] border border-red-500/10 rounded-lg p-3 whitespace-pre-wrap break-all">{log.errorMessage}</pre>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

function GroupedErrorsView({ errors, onFilterByError }: { errors: GroupedError[]; onFilterByError: (msg: string) => void }) {
  if (errors.length === 0) return <EmptyState icon={CheckCircle} title="No errors found" className="py-16" />
  return (
    <div className="divide-y divide-white/[.04]">
      {errors.map((err, i) => (
        <button key={i} onClick={() => onFilterByError(err.errorMessage)} className="w-full text-left px-4 py-3 hover:bg-white/[.02] transition-colors cursor-pointer">
          <div className="flex items-start gap-3">
            <div className="p-1.5 rounded-lg bg-red-500/10 shrink-0 mt-0.5"><AlertOctagon size={14} className="text-red-400" /></div>
            <div className="flex-1 min-w-0">
              <p className="text-xs text-red-400/80 line-clamp-2">{err.errorMessage}</p>
              <div className="flex items-center gap-3 mt-1.5 text-[11px] text-white/30">
                <span className="font-mono font-bold text-white/60">{err.count}x</span>
                <span>{formatDate(err.firstOccurrence)} — {formatDate(err.lastOccurrence)}</span>
                {err.affectedCountries.length > 0 && (
                  <span className="font-mono">{err.affectedCountries.slice(0, 5).join(', ')}{err.affectedCountries.length > 5 ? ` +${err.affectedCountries.length - 5}` : ''}</span>
                )}
              </div>
            </div>
          </div>
        </button>
      ))}
    </div>
  )
}

const CENTER_COLUMNS = ['status']

export function LogsPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<LogTab>('operations')
  const [page, setPage] = useState(0)
  const [groupedMode, setGroupedMode] = useState(false)

  const [opsTypeFilter, setOpsTypeFilter] = useState('')
  const [opsStatusFilter, setOpsStatusFilter] = useState('')
  const [opsTriggerFilter, setOpsTriggerFilter] = useState('')
  const [opsDateFrom, setOpsDateFrom] = useState('')
  const [opsDateTo, setOpsDateTo] = useState('')

  const [syncSourceFilter, setSyncSourceFilter] = useState('')
  const [syncStatusFilter, setSyncStatusFilter] = useState('')
  const [syncTypeFilter, setSyncTypeFilter] = useState('')
  const [syncCountryFilter, setSyncCountryFilter] = useState('')
  const [syncDateFrom, setSyncDateFrom] = useState('')
  const [syncDateTo, setSyncDateTo] = useState('')

  const [hintTriggerFilter, setHintTriggerFilter] = useState('')
  const [hintCountryFilter, setHintCountryFilter] = useState('')
  const [hintDateFrom, setHintDateFrom] = useState('')
  const [hintDateTo, setHintDateTo] = useState('')

  const [detailOps, setDetailOps] = useState<OperationBatchLogDto | null>(null)
  const [detailSync, setDetailSync] = useState<DataSyncLogDto | null>(null)
  const [detailHint, setDetailHint] = useState<HintGenerationLogDto | null>(null)

  const columns = useMemo(() => TAB_COLUMNS[tab], [tab])

  const { containerRef, ...table } = useAdminTable({
    storageKey: `admin-logs-${tab}`,
    columns,
    defaultSort: 'startedAt',
    defaultSortDir: 'desc',
    pinnedColumns: ['actions'],
    onSortChange: () => setPage(0),
  })

  const handleTabChange = (id: string) => { setTab(id as LogTab); setPage(0); setGroupedMode(false) }

  const { data: opsData, isLoading: opsLoading } = useQuery({
    queryKey: ['admin-logs-ops', page, opsTypeFilter, opsStatusFilter, opsTriggerFilter, table.sortBy, table.sortDir, opsDateFrom, opsDateTo],
    queryFn: () => adminApi.getOperationLogs({
      page, size: PAGE_SIZE,
      operationType: opsTypeFilter || undefined, status: opsStatusFilter || undefined,
      triggerType: opsTriggerFilter || undefined,
      startAfter: dateToParam(opsDateFrom), startBefore: dateToParam(opsDateTo, true),
      sortBy: table.sortBy, sortDir: table.sortDir,
    }),
    enabled: tab === 'operations' && !groupedMode, staleTime: LOG_STALE_TIME,
  })

  const { data: syncData, isLoading: syncLoading } = useQuery({
    queryKey: ['admin-logs-sync', page, syncSourceFilter, syncStatusFilter, syncTypeFilter, syncCountryFilter, table.sortBy, table.sortDir, syncDateFrom, syncDateTo],
    queryFn: () => adminApi.getSyncLogs({
      page, size: PAGE_SIZE,
      source: syncSourceFilter || undefined, status: syncStatusFilter || undefined,
      syncType: syncTypeFilter || undefined, countryCode: syncCountryFilter || undefined,
      startAfter: dateToParam(syncDateFrom), startBefore: dateToParam(syncDateTo, true),
      sortBy: table.sortBy, sortDir: table.sortDir,
    }),
    enabled: tab === 'sync' && !groupedMode, staleTime: LOG_STALE_TIME,
  })

  const { data: hintData, isLoading: hintLoading } = useQuery({
    queryKey: ['admin-logs-hints', page, hintTriggerFilter, hintCountryFilter, table.sortBy, table.sortDir, hintDateFrom, hintDateTo],
    queryFn: () => adminApi.getHintLogs({
      page, size: PAGE_SIZE,
      countryCode: hintCountryFilter || undefined, triggerType: hintTriggerFilter || undefined,
      startAfter: dateToParam(hintDateFrom), startBefore: dateToParam(hintDateTo, true),
      sortBy: table.sortBy, sortDir: table.sortDir,
    }),
    enabled: tab === 'hints' && !groupedMode, staleTime: LOG_STALE_TIME,
  })

  const { data: groupedErrors, isLoading: groupedLoading } = useQuery({
    queryKey: ['admin-logs-grouped-errors', tab],
    queryFn: () => adminApi.getGroupedErrors(tab, GROUPED_ERRORS_LIMIT),
    enabled: groupedMode, staleTime: LOG_STALE_TIME,
  })

  const { data: opsTypes } = useQuery({ queryKey: ['admin-logs-ops-types'], queryFn: adminApi.getOperationTypes, staleTime: FILTER_STALE_TIME })
  const { data: opsStatuses } = useQuery({ queryKey: ['admin-logs-ops-statuses'], queryFn: adminApi.getOperationStatuses, staleTime: FILTER_STALE_TIME })
  const { data: opsTriggers } = useQuery({ queryKey: ['admin-logs-ops-triggers'], queryFn: adminApi.getOperationTriggerTypes, staleTime: FILTER_STALE_TIME })
  const { data: syncSources } = useQuery({ queryKey: ['admin-logs-sync-sources'], queryFn: adminApi.getSyncSources, staleTime: FILTER_STALE_TIME })
  const { data: syncStatuses } = useQuery({ queryKey: ['admin-logs-sync-statuses'], queryFn: adminApi.getSyncStatuses, staleTime: FILTER_STALE_TIME })
  const { data: syncTypes } = useQuery({ queryKey: ['admin-logs-sync-types'], queryFn: adminApi.getSyncTypes, staleTime: FILTER_STALE_TIME })
  const { data: hintTriggers } = useQuery({ queryKey: ['admin-logs-hint-triggers'], queryFn: adminApi.getHintTriggerTypes, staleTime: FILTER_STALE_TIME })

  const isLoading = tab === 'operations' ? opsLoading : tab === 'sync' ? syncLoading : hintLoading
  const currentData = tab === 'operations' ? opsData : tab === 'sync' ? syncData : hintData

  const hasFilters = tab === 'operations'
    ? !!(opsTypeFilter || opsStatusFilter || opsTriggerFilter || opsDateFrom || opsDateTo)
    : tab === 'sync'
      ? !!(syncSourceFilter || syncStatusFilter || syncTypeFilter || syncCountryFilter || syncDateFrom || syncDateTo)
      : !!(hintTriggerFilter || hintCountryFilter || hintDateFrom || hintDateTo)

  const activeFilterCount = tab === 'operations'
    ? [opsTypeFilter, opsStatusFilter, opsTriggerFilter, opsDateFrom, opsDateTo].filter(Boolean).length
    : tab === 'sync'
      ? [syncSourceFilter, syncStatusFilter, syncTypeFilter, syncCountryFilter, syncDateFrom, syncDateTo].filter(Boolean).length
      : [hintTriggerFilter, hintCountryFilter, hintDateFrom, hintDateTo].filter(Boolean).length

  const clearFilters = () => {
    if (tab === 'operations') { setOpsTypeFilter(''); setOpsStatusFilter(''); setOpsTriggerFilter(''); setOpsDateFrom(''); setOpsDateTo('') }
    else if (tab === 'sync') { setSyncSourceFilter(''); setSyncStatusFilter(''); setSyncTypeFilter(''); setSyncCountryFilter(''); setSyncDateFrom(''); setSyncDateTo('') }
    else { setHintTriggerFilter(''); setHintCountryFilter(''); setHintDateFrom(''); setHintDateTo('') }
    setPage(0)
  }

  const tabs = [
    { id: 'operations', label: t.admin.operationLogs, shortLabel: 'Operations' },
    { id: 'sync', label: t.admin.syncLogs, shortLabel: 'Sync' },
    { id: 'hints', label: t.admin.hintLogs, shortLabel: 'Hints' },
  ]

  return (
    <div className="flex flex-col h-full">
      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">

        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.logs}</h1>
            <p className="text-white/40 text-sm mt-1">View history of all operations</p>
          </div>
          <div className="flex items-center gap-1.5">
            <TableToolbarButtons
              showColPicker={table.showColPicker}
              onToggleColPicker={() => table.setShowColPicker(o => !o)}
              onFitToContainer={table.handleFitToContainer}
              onResetWidths={table.handleResetWidths}
              toggleableColumns={table.toggleableColumns}
              visibleCols={table.visibleCols}
              onToggleCol={table.toggleCol}
              onShowAllCols={table.showAllCols}
              onResetCols={table.resetCols}
            />
          </div>
        </div>

        <Tabs tabs={tabs} activeTab={tab} onChange={handleTabChange} />

        <AdminFilterBar
          activeFilterCount={activeFilterCount}
          onClearFilters={clearFilters}
          hasFilters={hasFilters}
          resultCount={groupedMode ? (groupedErrors?.length ?? 0) : currentData?.totalElements}
        >
          {tab === 'operations' && <>
            <CustomSelect value={opsTypeFilter} onChange={v => { setOpsTypeFilter(v); setPage(0) }} options={[{ value: '', label: 'All Types' }, ...(opsTypes?.map(t => ({ value: t, label: t })) ?? [])]} placeholder="All Types" />
            <CustomSelect value={opsStatusFilter} onChange={v => { setOpsStatusFilter(v); setPage(0) }} options={[{ value: '', label: 'All Statuses' }, ...(opsStatuses?.map(s => ({ value: s, label: s })) ?? [])]} placeholder="All Statuses" />
            <CustomSelect value={opsTriggerFilter} onChange={v => { setOpsTriggerFilter(v); setPage(0) }} options={[{ value: '', label: 'All Triggers' }, ...(opsTriggers?.map(t => ({ value: t, label: t })) ?? [])]} placeholder="All Triggers" />
            <div className="ml-auto w-px h-4 bg-white/[.08] shrink-0" />
            <DatePicker value={opsDateFrom} onChange={v => { setOpsDateFrom(v); setPage(0) }} placeholder="From" size="sm" clearable />
            <span className="text-white/15 shrink-0">–</span>
            <DatePicker value={opsDateTo} onChange={v => { setOpsDateTo(v); setPage(0) }} placeholder="To" size="sm" clearable />
          </>}

          {tab === 'sync' && <>
            <SourceSelect
              value={syncSourceFilter}
              onChange={v => { setSyncSourceFilter(v); setPage(0) }}
              sources={syncSources ?? []}
              allowAll
              allLabel="All Sources"
              placeholder="All Sources"
              size="sm"
              className="w-[190px]"
            />
            <CustomSelect value={syncStatusFilter} onChange={v => { setSyncStatusFilter(v); setPage(0) }} options={[{ value: '', label: 'All Statuses' }, ...(syncStatuses?.map(s => ({ value: s, label: s })) ?? [])]} placeholder="All Statuses" />
            <CustomSelect value={syncTypeFilter} onChange={v => { setSyncTypeFilter(v); setPage(0) }} options={[{ value: '', label: 'All Types' }, ...(syncTypes?.map(t => ({ value: t, label: t })) ?? [])]} placeholder="All Types" />
            <Input
              value={syncCountryFilter}
              onChange={e => { setSyncCountryFilter(e.target.value.toUpperCase()); setPage(0) }}
              containerClassName="w-[72px] shrink-0"
              className="h-8 text-xs px-2 py-1"
              placeholder="CC"
            />
            <div className="w-px h-4 bg-white/[.08] shrink-0" />
            <DatePicker value={syncDateFrom} onChange={v => { setSyncDateFrom(v); setPage(0) }} placeholder="From" size="sm" clearable />
            <span className="text-white/15 shrink-0">–</span>
            <DatePicker value={syncDateTo} onChange={v => { setSyncDateTo(v); setPage(0) }} placeholder="To" size="sm" clearable />
          </>}

          {tab === 'hints' && <>
            <CustomSelect value={hintTriggerFilter} onChange={v => { setHintTriggerFilter(v); setPage(0) }} options={[{ value: '', label: 'All Triggers' }, ...(hintTriggers?.map(t => ({ value: t, label: t })) ?? [])]} placeholder="All Triggers" />
            <Input
              value={hintCountryFilter}
              onChange={e => { setHintCountryFilter(e.target.value.toUpperCase()); setPage(0) }}
              containerClassName="w-[72px] shrink-0"
              className="h-8 text-xs px-2 py-1"
              placeholder="CC"
            />
            <div className="w-px h-4 bg-white/[.08] shrink-0" />
            <DatePicker value={hintDateFrom} onChange={v => { setHintDateFrom(v); setPage(0) }} placeholder="From" size="sm" clearable />
            <span className="text-white/15 shrink-0">–</span>
            <DatePicker value={hintDateTo} onChange={v => { setHintDateTo(v); setPage(0) }} placeholder="To" size="sm" clearable />
          </>}

          <button
            onClick={() => { setGroupedMode(!groupedMode); setPage(0) }}
            className={cn('flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs transition-colors cursor-pointer shrink-0',
              groupedMode ? 'bg-red-500/15 text-red-400' : 'text-white/25 hover:text-white/50 hover:bg-white/[.04]'
            )}
            title="Group by errors"
          >
            <AlertOctagon size={13} /> <span className="hidden sm:inline">Errors</span>
          </button>
        </AdminFilterBar>
      </div>

      <div className="flex-1 min-h-0 px-5 sm:px-6 lg:px-8 pb-3">
        <div ref={containerRef} className="h-full border border-white/[.06] rounded-xl bg-white/[.03] overflow-auto scrollbar scrollbar-thumb-white/15 scrollbar-track-transparent [&::-webkit-scrollbar-corner]:bg-transparent">

          {groupedMode ? (
            groupedLoading ? <div className="p-5"><SkeletonTable rows={8} cols={3} /></div>
              : <GroupedErrorsView errors={groupedErrors ?? []} onFilterByError={() => setGroupedMode(false)} />
          ) : (
            <>
              <AdminTableHeader
                dataCols={table.dataCols}
                hasActions={table.hasActions}
                dataColsTemplate={table.dataColsTemplate}
                actionsW={table.actionsW}
                sortBy={table.sortBy}
                sortDir={table.sortDir}
                onSort={table.handleSort}
                onResizeStart={table.onResizeStart}
                getWidth={table.getWidth}
                actionsRefCb={table.actionsRefCb}
                centerColumns={CENTER_COLUMNS}
              />

              {isLoading ? (
                <div className="p-5"><SkeletonTable rows={10} cols={table.dataCols.length} /></div>
              ) : !currentData?.content?.length ? (
                <EmptyState icon={Clock} title={t.common.noResults} className="py-16" />
              ) : (
                <>
                  {tab === 'operations' && opsData?.content.map(log => (
                    <div key={log.id}>
                      <div className="hidden sm:flex items-center min-w-max border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors">
                        <div className="grid gap-0 px-3 py-2.5 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                          {table.dataCols.map(col => <div key={col.key} className={cn('overflow-hidden px-2', col.key === 'status' && 'flex justify-center')}><OpsCellValue col={col} log={log} onDetail={() => setDetailOps(log)} /></div>)}
                        </div>
                        {table.hasActions && <div className="sticky right-0 z-10 shrink-0 flex items-center justify-center" style={{ width: 40 }}><OpsCellValue col={{ key: 'actions', label: '', defaultWidth: 40, defaultVisible: true }} log={log} onDetail={() => setDetailOps(log)} /></div>}
                      </div>
                      <div className="sm:hidden flex flex-col gap-1.5 px-4 py-3 border-b border-white/[.04]">
                        <div className="flex items-center justify-between gap-2"><span className="text-sm text-white/70">{log.operationType}</span><StatusBadge status={log.status} /></div>
                        <div className="flex items-center gap-2 text-xs text-white/30"><Badge variant="neutral" className="text-[10px]">{log.triggerType}</Badge><span>{log.successfulItems}/{log.totalItems}</span>{log.failedItems > 0 && <span className="text-red-400">{log.failedItems} failed</span>}<span className="ml-auto">{formatDuration(log.durationMs)}</span></div>
                        <span className="text-xs text-white/20">{formatDate(log.startedAt)}</span>
                      </div>
                    </div>
                  ))}
                  {tab === 'sync' && syncData?.content.map(log => (
                    <div key={log.id}>
                      <div className="hidden sm:flex items-center min-w-max border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors">
                        <div className="grid gap-0 px-3 py-2.5 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                          {table.dataCols.map(col => <div key={col.key} className={cn('overflow-hidden px-2', col.key === 'status' && 'flex justify-center')}><SyncCellValue col={col} log={log} onDetail={() => setDetailSync(log)} /></div>)}
                        </div>
                        {table.hasActions && <div className="sticky right-0 z-10 shrink-0 flex items-center justify-center" style={{ width: 40 }}><SyncCellValue col={{ key: 'actions', label: '', defaultWidth: 40, defaultVisible: true }} log={log} onDetail={() => setDetailSync(log)} /></div>}
                      </div>
                      <div className="sm:hidden flex flex-col gap-1.5 px-4 py-3 border-b border-white/[.04]">
                        <div className="flex items-center justify-between gap-2"><span className="text-sm text-white/70">{log.source}</span><StatusBadge status={log.status} /></div>
                        <div className="flex items-center gap-2 text-xs text-white/30"><Badge variant="neutral" className="text-[10px]">{log.syncType}</Badge>{log.countryCode && <span className="font-mono">{log.countryCode}</span>}<span>{log.statisticsSaved} stats</span><span className="ml-auto">{formatDuration(log.durationMs)}</span></div>
                        <span className="text-xs text-white/20">{formatDate(log.startedAt)}</span>
                      </div>
                    </div>
                  ))}
                  {tab === 'hints' && hintData?.content.map(log => (
                    <div key={log.id}>
                      <div className="hidden sm:flex items-center min-w-max border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors">
                        <div className="grid gap-0 px-3 py-2.5 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                          {table.dataCols.map(col => <div key={col.key} className="overflow-hidden px-2"><HintCellValue col={col} log={log} onDetail={() => setDetailHint(log)} /></div>)}
                        </div>
                        {table.hasActions && <div className="sticky right-0 z-10 shrink-0 flex items-center justify-center" style={{ width: 40 }}><HintCellValue col={{ key: 'actions', label: '', defaultWidth: 40, defaultVisible: true }} log={log} onDetail={() => setDetailHint(log)} /></div>}
                      </div>
                      <div className="sm:hidden flex flex-col gap-1.5 px-4 py-3 border-b border-white/[.04]">
                        <div className="flex items-center justify-between gap-2"><span className="text-sm text-white/60 font-mono">{log.countryCode || '—'}</span><Badge variant="neutral" className="text-[10px]">{log.triggerType}</Badge></div>
                        <div className="flex items-center gap-2 text-xs text-white/30"><span className="text-emerald-400/80">{log.hintsCreated} created</span>{log.hintsDuplicates > 0 && <span className="text-amber-400/70">{log.hintsDuplicates} dupes</span>}{log.hintsFailed > 0 && <span className="text-red-400">{log.hintsFailed} failed</span>}<span className="ml-auto">{formatDuration(log.durationMs)}</span></div>
                        <span className="text-xs text-white/20">{formatDate(log.startedAt)}</span>
                      </div>
                    </div>
                  ))}
                </>
              )}
            </>
          )}
        </div>
      </div>

      {!groupedMode && currentData && currentData.totalPages > 1 && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination page={page} totalPages={currentData.totalPages} totalElements={currentData.totalElements} onPageChange={setPage} />
        </div>
      )}

      <OpsDetailModal log={detailOps} onClose={() => setDetailOps(null)} />
      <SyncDetailModal log={detailSync} onClose={() => setDetailSync(null)} />
      <HintDetailModal log={detailHint} onClose={() => setDetailHint(null)} />
    </div>
  )
}
