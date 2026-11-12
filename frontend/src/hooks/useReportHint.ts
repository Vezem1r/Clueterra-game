import { useMutation } from '@tanstack/react-query'
import { gameApi } from '@/api/game'
import { toast } from '@/store/toastStore'
import type { HintReportRequest } from '@/types'

export function useReportHint(onSuccess?: () => void) {
  return useMutation({
    mutationFn: (report: HintReportRequest) => gameApi.reportHint(report),
    onSuccess: () => {
      toast.success('Report submitted. Thank you!')
      onSuccess?.()
    },
    onError: () => {
      toast.error('Failed to submit report')
    },
  })
}
