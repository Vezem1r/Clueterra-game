import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Globe2, Edit2, Eye, EyeOff, RefreshCw, Lightbulb,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { AdminTableHeader } from '@/components/admin/AdminTableHeader'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { TableToolbarButtons } from '@/components/admin/TableToolbarButtons'
import { adminApi } from '@/api/admin'
import { useDebounce } from '@/hooks/useDebounce'
import { useAdminTable, type ColumnDef } from '@/hooks/useAdminTable'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import type { CountryAdminDto } from '@/types'
import { cn } from '@/utils/cn'
import { getFlagUrlByIso2 } from '@/utils/country'
import { extractApiErrorMessage, extractFieldErrors } from '@/utils/errors'

const FIELD_LABELS: Record<string, string> = {
  nameEn: 'English Name', nameNative: 'Native Name', capital: 'Capital',
  difficultyModifier: 'Difficulty Modifier', isActive: 'Status',
  isoAlpha2: 'ISO α2', isoAlpha3: 'ISO α3', region: 'Region',
}

const ACTIONS_GRAD = 'linear-gradient(to right, transparent, rgb(15,15,20) 28px, rgb(15,15,20))'

function extractApiError(err: unknown, fallback: string): string {
  const fieldErrors = extractFieldErrors(err)
  if (fieldErrors) {
    return Object.entries(fieldErrors).map(([f, m]) => `${FIELD_LABELS[f] || f}: ${m}`).join('; ')
  }
  return extractApiErrorMessage(err, fallback)
}

const ALL_COLUMNS: ColumnDef[] = [
  { key: 'flag',             label: '🏳',         sortKey: undefined,               defaultVisible: true,  defaultWidth: 36  },
  { key: 'isoAlpha2',        label: 'ISO',         sortKey: 'isoAlpha2',             defaultVisible: true,  defaultWidth: 52  },
  { key: 'nameEn',           label: 'Name',        sortKey: 'nameEn',                defaultVisible: true,  defaultWidth: 200 },
  { key: 'region',           label: 'Region',      sortKey: 'region',                defaultVisible: true,  defaultWidth: 110 },
  { key: 'capital',          label: 'Capital',     sortKey: 'capital',               defaultVisible: true,  defaultWidth: 110 },
  { key: 'subregion',        label: 'Subregion',   sortKey: 'subregion',             defaultVisible: false, defaultWidth: 120 },
  { key: 'nameNative',       label: 'Native Name', sortKey: undefined,               defaultVisible: false, defaultWidth: 120 },
  { key: 'difficultyModifier', label: 'Difficulty',sortKey: 'difficultyModifier',    defaultVisible: false, defaultWidth: 80  },
  { key: 'timesFeatured',    label: 'Featured',    sortKey: 'timesFeatured',         defaultVisible: false, defaultWidth: 80  },
  { key: 'successRate',      label: 'Win%',        sortKey: 'successRate',           defaultVisible: false, defaultWidth: 70  },
  { key: 'hintCount',        label: 'Hints',       sortKey: 'hintCount',             defaultVisible: true,  defaultWidth: 70  },
  { key: 'statisticCount',   label: 'Stats',       sortKey: 'statisticCount',        defaultVisible: true,  defaultWidth: 70  },
  { key: 'isActive',         label: 'Status',      sortKey: 'isActive',              defaultVisible: true,  defaultWidth: 90  },
  { key: 'actions',          label: '',            sortKey: undefined,               defaultVisible: true,  defaultWidth: 152 },
]

