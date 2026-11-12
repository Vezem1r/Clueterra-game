import { apiClient } from './client'

export type BudgetDifficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type BudgetCategory =
  | 'BRIDGE'
  | 'METRO_RAIL'
  | 'STADIUM'
  | 'SKYSCRAPER'
  | 'AIRPORT'
  | 'TUNNEL'
  | 'DAM'
  | 'ENERGY'
  | 'SPACE'
  | 'ROAD_HIGHWAY'
  | 'PORT'
export type BudgetQuestionType =
  | 'HOW_MUCH'
  | 'ORIGINAL_BUDGET'
  | 'BUDGET_OVERRUN'
  | 'HOW_LATE'
  | 'HIGHER_LOWER'
  | 'COST_PER_UNIT'
  | 'CURRENT_ESTIMATE'
  | 'SPENT_SO_FAR'
export type BudgetProjectLifecycleStatus =
  | 'UNKNOWN'
  | 'PLANNED'
  | 'UNDER_CONSTRUCTION'
  | 'ON_HOLD'
  | 'CANCELLED'
  | 'ABANDONED'
  | 'COMPLETED'
export type BudgetAnswerChoice = 'PRIMARY' | 'COMPARISON'
export type BudgetGameSessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED'
export type BudgetMetricType =
  | 'LENGTH'
  | 'HEIGHT'
  | 'FLOOR_AREA'
  | 'CAPACITY'
  | 'POWER'
  | 'ANNUAL_PASSENGERS'
  | 'ANNUAL_CARGO_TONNES'
  | 'CONTAINER_CAPACITY'
  | 'STORAGE_VOLUME'
  | 'MASS'

export interface BudgetImage {
  url: string
  altText: string | null
  authorName: string | null
  attributionText: string | null
  licenseLabel: string | null
  licenseUrl: string | null
  sourcePageUrl: string | null
}

export interface BudgetPromptProject {
  name: string
  countryCode: string
  countryName: string
  regionName?: string
  category: BudgetCategory
  lifecycleStatus?: BudgetProjectLifecycleStatus
  projectYear?: number
  completionYear?: number
  openingYear?: number
  expectedCompletionYear?: number
  answerCostKind?: string
}

export interface BudgetQuestionPrompt {
  project?: BudgetPromptProject
  primaryProject?: BudgetPromptProject
  comparisonProject?: BudgetPromptProject
  metric?: { type: BudgetMetricType; value: number; unit: string }
}

export interface BudgetRevealProject extends BudgetPromptProject {
  description?: string
  descriptionAttribution?: {
    sourceName?: string
    sourceUrl?: string
    licenseLabel?: string
    licenseUrl?: string
  }
}

export interface BudgetComparableHintPayload {
  type: 'COMPARABLE_PROJECT'
  multiplierPercent: number
  projectName: string
  value: number
  unit: string
  metricType?: string
  metricValue?: number
  metricUnit?: string
}

export interface BudgetAnswerRangeHintPayload {
  type: 'ANSWER_RANGE'
  multiplierPercent: number
  minimum: number
  maximum: number
  unit: string
}

export type BudgetHintPayload = BudgetComparableHintPayload | BudgetAnswerRangeHintPayload

export interface BudgetRevealedHint {
  index: number
  multiplierPercent: number
  payload: BudgetHintPayload
  revealedAt: string
}

export interface BudgetAnswerConversion {
  sourceAmountLow: number
  sourceAmountHigh: number
  sourceCurrency: string
  amountUsdLow: number
  amountUsdHigh: number
  costYear?: number
}

export interface BudgetFormulaMilestone {
  date?: string
  year?: number
}

export interface BudgetAnswerFormula {
  operation: 'DIVIDE' | 'PERCENT_INCREASE' | 'MONTHS_BETWEEN'
  numerator?: number
  numeratorUnit?: string
  denominator?: number
  denominatorUnit?: string
  original?: number
  final?: number
  unit?: string
  planned?: BudgetFormulaMilestone
  actual?: BudgetFormulaMilestone
  result?: number
  resultUnit?: string
}

export interface BudgetAnswerExplanation {
  conversion?: BudgetAnswerConversion
  formula?: BudgetAnswerFormula
}

