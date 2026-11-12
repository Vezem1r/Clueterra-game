import { useState, useMemo, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { BarChart, Edit2, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { SourceSelect } from '@/components/admin/SourceSelect'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { AdminTableHeader } from '@/components/admin/AdminTableHeader'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { TableToolbarButtons } from '@/components/admin/TableToolbarButtons'
import { ImageMetadataPanel } from '@/components/admin/ImageMetadataPanel'
import { adminApi } from '@/api/admin'
import { useDebounce } from '@/hooks/useDebounce'
import { useAdminTable, type ColumnDef } from '@/hooks/useAdminTable'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import type { CountryStatisticAdminDto } from '@/types'
import { cn } from '@/utils/cn'
import { getFlagUrlByIso2 } from '@/utils/country'

const PAGE_SIZE = 30

const ACTIONS_GRAD = 'linear-gradient(to right, transparent, rgb(15,15,20) 28px, rgb(15,15,20))'

function displayUrl(url?: string | null): string {
  if (!url) return '—'
  try {
    const parsed = new URL(url)
    return `${parsed.hostname}${parsed.pathname}`.replace(/\/$/, '')
  } catch {
    return url
  }
}

const ALL_COLUMNS: ColumnDef[] = [
  { key: 'countryName',   label: 'Country',    sortKey: 'countryName',   defaultVisible: true,  defaultWidth: 170 },
  { key: 'category',      label: 'Category',   sortKey: 'category',      defaultVisible: true,  defaultWidth: 110 },
  { key: 'indicatorName', label: 'Indicator',  sortKey: 'indicatorName', defaultVisible: true,  defaultWidth: 280 },
  { key: 'value',         label: 'Value',      sortKey: 'value',         defaultVisible: true,  defaultWidth: 160 },
  { key: 'unit',          label: 'Unit',       sortKey: undefined,       defaultVisible: false, defaultWidth: 70 },
  { key: 'source',        label: 'Source',     sortKey: 'source',        defaultVisible: true,  defaultWidth: 130 },
  { key: 'year',          label: 'Year',       sortKey: 'year',          defaultVisible: true,  defaultWidth: 60 },
  { key: 'globalRank',    label: 'Rank',       sortKey: 'globalRank',    defaultVisible: false, defaultWidth: 60 },
  { key: 'percentile',    label: '%ile',       sortKey: 'percentile',    defaultVisible: false, defaultWidth: 65 },
  { key: 'isCurrent',     label: 'Current',    sortKey: 'isCurrent',     defaultVisible: true,  defaultWidth: 75 },
  { key: 'weight',        label: 'Weight',     sortKey: 'weight',        defaultVisible: false, defaultWidth: 65 },
  { key: 'imageAuthorName', label: 'Author',   sortKey: undefined,       defaultVisible: false, defaultWidth: 140 },
  { key: 'imageLicenseLabel', label: 'License', sortKey: undefined,      defaultVisible: false, defaultWidth: 130 },
  { key: 'imageAttributionText', label: 'Attribution', sortKey: undefined, defaultVisible: false, defaultWidth: 260 },
  { key: 'imageSourcePageUrl', label: 'Source Page', sortKey: undefined, defaultVisible: false, defaultWidth: 220 },
  { key: 'originalImageUrl', label: 'Original Image', sortKey: undefined, defaultVisible: false, defaultWidth: 220 },
  { key: 'fetchedAt',     label: 'Fetched',    sortKey: 'fetchedAt',     defaultVisible: false, defaultWidth: 140 },
  { key: 'actions',       label: '',           sortKey: undefined,       defaultVisible: true,  defaultWidth: 50 },
]

function CellValue({ col, stat }: { col: ColumnDef; stat: CountryStatisticAdminDto }) {
  switch (col.key) {
    case 'countryName':
      return (
        <span className="flex items-center gap-2 text-sm text-white/85 truncate">
          {stat.countryCode && (
            <img
              src={getFlagUrlByIso2(stat.countryCode, 'w20') ?? ''}
              alt={stat.countryCode} width={16} height={12}
              className="rounded-[2px] shrink-0"
              onError={e => { (e.target as HTMLImageElement).style.display = 'none' }}
            />
          )}
          {stat.countryName}
        </span>
      )
    case 'category':
      return <Badge variant="info" className="text-[10px]">{stat.category}</Badge>
    case 'indicatorName':
      return <span className="text-xs text-white/60 truncate" title={stat.indicatorName}>{stat.indicatorName}</span>
    case 'value':
      return (
        <div className="flex items-center gap-1.5 min-w-0">
          {stat.imageUrl && (
            <img
              src={stat.imageUrl}
              alt=""
              className="w-6 h-6 rounded object-cover shrink-0 border border-white/10"
              loading="lazy"
            />
          )}
          <span className="text-sm text-white/70 font-mono truncate">{stat.value ?? stat.valueText ?? '—'}</span>
        </div>
      )
    case 'unit':
      return <span className="text-xs text-white/40">{stat.unit || '—'}</span>
    case 'source':
      return <span className="text-xs text-white/40 truncate">{stat.source}</span>
    case 'year':
      return <span className="text-xs text-white/40 font-mono">{stat.year ?? '—'}</span>
    case 'globalRank':
      return <span className="text-xs text-white/40 font-mono">{stat.globalRank ?? '—'}</span>
    case 'percentile':
      return <span className="text-xs text-white/40 font-mono">{stat.percentile != null ? Number(stat.percentile).toFixed(1) : '—'}</span>
    case 'isCurrent':
      return (
        <Badge variant={stat.isCurrent ? 'success' : 'neutral'} className="text-[10px]">
          {stat.isCurrent ? 'Yes' : 'No'}
          {stat.manualOverride && ' ✱'}
        </Badge>
      )
    case 'weight':
      return <span className="text-xs text-white/40 font-mono">{stat.weight != null ? Number(stat.weight).toFixed(2) : '—'}</span>
    case 'imageAuthorName':
      return <span className="text-xs text-white/40 truncate">{stat.imageAuthorName || '—'}</span>
    case 'imageLicenseLabel':
      return <span className="text-xs text-white/40 truncate">{stat.imageLicenseLabel || '—'}</span>
    case 'imageAttributionText':
      return <span className="text-xs text-white/35 truncate" title={stat.imageAttributionText || undefined}>{stat.imageAttributionText || '—'}</span>
    case 'imageSourcePageUrl':
      return stat.imageSourcePageUrl ? (
        <a href={stat.imageSourcePageUrl} target="_blank" rel="noreferrer" className="text-xs text-sky-300/75 hover:text-sky-200 truncate block">
          {displayUrl(stat.imageSourcePageUrl)}
        </a>
      ) : <span className="text-xs text-white/35">—</span>
    case 'originalImageUrl':
      return stat.originalImageUrl ? (
        <a href={stat.originalImageUrl} target="_blank" rel="noreferrer" className="text-xs text-sky-300/75 hover:text-sky-200 truncate block">
          {displayUrl(stat.originalImageUrl)}
        </a>
      ) : <span className="text-xs text-white/35">—</span>
    case 'fetchedAt':
      return <span className="text-[11px] text-white/30">{stat.fetchedAt ? new Date(stat.fetchedAt).toLocaleString() : '—'}</span>
    default:
      return null
  }
}

export function StatisticsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToastStore()

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search)
  const [categoryFilter, setCategoryFilter] = useState('')
  const [sourceFilter, setSourceFilter] = useState('')
  const [yearFilter, setYearFilter] = useState('')
  const [currentFilter, setCurrentFilter] = useState('')
  const [countryFilter, setCountryFilter] = useState('')
  const [countrySearch, setCountrySearch] = useState('')
  const [countryDropOpen, setCountryDropOpen] = useState(false)
  const debouncedCountrySearch = useDebounce(countrySearch, 200)
  const countryBtnRef = useRef<HTMLButtonElement>(null)
  const countryDropRef = useRef<HTMLDivElement>(null)
  const [countryDropPos, setCountryDropPos] = useState({ top: 0, left: 0 })

  const { containerRef, ...table } = useAdminTable({
    storageKey: 'admin-stats',
    columns: ALL_COLUMNS,
    defaultSort: 'fetchedAt',
    defaultSortDir: 'desc',
    pinnedColumns: ['actions'],
    onSortChange: () => setPage(0),
  })

  const [editing, setEditing] = useState<CountryStatisticAdminDto | null>(null)
  const [editForm, setEditForm] = useState({ value: '', valueText: '', weight: '', isCurrent: true, confidence: '' })

  const handleEdit = (stat: CountryStatisticAdminDto) => {
    setEditing(stat)
    setEditForm({
      value: stat.value != null ? String(stat.value) : '',
      valueText: stat.valueText ?? '',
      weight: stat.weight != null ? String(stat.weight) : '',
      isCurrent: stat.isCurrent,
      confidence: stat.confidence != null ? String(stat.confidence) : '',
    })
  }

  useEffect(() => {
    if (!countryDropOpen) return
    const handler = (e: MouseEvent) => {
      const t = e.target as Node
      if (
        countryBtnRef.current && !countryBtnRef.current.contains(t) &&
        countryDropRef.current && !countryDropRef.current.contains(t)
      ) setCountryDropOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [countryDropOpen])

  const { data: statistics, isLoading } = useQuery({
    queryKey: ['admin-statistics', page, debouncedSearch, categoryFilter, sourceFilter, yearFilter, currentFilter, countryFilter, table.sortBy, table.sortDir],
    queryFn: () => adminApi.getStatistics({
      search: debouncedSearch || undefined,
      category: categoryFilter || undefined,
      source: sourceFilter || undefined,
      year: yearFilter ? Number(yearFilter) : undefined,
      isCurrent: currentFilter === '' ? undefined : currentFilter === 'true',
      countryId: countryFilter || undefined,
      page,
      size: PAGE_SIZE,
      sortBy: table.sortBy,
      sortDir: table.sortDir,
    }),
  })

  const { data: categories } = useQuery({ queryKey: ['admin-stat-categories'], queryFn: adminApi.getStatCategories })
  const { data: sources } = useQuery({ queryKey: ['admin-stat-sources'], queryFn: adminApi.getStatSources })
  const { data: years } = useQuery({ queryKey: ['admin-stat-years'], queryFn: adminApi.getStatYears })

  const { data: statCountries } = useQuery({
    queryKey: ['admin-stat-countries', debouncedCountrySearch],
    queryFn: () => adminApi.getStatCountries(debouncedCountrySearch || undefined),
    staleTime: 60_000,
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Record<string, unknown> }) =>
      adminApi.updateStatistic(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-statistics'] })
      setEditing(null)
      toast.addToast({ type: 'success', message: 'Statistic updated' })
    },
    onError: () => toast.addToast({ type: 'error', message: 'Failed to update statistic' }),
  })

  const handleSave = () => {
    if (!editing) return
    updateMutation.mutate({
      id: editing.id,
      data: {
        value: editForm.value ? parseFloat(editForm.value) : undefined,
        valueText: editForm.valueText || undefined,
        weight: editForm.weight ? parseFloat(editForm.weight) : undefined,
        isCurrent: editForm.isCurrent,
        confidence: editForm.confidence ? parseFloat(editForm.confidence) : undefined,
      },
    })
  }

  const hasFilters = !!(search || categoryFilter || sourceFilter || yearFilter || currentFilter || countryFilter)
  const activeFilterCount = [categoryFilter, sourceFilter, yearFilter, currentFilter, countryFilter].filter(Boolean).length
  const clearFilters = () => {
    setSearch(''); setCategoryFilter(''); setSourceFilter(''); setYearFilter('')
    setCurrentFilter(''); setCountryFilter(''); setCountrySearch(''); setPage(0)
  }

  const selectedCountryLabel = useMemo(() => {
    if (!countryFilter || !statCountries) return ''
    const found = statCountries.find(c => String(c.id) === countryFilter)
    return found ? found.nameEn : ''
  }, [countryFilter, statCountries])

  return (
    <div className="flex flex-col h-full">
      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">

        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.statistics}</h1>
            <p className="text-white/40 text-sm mt-1">Browse and edit country statistics data</p>
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

        <AdminFilterBar
          search={search}
          onSearchChange={v => { setSearch(v); setPage(0) }}
          searchPlaceholder="Search indicators, values, countries..."
          activeFilterCount={activeFilterCount}
          onClearFilters={clearFilters}
          hasFilters={hasFilters}
        >
          <CustomSelect
            size="sm"
            value={categoryFilter}
            onChange={v => { setCategoryFilter(v); setPage(0) }}
            options={[{ value: '', label: 'All Categories' }, ...(categories?.map(c => ({ value: c, label: c })) ?? [])]}
            placeholder="Category"
          />
          <SourceSelect
            size="sm"
            value={sourceFilter}
            onChange={v => { setSourceFilter(v); setPage(0) }}
            sources={sources ?? []}
            allowAll
            allLabel="All Sources"
            placeholder="Source"
            className="w-[190px]"
          />
          <CustomSelect
            size="sm"
            value={yearFilter}
            onChange={v => { setYearFilter(v); setPage(0) }}
            options={[{ value: '', label: 'All Years' }, ...(years?.map(y => ({ value: String(y), label: String(y) })) ?? [])]}
            placeholder="Year"
          />

          <div className="flex items-center gap-1">
            {(['', 'true', 'false'] as const).map(val => (
              <button
                key={val}
                type="button"
                onClick={() => { setCurrentFilter(val); setPage(0) }}
                className={cn(
                  'h-8 px-2.5 rounded-lg text-xs transition-colors border',
                  currentFilter === val
                    ? 'bg-accent/15 border-accent/30 text-accent'
                    : 'bg-white/[.04] border-white/[.08] text-white/50 hover:bg-white/[.06]'
                )}
              >
                {val === '' ? 'All' : val === 'true' ? 'Current' : 'Not Current'}
              </button>
            ))}
          </div>

          <div className="shrink-0 relative">
            <button
              ref={countryBtnRef}
              type="button"
              onClick={() => {
                if (!countryDropOpen && countryBtnRef.current) {
                  const r = countryBtnRef.current.getBoundingClientRect()
                  setCountryDropPos({ top: r.bottom + 4, left: r.left })
                }
                setCountryDropOpen(o => !o)
              }}
              className={cn(
                'flex items-center gap-1.5 h-8 px-2.5 rounded-lg border text-xs cursor-pointer transition-all',
                'bg-white/[.04] border-white/[.08] text-white hover:bg-white/[.06]',
                countryFilter && 'border-accent/30 text-accent',
              )}
            >
              {countryFilter ? selectedCountryLabel || 'Country' : 'Country'}
              {countryFilter && (
                <X size={10} className="text-white/40 hover:text-white/70" onClick={e => { e.stopPropagation(); setCountryFilter(''); setCountrySearch(''); setPage(0) }} />
              )}
            </button>
            {countryDropOpen && (
              <div ref={countryDropRef}
                className="fixed z-[100] w-[240px] rounded-xl border border-white/[.08] bg-[#0e0e1a] shadow-2xl shadow-black/60 overflow-hidden"
                style={{ top: countryDropPos.top, left: countryDropPos.left }}>
                <div className="p-2 border-b border-white/[.06]">
                  <input type="text" placeholder="Search..." value={countrySearch}
                    onChange={e => setCountrySearch(e.target.value)}
                    className="w-full h-7 px-2.5 bg-white/[.04] border border-white/[.08] rounded-lg text-xs text-white placeholder:text-white/30 focus:outline-none focus:border-white/20"
                    autoFocus />
                </div>
                <div className="py-1 max-h-56 overflow-y-auto">
                  {statCountries?.map(c => (
                    <button key={c.id} type="button"
                      onClick={() => { setCountryFilter(String(c.id)); setCountrySearch(''); setCountryDropOpen(false); setPage(0) }}
                      className={cn('w-full flex items-center gap-2 px-3 py-1.5 text-left text-sm hover:bg-white/[.04]',
                        countryFilter === String(c.id) && 'bg-accent/10 text-accent')}>
                      <img src={getFlagUrlByIso2(c.isoAlpha2, 'w20') ?? ''} alt={c.isoAlpha2} width={16} height={12}
                        className="rounded-[2px] shrink-0" onError={e => { (e.target as HTMLImageElement).style.display = 'none' }} />
                      <span className="text-white/70 truncate">{c.nameEn}</span>
                      <span className="text-white/25 text-xs ml-auto">{c.isoAlpha2}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        </AdminFilterBar>
      </div>

      <div className="flex-1 min-h-0 px-5 sm:px-6 lg:px-8 pb-3">
        <div ref={containerRef} className="h-full border border-white/[.06] rounded-xl bg-white/[.03] overflow-auto scrollbar scrollbar-thumb-white/15 scrollbar-track-transparent [&::-webkit-scrollbar-corner]:bg-transparent">

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
          />

          {isLoading ? (
            <div className="p-5"><SkeletonTable rows={8} cols={table.activeCols.length} /></div>
          ) : !statistics?.content.length ? (
            <EmptyState icon={BarChart} title={t.common.noResults} className="py-16" />
          ) : (
            statistics.content.map(stat => (
              <div key={stat.id}>
                <div className="hidden sm:flex items-center border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors min-w-max">
                  <div className="grid gap-0 px-3 py-2 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                    {table.dataCols.map(col => (
                      <div key={col.key} className="overflow-hidden px-2">
                        <CellValue col={col} stat={stat} />
                      </div>
                    ))}
                  </div>
                  {table.hasActions && (
                    <div ref={table.actionsRefCb} className="sticky right-0 z-10 shrink-0 flex items-center justify-center pl-8 pr-2 ml-auto"
                      style={{ background: ACTIONS_GRAD }}>
                      <Button variant="ghost" size="sm" onClick={() => handleEdit(stat)} title={t.common.edit}>
                        <Edit2 size={14} />
                      </Button>
                    </div>
                  )}
                </div>
                <div className="sm:hidden flex flex-col gap-1.5 px-4 py-3 border-b border-white/[.04] last:border-0">
                  <div className="flex items-center justify-between gap-2">
                    <span className="flex items-center gap-2 text-sm text-white/85 min-w-0">
                      {stat.countryCode && (
                        <img src={getFlagUrlByIso2(stat.countryCode, 'w20') ?? ''} alt={stat.countryCode} width={16} height={12}
                          className="rounded-[2px] shrink-0" onError={e => { (e.target as HTMLImageElement).style.display = 'none' }} />
                      )}
                      <span className="truncate">{stat.countryName}</span>
                    </span>
                    <Badge variant={stat.isCurrent ? 'success' : 'neutral'} className="text-[10px] shrink-0">
                      {stat.isCurrent ? 'Current' : 'Old'}{stat.manualOverride && ' ✱'}
                    </Badge>
                  </div>
                  <p className="text-xs text-white/40 truncate">{stat.indicatorName}</p>
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5 min-w-0">
                      {stat.imageUrl && (
                        <img src={stat.imageUrl} alt="" className="w-6 h-6 rounded object-cover shrink-0 border border-white/10" loading="lazy" />
                      )}
                      <span className="text-sm text-white/70 font-mono truncate">{stat.value ?? stat.valueText ?? '—'}</span>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-white/30 shrink-0">
                      <span>{stat.source}</span>
                      {stat.year && <span>{stat.year}</span>}
                    </div>
                  </div>
                  {table.hasActions && (
                    <div className="flex justify-end">
                      <Button variant="ghost" size="sm" onClick={() => handleEdit(stat)} title={t.common.edit}>
                        <Edit2 size={14} />
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {statistics && statistics.totalPages > 1 && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination page={page} totalPages={statistics.totalPages} totalElements={statistics.totalElements} onPageChange={setPage} />
        </div>
      )}

      <Modal
        isOpen={!!editing}
        onClose={() => setEditing(null)}
        title="Edit Statistic"
        className={editing?.imageUrl ? 'max-w-3xl' : undefined}
      >
        <div className={editing?.imageUrl ? 'flex gap-5' : ''}>
          {editing?.imageUrl && (
            <div className="w-56 shrink-0">
              <div className="overflow-hidden rounded-lg border border-white/10">
                <img
                  src={editing.imageUrl}
                  alt=""
                  decoding="async"
                  className="w-full object-contain max-h-80 bg-black/40"
                />
              </div>
            </div>
          )}

          <div className="flex-1 min-w-0 space-y-4">
            {editing && (
              <div className="grid grid-cols-2 gap-3">
                {[
                  { label: 'Country', value: `${editing.countryCode || ''} ${editing.countryName}`.trim() },
                  { label: 'Indicator', value: editing.indicatorName },
                  { label: 'Category', value: editing.category },
                  { label: 'Source', value: editing.source },
                  { label: 'Year', value: editing.year ?? '—' },
                  { label: 'Fetched', value: editing.fetchedAt ? new Date(editing.fetchedAt).toLocaleString() : '—' },
                ].map(f => (
                  <div key={f.label} className="rounded-lg bg-white/[.03] px-3 py-2">
                    <p className="text-[10px] text-white/25 uppercase tracking-widest">{f.label}</p>
                    <p className="text-sm text-white/60 mt-0.5 truncate">{f.value}</p>
                  </div>
                ))}
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-white/35 mb-1.5">Value</label>
                <Input value={editForm.value} onChange={e => setEditForm(f => ({ ...f, value: e.target.value }))} />
              </div>
              <div>
                <label className="block text-xs text-white/35 mb-1.5">Value Text</label>
                <Input value={editForm.valueText} onChange={e => setEditForm(f => ({ ...f, valueText: e.target.value }))} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-white/35 mb-1.5">Weight (0-1)</label>
                <Input type="number" step="0.01" min={0} max={1} value={editForm.weight} onChange={e => setEditForm(f => ({ ...f, weight: e.target.value }))} />
              </div>
              <div>
                <label className="block text-xs text-white/35 mb-1.5">Confidence (0-1)</label>
                <Input type="number" step="0.01" min={0} max={1} value={editForm.confidence} onChange={e => setEditForm(f => ({ ...f, confidence: e.target.value }))} />
              </div>
            </div>

            {editing && (
              <ImageMetadataPanel
                imageUrl={editing.imageUrl}
                originalImageUrl={editing.originalImageUrl}
                imageSourcePageUrl={editing.imageSourcePageUrl}
                imageAuthorName={editing.imageAuthorName}
                imageAttributionText={editing.imageAttributionText}
                imageLicenseLabel={editing.imageLicenseLabel}
                imageLicenseUrl={editing.imageLicenseUrl}
              />
            )}

            <div className="flex items-center gap-3 pt-1">
              <label className="flex items-center gap-2.5 cursor-pointer">
                <div
                  onClick={() => setEditForm(f => ({ ...f, isCurrent: !f.isCurrent }))}
                  className={cn('w-9 h-5 rounded-full relative transition-colors cursor-pointer',
                    editForm.isCurrent ? 'bg-emerald-500/70' : 'bg-white/[.08]')}>
                  <div className={cn('absolute top-0.5 w-4 h-4 rounded-full bg-white transition-all',
                    editForm.isCurrent ? 'left-4' : 'left-0.5')} />
                </div>
                <span className="text-sm text-white/60">Is Current</span>
              </label>
              {editing?.manualOverride && (
                <span className="text-[11px] text-amber-400/60">Manual override active — sync will not overwrite this value</span>
              )}
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" onClick={() => setEditing(null)}>{t.common.cancel}</Button>
              <Button onClick={handleSave} isLoading={updateMutation.isPending}>{t.common.save}</Button>
            </div>
          </div>
        </div>
      </Modal>
    </div>
  )
}
