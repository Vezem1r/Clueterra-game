import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ListFilter, Database, Star, TrendingUp,
  ArrowDownNarrowWide, ShieldOff, Eye, Layers3, GitBranchPlus, FileText,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { Tabs } from '@/components/ui/Tabs'
import { AdminTableHeader } from '@/components/admin/AdminTableHeader'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { TableToolbarButtons } from '@/components/admin/TableToolbarButtons'
import { adminApi } from '@/api/admin'
import { useDebounce } from '@/hooks/useDebounce'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import { useAdminTable, type ColumnDef } from '@/hooks/useAdminTable'
import type { IndicatorConfigAdminDto } from '@/types'
import { cn } from '@/utils/cn'

const PAGE_SIZE = 25
const FILTER_STALE_TIME = 300_000
const ACTIONS_GRAD = 'linear-gradient(to right, transparent, rgb(15,15,20) 28px, rgb(15,15,20))'

type IndicatorMode = 'regular' | 'combo'

const REGULAR_COLUMNS: ColumnDef[] = [
  { key: 'indicatorCode', label: 'Code', sortKey: 'indicatorCode', defaultVisible: true, defaultWidth: 200 },
  { key: 'displayName', label: 'Name', sortKey: 'displayName', defaultVisible: true, defaultWidth: 220 },
  { key: 'category', label: 'Category', sortKey: 'category', defaultVisible: true, defaultWidth: 120 },
  { key: 'unit', label: 'Unit', sortKey: undefined, defaultVisible: false, defaultWidth: 70 },
  { key: 'profileLevel', label: 'Level', sortKey: undefined, defaultVisible: true, defaultWidth: 90 },
  { key: 'weight', label: 'Weight', sortKey: 'weight', defaultVisible: true, defaultWidth: 70 },
  { key: 'baseDifficulty', label: 'Difficulty', sortKey: 'baseDifficulty', defaultVisible: true, defaultWidth: 80 },
  { key: 'qualityScore', label: 'Quality', sortKey: 'qualityScore', defaultVisible: true, defaultWidth: 75 },
  { key: 'hintCount', label: 'Hints', sortKey: 'hintCount', defaultVisible: true, defaultWidth: 65 },
  { key: 'flags', label: 'Flags', sortKey: undefined, defaultVisible: true, defaultWidth: 110 },
  { key: 'hintType', label: 'Hint Type', sortKey: undefined, defaultVisible: false, defaultWidth: 100 },
  { key: 'isActive', label: 'Active', sortKey: undefined, defaultVisible: false, defaultWidth: 65 },
]

const COMBO_COLUMNS: ColumnDef[] = [
  { key: 'indicatorCode', label: 'Code', sortKey: 'indicatorCode', defaultVisible: true, defaultWidth: 200 },
  { key: 'displayName', label: 'Name', sortKey: 'displayName', defaultVisible: true, defaultWidth: 220 },
  { key: 'category', label: 'Category', sortKey: 'category', defaultVisible: true, defaultWidth: 120 },
  { key: 'familyType', label: 'Family', sortKey: 'familyType', defaultVisible: true, defaultWidth: 100 },
  { key: 'componentCount', label: 'Stats', sortKey: 'componentCount', defaultVisible: true, defaultWidth: 65 },
  { key: 'predicateCount', label: 'Rules', sortKey: 'predicateCount', defaultVisible: true, defaultWidth: 65 },
  { key: 'variantCount', label: 'Texts', sortKey: 'variantCount', defaultVisible: true, defaultWidth: 65 },
  { key: 'baseDifficulty', label: 'Difficulty', sortKey: 'baseDifficulty', defaultVisible: true, defaultWidth: 80 },
  { key: 'qualityScore', label: 'Quality', sortKey: 'qualityScore', defaultVisible: true, defaultWidth: 75 },
  { key: 'maxAmbiguityCount', label: 'Max Amb.', sortKey: 'maxAmbiguityCount', defaultVisible: true, defaultWidth: 85 },
  { key: 'hintCount', label: 'Hints', sortKey: 'hintCount', defaultVisible: true, defaultWidth: 65 },
  { key: 'hintType', label: 'Hint Type', sortKey: undefined, defaultVisible: false, defaultWidth: 100 },
  { key: 'isActive', label: 'Active', sortKey: undefined, defaultVisible: true, defaultWidth: 65 },
]

