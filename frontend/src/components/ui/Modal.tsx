import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion'
import { X } from 'lucide-react'
import { cn } from '@/utils/cn'

interface ModalProps {
  ariaLabel?: string
  isOpen: boolean
  onClose: () => void
  children: ReactNode
  title?: string
  className?: string
  footer?: ReactNode
}

export function Modal({ ariaLabel, isOpen, onClose, children, title, className, footer }: ModalProps) {
  const titleId = useId()
  const onCloseRef = useRef(onClose)
  const shouldReduceMotion = useReducedMotion()

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current()
    }

    if (!isOpen) return

    const previousOverflow = document.body.style.overflow
    document.addEventListener('keydown', handleEscape)
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', handleEscape)
      document.body.style.overflow = previousOverflow
    }
  }, [isOpen])

  if (typeof document === 'undefined') {
    return null
  }

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
          <motion.div
            data-modal-backdrop
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: shouldReduceMotion ? 0.01 : 0.14, ease: 'easeOut' }}
            className="absolute inset-0 bg-black/75 will-change-opacity"
            onClick={() => onCloseRef.current()}
          />
          <motion.div
            data-modal-panel
            initial={shouldReduceMotion ? false : { y: 24 }}
            animate={shouldReduceMotion
              ? { y: 0, transition: { duration: 0 } }
              : {
                  y: 0,
                  transition: { type: 'spring', damping: 32, stiffness: 380, mass: 0.7 },
                }}
            exit={shouldReduceMotion
              ? { y: 0, transition: { duration: 0 } }
              : {
                  y: 18,
                  transition: { duration: 0.16, ease: [0.4, 0, 1, 1] },
                }}
            role="dialog"
            aria-modal="true"
            aria-label={ariaLabel}
            aria-labelledby={title ? titleId : undefined}
            className={cn(
              'relative z-10 flex w-full flex-col sm:mx-4 max-w-lg will-change-transform',
              'max-h-[94dvh] sm:max-h-[88dvh]',
              'overflow-hidden rounded-t-[1.5rem] sm:rounded-[1.75rem]',
              'border border-white/[.07] shadow-[0_-8px_40px_rgba(0,0,0,.3),0_32px_80px_rgba(0,0,0,.5)] sm:shadow-[0_32px_80px_rgba(0,0,0,.5)]',
              'bg-[linear-gradient(180deg,rgba(18,22,36,.98),rgba(10,12,20,.99))]',
              className
            )}
          >
            <div className="pointer-events-none absolute inset-x-10 top-0 h-px bg-gradient-to-r from-transparent via-white/12 to-transparent" />
            <div className="pointer-events-none absolute -left-20 top-8 h-44 w-44 rounded-full bg-sky-400/[.04] blur-3xl" />
            <div className="pointer-events-none absolute -right-16 bottom-12 h-36 w-36 rounded-full bg-violet-400/[.04] blur-3xl" />

            <div className="flex justify-center pt-2.5 sm:hidden">
              <div className="h-1 w-8 rounded-full bg-white/14" />
            </div>

            {title && (
              <div className="flex shrink-0 items-center justify-between px-5 pb-3.5 pt-3 sm:border-b sm:border-white/[.06] sm:px-6 sm:py-4">
                <h2 id={titleId} className="text-[1.05rem] font-semibold tracking-[-0.01em] text-white/92 sm:text-lg">
                  {title}
                </h2>
                <button
                  type="button"
                  onClick={() => onCloseRef.current()}
                  aria-label="Close"
                  className="flex h-8 w-8 items-center justify-center rounded-xl border border-white/[.06] bg-white/[.03] text-white/36 transition-all hover:bg-white/[.08] hover:text-white/60 cursor-pointer"
                >
                  <X size={15} />
                </button>
              </div>
            )}
            <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-[max(1.25rem,env(safe-area-inset-bottom))] pt-0.5 sm:px-6 sm:py-6">{children}</div>
            {footer && (
              <div className="shrink-0 border-t border-white/[.06] bg-[rgba(10,12,20,0.95)] px-4 py-3 backdrop-blur-sm sm:px-6">
                {footer}
              </div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  )
}