export interface BudgetReveal {
  project?: BudgetRevealProject
  actualCost?: {
    amountUsd: number
    sourceName: string
    sourceUrl: string
    referenceUrl?: string
  }
  costs?: Array<{
    kind: string
    amountUsdLow: number
    amountUsdHigh: number
    year?: number
    sourceName: string
    sourceUrl: string
    referenceUrl?: string
  }>
  metrics?: Array<{
    type: string
    value: number
    unit: string
    sourceName: string
    sourceUrl: string
    referenceUrl?: string
  }>
  milestones?: Array<{
    type: string
    date?: string
    year?: number
    sourceName: string
    sourceUrl: string
    referenceUrl?: string
  }>
  budgetOverrunPercent?: number
  delayMonths?: number
  explanation?: BudgetAnswerExplanation
  hints?: BudgetHintPayload[]
  primary?: BudgetReveal
  comparison?: BudgetReveal
}

export interface BudgetChallengeQuestion {
  variantId: string
  position: number
  type: BudgetQuestionType
  difficulty: BudgetDifficulty
  prompt: BudgetQuestionPrompt
  availableHintCount: number
  revealedHints: BudgetRevealedHint[]
  primaryImage: BudgetImage | null
  comparisonImage: BudgetImage | null
}

export interface BudgetAnsweredQuestion {
  question: BudgetChallengeQuestion
  guessedValue: number | null
  guessedChoice: BudgetAnswerChoice | null
  correctValue: number | null
  correctUnit: string | null
  correctChoice: BudgetAnswerChoice | null
  accuracyPercent: number
  rawXpPercent: number
  rawXpEarned: number
  hintMultiplierPercent: number
  hintsUsed: number
  xpPercent: number
  xpEarned: number
  reveal: BudgetReveal
  answeredAt: string
}

export interface BudgetGameSession {
  id: string
  challengeDate: string
  status: BudgetGameSessionStatus
  guest: boolean
  currentPosition: number
  questionCount: number
  totalXpEarned: number
  streakBonusXp: number
  maximumXp: number
  fullXpThresholdPercent: number
  xpBefore: number
  levelBefore: number
  startedAt: string
  completedAt: string | null
  answeredQuestions: BudgetAnsweredQuestion[]
  currentQuestion: BudgetChallengeQuestion | null
}

export interface BudgetAnswerResult {
  replayed: boolean
  answer: BudgetAnsweredQuestion
  session: BudgetGameSession
}

export interface BudgetHintResult {
  replayed: boolean
  hint: BudgetRevealedHint
  effectiveMultiplierPercent: number
  session: BudgetGameSession
}

export interface BudgetTodayChallenge {
  date: string
  questionCount: number
  available: boolean
}

export const budgetApi = {
  getTodayChallenge: async (): Promise<BudgetTodayChallenge> => {
    const { data } = await apiClient.get<BudgetTodayChallenge>('/budget/challenges/today')
    return data
  },

  startGuestSession: async (
    termsVersion: string,
    privacyVersion: string,
  ): Promise<BudgetGameSession> => {
    const { data } = await apiClient.post<BudgetGameSession>('/budget/sessions/guest', {
      termsVersion,
      privacyVersion,
    })
    return data
  },

  startUserSession: async (): Promise<BudgetGameSession> => {
    const { data } = await apiClient.post<BudgetGameSession>('/budget/sessions')
    return data
  },

  getTodaySession: async (): Promise<BudgetGameSession | null> => {
    const { data, status } = await apiClient.get<BudgetGameSession | null>('/budget/sessions/today')
    return status === 204 || !data ? null : data
  },

  getSession: async (sessionId: string): Promise<BudgetGameSession> => {
    const { data } = await apiClient.get<BudgetGameSession>(`/budget/sessions/${sessionId}`)
    return data
  },

  submitAnswer: async (
    sessionId: string,
    variantId: string,
    answer: { value: number; choice?: never } | { choice: BudgetAnswerChoice; value?: never },
  ): Promise<BudgetAnswerResult> => {
    const { data } = await apiClient.post<BudgetAnswerResult>(
      `/budget/sessions/${sessionId}/answers`,
      { variantId, ...answer },
    )
    return data
  },

  revealHint: async (
    sessionId: string,
    variantId: string,
    hintIndex: number,
  ): Promise<BudgetHintResult> => {
    const { data } = await apiClient.post<BudgetHintResult>(
      `/budget/sessions/${sessionId}/hints`,
      { variantId, hintIndex },
    )
    return data
  },
}
