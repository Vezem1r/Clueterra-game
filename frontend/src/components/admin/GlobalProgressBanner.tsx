import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Loader2, CheckCircle, XCircle, X } from 'lucide-react'
import { useOperationStore } from '@/store/operationStore'
import { useOperationPolling } from '@/hooks/useOperationPolling'
import { cn } from '@/utils/cn'

export function GlobalProgressBanner() {
  const navigate = useNavigate()
  const { dismiss, isDismissed } = useOperationStore()
  const questions = useOperationPolling('questions')
  const budget = useOperationPolling('budget')
  const active = [questions, budget]
    .filter(state => state.isPolling && state.operation && !isDismissed(state.operation.id))
    .sort((left, right) =>
      (right.operation?.startedAt ?? 0) - (left.operation?.startedAt ?? 0)
    )[0]
  const consoleState = active?.consoleState
  const operation = active?.operation ?? null

  const logs = consoleState?.logs ?? []
  const latestEntry = logs.length > 0 ? logs[logs.length - 1] : null

  if (!active || !operation) return null

  const isComplete = operation.status === 'COMPLETED'
  const isFailed = operation.status === 'FAILED'
  const isDone = isComplete || isFailed

  const handleDismiss = (e: React.MouseEvent) => {
    e.stopPropagation()
    dismiss(operation.id)
    active.stopPolling()
  }

  return (
    <AnimatePresence>
      <motion.div
        initial={{ y: -48, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        exit={{ y: -48, opacity: 0 }}
        transition={{ type: 'spring', damping: 25, stiffness: 300 }}
        onClick={() => navigate('/admin/operations')}
        className={cn(
          'flex items-center gap-3 px-4 py-2.5 border-b cursor-pointer transition-colors',
          isDone
            ? isComplete
              ? 'bg-emerald-500/[.06] border-emerald-500/10'
              : 'bg-red-500/[.06] border-red-500/10'
            : 'bg-violet-500/[.06] border-violet-500/10'
        )}
      >
        {isDone ? (
          isComplete
            ? <CheckCircle size={15} className="text-emerald-400 shrink-0" />
            : <XCircle size={15} className="text-red-400 shrink-0" />
        ) : (
          <Loader2 size={15} className="text-violet-400 animate-spin shrink-0" />
        )}

        <span className="text-xs text-white/70 font-medium truncate">{operation.name}</span>

        {!isDone && (
          <div className="flex-1 max-w-[200px] h-1.5 bg-white/[.06] rounded-full overflow-hidden">
            <motion.div
              className="h-full bg-violet-500 rounded-full"
              animate={{ width: ['0%', '100%'] }}
              transition={{ duration: 2, repeat: Infinity, ease: 'easeInOut' }}
            />
          </div>
        )}

        {!isDone && latestEntry && (
          <span className="text-[11px] text-white/25 truncate max-w-[200px]">{latestEntry.text}</span>
        )}

        {isDone && (
          <span className={cn('text-xs', isComplete ? 'text-emerald-400/70' : 'text-red-400/70')}>
            {isComplete ? 'Completed' : `Failed${operation.error ? ': ' + operation.error : ''}`}
          </span>
        )}

        <button
          onClick={handleDismiss}
          className="ml-auto text-white/20 hover:text-white/50 transition-colors shrink-0 p-1"
          title="Dismiss"
        >
          <X size={14} />
        </button>
      </motion.div>
    </AnimatePresence>
  )
}
