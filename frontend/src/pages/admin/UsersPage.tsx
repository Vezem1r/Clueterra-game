import { useState, useMemo, useRef, type ChangeEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Users, Edit2, Upload, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { Tabs } from '@/components/ui/Tabs'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { AdminTableHeader } from '@/components/admin/AdminTableHeader'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { TableToolbarButtons } from '@/components/admin/TableToolbarButtons'
import { BadgePickerModal } from '@/components/admin/BadgePickerModal'
import { adminApi } from '@/api/admin'
import { useDebounce } from '@/hooks/useDebounce'
import { useAdminTable, type ColumnDef } from '@/hooks/useAdminTable'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import type { UserAdminDto, BadgeAdminDto } from '@/types'
import { AVATAR_FILE_ACCEPT, validateAvatarFile } from '@/utils/avatarUpload'
import { cn } from '@/utils/cn'
import { extractApiErrorMessage } from '@/utils/errors'

const ACTIONS_GRAD = 'linear-gradient(to right, transparent, rgb(15,15,20) 28px, rgb(15,15,20))'

const ALL_COLUMNS: ColumnDef[] = [
  { key: 'avatar',           label: '',             sortKey: undefined,           defaultVisible: true,  defaultWidth: 36  },
  { key: 'email',            label: 'Email',        sortKey: 'email',             defaultVisible: true,  defaultWidth: 220 },
  { key: 'displayName',      label: 'Name',         sortKey: 'displayName',       defaultVisible: true,  defaultWidth: 150 },
  { key: 'role',             label: 'Role',         sortKey: 'role',              defaultVisible: true,  defaultWidth: 80  },
  { key: 'currentLevel',     label: 'Level',        sortKey: 'currentLevel',      defaultVisible: true,  defaultWidth: 65  },
  { key: 'totalXp',          label: 'XP',           sortKey: 'totalXp',           defaultVisible: true,  defaultWidth: 80  },
  { key: 'challengesPlayed', label: 'Played',       sortKey: 'challengesPlayed',  defaultVisible: true,  defaultWidth: 70  },
  { key: 'budgetCompletedChallenges', label: 'Budget', sortKey: 'budgetCompletedChallenges', defaultVisible: true, defaultWidth: 75 },
  { key: 'riversPlayed', label: 'Rivers played', sortKey: 'riversPlayed', defaultVisible: true, defaultWidth: 105 },
  { key: 'silhouettesPlayed', label: 'Silhouettes', sortKey: 'silhouettesPlayed', defaultVisible: true, defaultWidth: 100 },
  { key: 'flagsPlayed', label: 'Flags', sortKey: 'flagsPlayed', defaultVisible: true, defaultWidth: 90 },
  { key: 'quizCountries', label: 'World quiz', sortKey: 'quizCountries', defaultVisible: true, defaultWidth: 100 },
  { key: 'challengesWon',    label: 'Won',          sortKey: 'challengesWon',     defaultVisible: true,  defaultWidth: 60  },
  { key: 'currentStreak',    label: 'Streak',       sortKey: 'currentStreak',     defaultVisible: false, defaultWidth: 70  },
  { key: 'bestStreak',       label: 'Best',         sortKey: 'bestStreak',        defaultVisible: false, defaultWidth: 60  },
  { key: 'reportsMuted',     label: 'Muted',        sortKey: 'reportsMuted',      defaultVisible: true,  defaultWidth: 70  },
  { key: 'enabled',          label: 'Status',       sortKey: 'enabled',           defaultVisible: true,  defaultWidth: 90  },
  { key: 'actions',          label: '',             sortKey: undefined,           defaultVisible: true,  defaultWidth: 60  },
]

function CellValue({ col, user }: { col: ColumnDef; user: UserAdminDto }) {
  switch (col.key) {
    case 'avatar':
      return (
        <div className="w-7 h-7 rounded-full bg-white/[.06] flex items-center justify-center shrink-0">
          {user.avatarUrl ? (
            <img src={user.avatarUrl} alt="" className="w-7 h-7 rounded-full object-cover" />
          ) : (
            <span className="text-[10px] font-medium text-white/40">
              {(user.displayName || user.email)?.[0]?.toUpperCase()}
            </span>
          )}
        </div>
      )
    case 'email':
      return <span className="text-sm text-white/85 truncate">{user.email}</span>
    case 'displayName':
      return <span className="text-sm text-white/60 truncate">{user.displayName || '—'}</span>
    case 'role':
      return (
        <Badge variant={user.role === 'ADMIN' ? 'info' : 'neutral'} className="text-[10px]">
          {user.role}
        </Badge>
      )
    case 'currentLevel':
      return <span className="text-xs text-white/50 font-mono">{user.currentLevel}</span>
    case 'totalXp':
      return <span className="text-xs text-amber-400/80 font-mono">{user.totalXp.toLocaleString()}</span>
    case 'challengesPlayed':
      return <span className="text-xs text-white/50 font-mono">{user.challengesPlayed}</span>
    case 'budgetCompletedChallenges':
      return <span className="text-xs text-amber-300/80 font-mono">{user.budgetCompletedChallenges}</span>
    case 'riversPlayed':
      return <span className="text-xs text-cyan-300/80 font-mono">{user.riverStats.attemptedRivers.toLocaleString()}</span>
    case 'silhouettesPlayed':
      return <span className="text-xs text-violet-300/80 font-mono">{user.silhouetteStats.attemptedCountries.toLocaleString()}</span>
    case 'flagsPlayed':
      return <span className="text-xs text-rose-300/80 font-mono">{user.flagStats.attemptedFlags.toLocaleString()}</span>
    case 'quizCountries':
      return <span className="text-xs text-orange-200/80 font-mono">{user.quizStats.exploredCountries.toLocaleString()}</span>
    case 'challengesWon':
      return <span className="text-xs text-emerald-400/80 font-mono">{user.challengesWon}</span>
    case 'currentStreak':
      return <span className="text-xs text-white/50 font-mono">{user.currentStreak}</span>
    case 'bestStreak':
      return <span className="text-xs text-white/50 font-mono">{user.bestStreak}</span>
    case 'reportsMuted':
      return (
        <Badge variant={user.reportsMuted ? 'error' : 'neutral'} className="text-[10px]">
          {user.reportsMuted ? 'Muted' : 'No'}
        </Badge>
      )
    case 'enabled':
      return (
        <Badge variant={user.enabled ? 'success' : 'neutral'} className="text-[10px]">
          {user.enabled ? 'Active' : 'Disabled'}
        </Badge>
      )
    default:
      return null
  }
}

function UserRow({ user, pinned, dataCols, dataColsTemplate, hasActions, actionsRefCb, onEdit, t }: {
  user: UserAdminDto; pinned: boolean; dataCols: ColumnDef[]; dataColsTemplate: string
  hasActions: boolean; actionsRefCb: (node: HTMLDivElement | null) => void
  onEdit: (u: UserAdminDto) => void; t: ReturnType<typeof useTranslation>['t']
}) {
  return (
    <div className={cn('group', !user.enabled && 'opacity-60')}>
      <div className={cn(
        'hidden sm:flex items-center min-w-max border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors',
        pinned && 'bg-violet-500/[.04] border-b-violet-500/10'
      )}>
        <div className="grid gap-0 px-3 py-3 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: dataColsTemplate }}>
          {dataCols.map(col => (
            <div
              key={col.key}
              className={cn(
                'overflow-hidden px-2',
                col.key === 'enabled' && 'flex justify-center',
              )}
            >
              <CellValue col={col} user={user} />
            </div>
          ))}
        </div>
        {hasActions && (
          <div ref={actionsRefCb} className="sticky right-0 z-10 shrink-0 flex items-center gap-0.5 justify-end pl-8 pr-2 ml-auto"
            style={{ background: pinned ? 'linear-gradient(to right, transparent, rgb(18,14,24) 28px, rgb(18,14,24))' : ACTIONS_GRAD }}>
            <Button variant="ghost" size="sm" onClick={() => onEdit(user)} title={t.common.edit}>
              <Edit2 size={13} />
            </Button>
          </div>
        )}
      </div>

      <div className={cn(
        'sm:hidden flex items-center gap-3 px-4 py-3 border-b border-white/[.04]',
        pinned && 'bg-violet-500/[.04]'
      )}>
        <div className="w-9 h-9 rounded-full bg-white/[.06] flex items-center justify-center shrink-0">
          {user.avatarUrl ? (
            <img src={user.avatarUrl} alt="" className="w-9 h-9 rounded-full object-cover" />
          ) : (
            <span className="text-xs font-medium text-white/40">
              {(user.displayName || user.email)?.[0]?.toUpperCase()}
            </span>
          )}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-white/80 truncate">{user.displayName || user.email}</p>
          <p className="text-xs text-white/35">
            {user.role} · Lvl {user.currentLevel} · {user.totalXp.toLocaleString()} XP · {user.riverStats.attemptedRivers.toLocaleString()} rivers · {user.silhouetteStats.attemptedCountries.toLocaleString()} silhouettes · {user.flagStats.attemptedFlags.toLocaleString()} flags · {user.quizStats.exploredCountries.toLocaleString()} quiz countries
          </p>
        </div>
        <Badge variant={user.enabled ? 'success' : 'neutral'}>
          {user.enabled ? 'Active' : 'Off'}
        </Badge>
        <Button variant="ghost" size="sm" onClick={() => onEdit(user)}>
          <Edit2 size={14} />
        </Button>
      </div>
    </div>
  )
}

export function UsersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToastStore()

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [enabledFilter, setEnabledFilter] = useState('')
  const [mutedFilter, setMutedFilter] = useState('')
  const [guestFilter, setGuestFilter] = useState('')
  const [minRiversPlayed, setMinRiversPlayed] = useState('')
  const [maxRiversPlayed, setMaxRiversPlayed] = useState('')
  const debouncedSearch = useDebounce(search)
  const debouncedMinRiversPlayed = useDebounce(minRiversPlayed)
  const debouncedMaxRiversPlayed = useDebounce(maxRiversPlayed)

  const { containerRef, ...table } = useAdminTable({
    storageKey: 'admin-users',
    columns: ALL_COLUMNS,
    defaultSort: 'createdAt',
    defaultSortDir: 'desc',
    pinnedColumns: ['avatar', 'actions'],
    onSortChange: () => setPage(0),
  })

  const [editingUser, setEditingUser] = useState<UserAdminDto | null>(null)
  const [modalTab, setModalTab] = useState('account')
  const [badgePickerOpen, setBadgePickerOpen] = useState(false)
  const [revokingBadgeId, setRevokingBadgeId] = useState<string | null>(null)
  const [editForm, setEditForm] = useState({
    role: '',
    enabled: true,
    reportsMuted: false,
    profileLocked: false,
    username: '',
    displayName: '',
    totalXp: '0',
    challengesPlayed: '0',
    challengesWon: '0',
    currentStreak: '0',
    bestStreak: '0',
    streakFreezes: '0',
  })
  const avatarInputRef = useRef<HTMLInputElement>(null)

  const handleEdit = (user: UserAdminDto) => {
    setEditingUser(user)
    setModalTab('account')
    setEditForm({
      role: user.role,
      enabled: user.enabled,
      reportsMuted: user.reportsMuted,
      profileLocked: user.profileLocked,
      username: user.username || '',
      displayName: user.displayName || '',
      totalXp: String(user.totalXp),
      challengesPlayed: String(user.challengesPlayed),
      challengesWon: String(user.challengesWon),
      currentStreak: String(user.currentStreak),
      bestStreak: String(user.bestStreak),
      streakFreezes: String(user.streakFreezes),
    })
  }

  const { data: users, isLoading } = useQuery({
    queryKey: ['admin-users', page, debouncedSearch, roleFilter, enabledFilter, mutedFilter, guestFilter,
      debouncedMinRiversPlayed, debouncedMaxRiversPlayed, table.sortBy, table.sortDir],
    queryFn: () => adminApi.getUsers({
      search: debouncedSearch || undefined,
      role: roleFilter || undefined,
      enabled: enabledFilter === '' ? undefined : enabledFilter === 'true',
      reportsMuted: mutedFilter === '' ? undefined : mutedFilter === 'true',
      isGuest: guestFilter === '' ? false : guestFilter === 'true',
      minRiversPlayed: debouncedMinRiversPlayed === '' ? undefined : Number(debouncedMinRiversPlayed),
      maxRiversPlayed: debouncedMaxRiversPlayed === '' ? undefined : Number(debouncedMaxRiversPlayed),
      page,
      size: 20,
      sortBy: table.sortBy,
      sortDir: table.sortDir,
    }),
  })

  const { data: roles } = useQuery({
    queryKey: ['admin-user-roles'],
    queryFn: adminApi.getUserRoles,
    staleTime: 300_000,
  })

  const updateMutation = useMutation({
    mutationFn: async ({ id, form }: { id: string; form: typeof editForm }) => {
      await adminApi.updateUser(id, { role: form.role, enabled: form.enabled, reportsMuted: form.reportsMuted, profileLocked: form.profileLocked })
      await adminApi.updateUserProfile(id, {
        username: form.username || undefined,
        displayName: form.displayName || undefined,
        totalXp: parseInt(form.totalXp) || 0,
        challengesPlayed: parseInt(form.challengesPlayed) || 0,
        challengesWon: parseInt(form.challengesWon) || 0,
        currentStreak: parseInt(form.currentStreak) || 0,
        bestStreak: parseInt(form.bestStreak) || 0,
        streakFreezes: parseInt(form.streakFreezes) || 0,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setEditingUser(null)
      toast.addToast({ type: 'success', message: 'User updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to update user') }),
  })

  const uploadAvatarMutation = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => adminApi.uploadUserAvatar(id, file),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      if (editingUser) setEditingUser({ ...editingUser, avatarUrl: data.avatarUrl })
      toast.addToast({ type: 'success', message: 'Avatar updated' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to upload avatar') }),
  })

  const deleteAvatarMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteUserAvatar(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      if (editingUser) setEditingUser({ ...editingUser, avatarUrl: '' })
      toast.addToast({ type: 'success', message: 'Avatar removed' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to remove avatar') }),
  })

  const handleAvatarFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file && editingUser) {
      const validationError = validateAvatarFile(file)
      if (validationError) {
        toast.addToast({ type: 'error', message: validationError })
      } else {
        uploadAvatarMutation.mutate({ id: editingUser.id, file })
      }
    }
    e.target.value = ''
  }

  const handleSave = () => {
    if (!editingUser) return
    updateMutation.mutate({ id: editingUser.id, form: editForm })
  }

  const { data: userBadges, isLoading: userBadgesLoading } = useQuery({
    queryKey: ['admin-user-badges', editingUser?.id],
    queryFn: () => adminApi.getUserBadges(editingUser!.id),
    enabled: !!editingUser && modalTab === 'badges',
  })

  const grantBadgeMutation = useMutation({
    mutationFn: ({ userId, badgeId }: { userId: string; badgeId: string }) =>
      adminApi.grantUserBadge(userId, badgeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-user-badges', editingUser?.id] })
      toast.addToast({ type: 'success', message: 'Badge granted' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to grant badge') }),
  })

  const revokeBadgeMutation = useMutation({
    mutationFn: ({ userId, badgeId }: { userId: string; badgeId: string }) =>
      adminApi.revokeUserBadge(userId, badgeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-user-badges', editingUser?.id] })
      setRevokingBadgeId(null)
      toast.addToast({ type: 'success', message: 'Badge revoked' })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to revoke badge') }),
  })

  const alreadyGrantedIds = useMemo(() => new Set(userBadges?.map(b => b.badgeId) ?? []), [userBadges])

  const hasFilters = !!(search || roleFilter || enabledFilter || mutedFilter || guestFilter || minRiversPlayed || maxRiversPlayed)
  const activeFilterCount = [roleFilter, enabledFilter, mutedFilter, guestFilter, minRiversPlayed, maxRiversPlayed].filter(Boolean).length
  const clearFilters = () => {
    setSearch(''); setRoleFilter(''); setEnabledFilter(''); setMutedFilter(''); setGuestFilter('')
    setMinRiversPlayed(''); setMaxRiversPlayed(''); setPage(0)
  }

  return (
    <div className="flex flex-col h-full">

      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.users}</h1>
          <p className="text-white/40 text-sm mt-1">{t.admin.manageUsers}</p>
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
        searchPlaceholder="Search by email or name..."
        activeFilterCount={activeFilterCount}
        onClearFilters={clearFilters}
        hasFilters={hasFilters}
        resultCount={users ? users.totalElements + (users.pinned?.length ?? 0) : undefined}
      >
        <CustomSelect
          value={roleFilter}
          onChange={v => { setRoleFilter(v); setPage(0) }}
          options={[
            { value: '', label: 'All Roles' },
            ...(roles?.map(r => ({ value: r, label: r })) ?? []),
          ]}
          placeholder="All Roles"
        />
        <CustomSelect
          value={enabledFilter}
          onChange={v => { setEnabledFilter(v); setPage(0) }}
          options={[
            { value: '', label: t.common.all },
            { value: 'true', label: t.common.active },
            { value: 'false', label: t.common.inactive },
          ]}
          placeholder={t.common.all}
        />
        <CustomSelect
          value={mutedFilter}
          onChange={v => { setMutedFilter(v); setPage(0) }}
          options={[
            { value: '', label: 'All Muted' },
            { value: 'true', label: 'Muted' },
            { value: 'false', label: 'Not Muted' },
          ]}
          placeholder="All Muted"
        />
        <CustomSelect
          value={guestFilter}
          onChange={v => { setGuestFilter(v); setPage(0) }}
          options={[
            { value: '', label: 'Real Users' },
            { value: 'true', label: 'Guests Only' },
          ]}
          placeholder="Real Users"
        />
        <div className="flex items-center gap-2 shrink-0" aria-label="Rivers played range">
          <span className="text-[11px] text-white/40 whitespace-nowrap">Rivers played</span>
          <Input
            type="number" min="0" max="2147483647" inputMode="numeric"
            value={minRiversPlayed}
            onChange={e => { if (/^\d*$/.test(e.target.value) && Number(e.target.value) <= 2147483647) { setMinRiversPlayed(e.target.value); setPage(0) } }}
            placeholder="Min" aria-label="Minimum rivers played"
            containerClassName="w-[70px]" className="h-8 px-2 text-xs"
          />
          <Input
            type="number" min="0" max="2147483647" inputMode="numeric"
            value={maxRiversPlayed}
            onChange={e => { if (/^\d*$/.test(e.target.value) && Number(e.target.value) <= 2147483647) { setMaxRiversPlayed(e.target.value); setPage(0) } }}
            placeholder="Max" aria-label="Maximum rivers played"
            containerClassName="w-[70px]" className="h-8 px-2 text-xs"
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
          centerColumns={['enabled']}
        />

        {isLoading ? (
          <div className="p-5"><SkeletonTable rows={8} cols={table.activeCols.length} /></div>
        ) : !users?.content?.length && !users?.pinned?.length ? (
          <EmptyState icon={Users} title={t.common.noResults} className="py-16" />
        ) : (
          <>
            {users?.pinned?.map(user => (
              <UserRow key={user.id} user={user} pinned dataCols={table.dataCols} dataColsTemplate={table.dataColsTemplate} hasActions={table.hasActions} actionsRefCb={table.actionsRefCb} onEdit={handleEdit} t={t} />
            ))}
            {users?.pinned?.length && users?.content?.length ? (
              <div className="border-b border-white/[.08]" />
            ) : null}
            {users?.content?.map(user => (
              <UserRow key={user.id} user={user} pinned={false} dataCols={table.dataCols} dataColsTemplate={table.dataColsTemplate} hasActions={table.hasActions} actionsRefCb={table.actionsRefCb} onEdit={handleEdit} t={t} />
            ))}
          </>
        )}
      </div>
      </div>

      {users && users.totalPages > 1 && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination
            page={page}
            totalPages={users.totalPages}
            totalElements={users.totalElements}
            onPageChange={setPage}
          />
        </div>
      )}

      <Modal
        isOpen={!!editingUser}
        onClose={() => setEditingUser(null)}
        title={editingUser ? `Edit User — ${editingUser.email}` : ''}
        className="max-w-3xl"
      >
        <Tabs
          tabs={[
            { id: 'account', label: 'General' },
            { id: 'badges', label: t.admin.badges },
          ]}
          activeTab={modalTab}
          onChange={setModalTab}
          className="mb-6"
        />

        {modalTab === 'account' && (
          <div className="space-y-6">
            <div className="flex items-start gap-5">
              <div className="relative w-16 h-16 rounded-full bg-white/[.06] flex items-center justify-center shrink-0 overflow-hidden group">
                {editingUser?.avatarUrl ? (
                  <img src={editingUser.avatarUrl} alt="" className="w-16 h-16 rounded-full object-cover" />
                ) : (
                  <span className="text-xl font-medium text-white/40">
                    {(editForm.displayName || editForm.username || editingUser?.email)?.[0]?.toUpperCase()}
                  </span>
                )}
              </div>
              <div className="flex-1 space-y-2">
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline" size="sm"
                    onClick={() => avatarInputRef.current?.click()}
                    isLoading={uploadAvatarMutation.isPending}
                  >
                    <Upload size={13} /> Upload
                  </Button>
                  {editingUser?.avatarUrl && (
                    <Button
                      variant="ghost" size="sm"
                      onClick={() => editingUser && deleteAvatarMutation.mutate(editingUser.id)}
                      isLoading={deleteAvatarMutation.isPending}
                      className="text-red-400 hover:text-red-300"
                    >
                      <Trash2 size={13} /> Remove
                    </Button>
                  )}
                </div>
                <input
                  ref={avatarInputRef}
                  type="file"
                  accept={AVATAR_FILE_ACCEPT}
                  onChange={handleAvatarFileChange}
                  className="hidden"
                />
              </div>
            </div>

            <div>
              <p className="text-[10px] text-white/25 uppercase tracking-widest mb-3">Account</p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Role</label>
                  {editingUser?.role === 'ADMIN' ? (
                    <div className="h-10 flex items-center px-3 rounded-lg bg-white/[.03] border border-white/[.06]">
                      <Badge variant="info" className="text-[10px]">ADMIN</Badge>
                    </div>
                  ) : (
                    <CustomSelect
                      value={editForm.role}
                      onChange={v => setEditForm(f => ({ ...f, role: v }))}
                      options={roles?.filter(r => r !== 'ADMIN').map(r => ({ value: r, label: r })) ?? [{ value: editForm.role, label: editForm.role }]}
                      placeholder="Role"
                    />
                  )}
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Status</label>
                  <div className="flex items-center gap-2.5 h-10">
                    <div
                      onClick={() => setEditForm(f => ({ ...f, enabled: !f.enabled }))}
                      className={cn(
                        'w-9 h-5 rounded-full relative transition-colors cursor-pointer',
                        editForm.enabled ? 'bg-emerald-500/70' : 'bg-white/[.08]'
                      )}
                    >
                      <div className={cn(
                        'absolute top-0.5 w-4 h-4 rounded-full bg-white transition-all',
                        editForm.enabled ? 'left-4' : 'left-0.5'
                      )} />
                    </div>
                    <span className="text-sm text-white/60">{editForm.enabled ? 'Active' : 'Disabled'}</span>
                  </div>
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Reports Muted</label>
                  <div className="flex items-center gap-2.5 h-10">
                    <div
                      onClick={() => setEditForm(f => ({ ...f, reportsMuted: !f.reportsMuted }))}
                      className={cn(
                        'w-9 h-5 rounded-full relative transition-colors cursor-pointer',
                        editForm.reportsMuted ? 'bg-red-500/70' : 'bg-white/[.08]'
                      )}
                    >
                      <div className={cn(
                        'absolute top-0.5 w-4 h-4 rounded-full bg-white transition-all',
                        editForm.reportsMuted ? 'left-4' : 'left-0.5'
                      )} />
                    </div>
                    <span className="text-sm text-white/60">{editForm.reportsMuted ? 'Muted' : 'Not muted'}</span>
                  </div>
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Profile Locked</label>
                  <div className="flex items-center gap-2.5 h-10">
                    <div
                      onClick={() => setEditForm(f => ({ ...f, profileLocked: !f.profileLocked }))}
                      className={cn(
                        'w-9 h-5 rounded-full relative transition-colors cursor-pointer',
                        editForm.profileLocked ? 'bg-red-500/70' : 'bg-white/[.08]'
                      )}
                    >
                      <div className={cn(
                        'absolute top-0.5 w-4 h-4 rounded-full bg-white transition-all',
                        editForm.profileLocked ? 'left-4' : 'left-0.5'
                      )} />
                    </div>
                    <span className="text-sm text-white/60">{editForm.profileLocked ? 'Locked' : 'Unlocked'}</span>
                  </div>
                </div>
              </div>
            </div>

            <div>
              <p className="text-[10px] text-white/25 uppercase tracking-widest mb-3">Profile</p>
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs text-white/35 mb-1.5">Username</label>
                    <Input
                      value={editForm.username}
                      onChange={e => setEditForm(f => ({ ...f, username: e.target.value }))}
                      placeholder="Unique username"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-white/35 mb-1.5">Display Name</label>
                    <Input
                      value={editForm.displayName}
                      onChange={e => setEditForm(f => ({ ...f, displayName: e.target.value }))}
                      placeholder="Optional"
                    />
                  </div>
                </div>
              </div>
            </div>

            <div>
              <p className="text-[10px] text-white/25 uppercase tracking-widest mb-3">Stats</p>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Total XP</label>
                  <Input
                    type="number" min="0"
                    value={editForm.totalXp}
                    onChange={e => setEditForm(f => ({ ...f, totalXp: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Played</label>
                  <Input
                    type="number" min="0"
                    value={editForm.challengesPlayed}
                    onChange={e => setEditForm(f => ({ ...f, challengesPlayed: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Won</label>
                  <Input
                    type="number" min="0"
                    value={editForm.challengesWon}
                    onChange={e => setEditForm(f => ({ ...f, challengesWon: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Current Streak</label>
                  <Input
                    type="number" min="0"
                    value={editForm.currentStreak}
                    onChange={e => setEditForm(f => ({ ...f, currentStreak: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Best Streak</label>
                  <Input
                    type="number" min="0"
                    value={editForm.bestStreak}
                    onChange={e => setEditForm(f => ({ ...f, bestStreak: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="block text-xs text-white/35 mb-1.5">Shields</label>
                  <Input
                    type="number" min="0"
                    value={editForm.streakFreezes}
                    onChange={e => setEditForm(f => ({ ...f, streakFreezes: e.target.value }))}
                  />
                </div>
              </div>
            </div>

            {editingUser && (
              <div>
                <p className="text-[10px] text-amber-200/45 uppercase tracking-widest mb-3">BudgetGuessr · derived</p>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {[
                    ['Completed', editingUser.budgetCompletedChallenges.toLocaleString()],
                    ['Questions', editingUser.budgetAnsweredQuestions.toLocaleString()],
                    ['Projects', editingUser.budgetProjectsExplored.toLocaleString()],
                    ['Mastered', formatCompactUsd(editingUser.budgetProjectsMasteredValueUsd)],
                    ['Full XP', `${editingUser.budgetFullXpRate.toFixed(1)}%`],
                    ['Avg. accuracy', `${editingUser.budgetAverageAccuracy.toFixed(1)}%`],
                    ['Clean sweeps', editingUser.budgetCleanSweeps.toLocaleString()],
                    ['Best H/L streak', editingUser.budgetBestHigherLowerStreak.toLocaleString()],
                  ].map(([label, value]) => (
                    <div key={label} className="rounded-xl border border-amber-200/[.08] bg-amber-200/[.025] px-3 py-3">
                      <p className="text-base font-bold text-white/85">{value}</p>
                      <p className="mt-1 text-[10px] text-white/30">{label}</p>
                    </div>
                  ))}
                </div>
                <p className="mt-2 text-[10px] text-white/20">Read-only projection rebuilt from BudgetGuessr answers.</p>
              </div>
            )}

            {editingUser && (
              <div>
                <p className="text-[10px] text-cyan-200/45 uppercase tracking-widest mb-3">Rivers · derived</p>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {[
                    ['Tried', editingUser.riverStats.attemptedRivers],
                    ['Solved', editingUser.riverStats.solvedRivers],
                    ['Guesses', editingUser.riverStats.answerAttempts],
                    ['Skipped', editingUser.riverStats.skips],
                    ['XP earned', editingUser.riverStats.xpAwarded],
                  ].map(([label, value]) => (
                    <div key={label} className="rounded-xl border border-cyan-200/[.08] bg-cyan-200/[.025] px-3 py-3">
                      <p className="text-base font-bold text-white/85">{value.toLocaleString()}</p>
                      <p className="mt-1 text-[10px] text-white/30">{label}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {editingUser && (
              <div>
                <p className="text-[10px] text-violet-200/45 uppercase tracking-widest mb-3">Country silhouettes · derived</p>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {[
                    ['Tried', editingUser.silhouetteStats.attemptedCountries],
                    ['Painted', `${editingUser.silhouetteStats.solvedCountries.toLocaleString()} / ${editingUser.silhouetteStats.totalCountries.toLocaleString()}`],
                    ['Guesses', editingUser.silhouetteStats.answerAttempts],
                    ['Skipped', editingUser.silhouetteStats.skips],
                    ['XP earned', editingUser.silhouetteStats.xpAwarded],
                  ].map(([label, value]) => (
                    <div key={label} className="rounded-xl border border-violet-200/[.08] bg-violet-200/[.025] px-3 py-3">
                      <p className="text-base font-bold text-white/85">{value.toLocaleString()}</p>
                      <p className="mt-1 text-[10px] text-white/30">{label}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {editingUser && (
              <div>
                <p className="text-[10px] text-rose-200/45 uppercase tracking-widest mb-3">Guess the flag · derived</p>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {[
                    ['Tried', editingUser.flagStats.attemptedFlags],
                    ['Raised', `${editingUser.flagStats.solvedFlags.toLocaleString()} / ${editingUser.flagStats.totalFlags.toLocaleString()}`],
                    ['Guesses', editingUser.flagStats.answerAttempts],
                    ['Skipped', editingUser.flagStats.skips],
                    ['XP earned', editingUser.flagStats.xpAwarded],
                  ].map(([label, value]) => (
                    <div key={label} className="rounded-xl border border-rose-200/[.08] bg-rose-200/[.025] px-3 py-3">
                      <p className="text-base font-bold text-white/85">{value.toLocaleString()}</p>
                      <p className="mt-1 text-[10px] text-white/30">{label}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {editingUser && (
              <div>
                <p className="text-[10px] text-orange-200/45 uppercase tracking-widest mb-3">Around the World Quiz · derived</p>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {[
                    ['Explored', `${editingUser.quizStats.exploredCountries.toLocaleString()} / ${editingUser.quizStats.totalCountries.toLocaleString()}`],
                    ['Answered', editingUser.quizStats.answeredQuestions],
                    ['Correct', editingUser.quizStats.correctAnswers],
                    ['XP earned', editingUser.quizStats.xpAwarded],
                  ].map(([label, value]) => (
                    <div key={label} className="rounded-xl border border-orange-200/[.08] bg-orange-200/[.025] px-3 py-3">
                      <p className="text-base font-bold text-white/85">{value.toLocaleString()}</p>
                      <p className="mt-1 text-[10px] text-white/30">{label}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" onClick={() => setEditingUser(null)}>{t.common.cancel}</Button>
              <Button onClick={handleSave} isLoading={updateMutation.isPending}>{t.common.save}</Button>
            </div>
          </div>
        )}

        {modalTab === 'badges' && (
          <div className="space-y-5">
            {userBadgesLoading ? (
              <div className="flex justify-center py-8">
                <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
              </div>
            ) : userBadges && userBadges.length > 0 ? (
              <div className="border border-white/[.06] rounded-xl overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-white/[.06] bg-white/[.02]">
                      <th className="text-left text-[10px] text-white/25 uppercase tracking-widest px-4 py-2.5 font-medium">Badge</th>
                      <th className="text-left text-[10px] text-white/25 uppercase tracking-widest px-4 py-2.5 font-medium">Granted</th>
                      <th className="text-left text-[10px] text-white/25 uppercase tracking-widest px-4 py-2.5 font-medium">By</th>
                      <th className="w-16" />
                    </tr>
                  </thead>
                  <tbody>
                    {userBadges.map(badge => (
                      <tr key={badge.badgeId} className="border-b border-white/[.04] last:border-0 hover:bg-white/[.02]">
                        <td className="px-4 py-2.5">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-lg bg-white/[.06] flex items-center justify-center shrink-0 overflow-hidden">
                              {badge.imageUrl ? (
                                <img src={badge.imageUrl} alt="" className="w-6 h-6 object-contain" />
                              ) : (
                                <span className="text-[10px] text-white/20">★</span>
                              )}
                            </div>
                            <span className="text-white/80">{badge.name}</span>
                          </div>
                        </td>
                        <td className="px-4 py-2.5 text-white/40">
                          {new Date(badge.grantedAt).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-2.5">
                          <Badge variant={badge.grantedBy === 'ADMIN' ? 'info' : 'neutral'} className="text-[10px]">
                            {badge.grantedBy}
                          </Badge>
                        </td>
                        <td className="px-4 py-2.5">
                          <Button
                            variant="ghost" size="sm"
                            onClick={() => setRevokingBadgeId(badge.badgeId)}
                            className="text-red-400 hover:text-red-300"
                          >
                            <Trash2 size={13} />
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-center text-white/25 text-sm py-6">No badges granted yet</p>
            )}

            <div>
              <Button onClick={() => setBadgePickerOpen(true)} variant="outline" size="sm">
                {t.admin.grantBadge}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      <BadgePickerModal
        isOpen={badgePickerOpen}
        onClose={() => setBadgePickerOpen(false)}
        alreadyGrantedIds={alreadyGrantedIds}
        onGrant={(badge: BadgeAdminDto) => editingUser && grantBadgeMutation.mutate({ userId: editingUser.id, badgeId: badge.id })}
      />

      <ConfirmDialog
        isOpen={!!revokingBadgeId}
        onClose={() => setRevokingBadgeId(null)}
        onConfirm={() => editingUser && revokingBadgeId && revokeBadgeMutation.mutate({ userId: editingUser.id, badgeId: revokingBadgeId })}
        title={t.admin.revoke}
        message="Are you sure you want to revoke this badge?"
        confirmLabel={t.admin.revoke}
        isLoading={revokeBadgeMutation.isPending}
        variant="danger"
      />
    </div>
  )
}

function formatCompactUsd(value: number) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)
}
