import { useMemo, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  ArrowRight,
  Ban,
  Bell,
  ChevronDown,
  Eye,
  Search,
  Sparkles,
  Trophy,
  UserMinus,
  UserPlus,
  Users,
  type LucideIcon,
} from 'lucide-react'

import { friendsApi } from '@/api/friends'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { ProfileHeroBackground } from '@/components/game/ProfileHeroBackground'
import { ProfileGlassPanel } from '@/components/game/profile/ProfileGlassPanel'
import { Header } from '@/components/layout/Header'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { Input } from '@/components/ui/Input'
import { Skeleton } from '@/components/ui/Skeleton'
import { Tooltip } from '@/components/ui/Tooltip'
import { UserAvatar } from '@/components/ui/UserAvatar'
import { useDebounce } from '@/hooks/useDebounce'
import { useTranslation } from '@/i18n'
import { useAuthStore } from '@/store/authStore'
import { toast } from '@/store/toastStore'
import type {
  Friend,
  FriendLeaderboardEntry,
  FriendRequest,
  FriendUser,
  RelationshipLeaderboardPeriod,
} from '@/types'
import { cn } from '@/utils/cn'
import { extractApiErrorMessage } from '@/utils/errors'
import { HomeLegalStrip } from './home/HomeLegalStrip'

interface ConfirmAction {
  title: string
  message: string
  confirmLabel: string
  variant?: 'danger' | 'default'
  run: () => void
}

const FRIENDS_QUERY_KEY = ['friends'] as const

