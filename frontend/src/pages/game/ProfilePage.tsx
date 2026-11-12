import { useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Camera, Eye, EyeOff, Search, Trash2 } from 'lucide-react'
import { gameApi } from '@/api/game'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { ProfileOverview } from '@/components/game/profile/ProfileOverview'
import { Header } from '@/components/layout/Header'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Skeleton } from '@/components/ui/Skeleton'
import { UserAvatar } from '@/components/ui/UserAvatar'
import { useProfile } from '@/hooks/useGameQueries'
import { useTranslation } from '@/i18n'
import { useAuthStore } from '@/store/authStore'
import { useToastStore } from '@/store/toastStore'
import { AVATAR_FILE_ACCEPT, validateAvatarFile } from '@/utils/avatarUpload'
import { extractApiErrorMessage } from '@/utils/errors'
import { GuestProfileContent } from './GuestProfileContent'

export function ProfilePage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { user, authResolved, isAuthenticated } = useAuthStore()
  const toast = useToastStore()
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [editName, setEditName] = useState('')
  const [editDiscoverable, setEditDiscoverable] = useState(true)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const { data: profile, isLoading } = useProfile(authResolved && isAuthenticated)

  const updateMutation = useMutation({
    mutationFn: (payload: { displayName: string; socialDiscoverable: boolean }) => gameApi.updateProfile(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      queryClient.invalidateQueries({ queryKey: ['friends'] })
      toast.addToast({ type: 'success', message: t.profile.settingsUpdated })
    },
    onError: (err) => {
      toast.addToast({ type: 'error', message: extractApiErrorMessage(err, t.profile.settingsUpdateFailed) })
    },
  })

  const avatarUploadMutation = useMutation({
    mutationFn: (file: File) => gameApi.uploadAvatar(file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      toast.addToast({ type: 'success', message: t.profile.avatarUpdated })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, t.profile.avatarUploadFailed) }),
  })

  const avatarDeleteMutation = useMutation({
    mutationFn: () => gameApi.deleteAvatar(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      toast.addToast({ type: 'success', message: t.profile.avatarRemoved })
    },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, t.profile.avatarRemoveFailed) }),
  })

  const handleOpenSettings = () => {
    setEditName(profile?.displayName || '')
    setEditDiscoverable(profile?.socialDiscoverable ?? true)
    setSettingsOpen(true)
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      const validationError = validateAvatarFile(file)
      if (validationError) {
        toast.addToast({ type: 'error', message: validationError })
      } else {
        avatarUploadMutation.mutate(file)
      }
    }
    e.target.value = ''
  }

  const handleSaveProfile = () => {
    updateMutation.mutate({
      displayName: editName.trim(),
      socialDiscoverable: editDiscoverable,
    })
  }

  const avatarName = profile?.displayName || profile?.username || profile?.email || user?.email
  const profileDiscoverable = profile?.socialDiscoverable ?? true
  const settingsChanged = !!profile
    && (editName.trim() !== (profile.displayName || '') || editDiscoverable !== profileDiscoverable)

  return (
    <div className="relative min-h-screen overflow-hidden bg-background text-white">
      <GameAuroraBackdrop />

      <Header />

      <main className="relative z-10 mx-auto flex w-full max-w-[1940px] flex-col px-4 py-8 sm:px-8 sm:py-16 xl:px-10 2xl:px-12">
        {!authResolved || (isAuthenticated && isLoading) ? (
          <div className="grid gap-6 2xl:grid-cols-2 2xl:items-stretch">
            <div className="space-y-6">
              <Skeleton variant="rect" height={320} />
              <Skeleton variant="rect" height={180} />
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {Array.from({ length: 6 }).map((_, i) => (
                  <Skeleton key={i} variant="rect" height={116} />
                ))}
              </div>
            </div>
            <Skeleton variant="rect" height={760} />
          </div>
        ) : !isAuthenticated ? (
          <GuestProfileContent />
        ) : profile ? (
          <ProfileOverview
            profile={profile}
            email={profile.email || user?.email}
            showSettings={!profile.profileLocked}
            onSettingsClick={handleOpenSettings}
          />
        ) : (
          <div className="py-20 text-center text-white/25">
            Failed to load profile
          </div>
        )}
      </main>

      <Modal
        isOpen={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        title={t.profile.editProfile}
        className="sm:max-w-xl"
      >
        {profile && (
          <div className="space-y-4 sm:space-y-5">
            <div className="relative overflow-hidden rounded-[1.25rem] border border-white/[.06] bg-white/[.02] p-4 sm:rounded-[1.4rem] sm:p-5">
              <div className="pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent via-white/8 to-transparent" />

              <p className="mb-3 text-[9px] font-semibold uppercase tracking-[.2em] text-white/24 sm:text-[10px] sm:tracking-[.18em]">{t.profile.avatar}</p>

              <div className="flex items-center gap-3.5 sm:gap-4">
                <div className="relative shrink-0 group">
                  <UserAvatar avatarUrl={profile.avatarUrl} name={avatarName} size="md" />
                  <div
                    onClick={() => fileInputRef.current?.click()}
                    className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 transition-opacity cursor-pointer group-hover:opacity-100"
                  >
                    <Camera size={18} className="text-white/80" />
                  </div>
                </div>

                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-white/85">{avatarName}</p>
                  <p className="mt-0.5 text-[11px] leading-relaxed text-white/28 sm:text-xs sm:text-white/32">
                    {t.profile.avatarHelp}
                  </p>

                  <div className="mt-3 flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => fileInputRef.current?.click()}
                      isLoading={avatarUploadMutation.isPending}
                    >
                      {!avatarUploadMutation.isPending && <Camera size={12} />}
                      {t.common.upload}
                    </Button>
                    {profile.avatarUrl ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => avatarDeleteMutation.mutate()}
                        isLoading={avatarDeleteMutation.isPending}
                        className="text-red-400/80 hover:text-red-300"
                      >
                        <Trash2 size={12} />
                        {t.common.remove}
                      </Button>
                    ) : null}
                  </div>
                </div>

                <input
                  ref={fileInputRef}
                  type="file"
                  accept={AVATAR_FILE_ACCEPT}
                  onChange={handleFileChange}
                  className="hidden"
                />
              </div>
            </div>

            <div className="relative overflow-hidden rounded-[1.25rem] border border-white/[.06] bg-white/[.02] p-4 sm:rounded-[1.4rem] sm:p-5">
              <div className="pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent via-white/8 to-transparent" />

              <p className="mb-3 text-[9px] font-semibold uppercase tracking-[.2em] text-white/24 sm:text-[10px] sm:tracking-[.18em]">{t.profile.displayName}</p>

              <div className="flex flex-col gap-2.5 sm:flex-row sm:gap-3">
                <Input
                  value={editName}
                  onChange={e => setEditName(e.target.value)}
                  placeholder={t.profile.displayName}
                  onKeyDown={e => { if (e.key === 'Enter') handleSaveProfile() }}
                  className="flex-1"
                />
              </div>

              <p className="mt-3 text-[10px] leading-relaxed text-white/20 sm:text-[11px] sm:text-white/24">
                {profile.username
                  ? t.profile.displayNameHelp.replace('{username}', profile.username)
                  : t.profile.displayNameHelpFallback}
              </p>
            </div>

            <div className="relative overflow-hidden rounded-[1.25rem] border border-white/[.06] bg-white/[.02] p-4 sm:rounded-[1.4rem] sm:p-5">
              <div className="pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent via-white/8 to-transparent" />

              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <p className="mb-3 text-[9px] font-semibold uppercase tracking-[.2em] text-white/24 sm:text-[10px] sm:tracking-[.18em]">
                    Search visibility
                  </p>
                  <div className="flex items-center gap-2 text-sm font-semibold text-white/85">
                    <Search size={15} className="text-emerald-200/65" />
                    Player search
                  </div>
                  <p className="mt-2 max-w-md text-[11px] leading-relaxed text-white/28 sm:text-xs sm:text-white/32">
                    Allow other registered players to find your profile when they search by username or display name.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setEditDiscoverable(value => !value)}
                  className="flex shrink-0 items-center gap-2 rounded-xl border border-white/[.08] bg-black/20 px-3 py-2 text-xs font-semibold text-white/70 transition-colors hover:bg-white/[.06]"
                >
                  {editDiscoverable ? (
                    <Eye size={14} className="text-emerald-200/75" />
                  ) : (
                    <EyeOff size={14} className="text-white/35" />
                  )}
                  {editDiscoverable ? 'Visible' : 'Hidden'}
                </button>
              </div>
            </div>

            <div className="flex justify-end">
              <Button
                onClick={handleSaveProfile}
                isLoading={updateMutation.isPending}
                disabled={!settingsChanged}
                size="md"
                className="w-full shrink-0 sm:w-auto"
              >
                {t.profile.saveChanges}
              </Button>
            </div>

            <div className="rounded-[1rem] border border-white/[.04] bg-white/[.01] px-3.5 py-2.5 text-[10px] leading-relaxed text-white/24 sm:rounded-[1.15rem] sm:px-4 sm:py-3 sm:text-[11px] sm:text-white/28">
              {t.profile.applyNote}
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
