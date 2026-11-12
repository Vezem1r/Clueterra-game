import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/auth'

export function useRegistrationStatus() {
  return useQuery({
    queryKey: ['registration-status'],
    queryFn: authApi.getRegistrationStatus,
  })
}