export function FriendsPage() {
  const { t } = useTranslation()
  const copy = t.friends
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { authResolved, isAuthenticated } = useAuthStore()
  const [userSearch, setUserSearch] = useState('')
  const [friendsPeriod, setFriendsPeriod] = useState<RelationshipLeaderboardPeriod>('week')
  const [requestsView, setRequestsView] = useState<'incoming' | 'outgoing'>('incoming')
  const [friendsExpanded, setFriendsExpanded] = useState(false)
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null)
  const debouncedSearch = useDebounce(userSearch.trim(), 300)

  const friendsQuery = useQuery({
    queryKey: [...FRIENDS_QUERY_KEY, 'list'],
    queryFn: friendsApi.getFriends,
    enabled: authResolved && isAuthenticated,
  })

  const requestsQuery = useQuery({
    queryKey: [...FRIENDS_QUERY_KEY, 'requests'],
    queryFn: friendsApi.getFriendRequests,
    enabled: authResolved && isAuthenticated,
  })

  const friendsLeaderboardQuery = useQuery({
    queryKey: [...FRIENDS_QUERY_KEY, 'leaderboard', friendsPeriod],
    queryFn: () => friendsApi.getFriendsLeaderboard(friendsPeriod),
    enabled: authResolved && isAuthenticated,
  })

  const userSearchQuery = useQuery({
    queryKey: [...FRIENDS_QUERY_KEY, 'user-search', debouncedSearch],
    queryFn: () => friendsApi.searchUsers(debouncedSearch),
    enabled: authResolved && isAuthenticated && debouncedSearch.length >= 2,
  })

  const invalidateFriends = () => {
    void queryClient.invalidateQueries({ queryKey: FRIENDS_QUERY_KEY })
  }

  const requestFriendMutation = useMutation({
    mutationFn: friendsApi.requestFriend,
    onSuccess: () => {
      toast.success(copy.requestSent)
      invalidateFriends()
    },
    onError: (error) => toast.error(extractApiErrorMessage(error, copy.requestSendFailed)),
  })

  const acceptRequestMutation = useMutation({
    mutationFn: friendsApi.acceptFriendRequest,
    onSuccess: () => {
      toast.success(copy.requestAccepted)
      invalidateFriends()
    },
    onError: (error) => toast.error(extractApiErrorMessage(error, copy.requestAcceptFailed)),
  })

  const deleteRequestMutation = useMutation({
    mutationFn: friendsApi.deleteFriendRequest,
    onSuccess: () => {
      toast.success(copy.requestRemoved)
      invalidateFriends()
    },
    onError: (error) => toast.error(extractApiErrorMessage(error, copy.requestRemoveFailed)),
  })

  const removeFriendMutation = useMutation({
    mutationFn: friendsApi.removeFriend,
    onSuccess: () => {
      toast.success(copy.friendRemoved)
      invalidateFriends()
      setConfirmAction(null)
    },
    onError: (error) => toast.error(extractApiErrorMessage(error, copy.friendRemoveFailed)),
  })

  const blockUserMutation = useMutation({
    mutationFn: friendsApi.blockUser,
    onSuccess: () => {
      toast.success(copy.userBlocked)
      invalidateFriends()
      setConfirmAction(null)
    },
    onError: (error) => toast.error(extractApiErrorMessage(error, copy.userBlockFailed)),
  })

  const friends = useMemo(() => friendsQuery.data ?? [], [friendsQuery.data])
  const requests = requestsQuery.data
  const pendingCount = (requests?.incoming.length ?? 0) + (requests?.outgoing.length ?? 0)
  const leaderboard = friendsLeaderboardQuery.data ?? []

  const friendUserIds = useMemo(() => new Set(friends.map((friend) => friend.user.userId)), [friends])
  const pendingRequestUserIds = useMemo(() => new Set([
    ...(requests?.incoming ?? []).map((request) => request.user.userId),
    ...(requests?.outgoing ?? []).map((request) => request.user.userId),
  ]), [requests])

  const confirmLoading = removeFriendMutation.isPending || blockUserMutation.isPending
  const handleUserSearchChange = (value: string) => {
    setUserSearch(value)
    if (value.trim().length > 0 && friendsExpanded) {
      setFriendsExpanded(false)
    }
  }

  if (!authResolved) {
    return <FriendsPageLoading />
  }

  if (!isAuthenticated) {
    return <FriendsGuestLanding />
  }

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop />
      <Header />

      <main className="relative z-10 mx-auto flex w-full max-w-[1520px] flex-col px-4 pb-24 pt-8 sm:px-8 sm:pb-28 sm:pt-16 2xl:px-8">
        <motion.section
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(360px,0.4fr)] lg:items-start lg:gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(400px,0.44fr)] xl:gap-5 2xl:grid-cols-[minmax(0,1fr)_minmax(420px,0.46fr)]"
        >
          <div className="min-w-0">
            <FriendsLeaderboardPanel
              entries={leaderboard}
              friendsCount={friends.length}
              pendingCount={pendingCount}
              period={friendsPeriod}
              onPeriodChange={setFriendsPeriod}
              isLoading={friendsLeaderboardQuery.isLoading}
              onOpenProfile={(username) => navigate(`/profile/${username}`)}
            />
          </div>

          <div className="min-w-0 space-y-3 xl:space-y-4">
            <ProfileGlassPanel className="min-w-0 p-4 xl:p-5">
              <div className="pointer-events-none absolute -right-20 -top-16 h-72 w-72 rounded-full bg-amber-300/[.07] blur-[78px]" />
              <div className="pointer-events-none absolute -left-24 bottom-10 h-72 w-72 rounded-full bg-sky-300/[.06] blur-[82px]" />
              <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(150deg,rgba(255,255,255,.035),transparent_26%,rgba(125,211,252,.025)_62%,transparent)]" />

              <div className="relative flex flex-col gap-3">
                <div className="flex items-center justify-between gap-4">
                  <div className="min-w-0">
                    <p className="mb-1 text-[10px] font-bold uppercase tracking-[0.22em] text-sky-100/36">
                      {copy.searchEyebrow}
                    </p>
                    <h2 className="truncate text-2xl font-black tracking-tight text-white">
                      {copy.searchTitle}
                    </h2>
                  </div>
                  {debouncedSearch.length < 2 ? (
                    <div className="inline-flex min-w-0 shrink-0 items-center gap-2 rounded-full border border-sky-200/[.08] bg-sky-300/[.055] px-3 py-1.5 text-xs font-semibold text-sky-50/46">
                      <Search size={13} className="shrink-0" />
                      <span className="truncate">{copy.searchMinChars}</span>
                    </div>
                  ) : pendingCount ? (
                    <div className="inline-flex shrink-0 items-center gap-2 rounded-full border border-amber-200/18 bg-amber-300/[.08] px-3 py-1.5 text-xs font-bold text-amber-50/78 shadow-[0_12px_28px_rgba(251,191,36,.08)]">
                      <Bell size={14} />
                      {copy.pendingCount.replace('{count}', String(pendingCount))}
                    </div>
                  ) : (
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-sky-200/12 bg-sky-300/[.08] text-sky-100/60">
                      <Search size={18} />
                    </div>
                  )}
                </div>

                <div className="relative">
                  <Search size={16} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-white/25" />
                  <Input
                    id="friend-search"
                    name="friend-search"
                    value={userSearch}
                    onChange={(event) => handleUserSearchChange(event.target.value)}
                    placeholder={copy.searchPlaceholder}
                    className="pl-10"
                  />
                </div>

                {debouncedSearch.length >= 2 ? (
                  <div className="max-h-[300px] space-y-2 overflow-y-auto pr-1 scrollbar-thin scrollbar-track-transparent scrollbar-thumb-white/8">
                    {userSearchQuery.isLoading ? (
                      <ListSkeleton />
                    ) : (userSearchQuery.data ?? []).length ? (
                      userSearchQuery.data!.map((user) => {
                        const alreadyFriend = friendUserIds.has(user.userId)
                        const pendingRequest = pendingRequestUserIds.has(user.userId)
                        return (
                          <UserResultRow
                            key={user.userId}
                            user={user}
                            actionLabel={alreadyFriend ? copy.actionFriend : pendingRequest ? copy.actionPending : copy.actionAdd}
                            actionIcon={UserPlus}
                            disabled={requestFriendMutation.isPending || alreadyFriend || pendingRequest}
                            onAction={() => requestFriendMutation.mutate(user.userId)}
                          />
                        )
                      })
                    ) : (
                      <CompactEmpty icon={Users} title={copy.noPlayersFound} />
                    )}
                  </div>
                ) : null}
              </div>
            </ProfileGlassPanel>

            <Panel
              title={copy.friendsTitle}
              eyebrow={copy.friendsEyebrow}
              badge={friends.length}
              action={(
                <Button variant="ghost" size="sm" onClick={() => setFriendsExpanded((value) => !value)}>
                  <ChevronDown
                    size={14}
                    className={cn('transition-transform', friendsExpanded ? 'rotate-180' : 'rotate-0')}
                  />
                  {friendsExpanded ? copy.hide : copy.show}
                </Button>
              )}
            >
              {!friendsExpanded ? (
                <div className="rounded-2xl border border-white/[.07] bg-black/20 px-4 py-3 text-sm font-semibold text-white/45">
                  {friends.length ? copy.friendsHidden.replace('{count}', String(friends.length)) : copy.noFriends}
                </div>
              ) : friendsQuery.isLoading ? (
                <ListSkeleton />
              ) : friends.length ? (
                <div className="max-h-[320px] overflow-y-auto pr-1 scrollbar-thin scrollbar-track-transparent scrollbar-thumb-white/8">
                  <div className="grid gap-3">
                    {friends.map((friend) => (
                      <FriendCard
                        key={friend.friendshipId}
                        friend={friend}
                        onOpenProfile={() => navigate(`/profile/${friend.user.username}`)}
                        onRemove={() => setConfirmAction({
                          title: copy.removeFriend,
                          message: copy.removeFriendMessage.replace('{name}', friend.user.displayName),
                          confirmLabel: copy.removeConfirm,
                          variant: 'danger',
                          run: () => removeFriendMutation.mutate(friend.user.userId),
                        })}
                        onBlock={() => setConfirmAction({
                          title: copy.blockUser,
                          message: copy.blockUserMessage.replace('{name}', friend.user.displayName),
                          confirmLabel: copy.blockConfirm,
                          variant: 'danger',
                          run: () => blockUserMutation.mutate(friend.user.userId),
                        })}
                      />
                    ))}
                  </div>
                </div>
              ) : (
                <EmptyState icon={Users} title={copy.noFriends} className="py-10" />
              )}
            </Panel>

            <Panel title={copy.requestsTitle} eyebrow={copy.requestsEyebrow} badge={pendingCount}>
              <div className="mb-3 flex rounded-xl border border-white/[.07] bg-black/20 p-1">
                <Button
                  variant={requestsView === 'incoming' ? 'secondary' : 'ghost'}
                  size="sm"
                  className="flex-1"
                  onClick={() => setRequestsView('incoming')}
                >
                  {copy.incoming} {requests?.incoming.length ? `(${requests.incoming.length})` : ''}
                </Button>
                <Button
                  variant={requestsView === 'outgoing' ? 'secondary' : 'ghost'}
                  size="sm"
                  className="flex-1"
                  onClick={() => setRequestsView('outgoing')}
                >
                  {copy.outgoing} {requests?.outgoing.length ? `(${requests.outgoing.length})` : ''}
                </Button>
              </div>

              <div className="max-h-72 overflow-y-auto pr-1 scrollbar-thin scrollbar-track-transparent scrollbar-thumb-white/8">
                {requestsView === 'incoming' ? (
                  <RequestColumn
                    emptyTitle={copy.noIncoming}
                    requests={requests?.incoming ?? []}
                    renderActions={(request) => (
                      <>
                        <Button
                          size="sm"
                          onClick={() => acceptRequestMutation.mutate(request.requestId)}
                          isLoading={acceptRequestMutation.isPending}
                        >
                          {copy.accept}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => deleteRequestMutation.mutate(request.requestId)}
                          isLoading={deleteRequestMutation.isPending}
                        >
                          {copy.decline}
                        </Button>
                      </>
                    )}
                  />
                ) : (
                  <RequestColumn
                    emptyTitle={copy.noOutgoing}
                    requests={requests?.outgoing ?? []}
                    renderActions={(request) => (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => deleteRequestMutation.mutate(request.requestId)}
                        isLoading={deleteRequestMutation.isPending}
                      >
                        {copy.cancelRequest}
                      </Button>
                    )}
                  />
                )}
              </div>
            </Panel>
          </div>
        </motion.section>
      </main>

      <ConfirmDialog
        isOpen={!!confirmAction}
        onClose={() => setConfirmAction(null)}
        onConfirm={() => confirmAction?.run()}
        title={confirmAction?.title ?? ''}
        message={confirmAction?.message}
        confirmLabel={confirmAction?.confirmLabel}
        variant={confirmAction?.variant}
        isLoading={confirmLoading}
      />
      <HomeLegalStrip />
    </div>
  )
}