function hintCountColor(count: number): string {
  if (count === 0) return 'text-red-400/70'
  if (count < 5) return 'text-amber-400/70'
  return 'text-emerald-400/70'
}

function CellValue({ col, row }: { col: ColumnDef; row: IndicatorConfigAdminDto }) {
  switch (col.key) {
    case 'indicatorCode':
      return <span className="text-xs text-white/70 font-mono truncate" title={row.indicatorCode}>{row.indicatorCode}</span>
    case 'displayName':
      return <span className="text-sm text-white/80 truncate" title={row.displayName ?? ''}>{row.displayName ?? '—'}</span>
    case 'category':
      return row.category ? <Badge variant="info" className="text-[10px]">{row.category}</Badge> : <span className="text-white/25">—</span>
    case 'unit':
      return <span className="text-xs text-white/40">{row.unit || '—'}</span>
    case 'profileLevel':
      return row.profileLevel
        ? <Badge variant="neutral" className="text-[10px]">{row.profileLevel}</Badge>
        : <span className="text-white/25">—</span>
    case 'familyType':
      return row.familyType
        ? <Badge variant="neutral" className="text-[10px]">{row.familyType}</Badge>
        : <span className="text-white/25">—</span>
    case 'weight':
      return <span className="text-xs text-white/50 font-mono">{row.weight != null ? row.weight.toFixed(2) : '—'}</span>
    case 'baseDifficulty':
      return <span className="text-xs text-white/50 font-mono">{row.baseDifficulty ?? '—'}</span>
    case 'qualityScore':
      return <span className="text-xs text-white/50 font-mono">{row.qualityScore != null ? row.qualityScore.toFixed(2) : '—'}</span>
    case 'hintCount':
      return <span className={cn('text-sm font-mono font-medium', hintCountColor(row.hintCount))}>{row.hintCount}</span>
    case 'componentCount':
      return <span className="text-xs text-white/50 font-mono">{row.componentCount ?? '—'}</span>
    case 'predicateCount':
      return <span className="text-xs text-white/50 font-mono">{row.predicateCount ?? '—'}</span>
    case 'variantCount':
      return <span className="text-xs text-white/50 font-mono">{row.variantCount ?? '—'}</span>
    case 'maxAmbiguityCount':
      return <span className="text-xs text-white/50 font-mono">{row.maxAmbiguityCount ?? '—'}</span>
    case 'flags': {
      const flags = [
        row.highInterest && <Star key="interest" size={13} className="text-amber-400/70" aria-label="High Interest" />,
        row.isTrending && <TrendingUp key="trending" size={13} className="text-emerald-400/70" aria-label="Trending" />,
        row.lowerIsBetter && <ArrowDownNarrowWide key="lower" size={13} className="text-blue-400/70" aria-label="Lower is Better" />,
        row.excludeFromRanking && <ShieldOff key="ranking" size={13} className="text-red-400/50" aria-label="Excluded from Ranking" />,
      ].filter(Boolean)

      if (!flags.length) {
        return <span className="text-white/25">—</span>
      }

      return <div className="flex items-center gap-1.5">{flags}</div>
    }
    case 'hintType':
      return <span className="text-xs text-white/40">{row.hintType || '—'}</span>
    case 'isActive':
      return (
        <Badge variant={row.isActive ? 'success' : 'neutral'} className="text-[10px]">
          {row.isActive ? 'Yes' : 'No'}
        </Badge>
      )
    default:
      return null
  }
}

function renderField(label: string, value: string | null | undefined) {
  return (
    <div key={label} className="rounded-lg bg-white/[.03] px-3 py-2">
      <p className="text-[10px] text-white/25 uppercase tracking-widest">{label}</p>
      <p className="text-sm text-white/60 mt-0.5 truncate">{value || '—'}</p>
    </div>
  )
}

