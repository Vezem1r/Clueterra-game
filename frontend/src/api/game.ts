import { apiClient } from './client'
import type {
  Country,
  GuessResult,
  ChallengeDto,
  PaginatedLeaderboard,
  RevealHintResponse,
  ProfileDto,
  ProfileBadgePageResponse,
  GameResults,
  GameSessionSummary,
  UpdateProfileRequest,
  HintReportRequest,
  UnacknowledgedBadgeDto,
  ProfileBadgeDto,
  Hint,
  GameSessionStatus,
  GuestProgress,
  GuestState,
} from '@/types'

/* ── Backend DTO shapes ── */

interface BackendCountryDto {
  id: string
  code: string        // frontend: isoAlpha2
  name: string        // frontend: nameEn
  flagEmoji: string | null
  flagUrl: string | null
  region: string
}

interface BackendHintDto {
  id: string
  text: string        // frontend: hintText
  imageUrl?: string | null
  imageSourcePageUrl?: string | null
  imageAuthorName?: string | null
  imageAttributionText?: string | null
  imageLicenseLabel?: string | null
  imageLicenseUrl?: string | null
  difficulty: number
  category: string
  type: string
  source: string
}

interface BackendSessionCountryDto {
  id: string
  order: number
  totalHints: number
  hintsRevealed: number
  attemptsRemaining: number
  isGuessed: boolean
  guessed?: boolean
  xpEarned: number
  hints: BackendHintDto[]
  correctCountry?: BackendCountryDto
}

interface BackendGuessResultDto {
  correct: boolean
  attemptsRemaining: number
  hintsRevealed: number
  nextHint: BackendHintDto | null
  correctCountry: BackendCountryDto | null
  xpEarned: number
  countryComplete: boolean
  sessionComplete: boolean
}

interface BackendRevealResultDto {
  hint: BackendHintDto
  hintsRevealed: number
  attemptsRemaining: number
}

export interface GameStateResponse {
  id: string
  guestToken?: string | null
  guest?: boolean
  level: string
  status: string
  currentCountryIndex: number
  totalCountries: number
  maxHintsPerCountry?: number
  maxAttempts?: number
  startedAt: string
  activeTimeMs: number
  completedAt: string | null
  totalXpEarned: number
  xpBefore?: number
  levelBefore?: number
  countries: BackendSessionCountryDto[]
}

interface BackendGameSessionSummaryDto {
  id: string
  level: string
  status: GameSessionStatus
  currentCountryIndex: number
  totalCountries: number
  guessedCountries: number
  totalHintsRevealed: number
  totalAttemptsUsed: number
  startedAt: string
  activeTimeMs: number
  completedAt: string | null
  totalXpEarned: number
}

interface BackendGuessAttemptDto {
  attemptNumber: number
  guessedCountry: BackendCountryDto
  correct: boolean
  hintsSeen: number
  createdAt: string
}

interface BackendResultCountryDto {
  id: string
  order: number
  totalHints: number
  hintsSeen: number
  guessed: boolean
  xpEarned: number
  correctCountry: BackendCountryDto
  hints: BackendHintDto[]
  attempts: BackendGuessAttemptDto[]
}

interface BackendGameResultsDto {
  id: string
  level: string
  status: GameSessionStatus
  startedAt: string
  activeTimeMs: number
  completedAt: string | null
  totalXpEarned: number
  xpBefore: number
  levelBefore: number
  totalCountries: number
  guessedCountries: number
  maxAttempts: number
  guest: boolean
  streakBonusXp?: number
  countries: BackendResultCountryDto[]
  communityStats: BackendGameResultsCommunityStatsDto
}

interface BackendGameResultsCommunityStatsDto {
  enoughData: boolean
  avgXpPerCompletedRun: number
  accuracyRate: number
  avgActiveTimePerSessionMs: number
  avgAttemptsPerCountry: number
  avgHintsPerCountry: number
  hardestCountry: BackendHardestCountryInsightDto | null
}

interface BackendHardestCountryInsightDto {
  country: BackendCountryDto
  successRate: number
  avgAttemptsNeeded: number
  avgHintsNeeded: number
  avgActiveTimeMs: number
  mostConfusedWith: BackendCountryDto | null
}

/* ── Mapping helpers ── */

function mapCountry(dto: BackendCountryDto): Country {
  return {
    id: dto.id,
    isoAlpha2: dto.code,
    nameEn: dto.name,
    flagEmoji: dto.flagEmoji,
    flagUrl: dto.flagUrl,
    region: dto.region,
  }
}

function mapHint(dto: BackendHintDto): Hint {
  return {
    id: dto.id,
    hintText: dto.text,
    imageUrl: dto.imageUrl,
    imageSourcePageUrl: dto.imageSourcePageUrl,
    imageAuthorName: dto.imageAuthorName,
    imageAttributionText: dto.imageAttributionText,
    imageLicenseLabel: dto.imageLicenseLabel,
    imageLicenseUrl: dto.imageLicenseUrl,
    difficulty: dto.difficulty,
    category: dto.category,
    type: dto.type,
    source: dto.source,
  }
}