function FriendsPageLoading() {
  return (
    <div className="relative min-h-screen overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop />
      <Header />
      <main className="relative z-10 flex min-h-[60vh] items-center justify-center px-4">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-white/15 border-t-white/70" />
      </main>
      <HomeLegalStrip />
    </div>
  )
}

function FriendsGuestLanding() {
  const { t } = useTranslation()
  const copy = t.friends
  const guestLeaderboard = [
    { rank: 1, name: copy.guestLeaderboardYou, username: 'future-you', xp: '8,420', tone: 'gold' },
    { rank: 2, name: 'Mira Stone', username: 'mira', xp: '7,880', tone: 'sky' },
    { rank: 3, name: 'Alex Chen', username: 'alexc', xp: '6,940', tone: 'emerald' },
  ] as const

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop />
      <Header />

      <main className="relative z-10 mx-auto flex w-full max-w-[1180px] flex-col px-4 pb-24 pt-8 sm:px-8 sm:pb-28 sm:pt-16">
        <motion.section
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_390px] lg:items-stretch"
        >
          <ProfileHeroBackground className="rounded-[1.55rem] shadow-[0_30px_90px_rgba(0,0,0,.38)]">
            <div className="relative overflow-hidden px-5 py-7 sm:px-8 sm:py-9 lg:min-h-[520px]">
              <div className="pointer-events-none absolute -right-16 -top-20 h-64 w-64 rounded-full bg-amber-300/10 blur-[70px]" />
              <div className="pointer-events-none absolute -left-24 bottom-6 h-72 w-72 rounded-full bg-sky-300/10 blur-[76px]" />

              <div className="relative max-w-2xl">
                <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-sky-200/14 bg-sky-300/[.07] px-3 py-1 text-[10px] font-bold uppercase tracking-[0.22em] text-sky-100/62">
                  <Users size={13} />
                  {copy.eyebrow}
                </div>
                <h1 className="text-4xl font-black leading-[0.95] tracking-tight text-white sm:text-5xl">
                  {copy.guestHeadline}
                </h1>
                <p className="mt-4 max-w-xl text-sm font-semibold leading-6 text-white/52 sm:text-base">
                  {copy.guestDescription}
                </p>
              </div>

              <div className="relative mt-8 grid gap-3 sm:grid-cols-3">
                <GuestMetric label={copy.guestMetricRace} value={copy.guestMetricRaceValue} />
                <GuestMetric label={copy.guestMetricRequests} value={copy.guestMetricRequestsValue} />
                <GuestMetric label={copy.guestMetricProfiles} value={copy.guestMetricProfilesValue} />
              </div>

              <div className="relative mt-7 overflow-hidden rounded-[1.35rem] border border-white/[.07] bg-[linear-gradient(180deg,rgba(255,255,255,.04),rgba(2,6,23,.42))] p-2">
                <div className="space-y-1.5">
                  {guestLeaderboard.map((entry) => (
                    <GuestLeaderboardRow key={entry.rank} {...entry} />
                  ))}
                </div>
              </div>
            </div>
          </ProfileHeroBackground>

          <div className="min-w-0 space-y-4">
            <ProfileGlassPanel className="p-5 sm:p-6">
              <div className="pointer-events-none absolute -right-20 -top-16 h-72 w-72 rounded-full bg-emerald-300/[.07] blur-[78px]" />
              <div className="relative">
                <p className="mb-2 text-[10px] font-bold uppercase tracking-[0.22em] text-white/25">{copy.accountEyebrow}</p>
                <h2 className="text-2xl font-black tracking-tight text-white">{copy.accountTitle}</h2>
                <p className="mt-3 text-sm leading-6 text-white/45">
                  {copy.accountBody}
                </p>

                <div className="mt-5 grid gap-2.5">
                  <GuestBenefit icon={UserPlus} title={copy.benefitFindTitle} text={copy.benefitFindText} />
                  <GuestBenefit icon={Trophy} title={copy.benefitCompeteTitle} text={copy.benefitCompeteText} />
                  <GuestBenefit icon={Bell} title={copy.benefitRequestsTitle} text={copy.benefitRequestsText} />
                </div>

                <div className="mt-6 grid gap-3">
                  <Link
                    to="/register?redirect=%2Ffriends"
                    className="inline-flex h-12 items-center justify-center gap-2.5 rounded-xl bg-white px-5 text-sm font-bold text-black transition-all hover:bg-white/90"
                  >
                    {copy.createAccount}
                    <ArrowRight size={16} />
                  </Link>
                  <Link
                    to="/login?redirect=%2Ffriends"
                    className="inline-flex h-12 items-center justify-center gap-2.5 rounded-xl border border-white/[.1] bg-white/[.05] px-5 text-sm font-semibold text-white/78 transition-all hover:border-white/[.18] hover:bg-white/[.08] hover:text-white"
                  >
                    {copy.signIn}
                  </Link>
                </div>
              </div>
            </ProfileGlassPanel>

            <ProfileGlassPanel className="p-5">
              <div className="relative flex items-center gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-amber-200/16 bg-amber-300/[.08] text-amber-100/70">
                  <Sparkles size={19} />
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-bold text-white">{copy.guestPromptTitle}</div>
                  <div className="mt-1 text-xs leading-5 text-white/40">
                    {copy.guestPromptBody}
                  </div>
                </div>
              </div>
            </ProfileGlassPanel>
          </div>
        </motion.section>
      </main>
      <HomeLegalStrip />
    </div>
  )
}

function GuestMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-white/[.07] bg-black/22 px-4 py-3">
      <div className="text-xl font-black text-white">{value}</div>
      <div className="mt-1 text-[10px] font-bold uppercase tracking-[0.14em] text-white/30">{label}</div>
    </div>
  )
}

function GuestLeaderboardRow({
  rank,
  name,
  username,
  xp,
  tone,
}: {
  rank: number
  name: string
  username: string
  xp: string
  tone: 'gold' | 'sky' | 'emerald'
}) {
  return (
    <div className="grid grid-cols-[38px_minmax(0,1fr)_76px] items-center gap-2 rounded-[1.05rem] border border-white/[.07] bg-black/20 p-2.5 sm:grid-cols-[48px_minmax(0,1fr)_96px] sm:gap-3 sm:p-3">
      <div
        className={cn(
          'flex h-9 w-9 items-center justify-center rounded-xl border text-xs font-black tabular-nums sm:h-10 sm:w-10 sm:text-sm',
          tone === 'gold' && 'border-amber-200/26 bg-amber-300/[.09] text-amber-50/80',
          tone === 'sky' && 'border-sky-200/20 bg-sky-300/[.08] text-sky-50/74',
          tone === 'emerald' && 'border-emerald-200/20 bg-emerald-300/[.08] text-emerald-50/74',
        )}
      >
        #{rank}
      </div>
      <div className="flex min-w-0 items-center gap-2.5">
        <UserAvatar name={name} size="sm" className="h-9 w-9 shrink-0 ring-1 ring-white/[.1]" />
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold text-white">{name}</div>
          <div className="truncate text-xs text-white/32">@{username}</div>
        </div>
      </div>
      <div className="text-right">
        <div className="text-sm font-black tabular-nums text-white">{xp}</div>
        <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-white/25">XP</div>
      </div>
    </div>
  )
}

