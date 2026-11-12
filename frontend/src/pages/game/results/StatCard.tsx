import type { ReactNode } from 'react'
import { cn } from '@/utils/cn'
import {
  CARD_RADIUS_INNER,
  LABEL_TEXT,
  LABEL_TRACKING,
  SURFACE_INLAY,
  getToneSurfaceClasses,
  type ResultsTone,
} from './constants'

type StatCardSize = 'sm' | 'md' | 'lg'
type StatCardLayout = 'stacked' | 'header'

interface StatCardProps {
  icon?: ReactNode
  value: string
  label: string
  size?: StatCardSize
  tone?: ResultsTone
  layout?: StatCardLayout
  className?: string
}

const SIZE_STYLES: Record<StatCardSize, { container: string; value: string; iconBox: string }> = {
  sm: {
    container: 'p-4',
    value: 'text-xl font-bold tracking-tight',
    iconBox: 'w-8 h-8 rounded-lg',
  },
  md: {
    container: 'p-4',
    value: 'text-[1.85rem] font-bold tracking-tight',
    iconBox: 'w-9 h-9 rounded-xl',
  },
  lg: {
    container: 'p-5',
    value: 'text-[2.2rem] sm:text-[2.5rem] font-black tracking-tight leading-none',
    iconBox: 'w-10 h-10 rounded-2xl',
  },
}

export function StatCard({
  icon,
  value,
  label,
  size = 'md',
  tone = 'neutral',
  layout = 'stacked',
  className,
}: StatCardProps) {
  const styles = SIZE_STYLES[size]
  const toneClass = tone === 'neutral' ? SURFACE_INLAY : getToneSurfaceClasses(tone)

  if (layout === 'header') {
    return (
      <div
        className={cn(
          CARD_RADIUS_INNER,
          'border flex flex-col h-full',
          toneClass,
          styles.container,
          className
        )}
      >
        <div className="flex items-center gap-3 mb-3">
          {icon ? (
            <div
              className={cn(
                styles.iconBox,
                'bg-black/20 border border-white/[.08] flex items-center justify-center opacity-80 shrink-0'
              )}
            >
              {icon}
            </div>
          ) : null}
          <p className={cn(LABEL_TEXT, LABEL_TRACKING, 'opacity-65')}>{label}</p>
        </div>
        <p className={cn(styles.value, 'mt-auto')}>{value}</p>
      </div>
    )
  }

  return (
    <div
      className={cn(
        CARD_RADIUS_INNER,
        'border flex flex-col h-full',
        toneClass,
        styles.container,
        className
      )}
    >
      {icon ? (
        <div
          className={cn(
            styles.iconBox,
            'bg-black/20 border border-white/[.08] flex items-center justify-center opacity-80 mb-3'
          )}
        >
          {icon}
        </div>
      ) : null}
      <p className={styles.value}>{value}</p>
      <p className={cn(LABEL_TEXT, LABEL_TRACKING, 'opacity-65 mt-1.5')}>{label}</p>
    </div>
  )
}
