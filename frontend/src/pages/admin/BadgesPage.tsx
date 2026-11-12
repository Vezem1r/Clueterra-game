import { useState, useMemo, useRef, type ChangeEvent } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { DragDropContext, Droppable, Draggable, type DropResult } from '@hello-pangea/dnd'
import {
  Award, Plus, Edit2, Trash2, X, Play, Upload, Eye, EyeOff,
  FileText, AlertCircle, CheckCircle2, GripVertical, Layers, Save,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { Tabs } from '@/components/ui/Tabs'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { BadgeColorPresetPicker } from '@/components/admin/BadgeColorPresetPicker'
import { BadgeCategoryManager } from '@/components/admin/BadgeCategoryManager'
import { adminApi } from '@/api/admin'
import { DEFAULT_BADGE_ACCENT_KEY, DEFAULT_BADGE_EFFECT_KEY, getBadgeColorPreset } from '@/constants/badgeColors'
import { useDebounce } from '@/hooks/useDebounce'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import type { BadgeAdminDto, BadgeEvalLogDto, SaveBadgeRequest, BadgeTranslationEntry } from '@/types'
import { extractApiErrorMessage } from '@/utils/errors'

const GRANT_TYPE_OPTIONS = [
  { value: '', label: 'All Types' },
  { value: 'TEMPLATE', label: 'Template' },
  { value: 'CUSTOM', label: 'Custom' },
  { value: 'MANUAL', label: 'Manual' },
]

const REGION_OPTIONS = [
  { value: 'ALL', label: 'All Regions (World)' },
  { value: 'Africa', label: 'Africa' },
  { value: 'sub:North America', label: 'North America' },
  { value: 'sub:South America', label: 'South America' },
  { value: 'Asia', label: 'Asia' },
  { value: 'Europe', label: 'Europe' },
  { value: 'Oceania', label: 'Oceania' },
  { value: 'Antarctic', label: 'Antarctic' },
]

const LOCALES = ['en', 'ru', 'cs'] as const


interface FilterRow {
  field: string
  op: string
  value: string
}

function emptyTranslation(): BadgeTranslationEntry {
  return { name: '', descriptionUnlocked: '', descriptionLocked: '' }
}

export function BadgesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToastStore()

  const [pageTab, setPageTab] = useState<'badges' | 'evalLog'>('badges')
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [grantTypeFilter, setGrantTypeFilter] = useState('')
  const [secretFilter, setSecretFilter] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const debouncedSearch = useDebounce(search)
  const [logPage, setLogPage] = useState(0)

  const { data: badges, isLoading } = useQuery({
    queryKey: ['admin-badges', page, debouncedSearch, grantTypeFilter, secretFilter, activeFilter],
    queryFn: () => adminApi.getBadges({
      page,
      size: 20,
      search: debouncedSearch || undefined,
      grantType: grantTypeFilter || undefined,
      isSecret: secretFilter === '' ? undefined : secretFilter === 'true',
      isActive: activeFilter === '' ? undefined : activeFilter === 'true',
      sortBy: 'created_at',
      sortDir: 'desc',
    }),
  })

  const { data: metadata } = useQuery({
    queryKey: ['admin-badges-metadata'],
    queryFn: adminApi.getBadgeMetadata,
    staleTime: 300_000,
  })

  const { data: evalLogs, isLoading: isLoadingLogs } = useQuery({
    queryKey: ['admin-badge-eval-log', logPage],
    queryFn: () => adminApi.getBadgeEvalLog({ page: logPage, size: 20 }),
    enabled: pageTab === 'evalLog',
  })

  const [orderMode, setOrderMode] = useState(false)
  const [localOrderedBadges, setLocalOrderedBadges] = useState<BadgeAdminDto[] | null>(null)
  const [hasUnsavedOrder, setHasUnsavedOrder] = useState(false)

  const { data: allBadgesForOrder, isLoading: isLoadingForOrder } = useQuery({
    queryKey: ['admin-badges-order'],
    queryFn: () => adminApi.getBadges({ size: 500, sortBy: 'display_order', sortDir: 'asc' }),
    enabled: orderMode && pageTab === 'badges',
  })

  const { data: categories } = useQuery({
    queryKey: ['admin-badge-categories'],
    queryFn: adminApi.getCategories,
    staleTime: 30_000,
  })

  const orderedBadges = useMemo(
    () => localOrderedBadges ?? allBadgesForOrder?.content ?? [],
    [localOrderedBadges, allBadgesForOrder?.content]
  )

  const badgeGroups = useMemo(() => {
    const groups: { id: string | null; name: string | null; badges: BadgeAdminDto[] }[] = []
    for (const cat of categories ?? []) {
      const catBadges = orderedBadges.filter(b => b.categoryId === cat.id)
      groups.push({ id: cat.id, name: cat.name, badges: catBadges })
    }
    const uncategorized = orderedBadges.filter(b => !b.categoryId)
    groups.push({ id: null, name: 'Uncategorized', badges: uncategorized })
    return groups
  }, [orderedBadges, categories])

  const handleOrderDragEnd = (result: DropResult) => {
    if (!result.destination) return
    if (result.source.droppableId === result.destination.droppableId && result.source.index === result.destination.index) return

    const srcGroupId = result.source.droppableId === 'uncategorized' ? null : result.source.droppableId
    const dstGroupId = result.destination.droppableId === 'uncategorized' ? null : result.destination.droppableId

    const updated = [...orderedBadges]
    const idx = updated.findIndex(b => b.id === result.draggableId)
    if (idx === -1) return
    const [moved] = updated.splice(idx, 1)

    const updatedMoved = srcGroupId !== dstGroupId
      ? { ...moved, categoryId: dstGroupId, categoryName: categories?.find(c => c.id === dstGroupId)?.name ?? null }
      : moved

    const dstGroupBadges = updated.filter(b =>
      dstGroupId === null ? !b.categoryId : b.categoryId === dstGroupId
    )
    const insertAfterBadge = dstGroupBadges[result.destination.index - 1]
    const insertBeforeBadge = dstGroupBadges[result.destination.index]

    let insertIdx: number
    if (insertBeforeBadge) {
      insertIdx = updated.findIndex(b => b.id === insertBeforeBadge.id)
    } else if (insertAfterBadge) {
      insertIdx = updated.findIndex(b => b.id === insertAfterBadge.id) + 1
    } else {
      const lastInGroup = updated.map((b, i) => ({ b, i })).filter(({ b }) =>
        dstGroupId === null ? !b.categoryId : b.categoryId === dstGroupId
      ).at(-1)
      insertIdx = lastInGroup ? lastInGroup.i + 1 : updated.length
    }

    updated.splice(insertIdx, 0, updatedMoved)
    setLocalOrderedBadges(updated)
    setHasUnsavedOrder(true)
  }

  const saveOrderMutation = useMutation({
    mutationFn: async () => {
      await adminApi.reorderBadges(orderedBadges.map(b => b.id))
      const categoryChanges = orderedBadges.filter(b => {
        const original = allBadgesForOrder?.content.find(ob => ob.id === b.id)
        return original && original.categoryId !== b.categoryId
      })
      await Promise.all(categoryChanges.map(b =>
        adminApi.updateBadge(b.id, { categoryId: b.categoryId ?? null } as SaveBadgeRequest)
      ))
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-badges-order'] })
      queryClient.invalidateQueries({ queryKey: ['admin-badges'] })
      queryClient.invalidateQueries({ queryKey: ['admin-badge-categories'] })
      setLocalOrderedBadges(null)
      setHasUnsavedOrder(false)
      toast.addToast({ type: 'success', message: 'Order saved' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to save order') }),
  })

  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<BadgeAdminDto | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<BadgeAdminDto | null>(null)
  const [modalTab, setModalTab] = useState('details')
  const [langTab, setLangTab] = useState('en')

  const [form, setForm] = useState({
    slug: '',
    isSecret: false,
    isActive: true,
    accentColorKey: DEFAULT_BADGE_ACCENT_KEY,
    effectColorKey: DEFAULT_BADGE_EFFECT_KEY,
    grantType: 'TEMPLATE',
    displayOrder: 0,
    translations: { en: emptyTranslation() } as Record<string, BadgeTranslationEntry>,
    trigger: '',
    threshold: 1,
    thresholdRegion: '',
    categoryId: '',
    filters: [] as FilterRow[],
    filterLogic: 'AND',
  })

  const imageInputRef = useRef<HTMLInputElement>(null)

  const resetForm = () => {
    setForm({
      slug: '', isSecret: false, isActive: true, accentColorKey: DEFAULT_BADGE_ACCENT_KEY,
      effectColorKey: DEFAULT_BADGE_EFFECT_KEY, grantType: 'TEMPLATE', displayOrder: 0,
      translations: { en: emptyTranslation() }, trigger: '', threshold: 1, thresholdRegion: '',
      categoryId: '', filters: [], filterLogic: 'AND',
    })
    setModalTab('details')
    setLangTab('en')
  }

  const handleCreate = () => {
    setEditing(null)
    resetForm()
    setModalOpen(true)
  }

  const handleEdit = (badge: BadgeAdminDto) => {
    setEditing(badge)
    const translations: Record<string, BadgeTranslationEntry> = {}
    if (badge.translations) {
      for (const [locale, t] of Object.entries(badge.translations)) {
        translations[locale] = { ...t }
      }
    }
    if (!translations.en) translations.en = emptyTranslation()

    let trigger = ''
    let threshold = 1
    let thresholdRegion = ''
    let filters: FilterRow[] = []
    let filterLogic = 'AND'

    if (badge.grantConfig && typeof badge.grantConfig === 'object') {
      const gc = badge.grantConfig as Record<string, unknown>
      trigger = (gc.trigger as string) || ''
      if (trigger === 'REGION_COMPLETED') {
        thresholdRegion = (gc.threshold as string) || ''
      } else {
        threshold = (gc.threshold as number) || 1
      }
      if (Array.isArray(gc.filters)) {
        filters = (gc.filters as FilterRow[]).map(f => ({ ...f }))
      }
      filterLogic = (gc.logic as string) || 'AND'
    }

    setForm({
      slug: badge.slug, isSecret: badge.isSecret, isActive: badge.isActive,
      accentColorKey: badge.accentColorKey || DEFAULT_BADGE_ACCENT_KEY,
      effectColorKey: badge.effectColorKey || DEFAULT_BADGE_EFFECT_KEY,
      grantType: badge.grantType, displayOrder: badge.displayOrder,
      categoryId: badge.categoryId ?? '',
      translations, trigger, threshold, thresholdRegion, filters, filterLogic,
    })
    setModalTab('details')
    setLangTab('en')
    setModalOpen(true)
  }

  const buildRequest = (): SaveBadgeRequest => {
    let grantConfig: unknown = null
    if (form.grantType === 'TEMPLATE') {
      const threshold = form.trigger === 'REGION_COMPLETED' ? form.thresholdRegion : form.threshold
      grantConfig = { trigger: form.trigger, threshold }
    } else if (form.grantType === 'CUSTOM') {
      grantConfig = { filters: form.filters, logic: form.filterLogic }
    }

    const translations: Record<string, BadgeTranslationEntry> = {}
    for (const [locale, entry] of Object.entries(form.translations)) {
      if (entry.name.trim()) {
        translations[locale] = entry
      }
    }

    return {
      slug: form.slug, isSecret: form.isSecret, isActive: form.isActive,
      accentColorKey: form.accentColorKey, effectColorKey: form.effectColorKey,
      grantType: form.grantType, grantConfig, displayOrder: form.displayOrder,
      categoryId: form.categoryId || null,
      translations,
    }
  }

  const saveMutation = useMutation({
    mutationFn: (req: SaveBadgeRequest) =>
      editing ? adminApi.updateBadge(editing.id, req) : adminApi.createBadge(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-badges'] })
      setModalOpen(false)
      toast.addToast({ type: 'success', message: editing ? 'Badge updated' : 'Badge created' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to save badge') }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteBadge(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-badges'] })
      setDeleteTarget(null)
      toast.addToast({ type: 'success', message: 'Badge deleted' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to delete badge') }),
  })

  const evaluateAllMutation = useMutation({
    mutationFn: adminApi.evaluateAllBadges,
    onSuccess: () => toast.addToast({ type: 'success', message: 'Evaluation completed' }),
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Evaluation failed') }),
  })

  const uploadImageMutation = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => adminApi.uploadBadgeImage(id, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-badges'] })
      toast.addToast({ type: 'success', message: 'Image uploaded' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to upload') }),
  })

  const handleImageChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file && editing) {
      uploadImageMutation.mutate({ id: editing.id, file })
    }
    e.target.value = ''
  }

  const handleSave = () => saveMutation.mutate(buildRequest())

  const setTranslation = (locale: string, field: keyof BadgeTranslationEntry, value: string) => {
    setForm(f => ({
      ...f,
      translations: {
        ...f.translations,
        [locale]: { ...(f.translations[locale] || emptyTranslation()), [field]: value },
      },
    }))
  }

  const addFilter = () => {
    setForm(f => ({ ...f, filters: [...f.filters, { field: '', op: '>=', value: '' }] }))
  }

  const updateFilter = (idx: number, key: keyof FilterRow, value: string) => {
    setForm(f => {
      const filters = [...f.filters]
      filters[idx] = { ...filters[idx], [key]: value }
      return { ...f, filters }
    })
  }

  const removeFilter = (idx: number) => {
    setForm(f => ({ ...f, filters: f.filters.filter((_, i) => i !== idx) }))
  }

  const hasFilters = !!(search || grantTypeFilter || secretFilter || activeFilter)
  const activeFilterCount = [grantTypeFilter, secretFilter, activeFilter].filter(Boolean).length
  const clearFilters = () => { setSearch(''); setGrantTypeFilter(''); setSecretFilter(''); setActiveFilter(''); setPage(0) }

  const currentTranslation = form.translations[langTab] || emptyTranslation()

  const activePagination = pageTab === 'badges'
    ? (badges && badges.totalPages > 1 ? { page, totalPages: badges.totalPages, totalElements: badges.totalElements, onPageChange: setPage } : null)
    : (evalLogs && evalLogs.totalPages > 1 ? { page: logPage, totalPages: evalLogs.totalPages, totalElements: evalLogs.totalElements, onPageChange: setLogPage } : null)

  return (
    <div className="flex flex-col h-full">
      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.badges}</h1>
            <p className="text-white/40 text-sm mt-1">{t.admin.manageBadges}</p>
          </div>
          <div className="flex items-center gap-2">
            {hasUnsavedOrder && (
              <Button variant="outline" size="sm" onClick={() => saveOrderMutation.mutate()} isLoading={saveOrderMutation.isPending}>
                <Save size={14} /> Save Order
              </Button>
            )}
            <Button
              variant={orderMode ? 'outline' : 'ghost'}
              size="sm"
              onClick={() => { setOrderMode(m => !m); setLocalOrderedBadges(null); setHasUnsavedOrder(false) }}
            >
              <Layers size={14} /> {orderMode ? 'Exit Order' : 'Manage Order'}
            </Button>
            <Button variant="outline" size="sm" onClick={() => evaluateAllMutation.mutate()} isLoading={evaluateAllMutation.isPending}>
              <Play size={14} /> {t.admin.evaluateAll}
            </Button>
            <Button onClick={handleCreate}>
              <Plus size={14} /> {t.admin.createBadge}
            </Button>
          </div>
        </div>

        <Tabs
          tabs={[
            { id: 'badges', label: t.admin.badges },
            { id: 'evalLog', label: t.admin.evalLog },
          ]}
          activeTab={pageTab}
          onChange={id => setPageTab(id as 'badges' | 'evalLog')}
        />

        {pageTab === 'badges' && (
          <AdminFilterBar
            search={search}
            onSearchChange={v => { setSearch(v); setPage(0) }}
            searchPlaceholder="Search by slug..."
            activeFilterCount={activeFilterCount}
            onClearFilters={clearFilters}
            hasFilters={hasFilters}
          >
            <CustomSelect value={grantTypeFilter} onChange={v => { setGrantTypeFilter(v); setPage(0) }} options={GRANT_TYPE_OPTIONS} placeholder="All Types" />
            <CustomSelect
              value={secretFilter}
              onChange={v => { setSecretFilter(v); setPage(0) }}
              options={[{ value: '', label: t.common.all }, { value: 'true', label: t.admin.secret }, { value: 'false', label: 'Not Secret' }]}
              placeholder={t.common.all}
            />
            <CustomSelect
              value={activeFilter}
              onChange={v => { setActiveFilter(v); setPage(0) }}
              options={[{ value: '', label: t.common.all }, { value: 'true', label: t.common.active }, { value: 'false', label: t.common.inactive }]}
              placeholder={t.common.all}
            />
          </AdminFilterBar>
        )}
      </div>

      <div className="flex-1 min-h-0 px-5 sm:px-6 lg:px-8 pb-3">
        <div className="h-full border border-white/[.06] rounded-xl bg-white/[.03] overflow-auto">
          {pageTab === 'badges' && orderMode && (
            <div className="p-5 space-y-5">
              <BadgeCategoryManager onCategoryChange={() => queryClient.invalidateQueries({ queryKey: ['admin-badges-order'] })} />

              {isLoadingForOrder ? (
                <SkeletonTable rows={6} cols={3} />
              ) : (
                <DragDropContext onDragEnd={handleOrderDragEnd}>
                  <div className="space-y-4">
                    {badgeGroups.map(group => (
                      <div key={group.id ?? '__uncategorized'}>
                        <p className="text-[10px] text-white/25 uppercase tracking-widest mb-2">
                          {group.name} <span className="font-mono normal-case">({group.badges.length})</span>
                        </p>
                        <Droppable droppableId={group.id ?? 'uncategorized'}>
                          {(provided) => (
                            <div
                              ref={provided.innerRef}
                              {...provided.droppableProps}
                              className="space-y-1 min-h-[32px]"
                            >
                              {group.badges.map((badge, index) => (
                                <Draggable key={badge.id} draggableId={badge.id} index={index}>
                                  {(drag) => (
                                    <div
                                      ref={drag.innerRef}
                                      {...drag.draggableProps}
                                      className="flex items-center gap-2 rounded-lg bg-white/[.04] border border-white/[.06] px-2 py-1.5"
                                    >
                                      <div {...drag.dragHandleProps} className="text-white/20 hover:text-white/50 cursor-grab active:cursor-grabbing">
                                        <GripVertical size={14} />
                                      </div>
                                      <div className="w-6 h-6 rounded bg-white/[.06] flex items-center justify-center overflow-hidden shrink-0">
                                        {badge.imageUrl ? (
                                          <img src={badge.imageUrl} alt="" className="w-5 h-5 object-contain" />
                                        ) : (
                                          <Award size={11} className="text-white/20" />
                                        )}
                                      </div>
                                      <span className="flex-1 text-sm text-white/80">{badge.translations?.en?.name || badge.slug}</span>
                                      <span className="text-xs text-white/25 font-mono">{badge.slug}</span>
                                      <Badge variant={badge.grantType === 'TEMPLATE' ? 'info' : badge.grantType === 'CUSTOM' ? 'warning' : 'neutral'} className="text-[10px]">
                                        {badge.grantType}
                                      </Badge>
                                    </div>
                                  )}
                                </Draggable>
                              ))}
                              {provided.placeholder}
                            </div>
                          )}
                        </Droppable>
                      </div>
                    ))}
                  </div>
                </DragDropContext>
              )}
            </div>
          )}

          {pageTab === 'badges' && !orderMode && (
            <>
              {isLoading ? (
                <div className="p-5"><SkeletonTable rows={8} cols={7} /></div>
              ) : !badges?.content?.length ? (
                <EmptyState icon={Award} title={t.common.noResults} className="py-16" />
              ) : (
                <>
                  <table className="hidden sm:table w-full text-sm">
                    <thead>
                      <tr className="border-b border-white/[.06] bg-[#0e0e14] sticky top-0 z-10">
                        <th className="px-4 py-3 text-left text-[10px] text-white/25 uppercase tracking-widest font-medium w-12" />
                        <th className="px-4 py-3 text-left text-[10px] text-white/25 uppercase tracking-widest font-medium">Name</th>
                        <th className="px-4 py-3 text-left text-[10px] text-white/25 uppercase tracking-widest font-medium">{t.admin.slug}</th>
                        <th className="px-4 py-3 text-left text-[10px] text-white/25 uppercase tracking-widest font-medium">{t.admin.grantType}</th>
                        <th className="px-4 py-3 text-center text-[10px] text-white/25 uppercase tracking-widest font-medium">{t.admin.secret}</th>
                        <th className="px-4 py-3 text-center text-[10px] text-white/25 uppercase tracking-widest font-medium">Status</th>
                        <th className="px-4 py-3 text-center text-[10px] text-white/25 uppercase tracking-widest font-medium">{t.admin.owners}</th>
                        <th className="px-4 py-3 text-right text-[10px] text-white/25 uppercase tracking-widest font-medium w-24" />
                      </tr>
                    </thead>
                    <tbody>
                      {badges.content.map((badge: BadgeAdminDto) => (
                        <tr key={badge.id} className="border-b border-white/[.04] hover:bg-white/[.02] transition-colors">
                          <td className="px-4 py-3">
                            <div className="w-8 h-8 rounded-lg bg-white/[.06] flex items-center justify-center overflow-hidden">
                              {badge.imageUrl ? (
                                <img src={badge.imageUrl} alt="" className="w-6 h-6 object-contain" />
                              ) : (
                                <Award size={14} className="text-white/20" />
                              )}
                            </div>
                          </td>
                          <td className="px-4 py-3 text-white/80">{badge.translations?.en?.name || '—'}</td>
                          <td className="px-4 py-3 text-white/50 font-mono text-xs">{badge.slug}</td>
                          <td className="px-4 py-3">
                            <Badge variant={badge.grantType === 'TEMPLATE' ? 'info' : badge.grantType === 'CUSTOM' ? 'warning' : 'neutral'} className="text-[10px]">
                              {badge.grantType}
                            </Badge>
                          </td>
                          <td className="px-4 py-3 text-center">
                            {badge.isSecret ? <EyeOff size={14} className="text-amber-400/60 mx-auto" /> : <Eye size={14} className="text-white/15 mx-auto" />}
                          </td>
                          <td className="px-4 py-3 text-center">
                            <Badge variant={badge.isActive ? 'success' : 'neutral'} className="text-[10px]">
                              {badge.isActive ? t.common.active : t.common.inactive}
                            </Badge>
                          </td>
                          <td className="px-4 py-3 text-center text-white/50 font-mono text-xs">{badge.ownerCount}</td>
                          <td className="px-4 py-3 text-right">
                            <div className="flex items-center justify-end gap-1">
                              <Button variant="ghost" size="sm" onClick={() => handleEdit(badge)}><Edit2 size={13} /></Button>
                              <Button variant="ghost" size="sm" onClick={() => setDeleteTarget(badge)} className="text-red-400 hover:text-red-300"><Trash2 size={13} /></Button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div className="sm:hidden divide-y divide-white/[.04]">
                    {badges.content.map((badge: BadgeAdminDto) => (
                      <div key={badge.id} className="flex items-center gap-3 px-4 py-3">
                        <div className="w-10 h-10 rounded-xl bg-white/[.06] flex items-center justify-center overflow-hidden shrink-0">
                          {badge.imageUrl ? (
                            <img src={badge.imageUrl} alt="" className="w-8 h-8 object-contain" />
                          ) : (
                            <Award size={16} className="text-white/20" />
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm text-white/80 truncate">{badge.translations?.en?.name || badge.slug}</p>
                          <p className="text-[11px] text-white/35 font-mono truncate">{badge.slug}</p>
                          <div className="flex items-center gap-1.5 mt-1">
                            <Badge variant={badge.grantType === 'TEMPLATE' ? 'info' : badge.grantType === 'CUSTOM' ? 'warning' : 'neutral'} className="text-[9px]">
                              {badge.grantType}
                            </Badge>
                            <Badge variant={badge.isActive ? 'success' : 'neutral'} className="text-[9px]">
                              {badge.isActive ? t.common.active : t.common.inactive}
                            </Badge>
                            {badge.isSecret && <EyeOff size={11} className="text-amber-400/60" />}
                            <span className="text-[10px] text-white/25">{badge.ownerCount} owners</span>
                          </div>
                        </div>
                        <div className="flex items-center gap-1 shrink-0">
                          <Button variant="ghost" size="sm" onClick={() => handleEdit(badge)}><Edit2 size={13} /></Button>
                          <Button variant="ghost" size="sm" onClick={() => setDeleteTarget(badge)} className="text-red-400 hover:text-red-300"><Trash2 size={13} /></Button>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </>
          )}

          {pageTab === 'evalLog' && (
            <>
              {isLoadingLogs ? (
                <div className="p-5"><SkeletonTable rows={8} cols={6} /></div>
              ) : !evalLogs?.content?.length ? (
                <EmptyState icon={FileText} title={t.common.noResults} className="py-16" />
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-white/[.06] bg-[#0e0e14] sticky top-0 z-10">
                      <th className="px-4 py-3 text-left text-[10px] text-white/25 uppercase tracking-widest font-medium">Badge ID</th>
                      <th className="px-4 py-3 text-left text-[10px] text-white/25 uppercase tracking-widest font-medium">Type</th>
                      <th className="px-4 py-3 text-center text-[10px] text-white/25 uppercase tracking-widest font-medium">Evaluated</th>
                      <th className="px-4 py-3 text-center text-[10px] text-white/25 uppercase tracking-widest font-medium">Granted</th>
                      <th className="px-4 py-3 text-center text-[10px] text-white/25 uppercase tracking-widest font-medium">Duration</th>
                      <th className="px-4 py-3 text-left text-[10px] text-white/25 uppercase tracking-widest font-medium">Started</th>
                      <th className="px-4 py-3 text-center text-[10px] text-white/25 uppercase tracking-widest font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {evalLogs.content.map((log: BadgeEvalLogDto) => (
                      <tr key={log.id} className="border-b border-white/[.04] hover:bg-white/[.02] transition-colors">
                        <td className="px-4 py-3 text-white/50 font-mono text-xs">{log.badgeId}</td>
                        <td className="px-4 py-3">
                          <Badge variant={log.evaluationType === 'MANUAL' ? 'info' : 'neutral'} className="text-[10px]">
                            {log.evaluationType}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 text-center text-white/60 font-mono text-xs">{log.usersEvaluated}</td>
                        <td className="px-4 py-3 text-center text-white/60 font-mono text-xs">{log.usersGranted}</td>
                        <td className="px-4 py-3 text-center text-white/40 text-xs">
                          {log.durationMs != null ? `${log.durationMs}ms` : '—'}
                        </td>
                        <td className="px-4 py-3 text-white/40 text-xs">
                          {new Date(log.startedAt).toLocaleString()}
                        </td>
                        <td className="px-4 py-3 text-center">
                          {log.errorMessage ? (
                            <span className="inline-flex items-center gap-1 text-red-400/80 text-xs" title={log.errorMessage}>
                              <AlertCircle size={12} /> Error
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 text-emerald-400/70 text-xs">
                              <CheckCircle2 size={12} /> OK
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </div>
      </div>

      {activePagination && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination page={activePagination.page} totalPages={activePagination.totalPages} totalElements={activePagination.totalElements} onPageChange={activePagination.onPageChange} />
        </div>
      )}

      {/* Create/Edit Modal */}
      <Modal isOpen={modalOpen} onClose={() => setModalOpen(false)} title={editing ? t.admin.editBadge : t.admin.createBadge} className="max-w-4xl">
        <div className="space-y-5">
          <Tabs
            tabs={[
              { id: 'details', label: t.admin.details },
              { id: 'grant', label: t.admin.grantLogic },
            ]}
            activeTab={modalTab}
            onChange={setModalTab}
          />

          {modalTab === 'details' && (
            <div className="space-y-5">
              {/* Image upload (edit only) */}
              {editing && (
                <div className="flex items-center gap-4">
                  <div
                    className={`flex h-16 w-16 items-center justify-center overflow-hidden rounded-xl border bg-white/[.06] ${editing.imageUrl ? 'cursor-zoom-in' : ''}`}
                    style={{
                      borderColor: `${getBadgeColorPreset(form.accentColorKey)?.hex ?? '#7dd3fc'}66`,
                      boxShadow: `0 0 26px ${(getBadgeColorPreset(form.effectColorKey)?.hex ?? '#fcd34d')}22`,
                    }}
                    onClick={() => editing.imageUrl && setImagePreview(editing.imageUrl)}
                  >
                    {editing.imageUrl ? (
                      <img src={editing.imageUrl} alt="" className="w-12 h-12 object-contain" />
                    ) : (
                      <Award size={20} className="text-white/20" />
                    )}
                  </div>
                  <Button variant="outline" size="sm" onClick={() => imageInputRef.current?.click()} isLoading={uploadImageMutation.isPending}>
                    <Upload size={13} /> Upload Image
                  </Button>
                  <input ref={imageInputRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif,image/svg+xml" onChange={handleImageChange} className="hidden" />
                </div>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">{t.admin.slug}</label>
                  <Input value={form.slug} onChange={e => setForm(f => ({ ...f, slug: e.target.value }))} placeholder="level-10" />
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">{t.admin.displayOrder}</label>
                  <Input type="number" min="0" value={String(form.displayOrder)} onChange={e => setForm(f => ({ ...f, displayOrder: parseInt(e.target.value) || 0 }))} />
                </div>
              </div>

              <div>
                <label className="block text-xs text-white/35 mb-1.5">Category</label>
                <CustomSelect
                  value={form.categoryId}
                  onChange={v => setForm(f => ({ ...f, categoryId: v }))}
                  options={[
                    { value: '', label: 'Uncategorized' },
                    ...(categories ?? []).map(c => ({ value: c.id, label: c.name })),
                  ]}
                  placeholder="Uncategorized"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="flex items-center gap-2.5 h-10">
                  <label className="text-xs text-white/35">{t.common.active}</label>
                  <div
                    onClick={() => setForm(f => ({ ...f, isActive: !f.isActive }))}
                    className={`w-9 h-5 rounded-full relative transition-colors cursor-pointer ${form.isActive ? 'bg-emerald-500/70' : 'bg-white/[.08]'}`}
                  >
                    <div className={`absolute top-0.5 w-4 h-4 rounded-full bg-white transition-all ${form.isActive ? 'left-4' : 'left-0.5'}`} />
                  </div>
                </div>
                <div className="flex items-center gap-2.5 h-10">
                  <label className="text-xs text-white/35">{t.admin.secret}</label>
                  <div
                    onClick={() => setForm(f => ({ ...f, isSecret: !f.isSecret }))}
                    className={`w-9 h-5 rounded-full relative transition-colors cursor-pointer ${form.isSecret ? 'bg-amber-500/70' : 'bg-white/[.08]'}`}
                  >
                    <div className={`absolute top-0.5 w-4 h-4 rounded-full bg-white transition-all ${form.isSecret ? 'left-4' : 'left-0.5'}`} />
                  </div>
                </div>
              </div>

              <div className="grid gap-4 lg:grid-cols-2">
                <BadgeColorPresetPicker
                  label="Accent color"
                  helper="Controls the rotating ring, card border and glass backdrop."
                  value={form.accentColorKey}
                  onChange={value => setForm(f => ({ ...f, accentColorKey: value }))}
                />
                <BadgeColorPresetPicker
                  label="Effect color"
                  helper="Controls particles, glow bursts and secondary highlights."
                  value={form.effectColorKey}
                  onChange={value => setForm(f => ({ ...f, effectColorKey: value }))}
                />
              </div>

              {/* Translations */}
              <div>
                <p className="text-[10px] text-white/25 uppercase tracking-widest mb-3">{t.admin.translations}</p>
                <div className="flex gap-1 mb-3">
                  {LOCALES.map(l => (
                    <button
                      key={l}
                      onClick={() => setLangTab(l)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-colors ${langTab === l ? 'bg-white/[.08] text-white' : 'text-white/30 hover:text-white/60'}`}
                    >
                      {l.toUpperCase()}
                    </button>
                  ))}
                </div>
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs text-white/35 mb-1.5">Name</label>
                    <Input value={currentTranslation.name} onChange={e => setTranslation(langTab, 'name', e.target.value)} placeholder="Badge name" />
                  </div>
                  <div>
                    <label className="block text-xs text-white/35 mb-1.5">{t.admin.descriptionUnlocked}</label>
                    <textarea
                      value={currentTranslation.descriptionUnlocked}
                      onChange={e => setTranslation(langTab, 'descriptionUnlocked', e.target.value)}
                      placeholder="Shown when earned"
                      rows={2}
                      className="w-full rounded-lg bg-white/[.04] border border-white/[.08] px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-white/20 resize-none"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-white/35 mb-1.5">{t.admin.descriptionLocked}</label>
                    <textarea
                      value={currentTranslation.descriptionLocked}
                      onChange={e => setTranslation(langTab, 'descriptionLocked', e.target.value)}
                      placeholder="How to get it"
                      rows={2}
                      className="w-full rounded-lg bg-white/[.04] border border-white/[.08] px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-white/20 resize-none"
                    />
                  </div>
                </div>
              </div>
            </div>
          )}

          {modalTab === 'grant' && (
            <div className="space-y-5">
              <div>
                <label className="block text-xs text-white/35 mb-1.5">{t.admin.grantType}</label>
                <CustomSelect
                  value={form.grantType}
                  onChange={v => setForm(f => ({ ...f, grantType: v }))}
                  options={[
                    { value: 'TEMPLATE', label: t.admin.template },
                    { value: 'CUSTOM', label: t.admin.custom },
                    { value: 'MANUAL', label: t.admin.manual },
                  ]}
                  placeholder={t.admin.grantType}
                />
              </div>

              {form.grantType === 'TEMPLATE' && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs text-white/35 mb-1.5">{t.admin.trigger}</label>
                    <CustomSelect
                      value={form.trigger}
                      onChange={v => setForm(f => ({ ...f, trigger: v, thresholdRegion: '' }))}
                      options={metadata?.triggers.map(tr => ({ value: tr, label: tr })) ?? []}
                      placeholder={t.admin.trigger}
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-white/35 mb-1.5">{t.admin.threshold}</label>
                    {form.trigger === 'REGION_COMPLETED' ? (
                      <CustomSelect
                        value={form.thresholdRegion}
                        onChange={v => setForm(f => ({ ...f, thresholdRegion: v }))}
                        options={REGION_OPTIONS}
                        placeholder="Select region"
                      />
                    ) : (
                      <Input type="number" min="1" value={String(form.threshold)} onChange={e => setForm(f => ({ ...f, threshold: parseInt(e.target.value) || 1 }))} />
                    )}
                  </div>
                </div>
              )}

              {form.grantType === 'CUSTOM' && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <p className="text-xs text-white/35">{t.admin.filters}</p>
                    <div className="flex items-center gap-2">
                      <CustomSelect
                        value={form.filterLogic}
                        onChange={v => setForm(f => ({ ...f, filterLogic: v }))}
                        options={[{ value: 'AND', label: 'AND' }, { value: 'OR', label: 'OR' }]}
                        placeholder="Logic"
                      />
                      <Button variant="outline" size="sm" onClick={addFilter}>
                        <Plus size={13} /> {t.admin.addFilter}
                      </Button>
                    </div>
                  </div>
                  {form.filters.map((filter, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <CustomSelect
                        value={filter.field}
                        onChange={v => updateFilter(idx, 'field', v)}
                        options={metadata?.filterFields.map(f => ({ value: f, label: f })) ?? []}
                        placeholder={t.admin.field}
                      />
                      <CustomSelect
                        value={filter.op}
                        onChange={v => updateFilter(idx, 'op', v)}
                        options={['>=', '<=', '=', '>', '<', '!='].map(o => ({ value: o, label: o }))}
                        placeholder={t.admin.operator}
                      />
                      <Input
                        value={filter.value}
                        onChange={e => updateFilter(idx, 'value', e.target.value)}
                        placeholder={t.admin.value}
                        className="w-24"
                      />
                      <Button variant="ghost" size="sm" onClick={() => removeFilter(idx)} className="text-red-400">
                        <X size={13} />
                      </Button>
                    </div>
                  ))}
                </div>
              )}

              {form.grantType === 'MANUAL' && (
                <p className="text-sm text-white/30">Manual badges are granted by admins from the user management panel.</p>
              )}
            </div>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setModalOpen(false)}>{t.common.cancel}</Button>
            <Button onClick={handleSave} isLoading={saveMutation.isPending}>{t.common.save}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
        title={t.common.delete}
        message={`Delete badge "${deleteTarget?.slug}"? This will remove it from all users.`}
        isLoading={deleteMutation.isPending}
      />

      <AnimatePresence>
        {imagePreview && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 backdrop-blur-sm cursor-zoom-out"
            onClick={() => setImagePreview(null)}
          >
            <img src={imagePreview} alt="" className="max-h-[80vh] max-w-[80vw] object-contain rounded-2xl shadow-2xl" />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
