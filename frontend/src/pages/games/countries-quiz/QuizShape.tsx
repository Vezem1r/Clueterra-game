import { useMemo } from 'react'
import { cn } from '@/utils/cn'
import { shapeViewBox } from '../silhouettes/silhouetteShapeBounds'

export type QuizShapeTone = 'idle' | 'right' | 'wrong' | 'missed' | 'faded'

interface QuizShapeProps {
  shapePath: string
  tone?: QuizShapeTone
  className?: string
}

const SHAPE_STROKE = 1.2

const TONE_FILL: Record<QuizShapeTone, string> = {
  idle: '#f6d9b8',
  right: '#86efac',
  wrong: '#fda4af',
  missed: '#86efac',
  faded: '#9ca3af',
}

export function QuizShape({ shapePath, tone = 'idle', className }: QuizShapeProps) {
  const viewBox = useMemo(() => shapeViewBox(shapePath), [shapePath])
  const fill = TONE_FILL[tone]

  return <svg viewBox={viewBox} aria-hidden="true" preserveAspectRatio="xMidYMid meet"
    className={cn('rounded-xl bg-[radial-gradient(circle_at_50%_40%,rgba(253,186,116,.08),transparent_70%)] p-2', className)}>
    <path d={shapePath} fillRule="evenodd" fill={fill} stroke={fill} strokeWidth={SHAPE_STROKE} strokeLinejoin="round"
      vectorEffect="non-scaling-stroke" className="transition-[fill,stroke] duration-500" />
  </svg>
}
