import { useId } from 'react'
import { createPortal } from 'react-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { AlertTriangle } from 'lucide-react'
import { Button } from './Button'
import { cn } from '@/utils/cn'

interface ConfirmDialogProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  message?: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: 'danger' | 'default'
  isLoading?: boolean
}

export function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'default',
  isLoading,
}: ConfirmDialogProps) {
  const titleId = useId()

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center">
          <div
            className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.div
            initial={{ scale: 0.985, y: 5 }}
            animate={{
              scale: 1,
              y: 0,
              transition: { duration: 0.14, ease: [0.22, 1, 0.36, 1] },
            }}
            exit={{
              scale: 0.985,
              y: 5,
              transition: { duration: 0.1, ease: [0.4, 0, 1, 1] },
            }}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            className="relative z-10 mx-4 w-full max-w-md rounded-2xl border border-white/[.08] bg-[#0c0c14] p-6 shadow-2xl will-change-transform"
          >
            <div className="flex items-start gap-4 mb-5">
              <div
                className={cn(
                  'shrink-0 w-10 h-10 rounded-xl flex items-center justify-center',
                  variant === 'danger'
                    ? 'bg-error/12 text-error'
                    : 'bg-accent/12 text-accent'
                )}
              >
                <AlertTriangle size={20} />
              </div>
              <div>
                <h3 id={titleId} className="mb-1 text-base font-semibold text-white">{title}</h3>
                <p className="text-sm text-white/45 leading-relaxed">{message || description}</p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-3">
              <Button variant="ghost" size="sm" onClick={onClose} disabled={isLoading}>
                {cancelLabel}
              </Button>
              <Button
                variant={variant === 'danger' ? 'danger' : 'primary'}
                size="sm"
                onClick={onConfirm}
                isLoading={isLoading}
              >
                {confirmLabel}
              </Button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  )
}