function mapSummary(dto: BackendGameSessionSummaryDto): GameSessionSummary {
  return {
    id: dto.id,
    level: dto.level as GameSessionSummary['level'],
    status: dto.status as GameSessionSummary['status'],
    currentCountryIndex: dto.currentCountryIndex,
    totalCountries: dto.totalCountries,
    guessedCountries: dto.guessedCountries,
    totalHintsRevealed: dto.totalHintsRevealed,
    totalAttemptsUsed: dto.totalAttemptsUsed,
    startedAt: dto.startedAt,
    activeTimeMs: dto.activeTimeMs,
    completedAt: dto.completedAt,
    totalXpEarned: dto.totalXpEarned,
  }
}

function mapResults(dto: BackendGameResultsDto): GameResults {
  return {
    id: dto.id,
    level: dto.level as GameResults['level'],
    status: dto.status as GameResults['status'],
    startedAt: dto.startedAt,
    activeTimeMs: dto.activeTimeMs,
    completedAt: dto.completedAt,
    totalXpEarned: dto.totalXpEarned,
    xpBefore: dto.xpBefore,
    levelBefore: dto.levelBefore,
    totalCountries: dto.totalCountries,
    guessedCountries: dto.guessedCountries,
    maxAttempts: dto.maxAttempts,
    guest: dto.guest,
    streakBonusXp: dto.streakBonusXp ?? 0,
    countries: dto.countries.map(country => ({
      id: country.id,
      order: country.order,
      totalHints: country.totalHints,
      hintsSeen: country.hintsSeen,
      guessed: country.guessed,
      xpEarned: country.xpEarned,
      correctCountry: mapCountry(country.correctCountry),
      hints: country.hints.map(mapHint),
      attempts: country.attempts.map(attempt => ({
        attemptNumber: attempt.attemptNumber,
        guessedCountry: mapCountry(attempt.guessedCountry),
        correct: attempt.correct,
        hintsSeen: attempt.hintsSeen,
        createdAt: attempt.createdAt,
      })),
    })),
    communityStats: {
      enoughData: dto.communityStats.enoughData,
      avgXpPerCompletedRun: dto.communityStats.avgXpPerCompletedRun,
      accuracyRate: dto.communityStats.accuracyRate,
      avgActiveTimePerSessionMs: dto.communityStats.avgActiveTimePerSessionMs,
      avgAttemptsPerCountry: dto.communityStats.avgAttemptsPerCountry,
      avgHintsPerCountry: dto.communityStats.avgHintsPerCountry,
      hardestCountry: dto.communityStats.hardestCountry ? {
        country: mapCountry(dto.communityStats.hardestCountry.country),
        successRate: dto.communityStats.hardestCountry.successRate,
        avgAttemptsNeeded: dto.communityStats.hardestCountry.avgAttemptsNeeded,
        avgHintsNeeded: dto.communityStats.hardestCountry.avgHintsNeeded,
        avgActiveTimeMs: dto.communityStats.hardestCountry.avgActiveTimeMs,
        mostConfusedWith: dto.communityStats.hardestCountry.mostConfusedWith
          ? mapCountry(dto.communityStats.hardestCountry.mostConfusedWith)
          : null,
      } : null,
    },
  }
}

/* ── API ── */