function GuestBenefit({
  icon: Icon,
  title,
  text,
}: {
  icon: LucideIcon
  title: string
  text: string
}) {
  return (
    <div className="flex gap-3 rounded-2xl border border-white/[.07] bg-black/20 p-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-white/[.08] bg-white/[.05] text-white/55">
        <Icon size={16} />
      </div>
      <div className="min-w-0">
        <div className="text-sm font-bold text-white/86">{title}</div>
        <div className="mt-0.5 text-xs leading-5 text-white/38">{text}</div>
      </div>
    </div>
  )
}

function FriendsLeaderboardPanel({
  entries,
  friendsCount,
  pendingCount,
  period,
  onPeriodChange,
  isLoading,
  onOpenProfile,
}: {
  entries: FriendLeaderboardEntry[]
  friendsCount: number
  pendingCount: number
  period: RelationshipLeaderboardPeriod
  onPeriodChange: (value: RelationshipLeaderboardPeriod) => void
  isLoading?: boolean
  onOpenProfile: (username: string) => void
}) {
  const { t } = useTranslation()
  const copy = t.friends
  const leader = entries[0]
  const currentUser = entries.find((entry) => entry.currentUser)

  return (
    <ProfileHeroBackground className="rounded-[1.55rem] shadow-[0_30px_90px_rgba(0,0,0,.38)]">
      <div className="relative flex max-h-none flex-col overflow-hidden px-4 pb-4 pt-6 sm:px-8 sm:pb-7 sm:pt-8 lg:px-6 lg:py-5 2xl:px-8 2xl:pb-7 2xl:pt-8">
        <div className="pointer-events-none absolute -right-20 -top-24 h-64 w-64 rounded-full bg-amber-300/10 blur-[70px]" />
        <div className="pointer-events-none absolute -left-20 bottom-10 h-56 w-56 rounded-full bg-sky-300/10 blur-[70px]" />

        <div className="grid gap-5 lg:grid-cols-[148px_minmax(0,1fr)] lg:items-center 2xl:grid-cols-[170px_minmax(0,1fr)] 2xl:gap-6">
          <LeaderPortrait entry={leader} isLoading={isLoading} />

          <div className="min-w-0">
            <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between 2xl:gap-4">
              <div className="min-w-0">
                <div className="mb-2 inline-flex items-center gap-2 rounded-full border border-sky-200/14 bg-sky-300/[.07] px-3 py-1 text-[10px] font-bold uppercase tracking-[0.22em] text-sky-100/62 2xl:mb-3">
                  <Sparkles size={13} />
                  {copy.friendsTitle}
                </div>
                <h1 className="text-3xl font-black leading-none tracking-tight text-white sm:text-[2.15rem] 2xl:text-4xl">
                  {copy.boardTitle}
                </h1>
                <p className="mt-1.5 truncate text-sm font-semibold text-white/50 2xl:mt-2">
                  {leader ? copy.leaderLeads.replace('{name}', leader.user.displayName || leader.user.username).replace('{xp}', formatNumber(leader.scoreXp)) : copy.boardSubtitle}
                </p>
              </div>

              <div className="w-full xl:max-w-[210px] 2xl:max-w-[220px]">
                <PeriodSwitch value={period} onChange={onPeriodChange} />
              </div>
            </div>

            <div className="mt-4 overflow-hidden rounded-2xl border border-white/[.07] bg-black/22 2xl:mt-5">
              <div className="grid grid-cols-3 divide-x divide-white/[.06]">
                <LeaderboardMetric label={copy.topXp} value={leader ? formatNumber(leader.scoreXp) : '-'} />
                <LeaderboardMetric label={copy.yourRank} value={currentUser ? `#${currentUser.rank}` : '-'} />
                <LeaderboardMetric label={copy.friendsTitle} value={String(friendsCount)} hint={pendingCount ? copy.pendingCount.replace('{count}', String(pendingCount)) : undefined} />
              </div>
            </div>
          </div>
        </div>

        <div className="mt-4 min-h-0 overflow-hidden rounded-[1.35rem] border border-white/[.07] bg-[linear-gradient(180deg,rgba(255,255,255,.035),rgba(2,6,23,.40))] p-2 lg:flex-1 2xl:mt-5">
          <div className="max-h-[680px] min-h-0 overflow-y-auto pr-1 scrollbar-thin scrollbar-track-transparent scrollbar-thumb-white/8 xl:max-h-[720px] 2xl:max-h-[780px]">
            {isLoading ? (
              <ListSkeleton />
            ) : entries.length ? (
              <div className="space-y-1.5">
                {entries.map((entry) => (
                  <LeaderboardRow
                    key={entry.user.userId}
                    entry={entry}
                    onOpenProfile={() => onOpenProfile(entry.user.username)}
                  />
                ))}
              </div>
            ) : (
              <EmptyState icon={Trophy} title={copy.emptyBoard} className="py-14" />
            )}
          </div>
        </div>
      </div>
    </ProfileHeroBackground>
  )
}

