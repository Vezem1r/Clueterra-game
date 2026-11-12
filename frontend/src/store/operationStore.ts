import { create } from 'zustand'
import { persist } from 'zustand/middleware'

import type { ConsoleService } from '@/api/admin'

const STALE_TIMEOUT_MS = 30 * 60 * 1000

interface OperationState {
  pollingStartedAt: Record<ConsoleService, number | null>
  dismissedOperationId: string | null
  startPolling: (service: ConsoleService) => void
  stopPolling: (service: ConsoleService) => void
  dismiss: (operationId: string) => void
  isDismissed: (operationId: string | null) => boolean
  isStale: (service: ConsoleService) => boolean
}

export const useOperationStore = create<OperationState>()(
  persist(
    (set, get) => ({
      pollingStartedAt: {
        questions: null,
        budget: null,
      },
      dismissedOperationId: null,
      startPolling: service => set(state => ({
        pollingStartedAt: {
          ...state.pollingStartedAt,
          [service]: Date.now(),
        },
      })),
      stopPolling: service => set(state => ({
        pollingStartedAt: {
          ...state.pollingStartedAt,
          [service]: null,
        },
      })),
      dismiss: (operationId) => set({
        dismissedOperationId: operationId,
      }),
      isDismissed: (operationId) => {
        if (!operationId) return false
        return get().dismissedOperationId === operationId
      },
      isStale: service => {
        const startedAt = get().pollingStartedAt[service]
        if (!startedAt) return false
        return Date.now() - startedAt > STALE_TIMEOUT_MS
      },
    }),
    { name: 'clueterra-operation-v2' }
  )
)
