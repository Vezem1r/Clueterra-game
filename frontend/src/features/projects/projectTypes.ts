export interface ProjectCard {
  slug: string
  name: string
  country: string
  category: string
  year: number | null
  answer: string
  usdEquivalent: string | null
  description: string
  imageUrl: string
  imageAlt: string
  updatedDate: string
}

export interface ProjectDirectory {
  projects: ProjectCard[]
  pageNumber: number
  pageSize: number
  totalProjects: number
  totalPages: number
  countryHubs: ProjectHubLink[]
}

export const PROJECT_CATEGORIES = [
  'BRIDGE',
  'METRO_RAIL',
  'STADIUM',
  'SKYSCRAPER',
  'AIRPORT',
  'TUNNEL',
  'DAM',
  'ENERGY',
  'SPACE',
  'ROAD_HIGHWAY',
  'PORT',
] as const

export type ProjectCategory = typeof PROJECT_CATEGORIES[number]

export interface ProjectDirectoryFilters {
  search: string
  category: ProjectCategory | null
  minimumCostUsd: number | null
  maximumCostUsd: number | null
  minimumYear: number | null
  maximumYear: number | null
}

export function isProjectCategory(value: string | null): value is ProjectCategory {
  return PROJECT_CATEGORIES.some((category) => category === value)
}

export interface ProjectFact {
  label: string
  value: string
  sourceName: string
  sourceUrl: string
}

export interface ProjectCost {
  label: string
  amount: string
  usdEquivalent: string | null
  year: number | null
  featured: boolean
  scope: string | null
  sourceName: string
  sourceUrl: string
}

export interface ProjectMetric {
  label: string
  value: string
  sourceName: string
  sourceUrl: string
}

export interface ProjectMilestone {
  label: string
  value: string
  sourceName: string
  sourceUrl: string
}

export interface ProjectSource {
  name: string
  url: string
  claim: string
}

export interface ProjectImage {
  url: string
  alt: string
  author: string | null
  attribution: string | null
  license: string | null
  licenseUrl: string | null
  sourcePageUrl: string | null
}

export interface ProjectDetail {
  wikidataId: string
  slug: string | null
  seoTitle: string | null
  metaDescription: string | null
  keywords: string | null
  name: string
  costQuestion: string
  country: string
  category: string
  categoryHubPath: string | null
  countryHubPath: string | null
  year: number | null
  answer: string
  usdEquivalent: string | null
  costYear: number | null
  costType: string
  costScope: string | null
  status: string | null
  intro: string | null
  answerDetail: string | null
  scopeText: string | null
  contextText: string | null
  methodologyText: string | null
  sourceName: string
  sourceUrl: string
  image: ProjectImage
  costs: ProjectCost[]
  metrics: ProjectMetric[]
  timeline: ProjectMilestone[]
  facts: ProjectFact[]
  sources: ProjectSource[]
  updatedDate: string
}

export interface ProjectDetailPageData {
  project: ProjectDetail
  relatedProjects: ProjectCard[]
}

export type ProjectHubKind = 'ALL' | 'CATEGORY' | 'COUNTRY'

export interface ProjectHubLink {
  path: string
  kind: ProjectHubKind
  category: ProjectCategory | null
  countryCode: string | null
  countryName: string | null
  projectCount: number
}

export interface ProjectHubEntry {
  rank: number
  slug: string
  name: string
  country: string
  category: string
  year: number | null
  costUsd: string
  answer: string
  costType: string
  costScope: string | null
  estimate: boolean
  status: string | null
  rankingScope: ProjectRankingScope
  imageUrl: string
  imageAlt: string
}

export type ProjectRankingScope = 'WHOLE' | 'EXPANSION' | 'ACCESS_LINK'

export interface ProjectHubPage {
  kind: ProjectHubKind
  path: string
  category: ProjectCategory | null
  countryCode: string | null
  countryName: string | null
  totalProjects: number
  projects: ProjectHubEntry[]
  categoryHubs: ProjectHubLink[]
  countryHubs: ProjectHubLink[]
}

export type ProjectSsrData =
  | { kind: 'directory'; path: string; directory: ProjectDirectory }
  | { kind: 'hub'; path: string; hub: ProjectHubPage }
  | { kind: 'detail'; path: string; detail: ProjectDetailPageData; preview: boolean }
  | { kind: 'not-found'; path: string }
  | { kind: 'error'; path: string }
