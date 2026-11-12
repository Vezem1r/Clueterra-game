import { apiClient } from './client'
import type { LegalCurrent, PublicDataSourcesResponse } from '@/types'

export const legalApi = {
  getCurrent: async (): Promise<LegalCurrent> => {
    const { data } = await apiClient.get<LegalCurrent>('/legal/current')
    return data
  },

  getDataSources: async (): Promise<PublicDataSourcesResponse> => {
    const { data } = await apiClient.get<PublicDataSourcesResponse>('/legal/data-sources')
    return data
  },

  acceptCurrent: async (
    termsVersion: string,
    privacyVersion: string,
  ): Promise<void> => {
    await apiClient.post('/legal/accept', { termsVersion, privacyVersion })
  },
}
