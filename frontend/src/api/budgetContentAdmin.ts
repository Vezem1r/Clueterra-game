import { apiClient } from './client'
import type { BudgetQuestionPrompt, BudgetQuestionType, BudgetReveal } from './budget'

export type BudgetContentTab = 'REVIEW' | 'APPROVED' | 'REJECTED'
export type BudgetContentReviewStatus =
  | 'NEEDS_REVIEW'
  | 'NEEDS_IMAGE'
  | 'EDITORIAL_APPROVED'
  | 'APPROVED'
  | 'DISCOVERED'
  | 'REJECTED'
export type BudgetContentIssue =
  | 'DUPLICATE_NAME'
  | 'DUPLICATE_IMAGE'
  | 'SUSPICIOUS_NAME'
  | 'MISSING_DESCRIPTION'
  | 'LOW_QUALITY'
  | 'COST_OUTLIER'
  | 'WEAK_COST_EVIDENCE'
  | 'COST_REVIEW_REQUIRED'

export interface BudgetContentImage {
  url: string | null
  originalUrl: string
  sourcePageUrl: string
  fileTitle: string
  authorName: string
  credit: string | null
  licenseLabel: string
  licenseUrl: string
  description: string | null
  mimeType: string
  sourceBytes: number
  width: number
  height: number
  qualityScore: number
}

export interface BudgetContentCost {
  questionId: number | null
  answerCostUsd: number
  sourceAmount: number
  sourceCurrency: string
  costYear: number | null
  normalizationNote: string
  sourceName: string
  sourceUrl: string
  referenceUrl: string | null
  snapshotAt: string
}

export interface BudgetContentCandidate {
  wikidataId: string
  category: string
  name: string
  description: string | null
  countryCode: string
  countryName: string
  regionCode: string
  projectYear: number | null
  difficulty: string
  sitelinkCount: number
  monthlyPageviews: number
  interestScore: number
  qualityScore: number
  sourceRank: number
  qualityStatus: 'AUTO_APPROVED' | 'NEEDS_REVIEW'
  evidenceTier: string
  costConfidenceScore: number
  qualityFlags: string[]
  sourceUrl: string
  reviewStatus: BudgetContentReviewStatus
  reviewNote: string | null
  reviewedBy: string | null
  reviewedAt: string | null
  fetchedAt: string
  duplicateNameCount: number
  duplicateImageCount: number
  issues: BudgetContentIssue[]
  image: BudgetContentImage
  cost: BudgetContentCost | null
  descriptionSource: BudgetContentDescriptionSourceInput | null
  costClaims: BudgetContentCostClaimInput[]
  metrics: BudgetContentMetricInput[]
  milestones: BudgetContentMilestoneInput[]
}

export interface BudgetContentPage {
  content: BudgetContentCandidate[]
  nextCursor: string | null
  hasMore: boolean
}

export interface BudgetFacetCount {
  key: string
  label: string
  count: number
}

export interface BudgetContentFacets {
  total: number
  statuses: BudgetFacetCount[]
  categories: BudgetFacetCount[]
  countries: BudgetFacetCount[]
  difficulties: BudgetFacetCount[]
  regions: BudgetFacetCount[]
  issues: BudgetFacetCount[]
  questionStatuses: BudgetFacetCount[]
  challengeStatuses: BudgetFacetCount[]
}

export interface BudgetContentDescriptionSourceInput {
  name: string
  url: string
  licenseLabel: string
  licenseUrl: string
}

export type BudgetContentSourceCode =
  | ''
  | 'WIKIDATA'
  | 'WIKIPEDIA'
  | 'NASA'
  | 'FTA'
  | 'GAO'
  | 'CLUETERRA_RESEARCH'

export interface BudgetContentCostClaimInput {
  costKind: string
  sourceAmountLow: number
  sourceAmountHigh: number
  sourceCurrency: string
  amountUsdLow: number
  amountUsdHigh: number
  costYear: number | null
  unitsPerUsd: number
  exchangeRateDate: string
  exchangeRateBasis: string
  exchangeRateProvider: string
  sourceCode: BudgetContentSourceCode
  sourceName: string
  sourceUrl: string
  referenceUrl: string
  normalizationNote: string
  scopeNote: string
  sourceKey: string
  evidenceText: string
  verificationStatus: string
  primary: boolean
  answer: boolean
}

export interface BudgetContentMetricInput {
  metricType: string
  normalizedValue: number
  normalizedUnit: string
  sourceValue: number
  sourceUnit: string
  sourceCode: BudgetContentSourceCode
  sourceName: string
  sourceUrl: string
  referenceUrl: string
  sourceKey: string
  evidenceText: string
  primary: boolean
}

