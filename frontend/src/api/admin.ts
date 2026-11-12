import { apiClient } from './client'
import type { QuizFact, QuizKind } from './quiz'
import type { SilhouetteFacts, SilhouetteNames } from './silhouettes'
import type {
  CountryAdminDto,
  HintAdminDto,
  PaginatedResponse,
  HintReportAdminDto,
  ResolveReportRequest,
  HintStatsResponse,
  CountryStatisticAdminDto,
  CreateHintRequest,
  StatCountryOption,
  DashboardSummaryDto,
  SessionChartPointDto,
  UserAdminDto,
  UpdateUserRequest,
  UpdateUserProfileRequest,
  UserPageResponse,
  AnalyticsSummaryDto,
  SessionsByLevelDto,
  TopCountryDto,
  DailySessionDto,
  OperationBatchLogDto,
  DataSyncLogDto,
  HintGenerationLogDto,
  GroupedError,
  AppSettings,
  BadgeAdminDto,
  BadgeCategoryDto,
  SaveBadgeRequest,
  UserBadgeAdminDto,
  BadgeEvalLogDto,
  BadgeMetadataResponse,
  IndicatorConfigAdminDto,
  SettingsAuditEntry,
} from '@/types'

export interface SourceDetail {
  name: string
  displayName: string
  baseUrl: string | null
  priority: number
  enabled: boolean
  healthy: boolean
  syncAvailable: boolean
  hintGenerationAvailable: boolean
  status: 'AVAILABLE' | 'UNHEALTHY' | 'DISABLED'
  metadata: { clientClass: string; priority: number; baseUrl: string } | null
  message: string
}

export interface SourcesOverview {
  totalSources: number
  enabledSources: number
  healthySources: number
  sources: SourceDetail[]
  timestamp: string
  serviceStatus?: string
  serviceError?: string
  serviceDetails?: string
}

export interface BudgetServiceStatus {
  serviceStatus: 'UP' | 'DOWN'
  error: string | null
}

export interface BudgetOperationAccepted {
  operationId: string
  name: string
  status: string
}

export interface ChallengeAdminRow {
  id: string
  challengeDate: string
  challengeLevel: string
  difficulty: string
  totalCountries: number
  totalHints: number
  isActive: boolean
  createdAt: string
  canRerollCountry?: boolean
  canRerollHint?: boolean
  countriesJson?: string
  hintsJson?: string
}