function CellValue({ col, country }: { col: ColumnDef; country: CountryAdminDto }) {
  switch (col.key) {
    case 'flag':
      return (
        <img
          src={getFlagUrlByIso2(country.isoAlpha2, 'w20') ?? ''}
          alt={country.isoAlpha2}
          width={20}
          height={15}
          className="rounded-[2px] object-cover shrink-0"
          loading="lazy"
          onError={e => { (e.target as HTMLImageElement).style.display = 'none' }}
        />
      )
    case 'isoAlpha2':
      return <span className="font-mono text-xs text-white/50">{country.isoAlpha2}</span>
    case 'nameEn':
      return <span className="text-sm text-white/85 truncate">{country.nameEn}</span>
    case 'region':
      return <span className="text-xs text-white/40 truncate">{country.region || '—'}</span>
    case 'capital':
      return <span className="text-xs text-white/40 truncate">{country.capital || '—'}</span>
    case 'subregion':
      return <span className="text-xs text-white/35 truncate">{country.subregion || '—'}</span>
    case 'nameNative':
      return <span className="text-xs text-white/35 truncate">{country.nameNative || '—'}</span>
    case 'difficultyModifier':
      return <span className="text-xs text-white/50 font-mono">{country.difficultyModifier ?? '—'}</span>
    case 'timesFeatured':
      return <span className="text-xs text-white/50 font-mono">{country.timesFeatured ?? 0}</span>
    case 'successRate':
      return (
        <span className="text-xs text-white/50 font-mono">
          {country.successRate != null ? `${Number(country.successRate).toFixed(1)}%` : '—'}
        </span>
      )
    case 'hintCount':
      return (
        <span className="text-xs font-mono text-amber-400/80">
          {country.hintCount != null ? country.hintCount : '…'}
        </span>
      )
    case 'statisticCount':
      return (
        <span className="text-xs font-mono text-sky-400/80">
          {country.statisticCount != null ? country.statisticCount : '…'}
        </span>
      )
    case 'isActive':
      return (
        <Badge variant={country.isActive !== false ? 'success' : 'neutral'} className="text-[10px]">
          {country.isActive !== false ? 'Active' : 'Inactive'}
        </Badge>
      )
    default:
      return null
  }
}