export interface BudgetContentMilestoneInput {
  milestoneType: string
  milestoneDate: string | null
  milestoneYear: number | null
  datePrecision: 'DAY' | 'YEAR'
  sourceCode: BudgetContentSourceCode
  sourceName: string
  sourceUrl: string
  referenceUrl: string
  sourceKey: string
  evidenceText: string
  primary: boolean
}

export type BudgetProjectSeoStatus = 'DRAFT' | 'PUBLISHED'

export interface BudgetProjectSeoReferenceInput {
  name: string
  url: string
  claim: string
}

export interface BudgetProjectSeoFactInput {
  label: string
  value: string
  sourceName: string
  sourceUrl: string
}

export interface BudgetProjectSeoPage {
  wikidataId: string
  projectName: string | null
  countryName: string | null
  category: string | null
  projectAvailable: boolean
  imageAvailable: boolean
  slug: string | null
  costQuestion: string
  seoTitle: string
  metaDescription: string
  keywords: string
  intro: string
  answerDetail: string
  scopeText: string
  contextText: string
  methodologyText: string
  sourceName: string
  sourceUrl: string
  references: BudgetProjectSeoReferenceInput[]
  facts: BudgetProjectSeoFactInput[]
  publicationStatus: BudgetProjectSeoStatus
  publishedAt: string | null
  updatedBy: string
  updatedAt: string
}

export interface BudgetProjectSeoPageInput {
  slug: string
  costQuestion: string
  seoTitle: string
  metaDescription: string
  keywords: string
  intro: string
  answerDetail: string
  scopeText: string
  contextText: string
  methodologyText: string
  sourceName: string
  sourceUrl: string
  references: BudgetProjectSeoReferenceInput[]
  facts: BudgetProjectSeoFactInput[]
  publicationStatus: BudgetProjectSeoStatus
}