export interface ChallengePageResponse {
  pinned: ChallengeAdminRow[]
  content: ChallengeAdminRow[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ChallengeRerollResponse {
  challengeId: string
  status: string
  totalCountries: number
  totalHints: number
}

export interface AiQuotaUsageResponse {
  requestsPerMinuteUsed: number
  requestsPerMinuteLimit: number
  requestsPerDayUsed: number
  requestsPerDayLimit: number
  groundedPromptsPerDayUsed: number | null
  groundedPromptsPerDayLimit: number | null
  inputTokensToday: number
  outputTokensToday: number
  failedToday: number
  nextResetAt: string
}

export interface AiLaneOverviewResponse {
  model: string
  schedulerEnabled: boolean
  maxRequestsPerRun: number
  quota: AiQuotaUsageResponse
  reviewCoverage: AiReviewCoverageResponse | null
}

export interface RiverQuestionPreview {
  id: number
  countryCode: string
  countryName: string
  name: string
  rank: number
  lengthKm: number
  plainImageUrl: string
  bordersImageUrl: string
  active: boolean
  hints: { position: number; kind: string; text: string; sourceUrl: string }[]
  photoUrl: string | null
  photoAuthorName: string | null
  photoLicenseLabel: string | null
  wikipediaUrl: string | null
}

export interface RiverQuestionPreviewPage {
  content: RiverQuestionPreview[]
  totalElements: number
  page: number
  size: number
}

export interface RiverQuestionFilters {
  countryCode?: string
  search?: string
  status?: 'ALL' | 'ACTIVE' | 'INACTIVE'
  sort?: 'COUNTRY' | 'NAME' | 'LONGEST' | 'SHORTEST' | 'EASIEST' | 'HARDEST' | 'NEWEST'
}

export interface SilhouetteHintPreview {
  position: number
  kind: 'REGION' | 'LOCATION' | 'CAPITAL' | 'FLAG_COLORS' | 'LANDMARK'
  costUnits: number
  payload: Record<string, unknown>
}

export interface SilhouetteQuestionPreview {
  id: number
  countryCode: string
  name: string
  tier: number
  region: string
  subregion: string
  shapePath: string
  active: boolean
  facts: SilhouetteFacts
  hints: SilhouetteHintPreview[]
  photoUrl: string | null
  photoAuthorName: string | null
  photoSourcePageUrl: string | null
  photoLicenseLabel: string | null
  photoLicenseUrl: string | null
  photoWidth: number | null
  photoHeight: number | null
  photoSelection: SilhouettePhotoSelection
  photoSelectedBy: string | null
  landmarkQid: string | null
  landmarkName: string | null
  landmarkSitelinks: number | null
  players: number
  solved: number
  skips: number
  reveals: number
  averageHints: number | null
  generatedAt: string | null
}

export interface SilhouetteQuestionSummary {
  activeQuestions: number
  landmarkClues: number
  players: number
  solves: number
  guesses: number
}

export interface SilhouetteQuestionPreviewPage {
  content: SilhouetteQuestionPreview[]
  page: number
  size: number
  hasNext: boolean
  summary: SilhouetteQuestionSummary
}

export type SilhouettePhotoSelection = 'AUTO' | 'MANUAL'

export type SilhouettePhotoFollowUp = 'NONE' | 'CLUES_REGENERATING' | 'SYNC_STARTED'

export interface SilhouettePhotoChange {
  question: SilhouetteQuestionPreview
  followUp: SilhouettePhotoFollowUp
}

export interface FlagHintPreview {
  position: number
  kind: 'REGION' | 'INITIAL' | 'CAPITAL'
  costUnits: number
  payload: Record<string, unknown>
}

export interface FlagQuestionPreview {
  id: number
  countryCode: string
  name: string
  tier: number
  region: string
  subregion: string
  active: boolean
  flagUrl: string
  hints: FlagHintPreview[]
  players: number
  solved: number
  skips: number
  reveals: number
  wrongGuesses: number
  averageHints: number | null
  generatedAt: string | null
}

export interface FlagQuestionSummary {
  activeQuestions: number
  capitalClues: number
  players: number
  solves: number
  guesses: number
}

export interface FlagQuestionPreviewPage {
  content: FlagQuestionPreview[]
  page: number
  size: number
  hasNext: boolean
  summary: FlagQuestionSummary
}

export interface FlagQuestionFilters {
  countryCode?: string
  search?: string
  status?: 'ALL' | 'ACTIVE' | 'INACTIVE'
  tier?: number
  capital?: 'ALL' | 'WITH_CAPITAL' | 'NO_CAPITAL'
  sort?: 'COUNTRY' | 'EASIEST' | 'HARDEST' | 'MOST_PLAYED' | 'MOST_SKIPPED' | 'MOST_CONFUSED' | 'NEWEST'
}

export interface QuizQuestionPreview {
  id: number
  kind: QuizKind
  level: number
  active: boolean
  subject: { iso: string, names: SilhouetteNames } | null
  options: Record<string, unknown>[]
  answerIndex: number
  fact: QuizFact
  answers: number
  correct: number
  generatedAt: string | null
}

export interface QuizStopPreview {
  id: number
  countryCode: string
  name: string
  tier: number
  region: string
  subregion: string
  active: boolean
  neighbours: string[]
  questions: QuizQuestionPreview[]
  visitors: number
  explored: number
}

export interface QuizContentSummary {
  activeCountries: number
  activeQuestions: number
  players: number
  explored: number
  answers: number
  correct: number
}

export interface QuizStopPreviewPage {
  content: QuizStopPreview[]
  page: number
  size: number
  hasNext: boolean
  summary: QuizContentSummary
}

export interface QuizStopFilters {
  countryCode?: string
  search?: string
  status?: 'ALL' | 'ACTIVE' | 'INACTIVE'
  tier?: number
  kind?: 'ALL' | QuizKind
  sort?: 'COUNTRY' | 'EASIEST' | 'HARDEST' | 'MOST_PLAYED' | 'LOWEST_ACCURACY' | 'NEWEST'
}

export interface SilhouetteQuestionFilters {
  countryCode?: string
  search?: string
  status?: 'ALL' | 'ACTIVE' | 'INACTIVE'
  tier?: number
  photo?: 'ALL' | 'WITH_PHOTO' | 'MISSING' | 'MANUAL'
  sort?: 'COUNTRY' | 'EASIEST' | 'HARDEST' | 'MOST_PLAYED' | 'MOST_SKIPPED' | 'NEWEST'
}

export interface AiReviewCoverageResponse {
  processedHints: number
  totalHints: number
  pendingHints: number
  inReviewHints: number
  actionPendingHints: number
  coveragePercent: number
}

export interface AiEmbeddingOverviewResponse {
  enabled: boolean
  available: boolean
  model: string
  dimensions: number
  activeHints: number
  embeddedHints: number
  missingHints: number
  coveragePercent: number
  duplicateThreshold: number
  relatedThreshold: number
}

export interface AiOverviewResponse {
  promptVersion: string
  review: AiLaneOverviewResponse
  generation: AiLaneOverviewResponse
  embedding: AiEmbeddingOverviewResponse
}

export interface AiPageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface AiRun {
  id: string
  runType: string
  triggerType: string
  status: string
  provider: string
  model: string
  promptVersion: string
  requestedBy: string | null
  totalStages: number
  preparedItems: number
  processedItems: number
  changedItems: number
  generatedItems: number
  estimatedInputTokens: number
  estimatedOutputTokens: number
  actualInputTokens: number
  actualOutputTokens: number
  toolCallCount: number
  configuration: Record<string, unknown> | null
  summary: Record<string, unknown> | null
  errorMessage: string | null
  createdAt: string | null
  startedAt: string | null
  completedAt: string | null
}

export interface AiRunStage {
  id: string
  runId: string
  stageIndex: number
  stageType: string
  status: string
  itemCount: number
  estimatedInputTokens: number
  estimatedOutputTokens: number
  actualInputTokens: number
  actualOutputTokens: number
  imageCount: number
  payload: Record<string, unknown> | null
  resultSummary: Record<string, unknown> | null
  errorMessage: string | null
  providerRequestId: string | null
  createdAt: string | null
  startedAt: string | null
  completedAt: string | null
}

export interface AiChangeSet {
  id: string
  runId: string
  changeSetType: string
  status: string
  title: string
  summary: string
  totalItems: number
  appliedItems: number
  rejectedItems: number
  metadata: Record<string, unknown> | null
  createdAt: string | null
  appliedAt: string | null
  rejectedAt: string | null
}

export interface AiChangeItem {
  id: string
  changeSetId: string
  runId: string
  proposalId: string | null
  itemType: string
  status: string
  hintId: string | null
  countryId: string | null
  reasonSummary: string | null
  diffSummary: string | null
  originalSnapshot: Record<string, unknown> | null
  proposedSnapshot: Record<string, unknown> | null
  createdAt: string | null
  decidedAt: string | null
}

export interface AiGeneratedHintProposal {
  id: string
  runId: string
  stageId: string
  changeSetId: string
  countryId: string
  countryCode: string | null
  countryName: string | null
  status: string
  category: string | null
  hintType: string | null
  hintText: string
  imageSourceUrl: string | null
  storedImageUrl: string | null
  difficulty: number | null
  qualityScore: number | null
  source: string | null
  sourceIndicator: string | null
  sourceYear: number | null
  factSummary: string | null
  evidenceSummary: string | null
  duplicateCheckSummary: string | null
  searchTerms: string[]
  createdAt: string | null
}

export interface AiRunDetailResponse {
  run: AiRun
  stages: AiRunStage[]
  changeSet: AiChangeSet | null
  changeItems: AiChangeItem[]
}

export interface AiChangeSetDetailResponse {
  changeSet: AiChangeSet
  items: AiChangeItem[]
  proposals: AiGeneratedHintProposal[]
}

export interface AiRunStartResponse {
  runId: string | null
  runType: string
  status: string
  preparedStages: number
  preparedItems: number
  estimatedInputTokens: number
  message: string
}

export interface AiChangeSetActionResponse {
  changeSetId: string
  status: string
  totalItems: number
  appliedItems: number
  rejectedItems: number
  message: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function normalizeSourceDetail(value: unknown): SourceDetail | null {
  if (!isRecord(value) || typeof value.name !== 'string') return null

  const status = value.status === 'AVAILABLE' || value.status === 'UNHEALTHY' || value.status === 'DISABLED'
    ? value.status
    : 'DISABLED'

  const metadata = isRecord(value.metadata)
    ? {
      clientClass: typeof value.metadata.clientClass === 'string' ? value.metadata.clientClass : '',
      priority: typeof value.metadata.priority === 'number' ? value.metadata.priority : 0,
      baseUrl: typeof value.metadata.baseUrl === 'string' ? value.metadata.baseUrl : '',
    }
    : null

  return {
    name: value.name,
    displayName: typeof value.displayName === 'string' && value.displayName.trim()
      ? value.displayName
      : value.name,
    baseUrl: typeof value.baseUrl === 'string' ? value.baseUrl : null,
    priority: typeof value.priority === 'number' ? value.priority : 0,
    enabled: value.enabled === true,
    healthy: value.healthy === true,
    syncAvailable: value.syncAvailable === true || (value.syncAvailable == null && value.enabled === true),
    hintGenerationAvailable: value.hintGenerationAvailable === true
      || (value.hintGenerationAvailable == null && value.enabled === true),
    status,
    metadata,
    message: typeof value.message === 'string' ? value.message : '',
  }
}

function normalizeSourcesOverview(data: unknown): SourcesOverview {
  const payload = isRecord(data) ? data : {}
  const sources = Array.isArray(payload.sources)
    ? payload.sources.map(normalizeSourceDetail).filter((source): source is SourceDetail => source !== null)
    : []

  const enabledSources = typeof payload.enabledSources === 'number'
    ? payload.enabledSources
    : sources.filter(source => source.enabled).length
  const healthySources = typeof payload.healthySources === 'number'
    ? payload.healthySources
    : sources.filter(source => source.enabled && source.healthy).length
  const totalSources = typeof payload.totalSources === 'number'
    ? payload.totalSources
    : sources.length

  return {
    totalSources,
    enabledSources,
    healthySources,
    sources,
    timestamp: typeof payload.timestamp === 'string' ? payload.timestamp : new Date().toISOString(),
    serviceStatus: typeof payload.status === 'string' ? payload.status : (sources.length > 0 ? 'UP' : 'UNKNOWN'),
    serviceError: typeof payload.error === 'string' ? payload.error : undefined,
    serviceDetails: typeof payload.details === 'string' ? payload.details : undefined,
  }
}

export const adminApi = {
  getOverview: async () => {
    const { data } = await apiClient.get('/admin')
    return data
  },

  checkHealth: async (): Promise<{ status: string }> => {
    const { data } = await apiClient.get<{ status: string }>('/admin/health')
    return data
  },

  getDataSources: async (): Promise<SourcesOverview> => {
    const { data } = await apiClient.get('/admin')
    return normalizeSourcesOverview(data)
  },

  // Dashboard

  getDashboardSummary: async (): Promise<DashboardSummaryDto> => {
    const { data } = await apiClient.get<DashboardSummaryDto>('/admin/dashboard/summary')
    return data
  },

  getDashboardSessionsChart: async (days = 30): Promise<SessionChartPointDto[]> => {
    const { data } = await apiClient.get<SessionChartPointDto[]>('/admin/dashboard/sessions-chart', { params: { days } })
    return data
  },

  // Users

  getUsers: async (params: {
    page?: number; size?: number; search?: string; role?: string;
    enabled?: boolean; reportsMuted?: boolean; isGuest?: boolean;
    minRiversPlayed?: number; maxRiversPlayed?: number;
    sortBy?: string; sortDir?: string
  } = {}): Promise<UserPageResponse> => {
    const { data } = await apiClient.get('/admin/users', { params })
    return data as UserPageResponse
  },

  getUser: async (id: string): Promise<UserAdminDto> => {
    const { data } = await apiClient.get<UserAdminDto>(`/admin/users/${id}`)
    return data
  },

  updateUser: async (id: string, body: UpdateUserRequest): Promise<UserAdminDto> => {
    const { data } = await apiClient.put<UserAdminDto>(`/admin/users/${id}`, body)
    return data
  },

  updateUserProfile: async (id: string, body: UpdateUserProfileRequest): Promise<UserAdminDto> => {
    const { data } = await apiClient.put<UserAdminDto>(`/admin/users/${id}/profile`, body)
    return data
  },

  getUserRoles: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/users/roles')
    return data
  },

  deleteUserAvatar: async (id: string): Promise<void> => {
    await apiClient.delete(`/admin/users/${id}/avatar`)
  },

  uploadUserAvatar: async (id: string, file: File): Promise<UserAdminDto> => {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post<UserAdminDto>(`/admin/users/${id}/avatar`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  // Sync & Operations

  getBudgetServiceStatus: async (): Promise<BudgetServiceStatus> => {
    const { data } = await apiClient.get<BudgetServiceStatus>('/admin/budget-operations/status')
    return data
  },

  getDashboardBudgetSessionsChart: async (days = 30): Promise<SessionChartPointDto[]> => {
    const { data } = await apiClient.get<SessionChartPointDto[]>('/admin/dashboard/budget-sessions-chart', { params: { days } })
    return data
  },

  syncBudgetFull: async (): Promise<BudgetOperationAccepted> => {
    const { data } = await apiClient.post<BudgetOperationAccepted>('/admin/budget-operations/full')
    return data
  },

  syncBudgetWikidata: async (): Promise<BudgetOperationAccepted> => {
    const { data } = await apiClient.post<BudgetOperationAccepted>('/admin/budget-operations/wikidata')
    return data
  },

  enrichBudgetImages: async (): Promise<BudgetOperationAccepted> => {
    const { data } = await apiClient.post<BudgetOperationAccepted>('/admin/budget-operations/images')
    return data
  },

  generateBudgetQuestions: async (): Promise<BudgetOperationAccepted> => {
    const { data } = await apiClient.post<BudgetOperationAccepted>('/admin/budget-operations/questions')
    return data
  },

  generateBudgetChallenges: async (): Promise<BudgetOperationAccepted> => {
    const { data } = await apiClient.post<BudgetOperationAccepted>('/admin/budget-operations/challenges')
    return data
  },

  syncFull: async (): Promise<{ status: string }> => {
    const { data } = await apiClient.post('/admin/sync/full')
    return data
  },

  syncSource: async (source: string): Promise<{ status: string }> => {
    const { data } = await apiClient.post(`/admin/sync/source/${source}`)
    return data
  },

  syncCountry: async (countryCode: string) => {
    const { data } = await apiClient.post(`/admin/sync/country/${countryCode}`)
    return data
  },

  syncSourceCountry: async (source: string, countryCode: string) => {
    const { data } = await apiClient.post(`/admin/sync/source/${source}/country/${countryCode}`)
    return data
  },

  enrichStatistics: async (): Promise<{ status: string }> => {
    const { data } = await apiClient.post('/admin/enrich')
    return data
  },

  generateHintsForCountry: async (countryCode: string) => {
    const { data } = await apiClient.post(`/admin/hints/generate/${countryCode}`)
    return data
  },

  generateHintsForSource: async (source: string): Promise<{ status: string }> => {
    const { data } = await apiClient.post(`/admin/hints/generate/source/${source}`)
    return data
  },

  generateAllHints: async (): Promise<{ status: string }> => {
    const { data } = await apiClient.post('/admin/hints/generate-all')
    return data
  },

  generateAllComboHints: async (): Promise<{ status: string }> => {
    const { data } = await apiClient.post('/admin/hints/generate-combo-all')
    return data
  },

  auditImageIntegrity: async (): Promise<{ status: string }> => {
    const { data } = await apiClient.post('/admin/images/integrity-audit')
    return data
  },

  generateTodayChallenge: async () => {
    const { data } = await apiClient.post('/admin/challenge/today')
    return data
  },

  generateChallengeForDate: async (date: string, level?: string) => {
    const { data } = await apiClient.post(`/admin/challenge/date/${date}`, null, {
      params: level ? { level } : undefined,
    })
    return data
  },

  generateUpcoming: async (days = 7) => {
    const { data } = await apiClient.post('/admin/challenge/upcoming', null, { params: { days } })
    return data
  },

  // AI

  getAiOverview: async (): Promise<AiOverviewResponse> => {
    const { data } = await apiClient.get<AiOverviewResponse>('/admin/ai/overview')
    return data
  },

  startAiReview: async (requestedBy?: string): Promise<AiRunStartResponse> => {
    const { data } = await apiClient.post<AiRunStartResponse>('/admin/ai/review/start', null, {
      params: requestedBy ? { requestedBy } : undefined,
    })
    return data
  },

  startAiGeneration: async (requestedBy?: string): Promise<AiRunStartResponse> => {
    const { data } = await apiClient.post<AiRunStartResponse>('/admin/ai/generation/start', null, {
      params: requestedBy ? { requestedBy } : undefined,
    })
    return data
  },

  getAiRuns: async (page = 0, size = 20): Promise<AiPageResponse<AiRun>> => {
    const { data } = await apiClient.get<AiPageResponse<AiRun>>('/admin/ai/runs', { params: { page, size } })
    return data
  },

  getAiRunDetail: async (runId: string): Promise<AiRunDetailResponse> => {
    const { data } = await apiClient.get<AiRunDetailResponse>(`/admin/ai/runs/${runId}`)
    return data
  },

  getAiChangeSets: async (page = 0, size = 20): Promise<AiPageResponse<AiChangeSet>> => {
    const { data } = await apiClient.get<AiPageResponse<AiChangeSet>>('/admin/ai/change-sets', { params: { page, size } })
    return data
  },

  getAiChangeSetDetail: async (changeSetId: string): Promise<AiChangeSetDetailResponse> => {
    const { data } = await apiClient.get<AiChangeSetDetailResponse>(`/admin/ai/change-sets/${changeSetId}`)
    return data
  },

  approveAiChangeItem: async (changeSetId: string, itemId: string): Promise<AiChangeSetActionResponse> => {
    const { data } = await apiClient.post<AiChangeSetActionResponse>(`/admin/ai/change-sets/${changeSetId}/items/${itemId}/approve`)
    return data
  },

  rejectAiChangeItem: async (changeSetId: string, itemId: string): Promise<AiChangeSetActionResponse> => {
    const { data } = await apiClient.post<AiChangeSetActionResponse>(`/admin/ai/change-sets/${changeSetId}/items/${itemId}/reject`)
    return data
  },

  applyAiChangeSet: async (changeSetId: string): Promise<AiChangeSetActionResponse> => {
    const { data } = await apiClient.post<AiChangeSetActionResponse>(`/admin/ai/change-sets/${changeSetId}/apply`)
    return data
  },

  rejectAllAiChangeSet: async (changeSetId: string, itemTypes?: string[]): Promise<AiChangeSetActionResponse> => {
    const { data } = await apiClient.post<AiChangeSetActionResponse>(`/admin/ai/change-sets/${changeSetId}/reject-all`, null, {
      params: itemTypes?.length ? { itemTypes: itemTypes.join(',') } : undefined,
    })
    return data
  },

  generateRiverQuestions: async (): Promise<{ status: string; operationId: string }> => {
    const { data } = await apiClient.post('/admin/rivers/generate')
    return data
  },

  generateFlagQuestions: async (): Promise<{ status: string; operationId: string }> => {
    const { data } = await apiClient.post('/admin/flags/generate')
    return data
  },

  getFlagQuestions: async (filters: FlagQuestionFilters, page: number): Promise<FlagQuestionPreviewPage> => {
    const { data } = await apiClient.get('/admin/flags/questions', { params: { ...filters, page, size: 24 } })
    return data
  },

  generateQuizQuestions: async (): Promise<{ status: string; operationId: string }> => {
    const { data } = await apiClient.post('/admin/quiz/generate')
    return data
  },

  getQuizStops: async (filters: QuizStopFilters, page: number): Promise<QuizStopPreviewPage> => {
    const { data } = await apiClient.get('/admin/quiz/stops', { params: { ...filters, page, size: 12 } })
    return data
  },

  generateSilhouetteQuestions: async (): Promise<{ status: string; operationId: string }> => {
    const { data } = await apiClient.post('/admin/silhouettes/generate')
    return data
  },

  getSilhouetteQuestions: async (filters: SilhouetteQuestionFilters, page: number): Promise<SilhouetteQuestionPreviewPage> => {
    const { data } = await apiClient.get('/admin/silhouettes/questions', { params: { ...filters, page, size: 24 } })
    return data
  },

  replaceSilhouettePhoto: async (iso2: string, fileTitle: string, landmarkQid: string | null): Promise<SilhouettePhotoChange> => {
    const { data } = await apiClient.put(`/admin/silhouettes/questions/${iso2}/photo`, { fileTitle, landmarkQid })
    return data
  },
  removeSilhouettePhoto: async (iso2: string): Promise<SilhouettePhotoChange> => {
    const { data } = await apiClient.delete(`/admin/silhouettes/questions/${iso2}/photo`)
    return data
  },
  automaticSilhouettePhoto: async (iso2: string): Promise<SilhouettePhotoChange> => {
    const { data } = await apiClient.post(`/admin/silhouettes/questions/${iso2}/photo/automatic`)
    return data
  },

  syncCountryPhotos: async (): Promise<{ status: string; operationId: string }> => {
    const { data } = await apiClient.post('/admin/countries/photos/sync')
    return data
  },
  syncRiverPhotos: async (): Promise<{ status: string; operationId: string }> => {
    const { data } = await apiClient.post('/admin/rivers/photos/sync')
    return data
  },

  getRiverQuestions: async (filters: RiverQuestionFilters, page: number): Promise<RiverQuestionPreviewPage> => {
    const { data } = await apiClient.get('/admin/rivers/questions', { params: { ...filters, page, size: 24 } })
    return data
  },
  approveAllAiChangeSet: async (changeSetId: string, itemTypes: string[]): Promise<AiChangeSetActionResponse> => {
    const { data } = await apiClient.post<AiChangeSetActionResponse>(`/admin/ai/change-sets/${changeSetId}/approve-all`, null, {
      params: { itemTypes: itemTypes.join(',') },
    })
    return data
  },

  // Countries

  getCountries: async (params: {
    page?: number; size?: number; region?: string; search?: string;
    active?: boolean; sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<CountryAdminDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<CountryAdminDto>>('/admin/countries', { params })
    return data
  },

  getCountry: async (id: string): Promise<CountryAdminDto> => {
    const { data } = await apiClient.get<CountryAdminDto>(`/admin/countries/${id}`)
    return data
  },

  getCountryStats: async (id: string) => {
    const { data } = await apiClient.get(`/admin/countries/${id}/stats`)
    return data
  },

  updateCountry: async (id: string, updates: Partial<CountryAdminDto>): Promise<CountryAdminDto> => {
    const { data } = await apiClient.put<CountryAdminDto>(`/admin/countries/${id}`, updates)
    return data
  },

  toggleCountryActive: async (id: string, active: boolean): Promise<CountryAdminDto> => {
    const { data } = await apiClient.patch<CountryAdminDto>(`/admin/countries/${id}/active`, null, {
      params: { active },
    })
    return data
  },

  getRegions: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/countries/regions')
    return data
  },

  // Challenges

  getChallenges: async (params: {
    page?: number; size?: number; startDate?: string; endDate?: string;
    level?: string; difficulty?: string; active?: boolean;
    sortBy?: string; sortDir?: string
  } = {}) => {
    const { data } = await apiClient.get('/admin/challenges', { params })
    return data as ChallengePageResponse
  },

  getChallenge: async (id: string) => {
    const { data } = await apiClient.get(`/admin/challenges/${id}`)
    return data as ChallengeAdminRow
  },

  toggleChallengeActive: async (id: string, active: boolean) => {
    const { data } = await apiClient.patch(`/admin/challenges/${id}/active`, null, { params: { active } })
    return data as ChallengeAdminRow
  },

  updateChallenge: async (id: string, body: { challengeLevel: string; difficulty: string }) => {
    const { data } = await apiClient.put(`/admin/challenges/${id}`, body)
    return data as ChallengeAdminRow
  },

  getChallengeLevels: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/challenges/levels')
    return data
  },

  getChallengeDifficulties: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/challenges/difficulties')
    return data
  },

  getChallengeStats: async () => {
    const { data } = await apiClient.get('/admin/challenges/stats')
    return data as Record<string, number>
  },

  invalidateChallengeCache: async (): Promise<{ evicted: number; status: string }> => {
    const { data } = await apiClient.post<{ evicted: number; status: string }>('/admin/challenges/cache/invalidate')
    return data
  },

  invalidateTodayChallengeCache: async (): Promise<{ status: string }> => {
    const { data } = await apiClient.post<{ status: string }>('/admin/challenges/cache/today/invalidate')
    return data
  },

  rerollChallengeCountry: async (challengeId: string, countryOrder: number): Promise<ChallengeRerollResponse> => {
    const { data } = await apiClient.post<ChallengeRerollResponse>(`/admin/challenge/${challengeId}/reroll-country`, { countryOrder })
    return data
  },

  rerollChallengeHint: async (challengeId: string, countryOrder: number, hintOrder: number): Promise<ChallengeRerollResponse> => {
    const { data } = await apiClient.post<ChallengeRerollResponse>(`/admin/challenge/${challengeId}/reroll-hint`, { countryOrder, hintOrder })
    return data
  },

  // Hints

  createHint: async (body: CreateHintRequest): Promise<HintAdminDto> => {
    const { data } = await apiClient.post<HintAdminDto>('/admin/hints', body)
    return data
  },

  getHints: async (params: {
    page?: number; size?: number; search?: string; countryIds?: string[]; category?: string;
    hintType?: string; source?: string; active?: boolean;
    difficultyMin?: number; difficultyMax?: number; sourceYear?: number;
    hasImage?: boolean; sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<HintAdminDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<HintAdminDto>>('/admin/hints', { params })
    return data
  },

  getHintCountries: async (search?: string): Promise<{ id: string; isoAlpha2: string; nameEn: string }[]> => {
    const { data } = await apiClient.get('/admin/hints/countries', { params: search ? { search } : undefined })
    return data
  },

  getHint: async (id: string): Promise<HintAdminDto> => {
    const { data } = await apiClient.get<HintAdminDto>(`/admin/hints/${id}`)
    return data
  },

  updateHint: async (id: string, updates: Partial<HintAdminDto>): Promise<HintAdminDto> => {
    const { data } = await apiClient.put<HintAdminDto>(`/admin/hints/${id}`, updates)
    return data
  },

  toggleHintActive: async (id: string, active: boolean): Promise<HintAdminDto> => {
    const { data } = await apiClient.patch<HintAdminDto>(`/admin/hints/${id}/active`, null, {
      params: { active },
    })
    return data
  },

  deleteHint: async (id: string): Promise<void> => {
    await apiClient.delete(`/admin/hints/${id}`)
  },

  getHintCategories: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/hints/categories')
    return data
  },

  getHintTypes: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/hints/types')
    return data
  },

  getHintSources: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/hints/sources')
    return data
  },