function LeaderPortrait({
  entry,
  isLoading,
}: {
  entry?: FriendLeaderboardEntry
  isLoading?: boolean
}) {
  const { t } = useTranslation()
  const name = entry?.user.displayName || entry?.user.username || t.friends.leaderFallback

  if (isLoading) {
    return <Skeleton variant="rect" height={148} className="mx-auto w-[148px] 2xl:w-[170px]" />
  }

  return (
    <div className="relative mx-auto h-[142px] w-[142px] shrink-0 sm:h-[154px] sm:w-[154px] lg:h-[148px] lg:w-[148px] 2xl:h-[170px] 2xl:w-[170px]">
      <div className="absolute inset-0 rounded-full border border-sky-200/12 bg-[linear-gradient(145deg,rgba(125,211,252,.08),rgba(251,191,36,.05)_48%,rgba(2,6,23,.18))] shadow-[0_22px_70px_rgba(0,0,0,.45)]" />
      <div className="absolute inset-[6px] rounded-full border border-amber-200/55 shadow-[0_0_0_1px_rgba(125,211,252,.18),0_0_34px_rgba(251,191,36,.18)]" />
      <div className="absolute inset-[14px] rounded-full border border-white/[.12]" />
      <div className="absolute inset-[19px] rounded-full bg-black/20 shadow-[inset_0_0_28px_rgba(0,0,0,.44)]" />

      <div className="absolute inset-0 flex items-center justify-center">
        {entry ? (
          <UserAvatar
            avatarUrl={entry.user.avatarUrl}
            name={name}
            size="lg"
            className="h-[102px] w-[102px] border border-white/[.1] bg-white/[.06] shadow-[0_20px_56px_rgba(0,0,0,.6)] sm:h-[112px] sm:w-[112px] lg:h-[106px] lg:w-[106px] 2xl:h-[124px] 2xl:w-[124px]"
          />
        ) : (
          <div className="flex h-[102px] w-[102px] items-center justify-center rounded-full border border-white/[.08] bg-white/[.04] text-white/22 sm:h-[112px] sm:w-[112px] lg:h-[106px] lg:w-[106px] 2xl:h-[124px] 2xl:w-[124px]">
            <Users size={38} />
          </div>
        )}
      </div>

      <div className="absolute -bottom-1 left-1/2 flex h-11 min-w-11 -translate-x-1/2 items-center justify-center 2xl:h-[50px] 2xl:min-w-[50px]">
        <div className="absolute inset-0 [clip-path:polygon(50%_0,88%_18%,88%_66%,50%_100%,12%_66%,12%_18%)] bg-[linear-gradient(180deg,#f8d66b,#d99214)] shadow-[0_14px_34px_rgba(251,191,36,.3)]" />
        <div className="absolute inset-[3px] [clip-path:polygon(50%_0,88%_18%,88%_66%,50%_100%,12%_66%,12%_18%)] bg-[linear-gradient(180deg,#31220b,#111827)]" />
        <span className="relative text-sm font-black text-white drop-shadow-[0_1px_6px_rgba(0,0,0,.55)] 2xl:text-base">
          {entry ? `#${entry.rank}` : '-'}
        </span>
      </div>
    </div>
  )
}

