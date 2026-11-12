import { useMemo } from 'react'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { SILHOUETTE_COLORS, SILHOUETTE_STROKES } from './silhouetteGameConstants'
import { shapeViewBox } from './silhouetteShapeBounds'

interface SilhouetteShapeProps {
  copy: TranslationKeys['silhouetteGame']
  shapePath: string
  revealed?: boolean
  className?: string
}

export function SilhouetteShape({ copy, shapePath, revealed = false, className }: SilhouetteShapeProps) {
  const tone = revealed ? SILHOUETTE_COLORS.shapeSolved : SILHOUETTE_COLORS.shape
  const viewBox = useMemo(() => shapeViewBox(shapePath), [shapePath])

  return <figure className={cn('relative isolate overflow-hidden rounded-2xl border border-white/[.08] bg-[radial-gradient(circle_at_50%_40%,rgba(103,232,249,.09),transparent_65%),linear-gradient(rgba(255,255,255,.035)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.035)_1px,transparent_1px)] bg-[length:100%_100%,28px_28px,28px_28px] bg-[#070c16]', className)}>
    <svg viewBox={viewBox} role="img" aria-label={copy.shapeLabel} className="h-full w-full p-3" preserveAspectRatio="xMidYMid meet">
      <path d={shapePath} fillRule="evenodd" fill={tone} stroke={tone} strokeWidth={SILHOUETTE_STROKES.shape} strokeLinejoin="round"
        vectorEffect="non-scaling-stroke" className="drop-shadow-[0_0_24px_rgba(165,243,252,.25)] transition-colors duration-500" />
    </svg>
  </figure>
}
