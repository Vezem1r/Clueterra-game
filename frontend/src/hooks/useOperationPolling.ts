import { useCallback, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminApi, type ConsoleService } from '@/api/admin'
import { useOperationStore } from '@/store/operationStore'

const POLL_INTERVAL = 2000
const DONE_DELAY_MS = 4000

export function useOperationPolling(service: ConsoleService = 'questions') {
  const pollingStartedAt = useOperationStore(state => state.pollingStartedAt[service])
  const startServicePolling = useOperationStore(state => state.startPolling)
  const stopServicePolling = useOperationStore(state => state.stopPolling)
  const isStale = useOperationStore(state => state.isStale)
  const isPolling = pollingStartedAt !== null
  const startPolling = useCallback(
    () => startServicePolling(service),
    [service, startServicePolling]
  )
  const stopPolling = useCallback(
    () => stopServicePolling(service),
    [service, stopServicePolling]
  )
  const doneTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (isPolling && isStale(service)) stopPolling()
  }, [isPolling, isStale, service, stopPolling])

  const { data: consoleState } = useQuery({
    queryKey: ['admin-console-state', service],
    queryFn: () => adminApi.getConsoleState(service),
    refetchInterval: isPolling ? POLL_INTERVAL : false,
    refetchOnWindowFocus: false,
  })

  const operation = consoleState?.operation ?? null

  useEffect(() => {
    if (consoleState?.operation?.status === 'RUNNING' && !isPolling) {
      startPolling()
    }
  }, [consoleState, isPolling, startPolling])

  useEffect(() => {
    if (!isPolling || !consoleState) return

    if (doneTimerRef.current) {
      clearTimeout(doneTimerRef.current)
      doneTimerRef.current = null
    }

    const isDone = !operation || operation.status === 'COMPLETED' || operation.status === 'FAILED'
    if (isDone) {
      doneTimerRef.current = setTimeout(() => stopPolling(), DONE_DELAY_MS)
    }

    return () => {
      if (doneTimerRef.current) clearTimeout(doneTimerRef.current)
    }
  }, [isPolling, consoleState, operation, stopPolling])

  return { service, consoleState, operation, isPolling, startPolling, stopPolling }
}
