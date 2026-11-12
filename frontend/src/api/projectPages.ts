import { apiClient } from './client'
import type {
  ProjectDetailPageData,
  ProjectDirectory,
  ProjectDirectoryFilters,
  ProjectHubPage,
} from '@/features/projects/projectTypes'

const PROJECTS_PATH_PREFIX = '/projects/'

export const projectPagesApi = {
  async getDirectory(page: number, filters: ProjectDirectoryFilters): Promise<ProjectDirectory> {
    const response = await apiClient.get<ProjectDirectory>('/project-pages/data', {
      params: {
        page,
        q: filters.search || undefined,
        category: filters.category ?? undefined,
        minCost: filters.minimumCostUsd ?? undefined,
        maxCost: filters.maximumCostUsd ?? undefined,
        yearFrom: filters.minimumYear ?? undefined,
        yearTo: filters.maximumYear ?? undefined,
      },
    })
    return response.data
  },

  async getProject(slug: string): Promise<ProjectDetailPageData> {
    const response = await apiClient.get<ProjectDetailPageData>(
      `/project-pages/data/${encodeURIComponent(slug)}`,
    )
    return response.data
  },

  async getHub(pagePath: string): Promise<ProjectHubPage> {
    const hubPath = pagePath.slice(PROJECTS_PATH_PREFIX.length)
    const response = await apiClient.get<ProjectHubPage>(`/project-pages/hubs/${hubPath}`)
    return response.data
  },

  async getPreview(wikidataId: string): Promise<ProjectDetailPageData> {
    const response = await apiClient.get<ProjectDetailPageData>(
      `/admin/budget-content/seo-pages/${encodeURIComponent(wikidataId)}/preview-data`,
    )
    return response.data
  },
}
