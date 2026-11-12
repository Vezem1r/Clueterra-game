// Auth

export interface User {
  id: string
  email: string
  username?: string
  role: 'USER' | 'ADMIN'
  enabled: boolean
  createdAt: string
  needsLegalAcceptance: boolean
}

export interface AuthResponse {
  email: string
  username: string | null
  role: 'USER' | 'ADMIN'
  expiresIn: number
  totpRequired: boolean
  legalAcceptanceRequired: boolean
  userId: string | null
}

export interface LegalCurrent {
  termsVersion: string
  privacyVersion: string
  effectiveDate: string
}

export interface PublicDataSourceItem {
  slug: string
  name: string
  summary: string
  notice: string
  sourceLabel: string | null
  sourceUrl: string | null
  licenseLabel: string | null
  licenseUrl: string | null
  note: string | null
}

export interface PublicDataSourcesResponse {
  registryVersion: string
  sources: PublicDataSourceItem[]
}

// Profile

export interface ProfileDto {
  username: string
  displayName: string | null
  avatarUrl: string | null
  totalXp: number
  level: number
  completedRuns: number
  countriesSolved: number
  perfectRuns: number
  solveRate: number
  currentStreak: number
  bestStreak: number
  leaderboardRank?: number | null
  favoriteDifficulty?: 'EASY' | 'MEDIUM' | 'HARD' | null
  profileLocked: boolean
  socialDiscoverable?: boolean | null
  email?: string
  xpForCurrentLevel: number
  xpForNextLevel: number
  levelDivisor: number
  streakBonusPercent?: number
  freezesAvailable?: number
  previousFreezeAtStreak?: number | null
  maxFreezes?: number | null
  nextFreezeAtStreak?: number | null
  todayCompleted?: boolean
  resetAtUtc?: string | null
  freezeUsedToday?: boolean
  freezesUsedSinceLastCompletion?: number | null
  freezeNoticeAcknowledged?: boolean | null
  difficultyRecommendation?: 'EASY' | 'MEDIUM' | 'HARD' | null
  budgetStats: BudgetProfileStatsDto
}

export interface BudgetProfileStatsDto {
  completedChallenges: number
  projectsExplored: number
  projectsMasteredValueUsd: number
}

export interface ProfileBadgeDto {
  id: string
  slug: string
  imageUrl: string | null
  accentColorKey: string | null
  effectColorKey: string | null
  name: string
  description: string
  unlocked: boolean
  grantedAt: string | null
  progress: ProfileBadgeProgress | null
}

export type ProfileBadgeProgressKind = 'COUNTER' | 'COUNTRY_COLLECTION' | 'BOOLEAN'

export interface ProfileBadgeProgressItem {
  code: string | null
  name: string
  flag: string | null
  flagUrl: string | null
}

export interface ProfileBadgeProgress {
  kind: ProfileBadgeProgressKind
  current: number
  target: number
  remaining: number
  percent: number
  currentDisplay: string
  targetDisplay: string
  remainingDisplay: string
  missingItems: ProfileBadgeProgressItem[]
  hiddenMissingCount: number
  hiddenMissingCountDisplay: string
}

export interface UnacknowledgedBadgeDto {
  id: string
  badgeId: string
  slug: string
  imageUrl: string | null
  accentColorKey: string | null
  effectColorKey: string | null
  name: string
  description: string
  grantedAt: string
}