export function IndicatorsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToastStore()

  const [mode, setMode] = useState<IndicatorMode>('regular')
  const [showCacheConfirm, setShowCacheConfirm] = useState(false)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search)
  const [categoryFilter, setCategoryFilter] = useState('')
  const [profileLevelFilter, setProfileLevelFilter] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const [detail, setDetail] = useState<IndicatorConfigAdminDto | null>(null)

  const columns = mode === 'combo' ? COMBO_COLUMNS : REGULAR_COLUMNS
  const facetPlaceholder = mode === 'combo' ? 'Family' : 'Level'
  const facetEmptyLabel = mode === 'combo' ? 'All Families' : 'All Levels'
  const searchPlaceholder = mode === 'combo'
    ? 'Search code, name, templates, components...'
    : 'Search code, name, template...'

  const { containerRef, ...table } = useAdminTable({
    storageKey: `admin-indicators-${mode}`,
    columns,
    defaultSort: 'category',
    defaultSortDir: 'asc',
    pinnedColumns: ['actions'],
    onSortChange: () => setPage(0),
  })

  const handleModeChange = (tab: string) => {
    const nextMode = tab as IndicatorMode
    if (nextMode === mode) {
      return
    }

    setMode(nextMode)
    setCategoryFilter('')
    setProfileLevelFilter('')
    setActiveFilter('')
    setPage(0)
    setDetail(null)
    table.setSortBy('category')
    table.setSortDir('asc')
  }

  const { data, isLoading } = useQuery({
    queryKey: ['admin-indicators', mode, page, debouncedSearch, categoryFilter, profileLevelFilter, activeFilter, table.sortBy, table.sortDir],
    queryFn: () => adminApi.getIndicatorConfigs({
      kind: mode,
      page,
      size: PAGE_SIZE,
      search: debouncedSearch || undefined,
      category: categoryFilter || undefined,
      profileLevel: mode === 'regular' ? profileLevelFilter || undefined : undefined,
      familyType: mode === 'combo' ? profileLevelFilter || undefined : undefined,
      isActive: activeFilter === '' ? undefined : activeFilter === 'true',
      sortBy: table.sortBy,
      sortDir: table.sortDir,
    }),
  })

  const { data: categories } = useQuery({
    queryKey: ['admin-indicator-categories', mode],
    queryFn: () => adminApi.getIndicatorCategories(mode),
    staleTime: FILTER_STALE_TIME,
  })

  const { data: profileLevels } = useQuery({
    queryKey: ['admin-indicator-profile-levels', mode],
    queryFn: () => adminApi.getIndicatorProfileLevels(mode),
    staleTime: FILTER_STALE_TIME,
  })

  const cacheMutation = useMutation({
    mutationFn: () => adminApi.invalidateIndicatorCache(),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['admin-indicators'] })
      queryClient.invalidateQueries({ queryKey: ['admin-indicator-categories'] })
      queryClient.invalidateQueries({ queryKey: ['admin-indicator-profile-levels'] })
      toast.addToast({ type: 'success', message: `Cache invalidated (${response.evicted} keys)` })
    },
    onError: () => toast.addToast({ type: 'error', message: 'Failed to invalidate cache' }),
  })

  const hasFilters = !!(search || categoryFilter || profileLevelFilter || activeFilter)
  const activeFilterCount = [categoryFilter, profileLevelFilter, activeFilter].filter(Boolean).length
  const clearFilters = () => {
    setSearch('')
    setCategoryFilter('')
    setProfileLevelFilter('')
    setActiveFilter('')
    setPage(0)
  }

  const variantTemplates = detail?.variantTemplates
    ? detail.variantTemplates.split('\n---\n').filter(Boolean)
    : []

  return (
    <div className="flex flex-col h-full">
      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.indicators}</h1>
            <p className="text-white/40 text-sm mt-1">
              {data
                ? `${data.totalElements} ${mode === 'combo' ? 'combo indicator configs' : 'indicator configs'}`
                : 'Indicator configuration overview'}
            </p>
          </div>
          <div className="flex items-center gap-1.5">
            <Button
              variant="ghost" size="sm"
              onClick={() => setShowCacheConfirm(true)}
              disabled={cacheMutation.isPending}
              title="Invalidate Redis cache"
            >
              <Database size={14} className={cacheMutation.isPending ? 'animate-pulse' : ''} />
            </Button>
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

        <Tabs
          tabs={[
            { id: 'regular', label: 'Regular' },
            { id: 'combo', label: 'Combo' },
          ]}
          activeTab={mode}
          onChange={handleModeChange}
          className="w-fit"
        />

        <AdminFilterBar
          search={search}
          onSearchChange={v => { setSearch(v); setPage(0) }}
          searchPlaceholder={searchPlaceholder}
          activeFilterCount={activeFilterCount}
          onClearFilters={clearFilters}
          hasFilters={hasFilters}
        >
          <CustomSelect
            size="sm"
            value={categoryFilter}
            onChange={value => { setCategoryFilter(value); setPage(0) }}
            options={[{ value: '', label: 'All Categories' }, ...(categories?.map(category => ({ value: category, label: category })) ?? [])]}
            placeholder="Category"
          />
          <CustomSelect
            size="sm"
            value={profileLevelFilter}
            onChange={value => { setProfileLevelFilter(value); setPage(0) }}
            options={[{ value: '', label: facetEmptyLabel }, ...(profileLevels?.map(level => ({ value: level, label: level })) ?? [])]}
            placeholder={facetPlaceholder}
          />

          <div className="flex items-center gap-1">
            {(['', 'true', 'false'] as const).map(value => (
              <button
                key={value}
                type="button"
                onClick={() => { setActiveFilter(value); setPage(0) }}
                className={cn(
                  'h-8 px-2.5 rounded-lg text-xs transition-colors border',
                  activeFilter === value
                    ? 'bg-accent/15 border-accent/30 text-accent'
                    : 'bg-white/[.04] border-white/[.08] text-white/50 hover:bg-white/[.06]'
                )}
              >
                {value === '' ? 'All' : value === 'true' ? 'Active' : 'Inactive'}
              </button>
            ))}
          </div>
        </AdminFilterBar>
      </div>

      <div className="flex-1 min-h-0 px-5 sm:px-6 lg:px-8 pb-3">
        <div ref={containerRef} className="h-full border border-white/[.06] rounded-xl bg-white/[.03] overflow-auto scrollbar scrollbar-thumb-white/15 scrollbar-track-transparent [&::-webkit-scrollbar-corner]:bg-transparent">
          <AdminTableHeader
            dataCols={table.dataCols}
            hasActions={false}
            dataColsTemplate={table.dataColsTemplate}
            actionsW={40}
            sortBy={table.sortBy}
            sortDir={table.sortDir}
            onSort={table.handleSort}
            onResizeStart={table.onResizeStart}
            getWidth={table.getWidth}
          />

          {isLoading ? (
            <div className="p-5"><SkeletonTable rows={10} cols={table.activeCols.length} /></div>
          ) : !data?.content.length ? (
            <EmptyState icon={ListFilter} title={t.common.noResults} className="py-16" />
          ) : (
            data.content.map(row => (
              <div key={`${mode}-${row.indicatorCode}`}>
                <div
                  className="hidden sm:flex items-center border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors min-w-max cursor-pointer"
                  onClick={() => setDetail(row)}
                >
                  <div className="grid gap-0 px-3 py-2.5 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                    {table.dataCols.map(col => (
                      <div key={col.key} className="overflow-hidden px-2">
                        <CellValue col={col} row={row} />
                      </div>
                    ))}
                  </div>
                  <div className="w-[40px] shrink-0 flex justify-center sticky right-0" style={{ background: ACTIONS_GRAD }}>
                    <Eye size={14} className="text-white/20" />
                  </div>
                </div>
                <div
                  className="sm:hidden flex items-center gap-3 px-4 py-3 border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors cursor-pointer"
                  onClick={() => setDetail(row)}
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-0.5">
                      <span className="text-xs text-white/50 font-mono truncate">{row.indicatorCode}</span>
                      <Badge variant={row.isActive ? 'success' : 'neutral'} className="text-[9px] shrink-0">
                        {row.isActive ? 'Active' : 'Off'}
                      </Badge>
                    </div>
                    <p className="text-sm text-white/80 truncate">{row.displayName ?? '—'}</p>
                    <div className="flex items-center gap-1.5 mt-1 flex-wrap">
                      {row.category && <Badge variant="info" className="text-[9px]">{row.category}</Badge>}
                      {row.familyType && <Badge variant="neutral" className="text-[9px]">{row.familyType}</Badge>}
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <span className={cn('text-sm font-mono font-medium', hintCountColor(row.hintCount))}>{row.hintCount}</span>
                    <span className="text-[9px] text-white/25">hints</span>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {data && data.totalPages > 1 && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={setPage} />
        </div>
      )}

      <Modal
        isOpen={!!detail}
        onClose={() => setDetail(null)}
        title={detail?.indicatorKind === 'combo' ? 'Combo Indicator Details' : 'Indicator Details'}
        className="max-w-3xl"
      >
        {detail?.indicatorKind === 'combo' ? (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              {[
                renderField('Code', detail.indicatorCode),
                renderField('Name', detail.displayName),
                renderField('Category', detail.category),
                renderField('Family', detail.familyType),
                renderField('Hint Type', detail.hintType),
                renderField('Base Difficulty', detail.baseDifficulty != null ? String(detail.baseDifficulty) : null),
                renderField('Quality Score', detail.qualityScore != null ? detail.qualityScore.toFixed(2) : null),
                renderField('Hint Count', String(detail.hintCount)),
                renderField('Max Ambiguity', detail.maxAmbiguityCount != null ? String(detail.maxAmbiguityCount) : null),
                renderField('Stats Combined', detail.componentCount != null ? String(detail.componentCount) : null),
                renderField('Rules', detail.predicateCount != null ? String(detail.predicateCount) : null),
                renderField('Text Variants', detail.variantCount != null ? String(detail.variantCount) : null),
              ]}
            </div>

            <div className="flex items-center gap-3 flex-wrap">
              <Badge variant={detail.isActive ? 'success' : 'neutral'} className="text-[10px]">
                Active: {detail.isActive ? 'Yes' : 'No'}
              </Badge>
            </div>

            {detail.hintTemplate && (
              <div className="rounded-lg bg-white/[.03] border border-white/[.06] px-4 py-3">
                <p className="text-[10px] text-white/25 uppercase tracking-widest mb-2">Primary Template</p>
                <p className="text-sm text-white/60 leading-relaxed whitespace-pre-wrap">{detail.hintTemplate}</p>
              </div>
            )}

            {detail.componentsSummary && (
              <div className="rounded-lg bg-white/[.03] border border-white/[.06] px-4 py-3">
                <div className="flex items-center gap-2 mb-2">
                  <Layers3 size={14} className="text-cyan-300/70" />
                  <p className="text-[10px] text-white/25 uppercase tracking-widest">Combined Statistics</p>
                </div>
                <p className="text-sm text-white/60 leading-relaxed whitespace-pre-wrap font-mono">{detail.componentsSummary}</p>
              </div>
            )}

            {detail.predicatesSummary && (
              <div className="rounded-lg bg-white/[.03] border border-white/[.06] px-4 py-3">
                <div className="flex items-center gap-2 mb-2">
                  <GitBranchPlus size={14} className="text-emerald-300/70" />
                  <p className="text-[10px] text-white/25 uppercase tracking-widest">Rules</p>
                </div>
                <p className="text-sm text-white/60 leading-relaxed whitespace-pre-wrap font-mono">{detail.predicatesSummary}</p>
              </div>
            )}

            {variantTemplates.length > 0 && (
              <div className="rounded-lg bg-white/[.03] border border-white/[.06] px-4 py-3">
                <div className="flex items-center gap-2 mb-3">
                  <FileText size={14} className="text-fuchsia-300/70" />
                  <p className="text-[10px] text-white/25 uppercase tracking-widest">Text Variants</p>
                </div>
                <div className="space-y-2">
                  {variantTemplates.map((template, index) => (
                    <div key={`${detail.indicatorCode}-variant-${index + 1}`} className="rounded-lg bg-black/20 px-3 py-2.5">
                      <p className="text-[10px] text-white/25 uppercase tracking-widest mb-1">Variant {index + 1}</p>
                      <p className="text-sm text-white/65 leading-relaxed whitespace-pre-wrap">{template}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {detail.exampleHint && (
              <div className="rounded-lg bg-accent/[.05] border border-accent/[.15] px-4 py-3">
                <p className="text-[10px] text-accent/50 uppercase tracking-widest mb-2">Existing Example Hint</p>
                <p className="text-sm text-white/70 leading-relaxed">{detail.exampleHint}</p>
              </div>
            )}

            <div className="flex justify-end pt-2">
              <Button variant="ghost" onClick={() => setDetail(null)}>{t.common.close}</Button>
            </div>
          </div>
        ) : detail ? (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              {[
                renderField('Code', detail.indicatorCode),
                renderField('Name', detail.displayName),
                renderField('Category', detail.category),
                renderField('Unit', detail.unit),
                renderField('Profile Level', detail.profileLevel),
                renderField('Hint Type', detail.hintType),
                renderField('Weight', detail.weight != null ? String(detail.weight) : null),
                renderField('Base Difficulty', detail.baseDifficulty != null ? String(detail.baseDifficulty) : null),
                renderField('Quality Score', detail.qualityScore != null ? detail.qualityScore.toFixed(2) : null),
                renderField('Hint Count', String(detail.hintCount)),
                renderField('Trending Frequency', detail.trendingFrequency),
              ]}
            </div>

            <div className="flex items-center gap-3 flex-wrap">
              {[
                { label: 'Active', value: detail.isActive, variant: 'success' as const },
                { label: 'High Interest', value: detail.highInterest, variant: 'warning' as const },
                { label: 'Trending', value: detail.isTrending, variant: 'info' as const },
                { label: 'Lower is Better', value: detail.lowerIsBetter, variant: 'neutral' as const },
                { label: 'Exclude Ranking', value: detail.excludeFromRanking, variant: 'error' as const },
                { label: 'Exclude Percentile', value: detail.excludeFromPercentile, variant: 'error' as const },
              ].map(flag => (
                <Badge key={flag.label} variant={flag.value ? flag.variant : 'neutral'} className="text-[10px]">
                  {flag.label}: {flag.value ? 'Yes' : 'No'}
                </Badge>
              ))}
            </div>

            {detail.hintTemplate && (
              <div className="rounded-lg bg-white/[.03] border border-white/[.06] px-4 py-3">
                <p className="text-[10px] text-white/25 uppercase tracking-widest mb-2">Hint Template</p>
                <p className="text-sm text-white/60 leading-relaxed whitespace-pre-wrap">{detail.hintTemplate}</p>
              </div>
            )}

            {detail.exampleHint && (
              <div className="rounded-lg bg-accent/[.05] border border-accent/[.15] px-4 py-3">
                <p className="text-[10px] text-accent/50 uppercase tracking-widest mb-2">Existing Example Hint</p>
                <p className="text-sm text-white/70 leading-relaxed">{detail.exampleHint}</p>
              </div>
            )}

            <div className="flex justify-end pt-2">
              <Button variant="ghost" onClick={() => setDetail(null)}>{t.common.close}</Button>
            </div>
          </div>
        ) : null}
      </Modal>

      <ConfirmDialog
        isOpen={showCacheConfirm}
        onClose={() => setShowCacheConfirm(false)}
        onConfirm={() => { cacheMutation.mutate(); setShowCacheConfirm(false) }}
        title="Invalidate indicator cache?"
        description="All cached regular and combo indicator data in Redis will be cleared. Fresh data will be loaded from the database."
        confirmLabel="Invalidate"
        variant="danger"
        isLoading={cacheMutation.isPending}
      />
    </div>
  )
}