function Panel({
  title,
  eyebrow,
  badge,
  action,
  children,
}: {
  title: string
  eyebrow: string
  badge?: number
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <ProfileGlassPanel className="p-4 sm:p-5">
      <div className="relative mb-4 flex items-center justify-between gap-3">
        <div>
          <p className="mb-1 text-[10px] font-bold uppercase tracking-[0.22em] text-white/25">{eyebrow}</p>
          <h2 className="text-base font-semibold text-white">{title}</h2>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {badge !== undefined && (
            <span className="rounded-full border border-white/[.08] bg-white/[.04] px-2.5 py-1 text-xs font-bold text-white/55">
              {badge}
            </span>
          )}
          {action}
        </div>
      </div>
      <div className="relative">{children}</div>
    </ProfileGlassPanel>
  )
}

function LeaderboardMetric({
  label,
  value,
  hint,
}: {
  label: string
  value: string
  hint?: string
}) {
  return (
    <div className="min-w-0 rounded-2xl border border-white/[.07] bg-black/22 px-3 py-2.5 text-center sm:px-4 2xl:py-3.5">
      <div className="truncate text-lg font-black text-white 2xl:text-xl">{value}</div>
      <div className="mt-1 flex items-center justify-center gap-2 text-[9px] font-bold uppercase tracking-[0.1em] text-white/30 sm:text-[10px]">
        <span className="whitespace-nowrap">{label}</span>
        {hint ? <span className="truncate text-amber-100/55">{hint}</span> : null}
      </div>
    </div>
  )
}

function LeaderboardRow({
  entry,
  onOpenProfile,
}: {
  entry: FriendLeaderboardEntry
  onOpenProfile: () => void
}) {
  const { t } = useTranslation()
  return (
    <button
      type="button"
      onClick={onOpenProfile}
      className={cn(
        'grid w-full grid-cols-[38px_minmax(0,1fr)_54px] items-center gap-2 rounded-[1.05rem] border p-2.5 text-left transition-transform duration-200 hover:-translate-y-0.5 sm:grid-cols-[48px_minmax(0,1fr)_80px_100px] sm:gap-3 sm:p-3 2xl:grid-cols-[52px_minmax(0,1fr)_92px_116px] 2xl:p-3.5',
        entry.currentUser
          ? 'border-emerald-200/24 bg-[linear-gradient(90deg,rgba(16,185,129,.09),rgba(255,255,255,.035))]'
          : 'border-white/[.07] bg-black/20 hover:border-white/[.12]',
      )}
    >
      <div className="flex h-8 w-8 items-center justify-center rounded-xl border border-white/[.08] bg-white/[.04] text-xs font-black tabular-nums text-white/64 sm:h-10 sm:w-10 sm:text-sm 2xl:h-11 2xl:w-11">
        #{entry.rank}
      </div>

      <div className="flex min-w-0 items-center gap-2 sm:gap-2.5 2xl:gap-3">
        <UserAvatar
          avatarUrl={entry.user.avatarUrl}
          name={entry.user.displayName || entry.user.username}
          size="sm"
          className="h-8 w-8 shrink-0 ring-1 ring-white/[.1] sm:h-9 sm:w-9 2xl:h-10 2xl:w-10"
        />
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold text-white">{entry.user.displayName || entry.user.username}</div>
          <div className="truncate text-xs text-white/32">@{entry.user.username}</div>
        </div>
      </div>

      <div className="hidden text-right sm:block">
        <div className="text-sm font-bold text-white/82">{t.friends.levelShort.replace('{level}', String(entry.level))}</div>
        <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-white/24">{t.friends.level}</div>
      </div>

      <div className="shrink-0 text-right">
        <div className="text-sm font-black tabular-nums text-white">{formatNumber(entry.scoreXp)}</div>
        <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-white/25">XP</div>
      </div>
    </button>
  )
}

function UserResultRow({
  user,
  actionLabel,
  actionIcon: ActionIcon,
  disabled,
  onAction,
}: {
  user: FriendUser
  actionLabel: string
  actionIcon: LucideIcon
  disabled?: boolean
  onAction: () => void
}) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-2xl border border-white/[.07] bg-black/20 p-3">
      <UserIdentity user={user} />
      <Button variant="secondary" size="sm" disabled={disabled} onClick={onAction}>
        <ActionIcon size={14} />
        {actionLabel}
      </Button>
    </div>
  )
}

