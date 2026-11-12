import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/utils/cn'

interface EmptyStateProps {
  icon?: LucideIcon
  title: string
  description?: string
  action?: ReactNode
  className?: string
}

export function EmptyState({ icon: Icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center py-16 px-6 text-center', className)}>
      {Icon && (
        <div className="w-14 h-14 rounded-2xl bg-white/[.04] border border-white/[.06] flex items-center justify-center mb-5">
          <Icon size={24} className="text-white/20" />
        </div>
      )}
      <h3 className="text-base font-semibold text-white/60 mb-1.5">{title}</h3>
      {description && (
        <p className="text-sm text-white/30 max-w-xs mb-6">{description}</p>
      )}
      {action}
    </div>
  )
}
