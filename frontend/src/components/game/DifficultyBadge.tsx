import type { CSSProperties } from 'react'
import { Flame, Globe2, Zap } from 'lucide-react'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'

type DifficultyLevel = 'EASY' | 'MEDIUM' | 'HARD'

type DifficultyBadgeProps = {
  level: DifficultyLevel
  size?: 'sm' | 'md'
  className?: string
}

const DIFFICULTY_STYLES: Record<DifficultyLevel, {
  Icon: typeof Globe2
  label: 'easy' | 'normal' | 'hard'
  style: CSSProperties
}> = {
  EASY: {
    Icon: Globe2,
    label: 'easy',
    style: {
      color: '#d1fae5',
      borderColor: 'rgba(110, 231, 183, 0.28)',
      background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.26), rgba(6, 95, 70, 0.12))',
      boxShadow: '0 14px 32px rgba(6, 95, 70, 0.24)',
    },
  },
  MEDIUM: {
    Icon: Zap,
    label: 'normal',
    style: {
      color: '#fef3c7',
      borderColor: 'rgba(253, 230, 138, 0.28)',
      background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.28), rgba(146, 64, 14, 0.14))',
      boxShadow: '0 14px 32px rgba(120, 53, 15, 0.24)',
    },
  },
  HARD: {
    Icon: Flame,
    label: 'hard',
    style: {
      color: '#ffe4e6',
      borderColor: 'rgba(253, 164, 175, 0.28)',
      background: 'linear-gradient(135deg, rgba(244, 63, 94, 0.28), rgba(136, 19, 55, 0.14))',
      boxShadow: '0 14px 32px rgba(136, 19, 55, 0.24)',
    },
  },
}

export function DifficultyBadge({ level, size = 'md', className }: DifficultyBadgeProps) {
  const { t } = useTranslation()
  const meta = DIFFICULTY_STYLES[level]
  const iconSize = size === 'sm' ? 12 : 14
  const text = meta.label === 'easy'
    ? t.home.easy
    : meta.label === 'normal'
      ? t.home.normal
      : t.home.hard

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border font-semibold uppercase backdrop-blur-md',
        size === 'sm' ? 'gap-1.5 px-3 py-1.5 text-[11px] tracking-[.18em]' : 'gap-2 px-4 py-2 text-xs tracking-[.2em]',
        className
      )}
      style={meta.style}
    >
      <meta.Icon size={iconSize} className="shrink-0" />
      <span>{text}</span>
    </span>
  )
}
