import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  Calendar, Eye, EyeOff, Zap, Plus, Database, Info,
  X, Edit2, Check, Pencil, RefreshCw, ImageIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { DatePicker } from '@/components/ui/DatePicker'
import { AdminTableHeader } from '@/components/admin/AdminTableHeader'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { AdminStats } from '@/components/admin/AdminStats'
import { TableToolbarButtons } from '@/components/admin/TableToolbarButtons'
import { adminApi, type ChallengeAdminRow } from '@/api/admin'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import { useAdminTable, type ColumnDef } from '@/hooks/useAdminTable'
import { cn } from '@/utils/cn'
import { getFlagUrlByIso2 } from '@/utils/country'
import { extractApiErrorMessage } from '@/utils/errors'


const levelColors: Record<string, 'success' | 'warning' | 'error'> = {
  EASY: 'success', MEDIUM: 'warning', HARD: 'error',
}
const diffColors: Record<string, string> = {
  EASY: 'text-emerald-400/80', MEDIUM: 'text-amber-400/80',
  HARD: 'text-red-400/80', EXPERT: 'text-purple-400/80',
}

const ACTIONS_GRAD = 'linear-gradient(to right, transparent, rgb(15,15,20) 28px, rgb(15,15,20))'
const ACTIONS_GRAD_TODAY = 'linear-gradient(to right, transparent, rgb(17,17,26) 28px, rgb(17,17,26))'

type ChallengeCountry = { order: number; countryId: number; code: string; name: string }
type ChallengeHint = {
  countryOrder: number
  hintOrder: number
  hintId: string
  text: string
  difficulty: number
  category: string
  type: string
  source: string
  imageUrl?: string | null
}

const ALL_COLUMNS: ColumnDef[] = [
  { key: 'challengeDate',  label: 'Date',        sortKey: 'challengeDate',  defaultVisible: true,  defaultWidth: 120 },
  { key: 'challengeLevel', label: 'Level',       sortKey: 'challengeLevel', defaultVisible: true,  defaultWidth: 90  },
  { key: 'difficulty',     label: 'Difficulty',  sortKey: 'difficulty',     defaultVisible: true,  defaultWidth: 100 },
  { key: 'totalCountries', label: 'Countries',   sortKey: 'totalCountries', defaultVisible: true,  defaultWidth: 80  },
  { key: 'totalHints',     label: 'Hints',       sortKey: 'totalHints',     defaultVisible: true,  defaultWidth: 70  },
  { key: 'isActive',       label: 'Status',      sortKey: 'isActive',       defaultVisible: true,  defaultWidth: 80  },
  { key: 'createdAt',      label: 'Created',     sortKey: 'createdAt',      defaultVisible: false, defaultWidth: 150 },
  { key: 'actions',        label: '',            sortKey: undefined,        defaultVisible: true,  defaultWidth: 140 },
]

function CellValue({ col, row, isToday }: { col: ColumnDef; row: ChallengeAdminRow; isToday?: boolean }) {
  switch (col.key) {
    case 'challengeDate':
      return (
        <div className="flex items-center gap-2">
          <Calendar size={13} className="text-white/20 shrink-0" />
          <span className="text-sm text-white/75 font-mono">{row.challengeDate}</span>
          {isToday && (
            <span className="text-[9px] font-bold tracking-wider bg-accent/15 text-accent border border-accent/20 px-1.5 py-0.5 rounded-full leading-none">TODAY</span>
          )}
        </div>
      )
    case 'challengeLevel':
      return <Badge variant={levelColors[row.challengeLevel] || 'neutral'} className="text-[10px]">{row.challengeLevel}</Badge>
    case 'difficulty':
      return <span className={cn('text-xs font-mono', diffColors[row.difficulty] || 'text-white/50')}>{row.difficulty}</span>
    case 'totalCountries':
      return <span className="text-xs text-white/50 font-mono">{row.totalCountries}</span>
    case 'totalHints':
      return <span className="text-xs text-white/50 font-mono">{row.totalHints}</span>
    case 'isActive':
      return (
        <Badge variant={row.isActive ? 'success' : 'neutral'} className="text-[10px]">
          {row.isActive ? 'Active' : 'Inactive'}
        </Badge>
      )
    case 'createdAt':
      return <span className="text-xs text-white/35 truncate">{row.createdAt?.replace('T', ' ').slice(0, 19)}</span>
    default:
      return null
  }
}

const fmtDate = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const todayStr = () => fmtDate(new Date())
const yesterdayStr = () => { const d = new Date(); d.setDate(d.getDate() - 1); return fmtDate(d) }