  getHintStats: async (): Promise<HintStatsResponse> => {
    const { data } = await apiClient.get<HintStatsResponse>('/admin/hints/stats')
    return data
  },

  invalidateHintCache: async (): Promise<{ evicted: number; status: string }> => {
    const { data } = await apiClient.post<{ evicted: number; status: string }>('/admin/hints/cache/invalidate')
    return data
  },

  deleteHintImage: async (id: string): Promise<HintAdminDto> => {
    const { data } = await apiClient.delete<HintAdminDto>(`/admin/hints/${id}/image`)
    return data
  },

  replaceHintImage: async (id: string, fileTitle: string): Promise<HintAdminDto> => {
    const { data } = await apiClient.put<HintAdminDto>(`/admin/hints/${id}/image`, { fileTitle })
    return data
  },

  getHintSourceYears: async (): Promise<number[]> => {
    const { data } = await apiClient.get<number[]>('/admin/hints/source-years')
    return data
  },

  // Statistics

  getStatistics: async (params: {
    page?: number; size?: number; search?: string; countryId?: string;
    category?: string; source?: string; indicatorCode?: string;
    isCurrent?: boolean; year?: number;
    sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<CountryStatisticAdminDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<CountryStatisticAdminDto>>('/admin/statistics', { params })
    return data
  },

  getStatistic: async (id: string): Promise<CountryStatisticAdminDto> => {
    const { data } = await apiClient.get<CountryStatisticAdminDto>(`/admin/statistics/${id}`)
    return data
  },

  updateStatistic: async (id: string, updates: Record<string, unknown>): Promise<CountryStatisticAdminDto> => {
    const { data } = await apiClient.put<CountryStatisticAdminDto>(`/admin/statistics/${id}`, updates)
    return data
  },

  getStatCategories: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/statistics/categories')
    return data
  },

