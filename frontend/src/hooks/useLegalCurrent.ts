import { useQuery } from '@tanstack/react-query'
import { legalApi } from '@/api/legal'
import type { LegalCurrent } from '@/types'

export function useLegalCurrent() {
  return useQuery<LegalCurrent>({
    queryKey: ['legal-current'],
    queryFn: legalApi.getCurrent,
    staleTime: 0,
  })
}
