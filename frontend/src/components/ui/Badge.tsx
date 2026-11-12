import type { ReactNode } from 'react'
import { cn } from '@/utils/cn'

interface BadgeProps {
  variant?: 'success' | 'warning' | 'error' | 'info' | 'neutral'
  children: ReactNode
  className?: string
  dot?: boolean
}

const variantStyles = {
  success: 'bg-success/12 text-success border-success/20',
  warning: 'bg-warning/12 text-warning border-warning/20',
  error: 'bg-error/12 text-error border-error/20',
  info: 'bg-accent/12 text-accent border-accent/20',
  neutral: 'bg-white/[.06] text-white/60 border-white/[.08]',
}

const dotColors = {
  success: 'bg-success',
  warning: 'bg-warning',
  error: 'bg-error',
  info: 'bg-accent',
  neutral: 'bg-white/40',
}

export function Badge({ variant = 'neutral', children, className, dot }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-lg text-xs font-medium border',
        variantStyles[variant],
        className
      )}
    >
      {dot && (
        <span className={cn('w-1.5 h-1.5 rounded-full', dotColors[variant])} />
      )}
      {children}
    </span>
  )
}