export const gameApi = {
  getTodaysChallenge: async (): Promise<ChallengeDto> => {
    const { data } = await apiClient.get<ChallengeDto>('/game/today/easy')
    return data
  },

  getTodaysChallenges: async (): Promise<ChallengeDto[]> => {
    const { data } = await apiClient.get<ChallengeDto[]>('/game/today')
    return data
  },

  getTodaySession: async (): Promise<GameStateResponse | null> => {
    try {
      const { data } = await apiClient.get<GameStateResponse>('/game/sessions/today')
      return data
    } catch {
      return null
    }
  },

  getTodaySessionSummary: async (): Promise<GameSessionSummary | null> => {
    const { data, status } = await apiClient.get<BackendGameSessionSummaryDto | null>('/game/sessions/today/summary')
    if (status === 204 || !data) return null
    return mapSummary(data)
  },

  getTodayResults: async (): Promise<GameResults> => {
    const { data } = await apiClient.get<BackendGameResultsDto>('/game/results/today')
    return mapResults(data)
  },

  getGuestProgress: async (): Promise<GuestProgress> => {
    const { data } = await apiClient.get<GuestProgress>('/game/guest/progress')
    return data
  },

  getGuestState: async (): Promise<GuestState> => {
    const { data } = await apiClient.get<GuestState>('/game/guest/state')
    return data
  },

  startGuestSession: async (
    termsVersion: string,
    privacyVersion: string,
  ): Promise<GameStateResponse> => {
    const { data } = await apiClient.post<GameStateResponse>('/game/sessions/guest', {
      termsVersion,
      privacyVersion,
    })
    return data
  },

  startUserSession: async (level: string): Promise<GameStateResponse> => {
    const { data } = await apiClient.post<GameStateResponse>('/game/sessions', { level })
    return data
  },

  getSession: async (sessionId: string): Promise<GameStateResponse> => {
    const { data } = await apiClient.get<GameStateResponse>(`/game/sessions/${sessionId}`)
    return data
  },

  revealHint: async (sessionId: string): Promise<RevealHintResponse> => {
    const { data } = await apiClient.post<BackendRevealResultDto>(`/game/sessions/${sessionId}/reveal`, {})
    return {
      hint: mapHint(data.hint),
      hintsRevealed: data.hintsRevealed,
      attemptsRemaining: data.attemptsRemaining,
    }
  },

  submitGuess: async (sessionId: string, countryCode: string): Promise<GuessResult> => {
    const { data } = await apiClient.post<BackendGuessResultDto>(
      `/game/sessions/${sessionId}/guess`, { countryCode }
    )
    return {
      correct: data.correct,
      xpEarned: data.xpEarned,
      attemptsRemaining: data.attemptsRemaining,
      hintsRevealed: data.hintsRevealed,
      nextHint: data.nextHint ? mapHint(data.nextHint) : undefined,
      correctCountry: data.correctCountry ? mapCountry(data.correctCountry) : undefined,
      countryComplete: data.countryComplete,
      gameCompleted: data.sessionComplete,
    }
  },

  searchCountries: async (query: string): Promise<Country[]> => {
    const { data } = await apiClient.get<BackendCountryDto[]>('/game/countries/search', { params: { q: query } })
    return data.map(mapCountry)
  },

  // Leaderboard
  getLeaderboard: async (page = 0, size = 20): Promise<PaginatedLeaderboard> => {
    const { data } = await apiClient.get<PaginatedLeaderboard>('/leaderboard', { params: { page, size } })
    return data
  },

  // Profile
  getProfile: async (): Promise<ProfileDto> => {
    const { data } = await apiClient.get<ProfileDto>('/profile')
    return data
  },

  getGuestProfile: async (): Promise<ProfileDto> => {
    const { data } = await apiClient.get<ProfileDto>('/profile/guest')
    return data
  },

  getPublicProfile: async (username: string): Promise<ProfileDto> => {
    const { data } = await apiClient.get<ProfileDto>(`/profile/${username}`)
    return data
  },

  updateProfile: async (req: UpdateProfileRequest): Promise<ProfileDto> => {
    const { data } = await apiClient.put<ProfileDto>('/profile', req)
    return data
  },

  uploadAvatar: async (file: File): Promise<ProfileDto> => {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post<ProfileDto>('/profile/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  deleteAvatar: async (): Promise<ProfileDto> => {
    const { data } = await apiClient.delete<ProfileDto>('/profile/avatar')
    return data
  },

  // Hint reporting
  reportHint: async (report: HintReportRequest): Promise<{ success: boolean; message: string; reportId: string }> => {
    const { data } = await apiClient.post('/hints/report', report)
    return data
  },

  getBadgesPaginated: async (filter: string, page: number, size: number): Promise<ProfileBadgePageResponse> => {
    const { data } = await apiClient.get<ProfileBadgePageResponse>('/profile/badges', {
      params: { filter, page, size },
    })
    return data
  },

  getGuestBadgesPaginated: async (filter: string, page: number, size: number): Promise<ProfileBadgePageResponse> => {
    const { data } = await apiClient.get<ProfileBadgePageResponse>('/profile/guest/badges', {
      params: { filter, page, size },
    })
    return data
  },

  getBadgeSamples: async (): Promise<ProfileBadgeDto[]> => {
    const { data } = await apiClient.get<ProfileBadgeDto[]>('/badges/samples')
    return data
  },

  getPublicBadgesPaginated: async (username: string, page: number, size: number): Promise<ProfileBadgePageResponse> => {
    const { data } = await apiClient.get<ProfileBadgePageResponse>(`/profile/${username}/badges`, {
      params: { page, size },
    })
    return data
  },

  // Badges
  getUnacknowledgedBadges: async (): Promise<UnacknowledgedBadgeDto[]> => {
    const { data } = await apiClient.get<UnacknowledgedBadgeDto[]>('/profile/badges/unacknowledged')
    return data
  },

  acknowledgeBadges: async (badgeIds: string[]): Promise<void> => {
    await apiClient.post('/profile/badges/acknowledge', { badgeIds })
  },

  acknowledgeStreakFreezeNotice: async (): Promise<void> => {
    await apiClient.post('/profile/streak-freeze-notice/acknowledge')
  },
}