function CompactEmpty({
  icon: Icon,
  title,
}: {
  icon: LucideIcon
  title: string
}) {
  return (
    <div className="flex min-h-11 items-center gap-3 rounded-xl border border-white/[.06] bg-white/[.035] px-3 py-2 text-sm font-semibold text-white/45">
      <Icon size={16} className="shrink-0 text-white/28" />
      <span className="truncate">{title}</span>
    </div>
  )
}

function RequestColumn({
  emptyTitle,
  requests,
  renderActions,
}: {
  emptyTitle: string
  requests: FriendRequest[]
  renderActions: (request: FriendRequest) => ReactNode
}) {
  return (
    <div className="rounded-2xl border border-white/[.07] bg-black/20 p-2.5">
      {requests.length ? (
        <div className="space-y-2">
          {requests.map((request) => (
            <div key={request.requestId} className="rounded-xl bg-white/[.035] p-3">
              <UserIdentity user={request.user} />
              <div className="mt-3 flex flex-wrap gap-2">{renderActions(request)}</div>
            </div>
          ))}
        </div>
      ) : (
        <CompactEmpty icon={Bell} title={emptyTitle} />
      )}
    </div>
  )
}

function FriendCard({
  friend,
  onOpenProfile,
  onRemove,
  onBlock,
}: {
  friend: Friend
  onOpenProfile: () => void
  onRemove: () => void
  onBlock: () => void
}) {
  const { t } = useTranslation()
  const copy = t.friends
  return (
    <div className="flex flex-col gap-4 rounded-2xl border border-white/[.07] bg-black/20 p-4 sm:flex-row sm:items-center sm:justify-between">
      <UserIdentity user={friend.user} />
      <div className="flex shrink-0 flex-wrap gap-2">
        <IconActionTooltip content={copy.openProfile}>
          <Button
            variant="icon"
            size="sm"
            onClick={onOpenProfile}
            aria-label={copy.openProfile}
            className="border-sky-300/20 bg-sky-300/[.09] text-sky-100/70 hover:border-sky-200/35 hover:bg-sky-300/[.16] hover:text-sky-50"
          >
            <Eye size={15} />
          </Button>
        </IconActionTooltip>
        <IconActionTooltip content={copy.removeFriend}>
          <Button
            variant="icon"
            size="sm"
            onClick={onRemove}
            aria-label={copy.removeFriend}
            className="border-amber-300/20 bg-amber-300/[.08] text-amber-100/70 hover:border-amber-200/35 hover:bg-amber-300/[.15] hover:text-amber-50"
          >
            <UserMinus size={15} />
          </Button>
        </IconActionTooltip>
        <IconActionTooltip content={copy.blockUser}>
          <Button
            variant="icon"
            size="sm"
            onClick={onBlock}
            aria-label={copy.blockUser}
            className="border-red-300/20 bg-red-400/[.08] text-red-100/70 hover:border-red-200/35 hover:bg-red-400/[.15] hover:text-red-50"
          >
            <Ban size={15} />
          </Button>
        </IconActionTooltip>
      </div>
    </div>
  )
}

function IconActionTooltip({
  content,
  children,
}: {
  content: string
  children: ReactNode
}) {
  return (
    <Tooltip content={content} className="shrink-0" maxWidth={180} disableTouch>
      {children}
    </Tooltip>
  )
}

function UserIdentity({ user }: { user: FriendUser }) {
  const { t } = useTranslation()
  return (
    <div className="flex min-w-0 items-center gap-3">
      <UserAvatar avatarUrl={user.avatarUrl} name={user.displayName} size="sm" className="h-10 w-10 shrink-0" />
      <div className="min-w-0">
        <div className="truncate text-sm font-semibold text-white">{user.displayName || user.username}</div>
        <div className="truncate text-xs text-white/32">
          @{user.username} · {t.friends.levelShort.replace('{level}', String(user.level))} · {formatNumber(user.totalXp)} XP
        </div>
      </div>
    </div>
  )
}

function PeriodSwitch({
  value,
  onChange,
}: {
  value: RelationshipLeaderboardPeriod
  onChange: (value: RelationshipLeaderboardPeriod) => void
}) {
  const { t } = useTranslation()
  return (
    <div className="flex w-full min-w-0 rounded-xl border border-white/[.07] bg-black/20 p-1">
      {([
        ['week', t.friends.periodWeek],
        ['alltime', t.friends.periodAllTime],
      ] as const).map(([period, label]) => (
        <button
          key={period}
          onClick={() => onChange(period)}
          className={cn(
            'min-w-0 flex-1 cursor-pointer rounded-lg px-3 py-2 text-xs font-semibold transition-all',
            value === period ? 'bg-white/[.09] text-white' : 'text-white/35 hover:text-white/60',
          )}
        >
          {label}
        </button>
      ))}
    </div>
  )
}

function ListSkeleton() {
  return (
    <div className="space-y-2">
      <Skeleton variant="rect" height={64} />
      <Skeleton variant="rect" height={64} />
      <Skeleton variant="rect" height={64} />
    </div>
  )
}

function formatNumber(value: number) {
  return value.toLocaleString()
}
