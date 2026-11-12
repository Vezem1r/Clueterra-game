import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { X, RotateCcw } from 'lucide-react'

interface MobileFilterSheetProps {
  isOpen: boolean
  onClose: () => void
  title?: string
  activeCount: number
  onClear?: () => void
  children: ReactNode
}

export function MobileFilterSheet({ isOpen, onClose, title = 'Filters', activeCount, onClear, children }: MobileFilterSheetProps) {
  useEffect(() => {
    if (!isOpen) return
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleEscape)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', handleEscape)
      document.body.style.overflow = 'unset'
    }
  }, [isOpen, onClose])

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 30, stiffness: 340 }}
            className="relative z-10 flex w-full max-h-[70dvh] flex-col overflow-hidden rounded-t-2xl border-t border-x border-white/[.08] bg-[#0c0e18] shadow-[0_-8px_40px_rgba(0,0,0,.5)]"
          >
            <div className="flex justify-center pt-2.5">
              <div className="h-1 w-8 rounded-full bg-white/14" />
            </div>

            <div className="flex items-center justify-between px-5 pb-3 pt-2">
              <div className="flex items-center gap-2">
                <h3 className="text-sm font-semibold text-white/90">{title}</h3>
                {activeCount > 0 && (
                  <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-accent/20 px-1.5 text-[10px] font-bold text-accent">
                    {activeCount}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                {activeCount > 0 && onClear && (
                  <button
                    type="button"
                    onClick={onClear}
                    className="flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs text-white/40 transition-colors hover:text-white/70 cursor-pointer"
                  >
                    <RotateCcw size={11} />
                    Clear
                  </button>
                )}
                <button
                  type="button"
                  onClick={onClose}
                  className="flex h-7 w-7 items-center justify-center rounded-lg border border-white/[.06] bg-white/[.04] text-white/40 transition-colors hover:text-white/70 cursor-pointer"
                >
                  <X size={14} />
                </button>
              </div>
            </div>

            <div className="border-t border-white/[.06]" />

            <div className="flex-1 overflow-y-auto px-5 py-4 pb-[max(1.25rem,env(safe-area-inset-bottom))]">
              <div className="flex flex-col gap-3 [&>*]:w-full [&>*]:!shrink [&>div>button]:w-full [&>div>button]:justify-between [&_.truncate]:!max-w-none [&>div>button]:h-10 [&>div>button]:text-sm [&_input]:!h-10 [&_input]:!text-sm">
                {children}
              </div>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  )
}