  getStatSources: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/statistics/sources')
    return data
  },

  getStatIndicators: async (category: string): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>(`/admin/statistics/categories/${category}/indicators`)
    return data
  },

  getStatYears: async (): Promise<number[]> => {
    const { data } = await apiClient.get<number[]>('/admin/statistics/years')
    return data
  },

  getStatCountries: async (search?: string): Promise<StatCountryOption[]> => {
    const { data } = await apiClient.get<StatCountryOption[]>('/admin/statistics/countries', {
      params: search ? { search } : undefined,
    })
    return data
  },

  // Reports

  getReports: async (params: {
    page?: number; size?: number; status?: string; excludeStatus?: string;
    hintId?: number; sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<HintReportAdminDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<HintReportAdminDto>>('/admin/reports', { params })
    return data
  },

  getReport: async (id: string): Promise<HintReportAdminDto> => {
    const { data } = await apiClient.get<HintReportAdminDto>(`/admin/reports/${id}`)
    return data
  },

  resolveReport: async (id: string, request: ResolveReportRequest): Promise<HintReportAdminDto> => {
    const { data } = await apiClient.put<HintReportAdminDto>(`/admin/reports/${id}`, request)
    return data
  },

  countPendingForHint: async (hintId: string): Promise<number> => {
    const { data } = await apiClient.get<{ count: number }>('/admin/reports/count-pending', { params: { hintId } })
    return data.count
  },

  // Analytics

  getAnalyticsSummary: async (startDate?: string, endDate?: string): Promise<AnalyticsSummaryDto> => {
    const { data } = await apiClient.get<AnalyticsSummaryDto>('/admin/analytics/summary', {
      params: { startDate, endDate },
    })
    return data
  },

  getAnalyticsByLevel: async (startDate?: string, endDate?: string): Promise<SessionsByLevelDto[]> => {
    const { data } = await apiClient.get<SessionsByLevelDto[]>('/admin/analytics/by-level', {
      params: { startDate, endDate },
    })
    return data
  },

  getAnalyticsTopCountries: async (startDate?: string, endDate?: string, limit = 10): Promise<TopCountryDto[]> => {
    const { data } = await apiClient.get<TopCountryDto[]>('/admin/analytics/top-countries', {
      params: { startDate, endDate, limit },
    })
    return data
  },

  getAnalyticsHardestCountries: async (startDate?: string, endDate?: string, limit = 10): Promise<TopCountryDto[]> => {
    const { data } = await apiClient.get<TopCountryDto[]>('/admin/analytics/hardest-countries', {
      params: { startDate, endDate, limit },
    })
    return data
  },

  getAnalyticsDailySessions: async (startDate?: string, endDate?: string): Promise<DailySessionDto[]> => {
    const { data } = await apiClient.get<DailySessionDto[]>('/admin/analytics/daily', {
      params: { startDate, endDate },
    })
    return data
  },

  aggregateAnalytics: async (startDate?: string, endDate?: string): Promise<{ aggregatedDays: number; status: string }> => {
    const { data } = await apiClient.post<{ aggregatedDays: number; status: string }>('/admin/analytics/aggregate', null, {
      params: { startDate, endDate },
    })
    return data
  },

  // Logs

  getOperationLogs: async (params: {
    page?: number; size?: number; operationType?: string; status?: string;
    triggerType?: string; startAfter?: string; startBefore?: string;
    sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<OperationBatchLogDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<OperationBatchLogDto>>('/admin/logs/operations', { params })
    return data
  },

  getOperationTypes: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/logs/operations/types')
    return data
  },

  getOperationStatuses: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/logs/operations/statuses')
    return data
  },

  getOperationTriggerTypes: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/logs/operations/trigger-types')
    return data
  },

  getSyncLogs: async (params: {
    page?: number; size?: number; source?: string; status?: string;
    syncType?: string; countryCode?: string; startAfter?: string; startBefore?: string;
    sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<DataSyncLogDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<DataSyncLogDto>>('/admin/logs/sync', { params })
    return data
  },

  getSyncSources: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/logs/sync/sources')
    return data
  },

  getSyncStatuses: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/logs/sync/statuses')
    return data
  },

  getSyncTypes: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/logs/sync/types')
    return data
  },

  getHintLogs: async (params: {
    page?: number; size?: number; countryCode?: string; triggerType?: string;
    startAfter?: string; startBefore?: string; sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<HintGenerationLogDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<HintGenerationLogDto>>('/admin/logs/hints', { params })
    return data
  },

  getHintTriggerTypes: async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/logs/hints/trigger-types')
    return data
  },

  getGroupedErrors: async (logType: string, limit = 20): Promise<GroupedError[]> => {
    const { data } = await apiClient.get<GroupedError[]>('/admin/logs/grouped-errors', { params: { logType, limit } })
    return data
  },

  // Settings

  getSettings: async (): Promise<AppSettings> => {
    const { data } = await apiClient.get<AppSettings>('/admin/settings')
    return data
  },

  updateSettings: async (settings: Partial<AppSettings>): Promise<AppSettings> => {
    const { data } = await apiClient.put<AppSettings>('/admin/settings', settings)
    return data
  },

  getSettingsAudit: async (params: {
    page?: number
    size?: number
  } = {}): Promise<PaginatedResponse<SettingsAuditEntry>> => {
    const { data } = await apiClient.get<PaginatedResponse<SettingsAuditEntry>>('/admin/settings/audit', { params })
    return data
  },

  // Badges

  getBadges: async (params: {
    page?: number; size?: number; search?: string; grantType?: string;
    isSecret?: boolean; isActive?: boolean; sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<BadgeAdminDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<BadgeAdminDto>>('/admin/badges', { params })
    return data
  },

  getBadge: async (id: string): Promise<BadgeAdminDto> => {
    const { data } = await apiClient.get<BadgeAdminDto>(`/admin/badges/${id}`)
    return data
  },

  createBadge: async (body: SaveBadgeRequest): Promise<BadgeAdminDto> => {
    const { data } = await apiClient.post<BadgeAdminDto>('/admin/badges', body)
    return data
  },

  updateBadge: async (id: string, body: SaveBadgeRequest): Promise<BadgeAdminDto> => {
    const { data } = await apiClient.put<BadgeAdminDto>(`/admin/badges/${id}`, body)
    return data
  },

  deleteBadge: async (id: string): Promise<void> => {
    await apiClient.delete(`/admin/badges/${id}`)
  },

  uploadBadgeImage: async (id: string, file: File): Promise<{ imageUrl: string }> => {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post<{ imageUrl: string }>(`/admin/badges/${id}/image`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  deleteBadgeImage: async (id: string): Promise<void> => {
    await apiClient.delete(`/admin/badges/${id}/image`)
  },

  evaluateAllBadges: async (): Promise<{ status: string }> => {
    const { data } = await apiClient.post<{ status: string }>('/admin/badges/evaluate')
    return data
  },

  evaluateBadge: async (id: string): Promise<{ status: string }> => {
    const { data } = await apiClient.post<{ status: string }>(`/admin/badges/${id}/evaluate`)
    return data
  },

  getBadgeMetadata: async (): Promise<BadgeMetadataResponse> => {
    const { data } = await apiClient.get<BadgeMetadataResponse>('/admin/badges/metadata')
    return data
  },

  getBadgeEvalLog: async (params: {
    badgeId?: string; page?: number; size?: number
  } = {}): Promise<PaginatedResponse<BadgeEvalLogDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<BadgeEvalLogDto>>('/admin/badges/eval-log', { params })
    return data
  },

  reorderBadges: async (orderedIds: string[]): Promise<void> => {
    await apiClient.patch('/admin/badges/reorder', { orderedIds })
  },

  // Badge Categories

  getCategories: async (): Promise<BadgeCategoryDto[]> => {
    const { data } = await apiClient.get<BadgeCategoryDto[]>('/admin/badge-categories')
    return data
  },

  createCategory: async (name: string): Promise<BadgeCategoryDto> => {
    const { data } = await apiClient.post<BadgeCategoryDto>('/admin/badge-categories', { name })
    return data
  },

  renameCategory: async (id: string, name: string): Promise<BadgeCategoryDto> => {
    const { data } = await apiClient.patch<BadgeCategoryDto>(`/admin/badge-categories/${id}`, { name })
    return data
  },

  deleteCategory: async (id: string): Promise<void> => {
    await apiClient.delete(`/admin/badge-categories/${id}`)
  },

  reorderCategories: async (orderedIds: string[]): Promise<void> => {
    await apiClient.patch('/admin/badge-categories/reorder', { orderedIds })
  },

  // User Badges

  getUserBadges: async (userId: string): Promise<UserBadgeAdminDto[]> => {
    const { data } = await apiClient.get<UserBadgeAdminDto[]>(`/admin/users/${userId}/badges`)
    return data
  },

  grantUserBadge: async (userId: string, badgeId: string): Promise<void> => {
    await apiClient.post(`/admin/users/${userId}/badges`, { badgeId })
  },

  revokeUserBadge: async (userId: string, badgeId: string): Promise<void> => {
    await apiClient.delete(`/admin/users/${userId}/badges/${badgeId}`)
  },

  // Console Logs

  getConsoleLogs: async (service: ConsoleService = 'questions'): Promise<ConsoleLogEntry[]> => {
    const { data } = await apiClient.get<ConsoleLogEntry[]>('/admin/console/logs', {
      params: { service: service.toUpperCase() },
    })
    return data
  },

  getConsoleState: async (service: ConsoleService = 'questions'): Promise<ConsoleState> => {
    const { data } = await apiClient.get<ConsoleState>('/admin/console/state', {
      params: { service: service.toUpperCase() },
    })
    return data
  },

  clearConsoleLogs: async (service: ConsoleService = 'questions'): Promise<void> => {
    await apiClient.delete('/admin/console/logs', {
      params: { service: service.toUpperCase() },
    })
  },

  clearOperationStatus: async (service: ConsoleService = 'questions'): Promise<void> => {
    await apiClient.delete('/admin/console/operation', {
      params: { service: service.toUpperCase() },
    })
  },

  // Indicators

  getIndicatorConfigs: async (params: {
    kind?: 'regular' | 'combo';
    page?: number; size?: number; search?: string; category?: string;
    profileLevel?: string; familyType?: string; isActive?: boolean; sortBy?: string; sortDir?: string
  } = {}): Promise<PaginatedResponse<IndicatorConfigAdminDto>> => {
    const { data } = await apiClient.get<PaginatedResponse<IndicatorConfigAdminDto>>('/admin/indicators', { params })
    return data
  },

  getIndicatorCategories: async (kind: 'regular' | 'combo' = 'regular'): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/indicators/categories', { params: { kind } })
    return data
  },

  getIndicatorProfileLevels: async (kind: 'regular' | 'combo' = 'regular'): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/indicators/profile-levels', { params: { kind } })
    return data
  },

  invalidateIndicatorCache: async (): Promise<{ evicted: number; status: string }> => {
    const { data } = await apiClient.post<{ evicted: number; status: string }>('/admin/indicators/cache/invalidate')
    return data
  },
}

export interface ConsoleLogEntry {
  text: string
  type: string
  timestamp: number
}

export type ConsoleService = 'questions' | 'budget'

export interface OperationStatus {
  id: string
  name: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  startedAt: number
  completedAt: number | null
  error: string | null
}

export interface ConsoleState {
  operation: OperationStatus | null
  logs: ConsoleLogEntry[]
}
