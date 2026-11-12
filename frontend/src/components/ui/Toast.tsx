import { motion, AnimatePresence } from 'framer-motion'
import { X, CheckCircle2, AlertTriangle, AlertCircle, Info } from 'lucide-react'
import { useToastStore, type ToastType } from '@/store/toastStore'
import { cn } from '@/utils/cn'

const icons: Record<ToastType, typeof CheckCircle2> = {
  success: CheckCircle2,
  error: AlertCircle,
  warning: AlertTriangle,
  info: Info,
}

const styles: Record<ToastType, string> = {
  success: 'border-success/25 bg-success/[.06]',
  error: 'border-error/25 bg-error/[.06]',
  warning: 'border-warning/25 bg-warning/[.06]',
  info: 'border-accent/25 bg-accent/[.06]',
}

const iconColors: Record<ToastType, string> = {
  success: 'text-success',
  error: 'text-error',
  warning: 'text-warning',
  info: 'text-accent',
}

export function ToastContainer() {
  const { toasts, removeToast } = useToastStore()

  return (
    <div className="fixed bottom-6 right-6 z-[100] flex flex-col gap-2.5 max-w-sm w-full pointer-events-none">
      <AnimatePresence mode="popLayout">
        {toasts.map((t) => {
          const Icon = icons[t.type]
          return (
            <motion.div
              key={t.id}
              layout
              initial={{ opacity: 0, y: 20, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, x: 80, scale: 0.95 }}
              transition={{ type: 'spring', stiffness: 400, damping: 30 }}
              className={cn(
                'pointer-events-auto flex items-start gap-3 p-4 rounded-xl border backdrop-blur-xl shadow-2xl',
                'bg-[#0c0c14]/90',
                styles[t.type]
              )}
            >
              <Icon size={18} className={cn('shrink-0 mt-0.5', iconColors[t.type])} />
              <p className="flex-1 text-sm text-white/80 leading-relaxed">{t.message}</p>
              <button
                onClick={() => removeToast(t.id)}
                className="shrink-0 p-0.5 text-white/25 hover:text-white/50 transition-colors cursor-pointer"
              >
                <X size={14} />
              </button>
            </motion.div>
          )
        })}
      </AnimatePresence>
    </div>
  )
}
