import { useQuery, type UseQueryOptions } from '@tanstack/react-query'
import { gameApi } from '@/api/game'
import { useAuthStore } from '@/store/authStore'
import type { ChallengeDto, GameResults, GameSessionSummary, GuestProgress, GuestState, ProfileDto } from '@/types'

const PROFILE_STALE_TIME = 60_000
const CHALLENGES_STALE_TIME = 3_600_000
const SESSION_SUMMARY_STALE_TIME = 30_000
const GUEST_PROGRESS_STALE_TIME = 15_000

function usePrincipalScopeKey() {
  const { authResolved, isAuthenticated, user } = useAuthStore()
  if (!authResolved) return 'unresolved'
  if (isAuthenticated) return `user:${user?.id ?? 'unknown'}`
  return 'guest'
}

export function useProfile(enabled = true, options?: Partial<UseQueryOptions<ProfileDto>>) {
  const scopeKey = usePrincipalScopeKey()

  return useQuery({
    queryKey: ['profile', scopeKey],
    queryFn: gameApi.getProfile,
    enabled,
    staleTime: PROFILE_STALE_TIME,
    ...options,
  })
}

export function useGuestProfile(enabled: boolean, expiresAt?: string | null) {
  return useQuery<ProfileDto>({
    queryKey: ['guest-profile', expiresAt ?? 'none'],
    queryFn: gameApi.getGuestProfile,
    enabled,
    staleTime: 0,
    refetchOnMount: 'always',
    retry: false,
  })
}

export function useTodayChallenges() {
  return useQuery<ChallengeDto[]>({
    queryKey: ['today-challenges'],
    queryFn: gameApi.getTodaysChallenges,
    staleTime: CHALLENGES_STALE_TIME,
  })
}

export function useTodaySessionSummary(enabled: boolean) {
  const scopeKey = usePrincipalScopeKey()

  return useQuery<GameSessionSummary | null>({
    queryKey: ['today-session-summary', scopeKey],
    queryFn: () => gameApi.getTodaySessionSummary(),
    enabled,
    staleTime: SESSION_SUMMARY_STALE_TIME,
  })
}

export function useTodayResults(enabled: boolean) {
  const scopeKey = usePrincipalScopeKey()

  return useQuery<GameResults>({
    queryKey: ['today-results', scopeKey],
    queryFn: () => gameApi.getTodayResults(),
    enabled,
    retry: false,
  })
}

export function useGuestProgress(enabled: boolean, refreshOnMount = false) {
  const scopeKey = usePrincipalScopeKey()

  return useQuery<GuestProgress>({
    queryKey: ['guest-progress', scopeKey],
    queryFn: gameApi.getGuestProgress,
    enabled,
    staleTime: refreshOnMount ? 0 : GUEST_PROGRESS_STALE_TIME,
    refetchOnMount: refreshOnMount ? 'always' : true,
    refetchOnWindowFocus: false,
    retry: false,
  })
}

export function useGuestState(enabled: boolean) {
  const scopeKey = usePrincipalScopeKey()

  return useQuery<GuestState>({
    queryKey: ['guest-state', scopeKey],
    queryFn: gameApi.getGuestState,
    enabled,
    staleTime: 15_000,
    refetchOnWindowFocus: false,
    retry: false,
  })
}