export function ChallengesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToastStore()

  const [page, setPage] = useState(0)
  const [levelFilter, setLevelFilter] = useState('')
  const [diffFilter, setDiffFilter] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const [startDate, setStartDate] = useState(todayStr)
  const [endDate, setEndDate] = useState('')

  const { containerRef, ...table } = useAdminTable({
    storageKey: 'admin-challenges',
    columns: ALL_COLUMNS,
    defaultSort: 'challengeDate',
    defaultSortDir: 'asc',
    pinnedColumns: ['actions'],
    onSortChange: () => setPage(0),
  })

  const [activePreset, setActivePreset] = useState<string | null>('active')
  const applyPreset = (preset: string) => {
    if (activePreset === preset) {
      setActivePreset(null); setStartDate(''); setEndDate('')
      table.setSortBy('challengeDate'); table.setSortDir('desc')
      setPage(0)
      return
    }
    setActivePreset(preset)
    if (preset === 'active') {
      setStartDate(todayStr()); setEndDate('')
      table.setSortBy('challengeDate'); table.setSortDir('asc')
    } else if (preset === 'past') {
      setStartDate(''); setEndDate(yesterdayStr())
      table.setSortBy('challengeDate'); table.setSortDir('desc')
    }
    setPage(0)
  }

  const [viewingChallenge, setViewingChallenge] = useState<ChallengeAdminRow | null>(null)
  const [editingChallenge, setEditingChallenge] = useState<ChallengeAdminRow | null>(null)
  const [editForm, setEditForm] = useState({ challengeLevel: '', difficulty: '' })
  const [showGenerate, setShowGenerate] = useState(false)
  const [generateDate, setGenerateDate] = useState('')
  const [generateLevel, setGenerateLevel] = useState('')
  const [showCacheConfirm, setShowCacheConfirm] = useState(false)

  const [selectedCountryOrder, setSelectedCountryOrder] = useState<number | null>(null)
  const [editingHint, setEditingHint] = useState<{ id: string; text: string } | null>(null)
  const [rerollCountryConfirm, setRerollCountryConfirm] = useState<{ challengeId: string; countryOrder: number; countryName: string } | null>(null)
  const [rerollHintConfirm, setRerollHintConfirm] = useState<{ challengeId: string; countryOrder: number; hintOrder: number } | null>(null)

  const { data: challenges, isLoading } = useQuery({
    queryKey: ['admin-challenges', page, levelFilter, diffFilter, activeFilter, startDate, endDate, table.sortBy, table.sortDir],
    queryFn: () => adminApi.getChallenges({
      page, size: 30,
      level: levelFilter || undefined,
      difficulty: diffFilter || undefined,
      active: activeFilter === '' ? undefined : activeFilter === 'true',
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      sortBy: table.sortBy, sortDir: table.sortDir,
    }),
  })

  const { data: challengeStats } = useQuery({
    queryKey: ['admin-challenge-stats'],
    queryFn: adminApi.getChallengeStats,
  })

  const { data: levels } = useQuery({
    queryKey: ['admin-challenge-levels'],
    queryFn: adminApi.getChallengeLevels,
    staleTime: 300_000,
  })

  const { data: difficulties } = useQuery({
    queryKey: ['admin-challenge-difficulties'],
    queryFn: adminApi.getChallengeDifficulties,
    staleTime: 300_000,
  })

  const { data: challengeDetail } = useQuery({
    queryKey: ['admin-challenge-detail', viewingChallenge?.id],
    queryFn: () => adminApi.getChallenge(viewingChallenge!.id),
    enabled: !!viewingChallenge,
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      adminApi.toggleChallengeActive(id, active),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-challenges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-stats'] })
      toast.addToast({ type: 'success', message: 'Status updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to update status') }),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: { challengeLevel: string; difficulty: string } }) =>
      adminApi.updateChallenge(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-challenges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-stats'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-levels'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-difficulties'] })
      setEditingChallenge(null)
      toast.addToast({ type: 'success', message: 'Challenge updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to update challenge') }),
  })

  const updateHintMutation = useMutation({
    mutationFn: ({ id, text }: { id: string; text: string }) =>
      adminApi.updateHint(id, { hintText: text } as never),
    onSuccess: (_, { id, text }) => {
      queryClient.setQueryData(
        ['admin-challenge-detail', viewingChallenge?.id],
        (old: ChallengeAdminRow | undefined) => {
          if (!old?.hintsJson) return old
          try {
            const safe = old.hintsJson.replace(/"hintId"\s*:\s*(\d+)/g, '"hintId":"$1"')
            const parsed = JSON.parse(safe) as ChallengeHint[]
            return { ...old, hintsJson: JSON.stringify(parsed.map(h => h.hintId === id ? { ...h, text } : h)) }
          } catch { return old }
        },
      )
      setEditingHint(null)
      toast.addToast({ type: 'success', message: 'Hint updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to update hint') }),
  })

  const generateTodayMutation = useMutation({
    mutationFn: () => adminApi.generateTodayChallenge(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-challenges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-stats'] })
      toast.addToast({ type: 'success', message: 'Today\'s challenges generated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to generate') }),
  })

  const generateDateMutation = useMutation({
    mutationFn: ({ date, level }: { date: string; level?: string }) =>
      adminApi.generateChallengeForDate(date, level || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-challenges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-stats'] })
      setShowGenerate(false); setGenerateDate(''); setGenerateLevel('')
      toast.addToast({ type: 'success', message: 'Challenge generated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to generate') }),
  })

  const generateUpcomingMutation = useMutation({
    mutationFn: (days: number) => adminApi.generateUpcoming(days),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-challenges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-stats'] })
      toast.addToast({ type: 'success', message: 'Upcoming challenges generated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to generate') }),
  })

  const cacheMutation = useMutation({
    mutationFn: () => adminApi.invalidateChallengeCache(),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin-challenges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-stats'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-levels'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-difficulties'] })
      toast.addToast({ type: 'success', message: `Cache invalidated (${data.evicted} keys)` })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to invalidate cache') }),
  })

  const rerollCountryMutation = useMutation({
    mutationFn: ({ challengeId, countryOrder }: { challengeId: string; countryOrder: number }) =>
      adminApi.rerollChallengeCountry(challengeId, countryOrder),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-challenges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-detail', viewingChallenge?.id] })
      toast.addToast({ type: 'success', message: 'Country rerolled' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to reroll country') }),
  })

  const rerollHintMutation = useMutation({
    mutationFn: ({ challengeId, countryOrder, hintOrder }: { challengeId: string; countryOrder: number; hintOrder: number }) =>
      adminApi.rerollChallengeHint(challengeId, countryOrder, hintOrder),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-challenges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-challenge-detail', viewingChallenge?.id] })
      toast.addToast({ type: 'success', message: 'Hint rerolled' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to reroll hint') }),
  })

  const hasFilters = !!(levelFilter || diffFilter || activeFilter || (!activePreset && (startDate || endDate)))
  const activeFilterCount = [levelFilter, diffFilter, activeFilter, !activePreset && startDate, !activePreset && endDate].filter(Boolean).length
  const clearFilters = () => {
    setLevelFilter(''); setDiffFilter(''); setActiveFilter('')
    applyPreset('active')
  }

  const countries = useMemo(() => {
    if (!challengeDetail?.countriesJson) return []
    try { return JSON.parse(challengeDetail.countriesJson) as ChallengeCountry[] }
    catch { return [] }
  }, [challengeDetail])

  const hints = useMemo(() => {
    if (!challengeDetail?.hintsJson) return []
    try {
      const safe = challengeDetail.hintsJson.replace(/"hintId"\s*:\s*(\d+)/g, '"hintId":"$1"')
      return (JSON.parse(safe) as ChallengeHint[])
        .sort((a, b) => a.countryOrder - b.countryOrder || a.hintOrder - b.hintOrder)
    } catch { return [] }
  }, [challengeDetail])

  const hintsByCountry = useMemo(() => {
    const map = new Map<number, typeof hints>()
    for (const h of hints) {
      let arr = map.get(h.countryOrder)
      if (!arr) { arr = []; map.set(h.countryOrder, arr) }
      arr.push(h)
    }
    return map
  }, [hints])

  const effectiveSelectedCountryOrder = useMemo(() => {
    if (countries.length === 0) return null
    return selectedCountryOrder != null && countries.some(country => country.order === selectedCountryOrder)
      ? selectedCountryOrder
      : countries[0].order
  }, [countries, selectedCountryOrder])

  const selectedCountry = useMemo(
    () => countries.find(c => c.order === effectiveSelectedCountryOrder),
    [countries, effectiveSelectedCountryOrder],
  )
  const selectedCountryHints = useMemo(
    () => (effectiveSelectedCountryOrder != null ? hintsByCountry.get(effectiveSelectedCountryOrder) ?? [] : []),
    [effectiveSelectedCountryOrder, hintsByCountry],
  )

  const activeChallenge = challengeDetail?.id === viewingChallenge?.id ? challengeDetail : viewingChallenge
  const fallbackChallengeDateKind = useMemo(() => {
    if (!activeChallenge) return 'past' as const
    const today = todayStr()
    const d = activeChallenge.challengeDate
    if (d < today) return 'past' as const
    if (d === today) return 'today' as const
    return 'future' as const
  }, [activeChallenge])

  const canRerollCountry = activeChallenge?.canRerollCountry ?? (fallbackChallengeDateKind === 'future')
  const canRerollHint = activeChallenge?.canRerollHint ?? (fallbackChallengeDateKind === 'today' || fallbackChallengeDateKind === 'future')

  const renderActions = (row: ChallengeAdminRow, isToday: boolean, isTodayActive: boolean) => (
    <div
      ref={table.actionsRefCb}
      className="sticky right-0 z-10 shrink-0 flex items-center gap-0.5 justify-end pl-8 pr-2 ml-auto"
      style={{ background: isToday ? ACTIONS_GRAD_TODAY : ACTIONS_GRAD }}
    >
      <Button variant="ghost" size="sm" onClick={() => setViewingChallenge(row)} title="View details">
        <Info size={13} />
      </Button>
      <Button variant="ghost" size="sm" onClick={() => { setEditingChallenge(row); setEditForm({ challengeLevel: row.challengeLevel, difficulty: row.difficulty }) }} title="Edit">
        <Edit2 size={13} />
      </Button>
      <div title={isTodayActive ? "Can't deactivate today's challenge" : row.isActive ? 'Deactivate' : 'Activate'}>
        <Button variant="ghost" size="sm"
          onClick={() => toggleMutation.mutate({ id: row.id, active: !row.isActive })}
          disabled={toggleMutation.isPending || isTodayActive}
        >
          {row.isActive
            ? <Eye size={13} className={isTodayActive ? 'text-emerald-400/40' : 'text-emerald-400'} />
            : <EyeOff size={13} className="text-white/30" />}
        </Button>
      </div>
    </div>
  )

  return (
    <div className="flex flex-col h-full">

      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.challenges}</h1>
          <p className="text-white/40 text-sm mt-1">View and manage daily challenges</p>
        </div>
        <div className="flex items-center gap-1.5 flex-wrap">
          <Button variant="outline" size="sm" onClick={() => generateTodayMutation.mutate()} isLoading={generateTodayMutation.isPending}>
            <Zap size={14} /> {t.admin.generateToday}
          </Button>
          <Button variant="outline" size="sm" onClick={() => generateUpcomingMutation.mutate(7)} isLoading={generateUpcomingMutation.isPending}>
            <Calendar size={14} /> {t.admin.generateUpcoming}
          </Button>
          <Button size="sm" onClick={() => setShowGenerate(true)}>
            <Plus size={14} /> Custom
          </Button>
          <div className="w-px h-5 bg-white/[.08] mx-1" />
          <Button variant="ghost" size="sm" onClick={() => setShowCacheConfirm(true)} disabled={cacheMutation.isPending} title="Invalidate Redis cache">
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

      {challengeStats && (
        <AdminStats items={[
          { label: 'Total', value: challengeStats.totalChallenges },
          { label: t.common.active, value: challengeStats.activeChallenges },
          { label: t.common.inactive, value: challengeStats.inactiveChallenges },
          { label: 'Levels', value: challengeStats.levels },
          { label: 'Difficulties', value: challengeStats.difficulties },
        ]} />
      )}

      <AdminFilterBar
        activeFilterCount={activeFilterCount}
        onClearFilters={clearFilters}
        hasFilters={hasFilters}
        resultCount={challenges ? challenges.totalElements + (challenges.pinned?.length || 0) : undefined}
      >
        <div className="flex items-center gap-1 shrink-0">
          {(['active', 'past'] as const).map(p => (
            <button key={p} type="button" onClick={() => applyPreset(p)}
              className={cn(
                'px-3 py-1 rounded-lg text-[11px] font-medium transition-colors border whitespace-nowrap',
                activePreset === p
                  ? 'bg-accent/15 text-accent border-accent/25'
                  : 'text-white/35 border-transparent hover:text-white/60 hover:bg-white/[.04]'
              )}
            >
              {p === 'active' ? 'Active' : 'Past'}
            </button>
          ))}
          <div className="w-px h-4 bg-white/[.08] mx-1" />
          <DatePicker value={startDate} onChange={v => { setStartDate(v); setActivePreset(null); setPage(0) }} placeholder="From" size="sm" clearable />
          <span className="text-white/15 shrink-0">–</span>
          <DatePicker value={endDate} onChange={v => { setEndDate(v); setActivePreset(null); setPage(0) }} placeholder="To" size="sm" clearable />
        </div>
        <CustomSelect value={levelFilter} onChange={v => { setLevelFilter(v); setPage(0) }} options={[{ value: '', label: 'All Levels' }, ...(levels?.map(l => ({ value: l, label: l })) ?? [])]} placeholder="Level" size="sm" />
        <CustomSelect value={diffFilter} onChange={v => { setDiffFilter(v); setPage(0) }} options={[{ value: '', label: 'All Difficulties' }, ...(difficulties?.map(d => ({ value: d, label: d })) ?? [])]} placeholder="Difficulty" size="sm" />
        <CustomSelect value={activeFilter} onChange={v => { setActiveFilter(v); setPage(0) }} options={[{ value: '', label: t.common.all }, { value: 'true', label: t.common.active }, { value: 'false', label: t.common.inactive }]} placeholder="Status" size="sm" />
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
          <div className="p-5"><SkeletonTable rows={10} cols={table.activeCols.length} /></div>
        ) : (!challenges?.pinned?.length && !challenges?.content?.length) ? (
          <EmptyState icon={Calendar} title={t.common.noResults} className="py-16" />
        ) : (
          <>
          {challenges?.pinned?.map((row: ChallengeAdminRow) => {
            const isTodayActive = row.isActive
            return (
            <div key={row.id} className={cn('group', !row.isActive && 'opacity-60')}>
              <div className="hidden sm:flex items-center min-w-max border-b border-white/[.04] hover:bg-white/[.02] transition-colors bg-accent/[.04] border-l-2 border-l-accent">
                <div className="grid gap-0 px-3 py-2.5 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                  {table.dataCols.map(col => (
                    <div key={col.key} className={cn('overflow-hidden px-2', col.key === 'isActive' && 'flex justify-center')}>
                      <CellValue col={col} row={row} isToday />
                    </div>
                  ))}
                </div>
                {table.hasActions && renderActions(row, true, isTodayActive)}
              </div>
              <div className="sm:hidden flex items-center gap-3 px-4 py-3 border-b border-white/[.04] bg-accent/[.04] border-l-2 border-l-accent">
                <Calendar size={15} className="text-white/25 shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-mono text-white/75">{row.challengeDate}</p>
                    <span className="text-[9px] font-bold tracking-wider bg-accent/15 text-accent border border-accent/20 px-1.5 py-0.5 rounded-full leading-none">TODAY</span>
                  </div>
                  <div className="flex gap-1.5 mt-0.5">
                    <Badge variant={levelColors[row.challengeLevel] || 'neutral'} className="text-[9px]">{row.challengeLevel}</Badge>
                    <span className="text-[10px] text-white/35">{row.totalCountries} countries · {row.totalHints} hints</span>
                  </div>
                </div>
                <Button variant="ghost" size="sm" onClick={() => setViewingChallenge(row)}><Info size={14} /></Button>
                <Button variant="ghost" size="sm" onClick={() => { setEditingChallenge(row); setEditForm({ challengeLevel: row.challengeLevel, difficulty: row.difficulty }) }}><Edit2 size={14} /></Button>
                <Button variant="ghost" size="sm" disabled={isTodayActive} title={isTodayActive ? "Can't deactivate today's challenge" : undefined}
                  onClick={() => toggleMutation.mutate({ id: row.id, active: !row.isActive })}>
                  {row.isActive ? <Eye size={14} className="text-emerald-400/40" /> : <EyeOff size={14} className="text-white/30" />}
                </Button>
              </div>
            </div>
          )})}

          {!!challenges?.pinned?.length && !!challenges?.content?.length && (
            <div className="border-b border-white/[.08]" />
          )}

          {challenges?.content?.map((row: ChallengeAdminRow) => (
            <div key={row.id} className={cn('group', !row.isActive && 'opacity-60')}>
              <div className="hidden sm:flex items-center min-w-max border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors">
                <div className="grid gap-0 px-3 py-2.5 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                  {table.dataCols.map(col => (
                    <div key={col.key} className={cn('overflow-hidden px-2', col.key === 'isActive' && 'flex justify-center')}>
                      <CellValue col={col} row={row} />
                    </div>
                  ))}
                </div>
                {table.hasActions && renderActions(row, false, false)}
              </div>
              <div className="sm:hidden flex items-center gap-3 px-4 py-3 border-b border-white/[.04]">
                <Calendar size={15} className="text-white/25 shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-mono text-white/75">{row.challengeDate}</p>
                  <div className="flex gap-1.5 mt-0.5">
                    <Badge variant={levelColors[row.challengeLevel] || 'neutral'} className="text-[9px]">{row.challengeLevel}</Badge>
                    <span className="text-[10px] text-white/35">{row.totalCountries} countries · {row.totalHints} hints</span>
                  </div>
                </div>
                <Button variant="ghost" size="sm" onClick={() => setViewingChallenge(row)}><Info size={14} /></Button>
                <Button variant="ghost" size="sm" onClick={() => { setEditingChallenge(row); setEditForm({ challengeLevel: row.challengeLevel, difficulty: row.difficulty }) }}><Edit2 size={14} /></Button>
                <Button variant="ghost" size="sm" onClick={() => toggleMutation.mutate({ id: row.id, active: !row.isActive })}>
                  {row.isActive ? <Eye size={14} className="text-emerald-400" /> : <EyeOff size={14} className="text-white/30" />}
                </Button>
              </div>
            </div>
          ))}
          </>
        )}
        </div>
      </div>

      {challenges && challenges.totalPages > 1 && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination page={page} totalPages={challenges.totalPages} totalElements={challenges.totalElements} onPageChange={setPage} />
        </div>
      )}

      {viewingChallenge && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5">
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.15 }}
            className="absolute inset-0 bg-black/80" onClick={() => { setViewingChallenge(null); setEditingHint(null) }} />
          <motion.div initial={{ opacity: 0, y: 10, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
            className="relative z-10 w-full max-w-7xl max-h-[92vh] bg-[#0a0a0f] border border-white/[.08] rounded-2xl shadow-2xl shadow-black/60 flex flex-col overflow-hidden">

            <div className="flex items-center justify-between px-6 py-4 border-b border-white/[.08] shrink-0">
              <div className="flex items-center gap-3 min-w-0">
                <h2 className="text-lg font-semibold text-white truncate">Challenge — {viewingChallenge.challengeDate}</h2>
                <Badge variant={levelColors[viewingChallenge.challengeLevel] || 'neutral'} className="text-[10px] shrink-0">{viewingChallenge.challengeLevel}</Badge>
                <span className={cn('text-xs font-mono shrink-0', diffColors[viewingChallenge.difficulty] || 'text-white/50')}>{viewingChallenge.difficulty}</span>
              </div>
              <button onClick={() => { setViewingChallenge(null); setEditingHint(null) }}
                className="p-1.5 text-white/40 hover:text-white/70 transition-colors rounded-lg hover:bg-white/[.05] shrink-0 ml-3">
                <X size={18} />
              </button>
            </div>

            <div className="grid grid-cols-4 gap-px bg-white/[.06] border-b border-white/[.06] shrink-0">
              {[
                { label: 'Status', node: <Badge variant={viewingChallenge.isActive ? 'success' : 'neutral'} className="text-[10px]">{viewingChallenge.isActive ? 'Active' : 'Inactive'}</Badge> },
                { label: 'Countries', node: <span className="text-base font-bold text-white">{viewingChallenge.totalCountries}</span> },
                { label: 'Hints', node: <span className="text-base font-bold text-white">{viewingChallenge.totalHints}</span> },
                { label: 'Created', node: <span className="text-xs text-white/50 font-mono">{viewingChallenge.createdAt?.replace('T', ' ').slice(0, 16)}</span> },
              ].map(s => (
                <div key={s.label} className="bg-[#0a0a0f] px-5 py-3">
                  <p className="text-[10px] text-white/25 uppercase tracking-widest mb-0.5">{s.label}</p>
                  {s.node}
                </div>
              ))}
            </div>

            <div className="flex-1 flex flex-col min-h-0 sm:flex-row">
              <div className="shrink-0 border-b border-white/[.06] sm:border-b-0 sm:border-r sm:w-56 sm:overflow-y-auto bg-white/[.01]">
                <p className="text-[10px] text-white/25 uppercase tracking-widest px-4 pt-3 pb-1.5 sm:block hidden">Countries ({countries.length})</p>
                <div className="sm:p-2 sm:space-y-0.5 flex sm:flex-col gap-1 p-2 overflow-x-auto scrollbar-thin">
                  {countries.map(c => {
                    const isSelected = c.order === effectiveSelectedCountryOrder
                    const cHints = hintsByCountry.get(c.order)
                    return (
                      <div key={c.order} className="group/country flex items-center sm:w-auto shrink-0">
                        <button type="button" onClick={() => { setSelectedCountryOrder(c.order); setEditingHint(null) }}
                          className={cn(
                            'sm:flex-1 sm:flex sm:items-center sm:gap-2.5 sm:px-3 sm:py-2.5 sm:rounded-xl sm:min-w-0 sm:text-left transition-colors',
                            'flex flex-col items-center justify-center px-2 py-1.5 rounded-xl min-w-[52px]',
                            isSelected ? 'bg-white/[.06] text-white' : 'text-white/40 hover:bg-white/[.03] hover:text-white/60',
                          )}>
                          <div className={cn('w-5 h-5 rounded-full flex items-center justify-center shrink-0', isSelected ? 'bg-accent/20' : 'bg-white/[.06]')}>
                            <span className={cn('text-[9px] font-bold', isSelected ? 'text-accent' : 'text-white/40')}>{c.order}</span>
                          </div>
                          <img src={getFlagUrlByIso2(c.code, 'w20') ?? ''} alt={c.code} width={16} height={12} className="rounded-[2px] shrink-0 mt-1 sm:mt-0" loading="lazy" />
                          <div className="min-w-0 sm:flex-1 hidden sm:block">
                            <p className="text-sm font-medium truncate">{c.name}</p>
                            <p className="text-[10px] text-white/25 font-mono">{cHints?.length || 0} hints</p>
                          </div>
                          <span className="text-[9px] text-white/40 mt-0.5 sm:hidden truncate max-w-[52px]">{c.code}</span>
                        </button>
                        {canRerollCountry && (
                          <button
                            type="button"
                            onClick={() => setRerollCountryConfirm({ challengeId: viewingChallenge!.id, countryOrder: c.order, countryName: c.name })}
                            className="shrink-0 p-1.5 rounded-lg text-white/0 group-hover/country:text-white/20 hover:!text-amber-400 hover:!bg-amber-500/10 transition-all mr-1 hidden sm:block"
                            title={`Reroll ${c.name}`}
                          >
                            <RefreshCw size={12} />
                          </button>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>

              <div className="flex-1 overflow-y-auto scrollbar-thin min-h-0">
                {selectedCountry ? (
                  <div className="p-5 sm:p-6">
                    <div className="flex items-center gap-3 mb-5">
                      <img src={getFlagUrlByIso2(selectedCountry.code, 'w40') ?? ''} alt={selectedCountry.code} width={32} height={24} className="rounded-[3px] shrink-0" loading="lazy" />
                      <div>
                        <p className="text-base font-semibold text-white">{selectedCountry.name}</p>
                        <p className="text-xs text-white/30 font-mono">{selectedCountry.code} · {selectedCountryHints.length} hints</p>
                      </div>
                    </div>

                    <div className="space-y-3">
                      {selectedCountryHints.map(h => (
                        <div key={h.hintOrder} className="rounded-xl bg-white/[.02] border border-white/[.05] p-4 group/hint">
                          <div className="flex items-start gap-3">
                            <span className="text-white/15 font-mono text-xs shrink-0 mt-0.5 w-4 text-right">{h.hintOrder}</span>
                            {h.imageUrl && (
                              <a
                                href={h.imageUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="relative shrink-0 overflow-hidden rounded-lg border border-white/[.08] bg-white/[.04] hover:border-accent/40 transition-colors"
                                title="Open hint image"
                              >
                                <img src={h.imageUrl} alt="" className="h-14 w-20 object-cover" loading="lazy" />
                                <span className="absolute bottom-1 right-1 rounded bg-black/65 p-0.5 text-white/70">
                                  <ImageIcon size={10} />
                                </span>
                              </a>
                            )}
                            {editingHint?.id === h.hintId ? (
                              <div className="flex-1 space-y-2">
                                <textarea
                                  value={editingHint.text}
                                  onChange={e => setEditingHint(prev => prev ? { ...prev, text: e.target.value } : null)}
                                  className="w-full bg-white/[.05] border border-white/10 rounded-lg px-4 py-3 text-sm text-white/70 resize-y focus:outline-none focus:border-accent/40 min-h-[100px]"
                                  rows={5}
                                  autoFocus
                                />
                                <div className="flex items-center gap-1.5 justify-end">
                                  <Button size="sm" variant="ghost" onClick={() => setEditingHint(null)}>
                                    <X size={11} /> Cancel
                                  </Button>
                                  <Button size="sm" onClick={() => updateHintMutation.mutate({ id: editingHint.id, text: editingHint.text })}
                                    isLoading={updateHintMutation.isPending} disabled={!editingHint.text.trim()}>
                                    <Check size={11} /> Save
                                  </Button>
                                </div>
                              </div>
                            ) : (
                              <p className="text-sm text-white/60 flex-1 leading-relaxed whitespace-pre-wrap">{h.text}</p>
                            )}
                            {editingHint?.id !== h.hintId && (
                              <div className="shrink-0 flex items-center gap-0.5">
                                <button type="button" onClick={() => setEditingHint({ id: h.hintId, text: h.text })}
                                  className="p-1.5 rounded text-white/0 group-hover/hint:text-white/20 hover:!text-white/50 transition-colors"
                                  title="Edit hint text">
                                  <Pencil size={12} />
                                </button>
                                {canRerollHint && (
                                  <button
                                    type="button"
                                    onClick={() => setRerollHintConfirm({ challengeId: viewingChallenge!.id, countryOrder: h.countryOrder, hintOrder: h.hintOrder })}
                                    className="p-1.5 rounded text-white/0 group-hover/hint:text-white/20 hover:!text-amber-400 transition-colors"
                                    title="Reroll this hint"
                                  >
                                    <RefreshCw size={12} />
                                  </button>
                                )}
                              </div>
                            )}
                          </div>
                          <div className="flex items-center gap-2 mt-2.5 ml-7">
                            <Badge variant="info" className="text-[9px]">{h.category}</Badge>
                            {h.type && <span className="text-[9px] text-white/20 font-mono">{h.type}</span>}
                            {h.source && <span className="text-[9px] text-white/20 font-mono">{h.source}</span>}
                            {h.difficulty != null && <span className="text-[9px] text-white/20 font-mono">d:{h.difficulty}</span>}
                          </div>
                        </div>
                      ))}
                      {selectedCountryHints.length === 0 && (
                        <p className="text-sm text-white/20 py-8 text-center">No hints for this country</p>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="flex items-center justify-center h-full text-white/20 text-sm">
                    Select a country to view hints
                  </div>
                )}
              </div>
            </div>
          </motion.div>
        </div>
      )}

      <Modal isOpen={!!editingChallenge} onClose={() => setEditingChallenge(null)}
        title={editingChallenge ? `Edit — ${editingChallenge.challengeDate}` : 'Edit Challenge'}>
        <div className="space-y-4">
          {editingChallenge && (
            <div className="grid grid-cols-2 gap-3">
              {[
                { label: 'Date', value: editingChallenge.challengeDate },
                { label: 'Countries', value: editingChallenge.totalCountries },
                { label: 'Hints', value: editingChallenge.totalHints },
                { label: 'Status', value: editingChallenge.isActive ? 'Active' : 'Inactive' },
              ].map(f => (
                <div key={f.label} className="rounded-lg bg-white/[.03] px-3 py-2">
                  <p className="text-[10px] text-white/25 uppercase tracking-widest">{f.label}</p>
                  <p className="text-sm text-white/60 mt-0.5">{f.value}</p>
                </div>
              ))}
            </div>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Level</label>
              <CustomSelect value={editForm.challengeLevel} onChange={v => setEditForm(f => ({ ...f, challengeLevel: v }))}
                options={levels?.map(l => ({ value: l, label: l })) ?? [{ value: editForm.challengeLevel, label: editForm.challengeLevel }]}
                placeholder="Level" />
            </div>
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Difficulty</label>
              <CustomSelect value={editForm.difficulty} onChange={v => setEditForm(f => ({ ...f, difficulty: v }))}
                options={difficulties?.map(d => ({ value: d, label: d })) ?? [{ value: editForm.difficulty, label: editForm.difficulty }]}
                placeholder="Difficulty" />
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setEditingChallenge(null)}>{t.common.cancel}</Button>
            <Button
              onClick={() => editingChallenge && updateMutation.mutate({ id: editingChallenge.id, body: editForm })}
              isLoading={updateMutation.isPending}
              disabled={!editForm.challengeLevel || !editForm.difficulty}
            >{t.common.save}</Button>
          </div>
        </div>
      </Modal>

      <Modal isOpen={showGenerate} onClose={() => setShowGenerate(false)} title={t.admin.generateChallenge}>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Date</label>
              <DatePicker value={generateDate} onChange={setGenerateDate} placeholder="Select date" />
            </div>
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Level (optional)</label>
              <CustomSelect value={generateLevel} onChange={setGenerateLevel}
                options={[{ value: '', label: 'Auto (all levels)' }, { value: 'EASY', label: 'Easy' }, { value: 'MEDIUM', label: 'Medium' }, { value: 'HARD', label: 'Hard' }]}
                placeholder="Auto"
              />
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setShowGenerate(false)}>{t.common.cancel}</Button>
            <Button
              onClick={() => generateDateMutation.mutate({ date: generateDate, level: generateLevel })}
              isLoading={generateDateMutation.isPending}
              disabled={!generateDate}
            >{t.admin.startOperation}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={showCacheConfirm}
        onClose={() => setShowCacheConfirm(false)}
        onConfirm={() => { cacheMutation.mutate(); setShowCacheConfirm(false) }}
        title="Invalidate challenge cache?"
        description="All cached challenge data in Redis will be cleared. Fresh data will be loaded from the database."
        confirmLabel="Invalidate"
        variant="danger"
        isLoading={cacheMutation.isPending}
      />

      <ConfirmDialog
        isOpen={!!rerollCountryConfirm}
        onClose={() => setRerollCountryConfirm(null)}
        onConfirm={() => {
          if (rerollCountryConfirm) {
            rerollCountryMutation.mutate({ challengeId: rerollCountryConfirm.challengeId, countryOrder: rerollCountryConfirm.countryOrder })
            setRerollCountryConfirm(null)
          }
        }}
        title={`Reroll country "${rerollCountryConfirm?.countryName}"?`}
        description="This will replace the country and all its hints with a new randomly selected country."
        confirmLabel="Reroll"
        variant="danger"
        isLoading={rerollCountryMutation.isPending}
      />

      <ConfirmDialog
        isOpen={!!rerollHintConfirm}
        onClose={() => setRerollHintConfirm(null)}
        onConfirm={() => {
          if (rerollHintConfirm) {
            rerollHintMutation.mutate({ challengeId: rerollHintConfirm.challengeId, countryOrder: rerollHintConfirm.countryOrder, hintOrder: rerollHintConfirm.hintOrder })
            setRerollHintConfirm(null)
          }
        }}
        title={`Reroll hint #${rerollHintConfirm?.hintOrder}?`}
        description="This will replace this hint with a new randomly generated one for the same country."
        confirmLabel="Reroll"
        variant="danger"
        isLoading={rerollHintMutation.isPending}
      />
    </div>
  )
}