export interface BudgetProjectSeoPageResult {
  content: BudgetProjectSeoPage[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type BudgetProjectSeoCandidateStatus = 'MISSING' | BudgetProjectSeoStatus

export interface BudgetProjectSeoCandidate {
  wikidataId: string
  projectName: string
  countryName: string
  category: string
  projectYear: number | null
  answerCostUsd: number
  sourceCurrency: string
  contentQualityStatus: string
  costEvidenceTier: string
  costConfidenceScore: number
  imageAvailable: boolean
  primaryCostAvailable: boolean
  factCount: number
  publicationStatus: BudgetProjectSeoStatus | null
  slug: string | null
}

export interface BudgetProjectSeoCandidateResult {
  content: BudgetProjectSeoCandidate[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface BudgetProjectSeoDraft {
  slug: string
  costQuestion: string
  seoTitle: string
  metaDescription: string
  keywords: string
  intro: string
  answerDetail: string
  scopeText: string
  contextText: string
  methodologyText: string
  sourceName: string
  sourceUrl: string
  references: BudgetProjectSeoReferenceInput[]
  facts: BudgetProjectSeoFactInput[]
}

export interface BudgetProjectSeoValidationIssue {
  code: string
  severity: 'ERROR' | 'WARNING'
  field: string
  message: string
}

export interface BudgetProjectSeoValidationResult {
  publishable: boolean
  errorCount: number
  warningCount: number
  issues: BudgetProjectSeoValidationIssue[]
}

export interface BudgetGeneratedQuestion {
  id: number
  questionType: BudgetQuestionType
  generationKey: string
  difficulty: string
  active: boolean
  projectName: string
  countryName: string
  category: string
  comparisonProjectName: string | null
  answerValue: number | null
  answerUnit: string | null
  answerChoice: string | null
  prompt: BudgetQuestionPrompt
  reveal: BudgetReveal
  generatedAt: string
  retiredAt: string | null
}

export interface BudgetGeneratedChallengeQuestion {
  position: number
  questionId: number
  questionType: BudgetQuestionType
  difficulty: string
  projectName: string
  comparisonProjectName: string | null
  active: boolean
  answerValue: number | null
  answerUnit: string | null
  answerChoice: string | null
  prompt: BudgetQuestionPrompt
  reveal: BudgetReveal
}

export interface BudgetGeneratedChallenge {
  id: number
  challengeDate: string
  generationTrigger: string
  status: string
  publishedAt: string
  deletable: boolean
  questions: BudgetGeneratedChallengeQuestion[]
}

export type BudgetChallengePeriod = 'UPCOMING' | 'PAST'

export interface BudgetPage<T> {
  content: T[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export interface BudgetContentUpdateInput {
  name: string
  description: string
  countryCode: string
  countryName: string
  regionCode: string
  projectYear: number | null
  difficulty: string
  imageFileTitle: string
  descriptionSource: BudgetContentDescriptionSourceInput | null
  costClaims: BudgetContentCostClaimInput[]
  metrics: BudgetContentMetricInput[]
  milestones: BudgetContentMilestoneInput[]
}

export interface BudgetContentFilters {
  tab: BudgetContentTab
  category?: string
  countryCode?: string
  difficulty?: string
  search?: string
  minimumQuality?: number
  issue?: BudgetContentIssue
}

export interface BudgetCandidateKey {
  wikidataId: string
  category: string
}

export const budgetContentAdminApi = {
  getCandidates: async (filters: BudgetContentFilters, cursor?: string) => {
    const { data } = await apiClient.get<BudgetContentPage>('/admin/budget-content/candidates', {
      params: { ...filters, cursor, size: 30 },
    })
    return data
  },

  getCandidate: async (candidate: BudgetCandidateKey) => {
    const { data } = await apiClient.get<BudgetContentCandidate>(
      `/admin/budget-content/candidates/${candidate.wikidataId}`,
      { params: { category: candidate.category } },
    )
    return data
  },

  getFacets: async (tab: BudgetContentTab) => {
    const { data } = await apiClient.get<BudgetContentFacets>('/admin/budget-content/facets', {
      params: { tab },
    })
    return data
  },

  updateCandidate: async (candidate: BudgetCandidateKey, content: BudgetContentUpdateInput) => {
    const { data } = await apiClient.patch<{ updated: boolean; publishedQuestionId: number | null }>(
      `/admin/budget-content/candidates/${candidate.wikidataId}`,
      content,
      { params: { category: candidate.category } },
    )
    return data
  },

  getQuestions: async (params: {
    status: string
    questionType?: string
    search?: string
    page: number
    size: number
  }) => {
    const { data } = await apiClient.get<BudgetPage<BudgetGeneratedQuestion>>(
      '/admin/budget-content/questions',
      { params },
    )
    return data
  },

  getChallenges: async (period: BudgetChallengePeriod, page: number) => {
    const { data } = await apiClient.get<BudgetPage<BudgetGeneratedChallenge>>(
      '/admin/budget-content/challenges',
      { params: { period, page, size: 10 } },
    )
    return data
  },

  deleteChallenge: async (challengeId: number) => {
    await apiClient.delete(`/admin/budget-content/challenges/${challengeId}`)
  },

  reviewSelected: async (
    candidates: BudgetCandidateKey[],
    status: BudgetContentReviewStatus,
  ) => {
    const { data } = await apiClient.patch<{ updatedCount: number }>(
      '/admin/budget-content/candidates/review',
      { candidates, status },
    )
    return data
  },

  getSeoPages: async ({
    page = 0,
    size = 24,
    search,
    publicationStatus,
  }: {
    page?: number
    size?: number
    search?: string
    publicationStatus?: BudgetProjectSeoStatus
  } = {}) => {
    const { data } = await apiClient.get<BudgetProjectSeoPageResult>('/admin/budget-content/seo-pages', {
      params: { page, size, search, publicationStatus },
    })
    return data
  },

  getSeoCandidates: async ({
    page = 0,
    size = 20,
    search,
    pageStatus = 'MISSING',
  }: {
    page?: number
    size?: number
    search?: string
    pageStatus?: BudgetProjectSeoCandidateStatus | ''
  } = {}) => {
    const { data } = await apiClient.get<BudgetProjectSeoCandidateResult>(
      '/admin/budget-content/seo-pages/candidates',
      { params: { page, size, search, pageStatus } },
    )
    return data
  },

  getSeoDraftSuggestion: async (wikidataId: string) => {
    const { data } = await apiClient.get<BudgetProjectSeoDraft>(
      `/admin/budget-content/seo-pages/candidates/${wikidataId}/suggestion`,
    )
    return data
  },

  validateSeoPage: async (
    wikidataId: string,
    content: BudgetProjectSeoPageInput,
  ) => {
    const { data } = await apiClient.post<BudgetProjectSeoValidationResult>(
      `/admin/budget-content/seo-pages/${wikidataId}/validate`,
      content,
    )
    return data
  },

  saveSeoPage: async (
    wikidataId: string,
    content: BudgetProjectSeoPageInput,
  ) => {
    const { data } = await apiClient.put<BudgetProjectSeoPage>(
      `/admin/budget-content/seo-pages/${wikidataId}`,
      content,
    )
    return data
  },
}