export interface ProfileBadgePageResponse {
  content: ProfileBadgeDto[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  earnedCount: number
  lockedVisibleCount: number
  hiddenSecretCount: number
}

export interface UpdateProfileRequest {
  displayName?: string
  avatarUrl?: string
  socialDiscoverable?: boolean
}

// Country

export interface Country {
  id: string
  isoAlpha2: string
  isoAlpha3?: string
  nameEn: string
  nameRu?: string | null
  region: string
  subregion?: string | null
  capital?: string | null
  population?: number | null
  area?: number | null
  flagEmoji?: string | null
  flagUrl?: string | null
}

// Hint

export interface Hint {
  id: string
  countryId?: string
  hintText: string
  imageUrl?: string | null
  imageSourcePageUrl?: string | null
  imageAuthorName?: string | null
  imageAttributionText?: string | null
  imageLicenseLabel?: string | null
  imageLicenseUrl?: string | null
  difficulty: number
  category: string
  type?: string
  source?: string
  sourceUrl?: string
  sourceDate?: string
  isActive?: boolean
}

// Game

export const GAME_SESSION_STATUS = {
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  ABANDONED: 'ABANDONED',
} as const

export type GameSessionStatus = typeof GAME_SESSION_STATUS[keyof typeof GAME_SESSION_STATUS]

export interface GameSession {
  id: string
  userId: string | null
  guestToken: string | null
  guest: boolean
  challengeId: string
  level: 'EASY' | 'MEDIUM' | 'HARD'
  status: GameSessionStatus
  currentCountryIndex: number
  startedAt: string
  activeTimeMs: number
  completedAt: string | null
  totalXpEarned: number
  xpBefore?: number
  levelBefore?: number
  countryCount?: number
  maxHintsPerCountry?: number
  maxAttempts?: number
}

export interface GameSessionSummary {
  id: string
  level: 'EASY' | 'MEDIUM' | 'HARD'
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

export interface GuestProgress {
  hasGuest: boolean
  playCount: number
  budgetPlayCount: number
  totalXp: number
  currentLevel: number
  levelDivisor: number
  currentStreak: number
  bestStreak: number
  badgesEarned: number
  expiresAt?: string | null
}

export interface GuestState {
  hasGuest: boolean
  needsLegalAcceptance: boolean
  hasTodaySession: boolean
  todaySessionId?: string | null
  todaySessionStatus?: GameSessionStatus | null
}

export interface GameResults {
  id: string
  level: 'EASY' | 'MEDIUM' | 'HARD'
  status: typeof GAME_SESSION_STATUS.COMPLETED
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
  streakBonusXp: number
  countries: ResultCountry[]
  communityStats: GameResultsCommunityStats
}

export interface GameResultsCommunityStats {
  enoughData: boolean
  avgXpPerCompletedRun: number
  accuracyRate: number
  avgActiveTimePerSessionMs: number
  avgAttemptsPerCountry: number
  avgHintsPerCountry: number
  hardestCountry: HardestCountryInsight | null
}

export interface HardestCountryInsight {
  country: Country
  successRate: number
  avgAttemptsNeeded: number
  avgHintsNeeded: number
  avgActiveTimeMs: number
  mostConfusedWith: Country | null
}

export interface ResultCountry {
  id: string
  order: number
  totalHints: number
  hintsSeen: number
  guessed: boolean
  xpEarned: number
  correctCountry: Country
  hints: Hint[]
  attempts: GuessAttempt[]
}

export interface GuessAttempt {
  attemptNumber: number
  guessedCountry: Country
  correct: boolean
  hintsSeen: number
  createdAt: string
}

export interface GuessResult {
  correct: boolean
  xpEarned: number
  attemptsRemaining: number
  hintsRevealed: number
  nextHint?: Hint
  correctCountry?: Country
  countryComplete: boolean
  gameCompleted: boolean
}

export interface RevealHintResponse {
  hint: Hint
  hintsRevealed: number
  attemptsRemaining: number
}

// Leaderboard

export interface LeaderboardEntry {
  rank: number
  username: string
  displayName: string
  avatarUrl: string | null
  totalXp: number
  level: number
  challengesWon: number
}

export interface PaginatedLeaderboard {
  entries: LeaderboardEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// Friends

export type FriendRequestDirection = 'INCOMING' | 'OUTGOING'
export type RelationshipLeaderboardPeriod = 'week' | 'alltime'

export interface FriendUser {
  userId: string
  username: string
  displayName: string
  avatarUrl: string | null
  totalXp: number
  level: number
}

export interface Friend {
  friendshipId: string
  user: FriendUser
  friendsSince: string
}

export interface FriendRequest {
  requestId: string
  user: FriendUser
  direction: FriendRequestDirection
  createdAt: string
}

export interface FriendRequests {
  incoming: FriendRequest[]
  outgoing: FriendRequest[]
}

export interface FriendLeaderboardEntry {
  rank: number
  user: FriendUser
  scoreXp: number
  totalXp: number
  level: number
  currentUser: boolean
}

// Daily Challenge

export interface DailyChallenge {
  id: string
  challengeDate: string
  countryIds: string[]
  createdAt: string
}

export interface ChallengeDto {
  id: string
  date: string
  level: string
  difficulty: string
  totalCountries: number
  totalHints: number
  available: boolean
  continentCount: number
  dataSourceCount: number
  categoryCount: number
  avgXp: number | null
}

// Hint Report

export interface HintReportRequest {
  hintId: string
  reportType: string
  description?: string
}

export interface ResolveReportRequest {
  status: 'REVIEWED' | 'FIXED' | 'REJECTED'
  adminNotes?: string
  applyToAll?: boolean
}

// Operation / Logs

export interface OperationBatchLogDto {
  id: string
  operationType: string
  triggerType: string
  status: string
  totalItems: number
  successfulItems: number
  failedItems: number
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  errorMessage: string | null
  summary: Record<string, unknown> | null
}

export interface DataSyncLogDto {
  id: string
  source: string
  syncType: string
  status: string
  countriesProcessed: number
  statisticsSaved: number
  hintsGenerated: number
  hintsDeactivated: number
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  batchId: string | null
  countryCode: string | null
  errorStack: string | null
  fetchDetails: string | null
}

export interface FetchFailureEntry {
  countryCode: string
  feedType: string
  error: string
  statusCode?: number
}

export interface HintGenerationLogDto {
  id: string
  countryId: string | null
  countryCode: string | null
  triggerType: string
  hintsAttempted: number
  hintsCreated: number
  hintsDuplicates: number
  hintsFailed: number
  avgDifficulty: number | null
  categoriesCovered: number
  hintTypesCovered: number
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  errorMessage: string | null
  batchId: string | null
  avgQualityScore: number | null
  details: Record<string, unknown> | null
  lensBreakdown: Record<string, unknown> | null
}

export interface GroupedError {
  errorMessage: string
  count: number
  firstOccurrence: string
  lastOccurrence: string
  affectedCountries: string[]
}

// Admin DTOs

export interface CountryAdminDto {
  id: string
  isoAlpha2: string
  isoAlpha3?: string | null
  nameEn: string
  nameNative?: string | null
  region: string
  subregion?: string | null
  capital?: string | null
  flagEmoji?: string | null
  flagUrl?: string | null
  difficultyModifier?: number | null
  timesFeatured?: number | null
  timesGuessed?: number | null
  successRate?: number | null
  isActive?: boolean | null
  createdAt?: string | null
  updatedAt?: string | null
  hintCount?: number | null
  statisticCount?: number | null
}

export interface HintAdminDto {
  id: string           // Snowflake Long serialized as string
  countryId: string    // Snowflake Long serialized as string
  countryCode?: string
  countryName?: string
  category: string
  hintType?: string
  hintText: string
  imageUrl?: string | null
  originalImageUrl?: string | null
  imageSourcePageUrl?: string | null
  imageAuthorName?: string | null
  imageAttributionText?: string | null
  imageLicenseLabel?: string | null
  imageLicenseUrl?: string | null
  difficulty: number
  qualityScore?: number | null
  source?: string
  sourceIndicator?: string | null
  sourceYear?: number | null
  ambiguityCount?: number | null
  timesUsed?: number
  timesCorrect?: number | null
  isActive?: boolean | null
  createdAt?: string | null
  updatedAt?: string | null
  reviewRetryAfterAt?: string | null
  editorialTextLocked?: boolean | null
  reportCount?: number
}

export interface HintReportAdminDto {
  id: string
  hintId: string
  userId: string
  userEmail: string
  hintText: string
  countryName: string
  reportType: string
  description: string | null
  status: 'PENDING' | 'REVIEWED' | 'FIXED' | 'REJECTED'
  adminNotes: string | null
  resolvedById: string | null
  resolvedByEmail: string | null
  createdAt: string
  resolvedAt: string | null
}

export interface HintStatsResponse {
  totalHints: number
  activeHints: number
  inactiveHints: number
  categories: number
  types: number
  sources: number
}

export interface CountryStatisticAdminDto {
  id: string
  countryId: string
  countryCode: string
  countryName: string
  source: string
  indicatorCode: string
  indicatorName: string
  category: string
  value: string | null
  valueText: string | null
  unit: string | null
  year: number | null
  globalRank: number | null
  regionalRank: number | null
  percentile: number | null
  deviationScore: number | null
  isCurrent: boolean
  manualOverride: boolean
  confidence: number | null
  weight: number | null
  fetchedAt: string | null
  imageUrl: string | null
  originalImageUrl: string | null
  imageSourcePageUrl: string | null
  imageAuthorName: string | null
  imageAttributionText: string | null
  imageLicenseLabel: string | null
  imageLicenseUrl: string | null
}

export interface CreateHintRequest {
  countryId: string
  category: string
  hintType: string
  hintText: string
  difficulty: number
  qualityScore: number | null
  sourceYear: number | null
}

export interface StatCountryOption {
  id: string
  isoAlpha2: string
  nameEn: string
}

// Admin Analytics

export interface AnalyticsSummaryDto {
  totalSessions: number
  completedSessions: number
  abandonedSessions: number
  uniqueUsers: number
  totalGuesses: number
  correctGuesses: number
  avgAttemptsPerCountry: number
  avgTimePerSessionMs: number
}

export interface SessionsByLevelDto {
  level: string
  total: number
  completed: number
}

export interface TopCountryDto {
  countryId: string
  countryCode: string
  countryName: string
  timesFeatured: number
  timesGuessedCorrect: number
  avgAttempts: number
}

export interface DailySessionDto {
  date: string
  totalSessions: number
  completedSessions: number
  uniqueUsers: number
  totalGuesses: number
  correctGuesses: number
}

// Admin Dashboard

export interface DashboardGameSummaryDto {
  totalSessions: number
  completedSessions: number
  abandonedSessions: number
  uniqueUsers: number
  activeToday: number
}

export interface DashboardSummaryDto {
  geography: DashboardGameSummaryDto
  budget: DashboardGameSummaryDto
  totalRegisteredUsers: number
}

export interface SessionChartPointDto {
  date: string
  totalSessions: number
  completedSessions: number
  uniqueUsers: number
}

// Admin Users

export interface UserAdminDto {
  id: string
  email: string
  username: string | null
  role: string
  enabled: boolean
  createdAt: string
  displayName: string | null
  avatarUrl: string | null
  totalXp: number
  currentLevel: number
  challengesPlayed: number
  challengesWon: number
  currentStreak: number
  bestStreak: number
  streakFreezes: number
  budgetCompletedChallenges: number
  budgetAnsweredQuestions: number
  budgetFullXpRate: number
  budgetAverageAccuracy: number
  budgetCleanSweeps: number
  budgetProjectsExplored: number
  budgetBestHigherLowerStreak: number
  budgetProjectsMasteredValueUsd: number
  riverStats: RiverPlayerStatsDto
  silhouetteStats: SilhouettePlayerStatsDto
  flagStats: FlagPlayerStatsDto
  quizStats: QuizPlayerStatsDto
  reportsMuted: boolean
  profileLocked: boolean
  isGuest: boolean
}

export interface RiverPlayerStatsDto {
  attemptedRivers: number
  solvedRivers: number
  answerAttempts: number
  skips: number
  xpAwarded: number
  currentQuestionId: string | null
}

export interface QuizPlayerStatsDto {
  exploredCountries: number
  totalCountries: number
  answeredQuestions: number
  correctAnswers: number
  xpAwarded: number
}

export interface FlagPlayerStatsDto {
  attemptedFlags: number
  solvedFlags: number
  totalFlags: number
  answerAttempts: number
  skips: number
  xpAwarded: number
}

export interface SilhouettePlayerStatsDto {
  attemptedCountries: number
  solvedCountries: number
  totalCountries: number
  answerAttempts: number
  skips: number
  xpAwarded: number
}

export interface UserPageResponse {
  pinned: UserAdminDto[]
  content: UserAdminDto[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface UpdateUserRequest {
  role?: string
  enabled?: boolean
  reportsMuted?: boolean
  profileLocked?: boolean
}

export interface UpdateUserProfileRequest {
  username?: string
  displayName?: string
  totalXp?: number
  challengesPlayed?: number
  challengesWon?: number
  currentStreak?: number
  bestStreak?: number
  streakFreezes?: number
}

// App Settings

export interface AppSettings {
  registrationEnabled: boolean
  countriesPerChallenge: number
  hintsPerCountry: number
  baseXpEasy: number
  baseXpMedium: number
  baseXpHard: number
  hintsBonusMultiplier: number
  attemptsBonusMultiplier: number
  participationMultiplier: number
  levelDivisor: number
  maxAttempts: number
  timeBonusMultiplier: number
  timeBonusThreshold: number
  autoApproveGeneration: boolean
  autoApproveReview: boolean
  streakMaxBonusPercent: number
  streakMaxDays: number
  streakCurvePower: number
  streakFreezeMax: number
  streakFreezeStreakInterval: number
  streakFreezePerBadge: number
  budgetBaseXpPerQuestion: number
  budgetFullXpThresholdPercent: number
  budgetXpCurvePower: number
  riverGameBaseXp: number
  riverGameHintPenaltyPercent: number
  silhouetteGameBaseXp: number
  silhouetteGameHintPenaltyPercent: number
  flagGameBaseXp: number
  flagGameHintPenaltyPercent: number
  quizGameAnswerXp: number
}

export interface SettingsAuditChange {
  fieldName: string
  oldValue: string | null
  newValue: string
}

export interface SettingsAuditEntry {
  batchId: string
  changedAt: string
  changedByUserId: string | null
  changedByEmail: string | null
  changedByUsername: string | null
  changes: SettingsAuditChange[]
}

// Common

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  code?: string
  errors?: Record<string, string>
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// Badges (Admin)

export interface BadgeTranslationEntry {
  name: string
  descriptionUnlocked: string
  descriptionLocked: string
}

export interface BadgeAdminDto {
  id: string
  slug: string
  imageUrl: string | null
  accentColorKey: string
  effectColorKey: string
  isSecret: boolean
  isActive: boolean
  grantType: string
  grantConfig: unknown
  displayOrder: number
  ownerCount: number
  createdAt: string
  updatedAt: string
  translations: Record<string, BadgeTranslationEntry> | null
  categoryId: string | null
  categoryName: string | null
}

export interface BadgeCategoryDto {
  id: string
  name: string
  displayOrder: number
  badgeCount: number
}

export interface SaveBadgeRequest {
  slug?: string
  isSecret?: boolean
  isActive?: boolean
  accentColorKey?: string
  effectColorKey?: string
  grantType?: string
  grantConfig?: unknown
  displayOrder?: number
  categoryId?: string | null
  translations?: Record<string, BadgeTranslationEntry>
}

export interface UserBadgeAdminDto {
  badgeId: string
  slug: string
  name: string
  imageUrl: string | null
  grantedAt: string
  grantedBy: string
}

export interface BadgeEvalLogDto {
  id: string
  badgeId: string
  evaluationType: string
  usersEvaluated: number
  usersGranted: number
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  errorMessage: string | null
}

export interface BadgeMetadataResponse {
  grantTypes: string[]
  triggers: string[]
  filterFields: string[]
  badgeColorKeys?: string[]
}

// Indicator Config (Admin)

export interface IndicatorConfigAdminDto {
  indicatorCode: string
  displayName: string | null
  category: string | null
  unit: string | null
  weight: number | null
  profileLevel: string | null
  lowerIsBetter: boolean | null
  excludeFromRanking: boolean | null
  excludeFromPercentile: boolean | null
  hintTemplate: string | null
  hintType: string | null
  baseDifficulty: number | null
  qualityScore: number | null
  isActive: boolean | null
  highInterest: boolean | null
  isTrending: boolean | null
  trendingFrequency: string | null
  hintCount: number
  exampleHint: string | null
  indicatorKind: 'regular' | 'combo'
  familyType: string | null
  maxAmbiguityCount: number | null
  componentCount: number | null
  predicateCount: number | null
  variantCount: number | null
  componentsSummary: string | null
  predicatesSummary: string | null
  variantTemplates: string | null
}
