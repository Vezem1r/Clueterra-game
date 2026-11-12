import { useState, useMemo, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { createPortal } from 'react-dom'
import {
  Lightbulb, Edit2, Eye, EyeOff, Trash2, Globe2, Database,
  ChevronDown, Plus,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { SourceSelect } from '@/components/admin/SourceSelect'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { AdminTableHeader } from '@/components/admin/AdminTableHeader'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { AdminStats } from '@/components/admin/AdminStats'
import { TableToolbarButtons } from '@/components/admin/TableToolbarButtons'
import { ImageMetadataPanel } from '@/components/admin/ImageMetadataPanel'
import { adminApi } from '@/api/admin'
import { useDebounce } from '@/hooks/useDebounce'
import { useAdminTable, type ColumnDef } from '@/hooks/useAdminTable'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import type { HintAdminDto, CreateHintRequest } from '@/types'
import { cn } from '@/utils/cn'
import { getFlagUrlByIso2 } from '@/utils/country'
import { extractApiErrorMessage, extractFieldErrors } from '@/utils/errors'

const FIELD_LABELS: Record<string, string> = {
  hintText: 'Hint Text', difficulty: 'Difficulty', qualityScore: 'Quality Score',
  isActive: 'Status', category: 'Category', hintType: 'Type',
  source: 'Source', sourceYear: 'Year',
}

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

function extractApiError(err: unknown, fallback: string): string {
  const fieldErrors = extractFieldErrors(err)
  if (fieldErrors) {
    return Object.entries(fieldErrors).map(([f, m]) => `${FIELD_LABELS[f] || f}: ${m}`).join('; ')
  }
  return extractApiErrorMessage(err, fallback)
}

const ALL_COLUMNS: ColumnDef[] = [
  { key: 'countryCode',     label: 'Flag',       sortKey: undefined,         defaultVisible: true,  defaultWidth: 44  },
  { key: 'countryName',     label: 'Country',    sortKey: 'country.nameEn',  defaultVisible: true,  defaultWidth: 140 },
  { key: 'hintText',        label: 'Hint Text',  sortKey: undefined,         defaultVisible: true,  defaultWidth: 320 },
  { key: 'category',        label: 'Category',   sortKey: 'category',        defaultVisible: true,  defaultWidth: 110 },
  { key: 'hintType',        label: 'Type',       sortKey: 'hintType',        defaultVisible: true,  defaultWidth: 120 },
  { key: 'difficulty',      label: 'Diff',       sortKey: 'difficulty',      defaultVisible: true,  defaultWidth: 60  },
  { key: 'qualityScore',    label: 'Quality',    sortKey: 'qualityScore',    defaultVisible: true,  defaultWidth: 70  },
  { key: 'source',          label: 'Source',     sortKey: 'source',          defaultVisible: true,  defaultWidth: 120 },
  { key: 'sourceYear',      label: 'Year',       sortKey: 'sourceYear',      defaultVisible: false, defaultWidth: 60  },
  { key: 'reviewRetryAfterAt', label: 'Review again', sortKey: undefined, defaultVisible: false, defaultWidth: 115 },
  { key: 'editorialTextLocked', label: 'Edited text', sortKey: undefined, defaultVisible: false, defaultWidth: 95 },
  { key: 'ambiguityCount',  label: 'Ambiguity',  sortKey: 'ambiguityCount',  defaultVisible: false, defaultWidth: 80  },
  { key: 'timesUsed',       label: 'Used',       sortKey: 'timesUsed',       defaultVisible: false, defaultWidth: 60  },
  { key: 'timesCorrect',    label: 'Correct',    sortKey: 'timesCorrect',    defaultVisible: false, defaultWidth: 70  },
  { key: 'sourceIndicator', label: 'Indicator',  sortKey: 'sourceIndicator', defaultVisible: false, defaultWidth: 120 },
  { key: 'imageAuthorName', label: 'Author',     sortKey: undefined,         defaultVisible: false, defaultWidth: 140 },
  { key: 'imageLicenseLabel', label: 'License',  sortKey: undefined,         defaultVisible: false, defaultWidth: 130 },
  { key: 'imageAttributionText', label: 'Attribution', sortKey: undefined,   defaultVisible: false, defaultWidth: 260 },
  { key: 'imageSourcePageUrl', label: 'Source Page', sortKey: undefined,     defaultVisible: false, defaultWidth: 220 },
  { key: 'originalImageUrl', label: 'Original Image', sortKey: undefined,    defaultVisible: false, defaultWidth: 220 },
  { key: 'isActive',        label: 'Status',     sortKey: 'isActive',        defaultVisible: true,  defaultWidth: 80  },
  { key: 'actions',         label: '',           sortKey: undefined,         defaultVisible: true,  defaultWidth: 120 },
]

function CellValue({ col, hint }: { col: ColumnDef; hint: HintAdminDto }) {
  switch (col.key) {
    case 'countryCode':
      return hint.countryCode ? (
        <img
          src={getFlagUrlByIso2(hint.countryCode, 'w20') ?? ''}
          alt={hint.countryCode}
          width={20}
          height={15}
          className="rounded-[2px] object-cover shrink-0"
          loading="lazy"
          onError={e => { (e.target as HTMLImageElement).style.display = 'none' }}
        />
      ) : <span className="text-white/20">--</span>
    case 'countryName':
      return <span className="text-sm text-white/85 truncate">{hint.countryName || '—'}</span>
    case 'hintText':
      return (
        <div className="flex items-center gap-1.5 min-w-0">
          {hint.imageUrl && (
            <img
              src={hint.imageUrl}
              alt=""
              className="w-6 h-6 rounded object-cover shrink-0 border border-white/10"
              loading="lazy"
            />
          )}
          <span className="text-xs text-white/60 truncate block" title={hint.hintText}>
            {hint.hintText}
          </span>
        </div>
      )
    case 'category':
      return <Badge variant="info" className="text-[10px]">{hint.category}</Badge>
    case 'hintType':
      return <span className="text-xs text-white/50 truncate">{hint.hintType || '—'}</span>
    case 'difficulty':
      return (
        <span className={cn(
          'text-xs font-mono',
          (hint.difficulty ?? 0) <= 30 ? 'text-emerald-400/80' :
          (hint.difficulty ?? 0) <= 60 ? 'text-amber-400/80' : 'text-red-400/80'
        )}>
          {hint.difficulty}
        </span>
      )
    case 'qualityScore':
      return (
        <span className="text-xs text-white/50 font-mono">
          {hint.qualityScore != null ? Number(hint.qualityScore).toFixed(2) : '—'}
        </span>
      )
    case 'source':
      return <span className="text-xs text-white/40 truncate">{hint.source || '—'}</span>
    case 'sourceYear':
      return <span className="text-xs text-white/40 font-mono">{hint.sourceYear ?? '—'}</span>
    case 'reviewRetryAfterAt':
      return <span className="text-xs text-amber-300/80" title={hint.reviewRetryAfterAt ?? undefined}>
        {hint.reviewRetryAfterAt ? new Date(hint.reviewRetryAfterAt).toLocaleDateString() : '—'}
      </span>
    case 'editorialTextLocked':
      return <span className="text-xs text-white/50">{hint.editorialTextLocked ? 'Protected' : '—'}</span>
    case 'ambiguityCount':
      return <span className="text-xs text-white/40 font-mono">{hint.ambiguityCount ?? '—'}</span>
    case 'timesUsed':
      return <span className="text-xs text-white/40 font-mono">{hint.timesUsed ?? 0}</span>
    case 'timesCorrect':
      return <span className="text-xs text-white/40 font-mono">{hint.timesCorrect ?? 0}</span>
    case 'sourceIndicator':
      return <span className="text-xs text-white/35 truncate">{hint.sourceIndicator || '—'}</span>
    case 'imageAuthorName':
      return <span className="text-xs text-white/40 truncate">{hint.imageAuthorName || '—'}</span>
    case 'imageLicenseLabel':
      return <span className="text-xs text-white/40 truncate">{hint.imageLicenseLabel || '—'}</span>
    case 'imageAttributionText':
      return <span className="text-xs text-white/35 truncate" title={hint.imageAttributionText || undefined}>{hint.imageAttributionText || '—'}</span>
    case 'imageSourcePageUrl':
      return hint.imageSourcePageUrl ? (
        <a href={hint.imageSourcePageUrl} target="_blank" rel="noreferrer" className="text-xs text-sky-300/75 hover:text-sky-200 truncate block">
          {displayUrl(hint.imageSourcePageUrl)}
        </a>
      ) : <span className="text-xs text-white/35">—</span>
    case 'originalImageUrl':
      return hint.originalImageUrl ? (
        <a href={hint.originalImageUrl} target="_blank" rel="noreferrer" className="text-xs text-sky-300/75 hover:text-sky-200 truncate block">
          {displayUrl(hint.originalImageUrl)}
        </a>
      ) : <span className="text-xs text-white/35">—</span>
    case 'isActive':
      return (
        <Badge variant={hint.isActive !== false ? 'success' : 'neutral'} className="text-[10px]">
          {hint.isActive !== false ? 'Active' : 'Inactive'}
        </Badge>
      )
    default:
      return null
  }
}

export function HintsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToastStore()

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [selectedCountryIds, setSelectedCountryIds] = useState<Set<string>>(new Set())
  const [countrySearch, setCountrySearch] = useState('')
  const [countryDropdownOpen, setCountryDropdownOpen] = useState(false)
  const countryBtnRef = useRef<HTMLButtonElement>(null)
  const countryDropRef = useRef<HTMLDivElement>(null)
  const [countryDropPos, setCountryDropPos] = useState<{ top?: number; bottom?: number; left: number }>({ left: 0 })
  const debouncedCountrySearch = useDebounce(countrySearch, 200)
  const [categoryFilter, setCategoryFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [sourceFilter, setSourceFilter] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const [imageFilter, setImageFilter] = useState('')
  const [yearFilter, setYearFilter] = useState('')
  const [diffMin, setDiffMin] = useState('')
  const [diffMax, setDiffMax] = useState('')
  const debouncedSearch = useDebounce(search)

  const { containerRef, ...table } = useAdminTable({
    storageKey: 'admin-hints',
    columns: ALL_COLUMNS,
    defaultSort: 'createdAt',
    defaultSortDir: 'desc',
    pinnedColumns: ['countryCode', 'actions'],
    onSortChange: () => setPage(0),
  })

  const [editingHint, setEditingHint] = useState<HintAdminDto | null>(null)
  const [editForm, setEditForm] = useState({
    hintText: '',
    difficulty: '',
    qualityScore: '',
    isActive: true,
  })
  const [deletingHintId, setDeletingHintId] = useState<string | null>(null)
  const [showCacheConfirm, setShowCacheConfirm] = useState(false)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [createForm, setCreateForm] = useState({
    countryId: '', category: '', hintType: '', hintText: '',
    difficulty: '', qualityScore: '', sourceYear: '',
  })
  const [createCountrySearch, setCreateCountrySearch] = useState('')
  const debouncedCreateCountrySearch = useDebounce(createCountrySearch, 200)
  const [wikimediaFileTitle, setWikimediaFileTitle] = useState('')

  const handleEdit = (hint: HintAdminDto) => {
    setEditingHint(hint)
    setWikimediaFileTitle('')
    setEditForm({
      hintText: hint.hintText,
      difficulty: String(hint.difficulty),
      qualityScore: hint.qualityScore != null ? String(hint.qualityScore) : '',
      isActive: hint.isActive !== false,
    })
  }

  const countryIdsArray = useMemo(() => [...selectedCountryIds], [selectedCountryIds])

  const COUNTRY_DROP_H = 280

  const toggleCountryDropdown = () => {
    if (!countryDropdownOpen && countryBtnRef.current) {
      const r = countryBtnRef.current.getBoundingClientRect()
      const spaceBelow = window.innerHeight - r.bottom - 8
      const spaceAbove = r.top - 8
      const openUp = spaceBelow < COUNTRY_DROP_H && spaceAbove > spaceBelow
      if (openUp) {
        setCountryDropPos({ bottom: window.innerHeight - r.top + 4, left: r.left })
      } else {
        setCountryDropPos({ top: r.bottom + 4, left: r.left })
      }
    }
    setCountryDropdownOpen(o => !o)
  }

  const toggleCountryId = (id: string) => {
    setSelectedCountryIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) { next.delete(id) } else { next.add(id) }
      return next
    })
    setPage(0)
  }

  useEffect(() => {
    if (!countryDropdownOpen) return
    const handler = (e: MouseEvent) => {
      const t = e.target as Node
      if (
        countryBtnRef.current && !countryBtnRef.current.contains(t) &&
        countryDropRef.current && !countryDropRef.current.contains(t)
      ) {
        setCountryDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [countryDropdownOpen])

  const { data: hints, isLoading } = useQuery({
    queryKey: ['admin-hints', page, debouncedSearch, countryIdsArray, categoryFilter, typeFilter, sourceFilter, activeFilter, imageFilter, yearFilter, diffMin, diffMax, table.sortBy, table.sortDir],
    queryFn: () => adminApi.getHints({
      search: debouncedSearch || undefined,
      countryIds: countryIdsArray.length ? countryIdsArray : undefined,
      category: categoryFilter || undefined,
      hintType: typeFilter || undefined,
      source: sourceFilter || undefined,
      active: activeFilter === '' ? undefined : activeFilter === 'true',
      hasImage: imageFilter === '' ? undefined : imageFilter === 'true',
      sourceYear: yearFilter ? Number(yearFilter) : undefined,
      difficultyMin: diffMin ? Number(diffMin) : undefined,
      difficultyMax: diffMax ? Number(diffMax) : undefined,
      page,
      size: 50,
      sortBy: table.sortBy,
      sortDir: table.sortDir,
    }),
  })

  const { data: hintCountries } = useQuery({
    queryKey: ['admin-hint-countries', debouncedCountrySearch],
    queryFn: () => adminApi.getHintCountries(debouncedCountrySearch || undefined),
    staleTime: 60_000,
  })

  const { data: hintStats } = useQuery({
    queryKey: ['admin-hint-stats'],
    queryFn: adminApi.getHintStats,
  })

  const { data: categories } = useQuery({
    queryKey: ['admin-hint-categories'],
    queryFn: adminApi.getHintCategories,
    staleTime: 300_000,
  })

  const { data: hintTypes } = useQuery({
    queryKey: ['admin-hint-types'],
    queryFn: adminApi.getHintTypes,
    staleTime: 300_000,
  })

  const { data: sources } = useQuery({
    queryKey: ['admin-hint-sources'],
    queryFn: adminApi.getHintSources,
    staleTime: 300_000,
  })

  const { data: sourceYears } = useQuery({
    queryKey: ['admin-hint-source-years'],
    queryFn: adminApi.getHintSourceYears,
    staleTime: 300_000,
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      adminApi.toggleHintActive(id, active),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-hints'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-stats'] })
      toast.addToast({ type: 'success', message: 'Status updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to update status') }),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Record<string, unknown> }) =>
      adminApi.updateHint(id, data as Partial<HintAdminDto>),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-hints'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-stats'] })
      setEditingHint(null)
      toast.addToast({ type: 'success', message: 'Hint updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to update hint') }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteHint(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-hints'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-stats'] })
      setDeletingHintId(null)
      toast.addToast({ type: 'success', message: 'Hint deleted' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to delete hint') }),
  })

  const cacheMutation = useMutation({
    mutationFn: () => adminApi.invalidateHintCache(),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin-hints'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-stats'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-categories'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-types'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-sources'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-source-years'] })
      toast.addToast({ type: 'success', message: `Cache invalidated (${data.evicted} keys)` })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to invalidate cache') }),
  })

  const { data: createCountries } = useQuery({
    queryKey: ['admin-hint-countries-create', debouncedCreateCountrySearch],
    queryFn: () => adminApi.getHintCountries(debouncedCreateCountrySearch || undefined),
    enabled: showCreateModal,
    staleTime: 60_000,
  })

  const createMutation = useMutation({
    mutationFn: (body: CreateHintRequest) => adminApi.createHint(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-hints'] })
      queryClient.invalidateQueries({ queryKey: ['admin-hint-stats'] })
      setShowCreateModal(false)
      setCreateForm({ countryId: '', category: '', hintType: '', hintText: '', difficulty: '', qualityScore: '', sourceYear: '' })
      setCreateCountrySearch('')
      toast.addToast({ type: 'success', message: 'Hint created' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to create hint') }),
  })

  const handleCreate = () => {
    if (!createForm.countryId || !createForm.category || !createForm.hintType || !createForm.hintText || !createForm.difficulty) return
    createMutation.mutate({
      countryId: createForm.countryId,
      category: createForm.category,
      hintType: createForm.hintType,
      hintText: createForm.hintText,
      difficulty: parseInt(createForm.difficulty),
      qualityScore: createForm.qualityScore ? parseFloat(createForm.qualityScore) : null,
      sourceYear: createForm.sourceYear ? parseInt(createForm.sourceYear) : null,
    })
  }

  const replaceImageMutation = useMutation({
    mutationFn: ({ id, fileTitle }: { id: string; fileTitle: string }) => adminApi.replaceHintImage(id, fileTitle),
    onSuccess: (result) => {
      setEditingHint(result)
      setWikimediaFileTitle('')
      queryClient.invalidateQueries({ queryKey: ['admin-hints'] })
      toast.addToast({ type: 'success', message: 'Wikimedia image saved' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to save Wikimedia image') }),
  })

  const deleteImageMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteHintImage(id),
    onSuccess: (result) => {
      setEditingHint(prev => prev ? {
        ...prev,
        imageUrl: result.imageUrl,
        originalImageUrl: result.originalImageUrl,
        imageSourcePageUrl: result.imageSourcePageUrl,
        imageAuthorName: result.imageAuthorName,
        imageAttributionText: result.imageAttributionText,
        imageLicenseLabel: result.imageLicenseLabel,
        imageLicenseUrl: result.imageLicenseUrl,
      } : null)
      queryClient.invalidateQueries({ queryKey: ['admin-hints'] })
      toast.addToast({ type: 'success', message: 'Image data removed' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiError(err, 'Failed to delete image') }),
  })

  const [showDeleteImageConfirm, setShowDeleteImageConfirm] = useState(false)

  const handleWikimediaImageSave = () => {
    const fileTitle = wikimediaFileTitle.trim()
    if (!editingHint || !fileTitle) return
    replaceImageMutation.mutate({ id: editingHint.id, fileTitle })
  }

  const handleSave = () => {
    if (!editingHint) return
    updateMutation.mutate({
      id: editingHint.id,
      data: {
        hintText: editForm.hintText,
        difficulty: parseInt(editForm.difficulty) || undefined,
        qualityScore: editForm.qualityScore ? parseFloat(editForm.qualityScore) : undefined,
        isActive: editForm.isActive,
      },
    })
  }

  const hasFilters = !!(search || selectedCountryIds.size || categoryFilter || typeFilter || sourceFilter || activeFilter || imageFilter || yearFilter || diffMin || diffMax)

  const activeFilterCount = [selectedCountryIds.size > 0, categoryFilter, typeFilter, sourceFilter, activeFilter, imageFilter, yearFilter, diffMin, diffMax].filter(Boolean).length

  const clearFilters = () => {
    setSearch('')
    setSelectedCountryIds(new Set())
    setCountrySearch('')
    setCategoryFilter('')
    setTypeFilter('')
    setSourceFilter('')
    setActiveFilter('')
    setImageFilter('')
    setYearFilter('')
    setDiffMin('')
    setDiffMax('')
    setPage(0)
  }

  return (
    <div className="flex flex-col h-full">

      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.hints}</h1>
          <p className="text-white/40 text-sm mt-1">View and manage game hints</p>
        </div>
        <div className="flex items-center gap-1.5">
          <Button variant="outline" size="sm" onClick={() => setShowCreateModal(true)}>
            <Plus size={14} />
            Add Hint
          </Button>
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

      {hintStats && (
        <AdminStats items={[
          { label: 'Total', value: hintStats.totalHints },
          { label: t.common.active, value: hintStats.activeHints },
          { label: t.common.inactive, value: hintStats.inactiveHints },
          { label: 'Categories', value: hintStats.categories },
          { label: 'Types', value: hintStats.types },
          { label: 'Sources', value: hintStats.sources },
        ]} />
      )}

      <AdminFilterBar
        search={search}
        onSearchChange={v => { setSearch(v); setPage(0) }}
        searchPlaceholder="Search..."
        activeFilterCount={activeFilterCount}
        onClearFilters={clearFilters}
        hasFilters={hasFilters}
        resultCount={hints?.totalElements}
      >
        <div className="shrink-0">
          <button
            ref={countryBtnRef}
            type="button"
            onClick={toggleCountryDropdown}
            className={cn(
              'flex items-center gap-1.5 h-8 px-2.5 rounded-lg border text-xs cursor-pointer transition-all',
              'bg-white/[.04] border-white/[.08] text-white',
              'hover:bg-white/[.06] hover:border-white/[.14]',
              countryDropdownOpen && 'bg-white/[.06] border-white/[.14]',
              selectedCountryIds.size > 0 && 'border-accent/30 text-accent',
            )}
          >
            <Globe2 size={12} className="shrink-0" />
            <span className="truncate max-w-[100px]">
              {selectedCountryIds.size > 0 ? `${selectedCountryIds.size} countries` : 'Country'}
            </span>
            <ChevronDown size={10} className={cn('text-white/30 shrink-0 transition-transform', countryDropdownOpen && 'rotate-180')} />
          </button>
          {countryDropdownOpen && createPortal(
            <div
              ref={countryDropRef}
              className="fixed z-[100] w-[260px] rounded-xl border border-white/[.08] bg-[#0e0e1a] shadow-2xl shadow-black/60 overflow-hidden"
              style={{ top: countryDropPos.top, bottom: countryDropPos.bottom, left: countryDropPos.left }}
            >
              <div className="p-2 border-b border-white/[.06]">
                <input
                  type="text"
                  placeholder="Search countries..."
                  value={countrySearch}
                  onChange={e => setCountrySearch(e.target.value)}
                  className="w-full h-7 px-2.5 bg-white/[.04] border border-white/[.08] rounded-lg text-xs text-white placeholder:text-white/30 focus:outline-none focus:border-white/20"
                  autoFocus
                />
              </div>
              <div className="py-1 max-h-56 overflow-y-auto scrollbar-thin">
                {selectedCountryIds.size > 0 && (
                  <button
                    type="button"
                    onClick={() => { setSelectedCountryIds(new Set()); setPage(0) }}
                    className="w-full px-3 py-1.5 text-[11px] text-red-400/70 hover:text-red-400 hover:bg-white/[.03] text-left transition-colors cursor-pointer"
                  >
                    Clear selection ({selectedCountryIds.size})
                  </button>
                )}
                {hintCountries?.slice().sort((a, b) => {
                  const aSelected = selectedCountryIds.has(a.id) ? 0 : 1
                  const bSelected = selectedCountryIds.has(b.id) ? 0 : 1
                  return aSelected - bSelected
                }).map(c => {
                  const isChecked = selectedCountryIds.has(c.id)
                  return (
                    <button
                      key={c.id}
                      type="button"
                      onClick={() => toggleCountryId(c.id)}
                      className={cn(
                        'w-full flex items-center gap-2 px-3 py-1.5 text-left transition-colors cursor-pointer',
                        'text-xs text-white/70 hover:text-white hover:bg-white/[.05]',
                        isChecked && 'text-accent bg-accent/[.06]',
                      )}
                    >
                      <div className={cn(
                        'w-3.5 h-3.5 rounded border flex items-center justify-center shrink-0 transition-colors',
                        isChecked ? 'bg-accent border-accent' : 'border-white/20 bg-white/[.04]'
                      )}>
                        {isChecked && (
                          <svg width="8" height="6" viewBox="0 0 9 7" fill="none">
                            <path d="M1 3.5L3.5 6L8 1" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                          </svg>
                        )}
                      </div>
                      <img
                        src={getFlagUrlByIso2(c.isoAlpha2, 'w20') ?? ''}
                        alt=""
                        width={16}
                        height={12}
                        className="rounded-[1px] object-cover shrink-0"
                        loading="lazy"
                      />
                      <span className="font-mono text-white/40 w-5">{c.isoAlpha2}</span>
                      <span className="flex-1 truncate">{c.nameEn}</span>
                    </button>
                  )
                })}
                {hintCountries?.length === 0 && (
                  <p className="px-3 py-3 text-xs text-white/25 text-center">No countries found</p>
                )}
              </div>
            </div>,
            document.body
          )}
        </div>

        <CustomSelect
          value={categoryFilter}
          onChange={v => { setCategoryFilter(v); setPage(0) }}
          options={[
            { value: '', label: 'All Categories' },
            ...(categories?.map(c => ({ value: c, label: c })) ?? []),
          ]}
          placeholder="Category"
          size="sm"
        />
        <CustomSelect
          value={typeFilter}
          onChange={v => { setTypeFilter(v); setPage(0) }}
          options={[
            { value: '', label: 'All Types' },
            ...(hintTypes?.map(ht => ({ value: ht, label: ht })) ?? []),
          ]}
          placeholder="Type"
          size="sm"
        />
        <SourceSelect
          value={sourceFilter}
          onChange={v => { setSourceFilter(v); setPage(0) }}
          sources={sources ?? []}
          allowAll
          allLabel="All Sources"
          placeholder="Source"
          size="sm"
          className="w-[190px]"
        />
        <CustomSelect
          value={activeFilter}
          onChange={v => { setActiveFilter(v); setPage(0) }}
          options={[
            { value: '', label: t.common.all },
            { value: 'true', label: t.common.active },
            { value: 'false', label: t.common.inactive },
          ]}
          placeholder="Status"
          size="sm"
        />
        <CustomSelect
          value={imageFilter}
          onChange={v => { setImageFilter(v); setPage(0) }}
          options={[
            { value: '', label: 'All' },
            { value: 'true', label: 'With Image' },
            { value: 'false', label: 'Without Image' },
          ]}
          placeholder="Image"
          size="sm"
        />
        <CustomSelect
          value={yearFilter}
          onChange={v => { setYearFilter(v); setPage(0) }}
          options={[
            { value: '', label: 'All Years' },
            ...(sourceYears?.map(y => ({ value: String(y), label: String(y) })) ?? []),
          ]}
          placeholder="Year"
          size="sm"
        />
        <div className="flex items-center gap-2 shrink-0">
          <span className="text-[10px] text-white/25 uppercase tracking-wider">Diff</span>
          <Input
            type="number"
            placeholder="min"
            value={diffMin}
            onChange={e => { setDiffMin(e.target.value); setPage(0) }}
            containerClassName="w-[60px] md:w-[60px] min-w-0 flex-1 md:flex-none shrink-0 md:shrink-0"
            className="h-10 md:h-8 text-sm md:text-xs px-3 md:px-2"
            min={1}
            max={100}
          />
          <span className="text-white/15 shrink-0">–</span>
          <Input
            type="number"
            placeholder="max"
            value={diffMax}
            onChange={e => { setDiffMax(e.target.value); setPage(0) }}
            containerClassName="w-[60px] md:w-[60px] min-w-0 flex-1 md:flex-none shrink-0 md:shrink-0"
            className="h-10 md:h-8 text-sm md:text-xs px-3 md:px-2"
            min={1}
            max={100}
          />
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
          centerColumns={['isActive']}
        />

        {isLoading ? (
          <div className="p-5"><SkeletonTable rows={10} cols={table.activeCols.length} /></div>
        ) : !hints?.content?.length ? (
          <EmptyState icon={Lightbulb} title={t.common.noResults} className="py-16" />
        ) : (
          hints.content.map(hint => (
            <div key={hint.id} className={cn('group', hint.isActive === false && 'opacity-60')}>
              <div className="hidden sm:flex items-center min-w-max border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors">
                <div className="grid gap-0 px-3 py-2.5 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: table.dataColsTemplate }}>
                  {table.dataCols.map(col => (
                    <div
                      key={col.key}
                      className={cn(
                        'overflow-hidden px-2',
                        col.key === 'isActive' && 'flex justify-center',
                      )}
                    >
                      <CellValue col={col} hint={hint} />
                    </div>
                  ))}
                </div>
                {table.hasActions && (
                  <div ref={table.actionsRefCb} className="sticky right-0 z-10 shrink-0 flex items-center gap-0.5 justify-end pl-8 pr-2 ml-auto"
                    style={{ background: ACTIONS_GRAD }}>
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(hint)} title={t.common.edit}>
                      <Edit2 size={13} />
                    </Button>
                    <Button
                      variant="ghost" size="sm"
                      onClick={() => toggleMutation.mutate({ id: hint.id, active: hint.isActive === false })}
                      title={hint.isActive !== false ? 'Deactivate' : 'Activate'}
                      disabled={toggleMutation.isPending}
                    >
                      {hint.isActive !== false
                        ? <Eye size={13} className="text-emerald-400" />
                        : <EyeOff size={13} className="text-white/30" />}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setDeletingHintId(hint.id)} title={t.common.delete}>
                      <Trash2 size={13} className="text-red-400/60" />
                    </Button>
                  </div>
                )}
              </div>

              <div className="sm:hidden flex items-start gap-3 px-4 py-3 border-b border-white/[.04]">
                {hint.countryCode && (
                  <img
                    src={getFlagUrlByIso2(hint.countryCode, 'w40') ?? ''}
                    alt={hint.countryCode}
                    width={28}
                    height={21}
                    className="rounded-[3px] object-cover shrink-0 mt-0.5"
                    loading="lazy"
                  />
                )}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5 mb-1 flex-wrap">
                    <span className="text-xs text-white/40">{hint.countryName}</span>
                    <Badge variant="info" className="text-[9px]">{hint.category}</Badge>
                    <span className={cn(
                      'text-[10px] font-mono',
                      (hint.difficulty ?? 0) <= 30 ? 'text-emerald-400/80' :
                      (hint.difficulty ?? 0) <= 60 ? 'text-amber-400/80' : 'text-red-400/80'
                    )}>
                      D{hint.difficulty}
                    </span>
                  </div>
                  <p className="text-sm text-white/70 line-clamp-2">{hint.hintText}</p>
                </div>
                <div className="flex items-center gap-0.5 shrink-0">
                  <Button variant="ghost" size="sm" onClick={() => handleEdit(hint)}>
                    <Edit2 size={13} />
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => toggleMutation.mutate({ id: hint.id, active: hint.isActive === false })}>
                    {hint.isActive !== false
                      ? <Eye size={13} className="text-emerald-400" />
                      : <EyeOff size={13} className="text-white/30" />}
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => setDeletingHintId(hint.id)}>
                    <Trash2 size={13} className="text-red-400/60" />
                  </Button>
                </div>
              </div>
            </div>
          ))
        )}
        </div>
      </div>

      {hints && hints.totalPages > 1 && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination
            page={page}
            totalPages={hints.totalPages}
            totalElements={hints.totalElements}
            onPageChange={setPage}
          />
        </div>
      )}

      <Modal
        isOpen={!!editingHint}
        onClose={() => setEditingHint(null)}
        title={editingHint ? `Edit Hint — ${editingHint.countryName || ''}` : 'Edit Hint'}
        className={editingHint?.imageUrl || editingHint?.hintType === 'IMAGE_FACT' ? 'max-w-3xl' : undefined}
      >
        <div className={editingHint?.imageUrl ? 'flex flex-col sm:flex-row gap-5' : ''}>
          {editingHint?.imageUrl && (
            <div className="sm:w-56 shrink-0 flex flex-col gap-2">
              <div className="relative group overflow-hidden rounded-lg border border-white/10">
                <img
                  src={editingHint.imageUrl}
                  alt=""
                  className="w-full object-contain max-h-80 bg-black/40"
                />
              </div>
              <button
                type="button"
                onClick={() => setShowDeleteImageConfirm(true)}
                className="text-[11px] text-red-400/60 hover:text-red-400 transition-colors cursor-pointer text-center"
              >
                Delete Image
              </button>
            </div>
          )}

          <div className="flex-1 min-w-0 space-y-4">
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Hint Text</label>
              <textarea
                value={editForm.hintText}
                onChange={e => setEditForm(f => ({ ...f, hintText: e.target.value }))}
                className="w-full h-32 px-4 py-3 bg-white/[.04] border border-white/[.08] rounded-xl text-white text-sm resize-none focus:outline-none focus:border-white/20"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-white/35 mb-1.5">Difficulty (1-100)</label>
                <Input
                  type="number"
                  min={1}
                  max={100}
                  value={editForm.difficulty}
                  onChange={e => setEditForm(f => ({ ...f, difficulty: e.target.value }))}
                />
              </div>
              <div>
                <label className="block text-xs text-white/35 mb-1.5">Quality Score (0.00-1.00)</label>
                <Input
                  type="number"
                  step="0.01"
                  min={0}
                  max={1}
                  value={editForm.qualityScore}
                  onChange={e => setEditForm(f => ({ ...f, qualityScore: e.target.value }))}
                />
              </div>
            </div>

            {editingHint && (
              <div className="grid grid-cols-3 gap-3 pt-1">
                {[
                  { label: 'Country', value: `${editingHint.countryCode || ''} ${editingHint.countryName || '—'}`.trim() },
                  { label: 'Category', value: editingHint.category },
                  { label: 'Type', value: editingHint.hintType || '—' },
                  { label: 'Source', value: editingHint.source || '—' },
                  { label: 'Year', value: editingHint.sourceYear ?? '—' },
                  { label: 'Used', value: `${editingHint.timesUsed ?? 0} times` },
                ].map(f => (
                  <div key={f.label} className="rounded-lg bg-white/[.03] px-3 py-2">
                    <p className="text-[10px] text-white/25 uppercase tracking-widest">{f.label}</p>
                    <p className="text-sm text-white/60 mt-0.5">{f.value}</p>
                  </div>
                ))}
              </div>
            )}

            {editingHint && (
              <ImageMetadataPanel
                imageUrl={editingHint.imageUrl}
                originalImageUrl={editingHint.originalImageUrl}
                imageSourcePageUrl={editingHint.imageSourcePageUrl}
                imageAuthorName={editingHint.imageAuthorName}
                imageAttributionText={editingHint.imageAttributionText}
                imageLicenseLabel={editingHint.imageLicenseLabel}
                imageLicenseUrl={editingHint.imageLicenseUrl}
              />
            )}

            {editingHint?.hintType === 'IMAGE_FACT' && (
              <div className="rounded-xl border border-white/[.08] bg-white/[.025] p-3.5 space-y-2.5">
                <div>
                  <p className="text-xs text-white/55">Wikimedia Commons image</p>
                  <p className="mt-0.5 text-[11px] text-white/25">
                    Enter an exact Commons file name with or without the File: prefix.
                  </p>
                </div>
                <div className="flex flex-col sm:flex-row gap-2">
                  <Input
                    value={wikimediaFileTitle}
                    onChange={event => setWikimediaFileTitle(event.target.value)}
                    onKeyDown={event => {
                      if (event.key === 'Enter') {
                        event.preventDefault()
                        handleWikimediaImageSave()
                      }
                    }}
                    placeholder="File:Example.jpg or Example.jpg"
                    disabled={replaceImageMutation.isPending}
                  />
                  <Button
                    type="button"
                    onClick={handleWikimediaImageSave}
                    disabled={!wikimediaFileTitle.trim() || replaceImageMutation.isPending}
                    isLoading={replaceImageMutation.isPending}
                    className="shrink-0"
                  >
                    {editingHint.imageUrl ? 'Replace image' : 'Add image'}
                  </Button>
                </div>
              </div>
            )}

            {editingHint && !editingHint.imageUrl && (editingHint.originalImageUrl || editingHint.imageAuthorName || editingHint.imageLicenseLabel || editingHint.imageAttributionText || editingHint.imageSourcePageUrl || editingHint.imageLicenseUrl) && (
              <button
                type="button"
                onClick={() => setShowDeleteImageConfirm(true)}
                className="text-[11px] text-red-400/60 hover:text-red-400 transition-colors cursor-pointer"
              >
                Delete Image Metadata
              </button>
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
              <Button variant="ghost" onClick={() => setEditingHint(null)}>{t.common.cancel}</Button>
              <Button onClick={handleSave} isLoading={updateMutation.isPending}>{t.common.save}</Button>
            </div>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={deletingHintId !== null}
        onClose={() => setDeletingHintId(null)}
        onConfirm={() => deletingHintId && deleteMutation.mutate(deletingHintId)}
        title="Delete hint?"
        description="This will soft-delete the hint (mark as inactive)."
        confirmLabel={t.common.delete}
        variant="danger"
        isLoading={deleteMutation.isPending}
      />

      <ConfirmDialog
        isOpen={showDeleteImageConfirm}
        onClose={() => setShowDeleteImageConfirm(false)}
        onConfirm={() => { if (editingHint) deleteImageMutation.mutate(editingHint.id); setShowDeleteImageConfirm(false) }}
        title="Delete image data?"
        description="All image-related fields (URL, attribution, license, author) will be cleared from this hint."
        confirmLabel="Delete"
        variant="danger"
        isLoading={deleteImageMutation.isPending}
      />

      <ConfirmDialog
        isOpen={showCacheConfirm}
        onClose={() => setShowCacheConfirm(false)}
        onConfirm={() => { cacheMutation.mutate(); setShowCacheConfirm(false) }}
        title="Invalidate hint cache?"
        description="All cached hint data in Redis will be cleared. Fresh data will be loaded from the database."
        confirmLabel="Invalidate"
        variant="danger"
        isLoading={cacheMutation.isPending}
      />

      <Modal isOpen={showCreateModal} onClose={() => setShowCreateModal(false)} title="Create Hint">
        <div className="space-y-4">
          <div>
            <label className="block text-xs text-white/35 mb-1.5">Country</label>
            <Input
              placeholder="Search countries..."
              value={createCountrySearch}
              onChange={e => setCreateCountrySearch(e.target.value)}
              className="mb-2"
            />
            {createCountries && createCountries.length > 0 && (
              <div className="max-h-32 overflow-y-auto rounded-lg border border-white/[.06] bg-white/[.02]">
                {createCountries.map(c => (
                  <button
                    key={c.id}
                    type="button"
                    onClick={() => { setCreateForm(f => ({ ...f, countryId: c.id })); setCreateCountrySearch(c.nameEn) }}
                    className={cn(
                      'w-full flex items-center gap-2 px-3 py-1.5 text-left text-sm hover:bg-white/[.04] transition-colors',
                      createForm.countryId === c.id && 'bg-accent/10 text-accent'
                    )}
                  >
                    <img
                      src={getFlagUrlByIso2(c.isoAlpha2, 'w20') ?? ''}
                      alt={c.isoAlpha2} width={16} height={12}
                      className="rounded-[2px] shrink-0"
                      onError={e => { (e.target as HTMLImageElement).style.display = 'none' }}
                    />
                    <span className="text-white/70">{c.nameEn}</span>
                    <span className="text-white/25 text-xs ml-auto">{c.isoAlpha2}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Category</label>
              <CustomSelect
                value={createForm.category}
                onChange={v => setCreateForm(f => ({ ...f, category: v }))}
                options={categories?.map(c => ({ value: c, label: c })) ?? []}
                placeholder="Select category..."
              />
            </div>
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Hint Type</label>
              <CustomSelect
                value={createForm.hintType}
                onChange={v => setCreateForm(f => ({ ...f, hintType: v }))}
                options={hintTypes?.map(t => ({ value: t, label: t })) ?? []}
                placeholder="Select type..."
              />
            </div>
          </div>

          <div>
            <label className="block text-xs text-white/35 mb-1.5">
              Hint Text
              <span className="text-white/20 ml-2">{createForm.hintText.length} / 10000</span>
            </label>
            <textarea
              value={createForm.hintText}
              onChange={e => setCreateForm(f => ({ ...f, hintText: e.target.value }))}
              maxLength={10000}
              className="w-full h-32 px-4 py-3 bg-white/[.04] border border-white/[.08] rounded-xl text-white text-sm resize-none focus:outline-none focus:border-white/20"
            />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Difficulty (1-100)</label>
              <Input
                type="number" min={1} max={100}
                value={createForm.difficulty}
                onChange={e => setCreateForm(f => ({ ...f, difficulty: e.target.value }))}
              />
            </div>
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Quality (0-1)</label>
              <Input
                type="number" step="0.01" min={0} max={1}
                value={createForm.qualityScore}
                onChange={e => setCreateForm(f => ({ ...f, qualityScore: e.target.value }))}
              />
            </div>
            <div>
              <label className="block text-xs text-white/35 mb-1.5">Source Year</label>
              <Input
                type="number"
                value={createForm.sourceYear}
                onChange={e => setCreateForm(f => ({ ...f, sourceYear: e.target.value }))}
                placeholder="Optional"
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setShowCreateModal(false)}>{t.common.cancel}</Button>
            <Button
              onClick={handleCreate}
              isLoading={createMutation.isPending}
              disabled={!createForm.countryId || !createForm.category || !createForm.hintType || !createForm.hintText || !createForm.difficulty}
            >
              Create Hint
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