export function CountriesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToastStore()

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [regionFilter, setRegionFilter] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const debouncedSearch = useDebounce(search)

  const { containerRef, ...table } = useAdminTable({
    storageKey: 'admin-countries',
    columns: ALL_COLUMNS,
    defaultSort: 'nameEn',
    defaultSortDir: 'asc',
    pinnedColumns: ['flag', 'actions'],
    onSortChange: () => setPage(0),
  })

  const [editingCountry, setEditingCountry] = useState<CountryAdminDto | null>(null)
  const [editForm, setEditForm] = useState({
    nameEn: '',
    nameNative: '',
    capital: '',
    difficultyModifier: '1',
    isActive: true,
  })

  const handleEdit = (country: CountryAdminDto) => {
    setEditingCountry(country)
    setEditForm({
      nameEn: country.nameEn,
      nameNative: country.nameNative || '',
      capital: country.capital || '',
      difficultyModifier: String(country.difficultyModifier ?? 1),
      isActive: country.isActive !== false,
    })
  }

  const { data: countries, isLoading } = useQuery({
    queryKey: ['admin-countries', page, debouncedSearch, regionFilter, activeFilter, table.sortBy, table.sortDir],
    queryFn: () => adminApi.getCountries({
      search: debouncedSearch || undefined,
      region: regionFilter || undefined,
      active: activeFilter === '' ? undefined : activeFilter === 'true',
      page,
      size: 25,
      sortBy: table.sortBy,
      sortDir: table.sortDir,
    }),
  })

  const { data: regions } = useQuery({
    queryKey: ['admin-regions'],
    queryFn: adminApi.getRegions,
    staleTime: 300_000,
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      adminApi.toggleCountryActive(id, active),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-countries'] })
      toast.addToast({ type: 'success', message: 'Status updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to update status') }),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<CountryAdminDto> }) =>
      adminApi.updateCountry(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-countries'] })
      setEditingCountry(null)
      toast.addToast({ type: 'success', message: 'Country updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to update country') }),
  })

  const syncMutation = useMutation({
    mutationFn: (code: string) => adminApi.syncCountry(code),
    onSuccess: () => toast.addToast({ type: 'success', message: 'Sync started' }),
  })

  const hintsMutation = useMutation({
    mutationFn: (code: string) => adminApi.generateHintsForCountry(code),
    onSuccess: () => toast.addToast({ type: 'success', message: 'Hint generation started' }),
  })

  const handleSave = () => {
    if (!editingCountry) return
    updateMutation.mutate({
      id: editingCountry.id,
      data: {
        nameEn: editForm.nameEn,
        nameNative: editForm.nameNative || undefined,
        capital: editForm.capital || undefined,
        difficultyModifier: parseFloat(editForm.difficultyModifier) || 1,
        isActive: editForm.isActive,
      } as Partial<CountryAdminDto>,
    })
  }

  const hasFilters = !!(search || regionFilter || activeFilter)
  const activeFilterCount = [regionFilter, activeFilter].filter(Boolean).length
  const clearFilters = () => { setSearch(''); setRegionFilter(''); setActiveFilter(''); setPage(0) }

  return (
    <div className="flex flex-col h-full">

      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.countries}</h1>
          <p className="text-white/40 text-sm mt-1">Manage countries and their settings</p>
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
        searchPlaceholder="Search countries..."
        activeFilterCount={activeFilterCount}
        onClearFilters={clearFilters}
        hasFilters={hasFilters}
        resultCount={countries?.totalElements}
      >
        <CustomSelect
          value={regionFilter}
          onChange={v => { setRegionFilter(v); setPage(0) }}
          options={[
            { value: '', label: 'All Regions' },
            ...(regions?.map(r => ({ value: r, label: r })) ?? []),
          ]}
          placeholder="All Regions"
        />
        <CustomSelect
          value={activeFilter}
          onChange={v => { setActiveFilter(v); setPage(0) }}
          options={[
            { value: '', label: t.common.all },
            { value: 'true', label: t.common.active },
            { value: 'false', label: t.common.inactive },
          ]}
          placeholder={t.common.all}
        />
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
          centerColumns={['isActive']}
        />

        {isLoading ? (
          <div className="p-5"><SkeletonTable rows={8} cols={table.activeCols.length} /></div>
        ) : !countries?.content?.length ? (
          <EmptyState icon={Globe2} title={t.common.noResults} className="py-16" />
        ) : (
          countries.content.map(country => (
            <div key={country.id} className={cn('group', country.isActive === false && 'opacity-60')}>
              <div className="hidden sm:flex items-center min-w-max border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors">
                <div className="grid gap-0 px-3 py-3 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                  {table.dataCols.map(col => (
                    <div
                      key={col.key}
                      className={cn(
                        'overflow-hidden px-2',
                        col.key === 'isActive' && 'flex justify-center',
                      )}
                    >
                      <CellValue col={col} country={country} />
                    </div>
                  ))}
                </div>
                {table.hasActions && (
                  <div ref={table.actionsRefCb} className="sticky right-0 z-10 shrink-0 flex items-center gap-0.5 justify-end pl-8 pr-2 ml-auto"
                    style={{ background: ACTIONS_GRAD }}>
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(country)} title={t.common.edit}>
                      <Edit2 size={13} />
                    </Button>
                    <Button
                      variant="ghost" size="sm"
                      onClick={() => syncMutation.mutate(country.isoAlpha2)}
                      title="Sync"
                      disabled={syncMutation.isPending}
                    >
                      <RefreshCw size={13} />
                    </Button>
                    <Button
                      variant="ghost" size="sm"
                      onClick={() => hintsMutation.mutate(country.isoAlpha2)}
                      title={t.admin.generateHints}
                      disabled={hintsMutation.isPending}
                    >
                      <Lightbulb size={13} />
                    </Button>
                    <Button
                      variant="ghost" size="sm"
                      onClick={() => toggleMutation.mutate({ id: country.id, active: country.isActive === false })}
                      title={country.isActive !== false ? 'Deactivate' : 'Activate'}
                      disabled={toggleMutation.isPending}
                    >
                      {country.isActive !== false
                        ? <Eye size={13} className="text-emerald-400" />
                        : <EyeOff size={13} className="text-white/30" />}
                    </Button>
                  </div>
                )}
              </div>

              <div className="sm:hidden flex items-center gap-3 px-4 py-3 border-b border-white/[.04]">
                <img
                  src={getFlagUrlByIso2(country.isoAlpha2, 'w40') ?? ''}
                  alt={country.isoAlpha2}
                  width={32}
                  height={24}
                  className="rounded-[3px] object-cover shrink-0"
                  loading="lazy"
                />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-white/80 truncate">{country.nameEn}</p>
                  <p className="text-xs text-white/35">{country.isoAlpha2} · {country.region}</p>
                </div>
                <Badge variant={country.isActive !== false ? 'success' : 'neutral'}>
                  {country.isActive !== false ? 'Active' : 'Off'}
                </Badge>
                <Button variant="ghost" size="sm" onClick={() => handleEdit(country)}>
                  <Edit2 size={14} />
                </Button>
              </div>
            </div>
          ))
        )}
      </div>
      </div>

      {countries && countries.totalPages > 1 && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination
            page={page}
            totalPages={countries.totalPages}
            totalElements={countries.totalElements}
            onPageChange={setPage}
          />
        </div>
      )}

      <Modal
        isOpen={!!editingCountry}
        onClose={() => setEditingCountry(null)}
        title={editingCountry ? `${editingCountry.flagEmoji || ''} ${editingCountry.nameEn} — Edit`.trim() : ''}
      >
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-white/35 mb-1.5">English Name</label>
              <Input value={editForm.nameEn} onChange={e => setEditForm(f => ({ ...f, nameEn: e.target.value }))} />
            </div>
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Native Name</label>
              <Input
                value={editForm.nameNative}
                onChange={e => setEditForm(f => ({ ...f, nameNative: e.target.value }))}
                placeholder="Optional"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Capital</label>
              <Input
                value={editForm.capital}
                onChange={e => setEditForm(f => ({ ...f, capital: e.target.value }))}
                placeholder="Optional"
              />
            </div>
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Difficulty Modifier</label>
              <Input
                type="number"
                step="0.1"
                min="0.1"
                max="5"
                value={editForm.difficultyModifier}
                onChange={e => setEditForm(f => ({ ...f, difficultyModifier: e.target.value }))}
              />
            </div>
          </div>

          {editingCountry && (
            <div className="grid grid-cols-3 gap-3 pt-1">
              {[
                { label: 'ISO α2', value: editingCountry.isoAlpha2 },
                { label: 'ISO α3', value: editingCountry.isoAlpha3 || '—' },
                { label: 'Region', value: editingCountry.region },
              ].map(f => (
                <div key={f.label} className="rounded-lg bg-white/[.03] px-3 py-2">
                  <p className="text-[10px] text-white/25 uppercase tracking-widest">{f.label}</p>
                  <p className="text-sm text-white/60 mt-0.5">{f.value}</p>
                </div>
              ))}
            </div>
          )}

          <div className="flex items-center gap-3 pt-1">
            <label className="flex items-center gap-2.5 cursor-pointer">
              <div
                onClick={() => setEditForm(f => ({ ...f, isActive: !f.isActive }))}
                className={cn(
                  'w-9 h-5 rounded-full relative transition-colors cursor-pointer',
                  editForm.isActive ? 'bg-emerald-500/70' : 'bg-white/[.08]'
                )}
              >
                <div className={cn(
                  'absolute top-0.5 w-4 h-4 rounded-full bg-white transition-all',
                  editForm.isActive ? 'left-4' : 'left-0.5'
                )} />
              </div>
              <span className="text-sm text-white/60">Active</span>
            </label>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setEditingCountry(null)}>{t.common.cancel}</Button>
            <Button onClick={handleSave} isLoading={updateMutation.isPending}>{t.common.save}</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
