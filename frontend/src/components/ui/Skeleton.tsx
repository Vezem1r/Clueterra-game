import { cn } from '@/utils/cn'

interface SkeletonProps {
  variant?: 'text' | 'circle' | 'rect' | 'card'
  width?: string | number
  height?: string | number
  className?: string
  lines?: number
}

export function Skeleton({ variant = 'rect', width, height, className, lines = 1 }: SkeletonProps) {
  const baseClasses = 'relative overflow-hidden bg-white/[.04] after:absolute after:inset-0 after:translate-x-[-100%] after:bg-gradient-to-r after:from-transparent after:via-white/[.04] after:to-transparent after:animate-shimmer'

  if (variant === 'text' && lines > 1) {
    return (
      <div className={cn('space-y-2.5', className)}>
        {Array.from({ length: lines }).map((_, i) => (
          <div
            key={i}
            className={cn(baseClasses, 'h-4 rounded-md', i === lines - 1 && 'w-3/4')}
            style={{ width: i === lines - 1 ? '75%' : width }}
          />
        ))}
      </div>
    )
  }

  return (
    <div
      className={cn(
        baseClasses,
        variant === 'text' && 'h-4 rounded-md',
        variant === 'circle' && 'rounded-full',
        variant === 'rect' && 'rounded-xl',
        variant === 'card' && 'rounded-2xl min-h-[120px]',
        className
      )}
      style={{ width, height }}
    />
  )
}

export function SkeletonTable({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="space-y-3">
      <div className="flex gap-4 pb-3 border-b border-white/[.06]">
        {Array.from({ length: cols }).map((_, i) => (
          <Skeleton key={i} variant="text" className="flex-1" />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex gap-4 py-2">
          {Array.from({ length: cols }).map((_, c) => (
            <Skeleton key={c} variant="text" className="flex-1" />
          ))}
        </div>
      ))}
    </div>
  )
}
