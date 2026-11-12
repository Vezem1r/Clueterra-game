import { cn } from '@/utils/cn'
import { flagUrl, SILHOUETTE_MISS } from './silhouetteGameConstants'
import type { SilhouetteMissMark } from './silhouetteMissArrow'

export interface SilhouetteMissMarksProps {
  marks: SilhouetteMissMark[]
}

const PATH_LENGTH = 100
const GLOW_FILTER = 'silhouette-miss-glow'
const GRADIENT_PREFIX = 'silhouette-miss-'
const ARROW_HEAD = 'M4 0 L-9 -7 L-5 0 L-9 7 Z'

export function SilhouetteMissMarks({ marks }: SilhouetteMissMarksProps) {
  if (marks.length === 0) return null

  return <div aria-hidden="true" data-testid="silhouette-miss-marks" className="pointer-events-none absolute inset-0 z-[5]">
    <svg className="absolute inset-0 h-full w-full overflow-visible">
      <defs>
        <filter id={GLOW_FILTER} x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="2.4" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
        {marks.map(mark => mark.head && <linearGradient key={mark.iso} id={GRADIENT_PREFIX + mark.iso} gradientUnits="userSpaceOnUse"
          x1={mark.x} y1={mark.y} x2={mark.head.x} y2={mark.head.y}>
          <stop offset="0" stopColor={SILHOUETTE_MISS.arrowFrom} stopOpacity={0.2} />
          <stop offset="1" stopColor={SILHOUETTE_MISS.arrowTo} />
        </linearGradient>)}
      </defs>
      {marks.map(mark => mark.path && mark.head && <g key={mark.iso} className={cn('transition-opacity duration-500', !mark.latest && 'opacity-50')}>
        <path d={mark.path} pathLength={PATH_LENGTH} fill="none" stroke={`url(#${GRADIENT_PREFIX}${mark.iso})`} strokeWidth={4} strokeLinecap="round"
          filter={`url(#${GLOW_FILTER})`} className="[stroke-dasharray:100] motion-safe:animate-miss-draw" />
        <path d={mark.path} pathLength={PATH_LENGTH} fill="none" stroke="white" strokeOpacity={0.8} strokeWidth={1.4} strokeLinecap="round"
          className="[stroke-dasharray:2_8] motion-safe:animate-miss-flow" />
        <g transform={`translate(${mark.head.x} ${mark.head.y}) rotate(${mark.head.angle})`} className="motion-safe:animate-miss-fade">
          <path d={ARROW_HEAD} fill={SILHOUETTE_MISS.arrowTo} filter={`url(#${GLOW_FILTER})`} className="motion-safe:animate-miss-head" />
        </g>
      </g>)}
    </svg>
    {marks.map(mark => <div key={mark.iso} className={cn('absolute -translate-x-1/2 -translate-y-full transition-opacity duration-500', !mark.latest && 'opacity-60')}
      style={{ left: mark.x, top: mark.y + SILHOUETTE_MISS.pinDotOffsetPx }}>
      <div className="flex flex-col items-center motion-safe:animate-miss-drop">
        <div className="flex items-center gap-1.5 rounded-full bg-[rgba(9,11,21,.86)] py-1 pl-1 pr-2.5 shadow-[0_8px_24px_rgba(0,0,0,.5)] ring-1 ring-rose-300/40 backdrop-blur-md">
          <img src={flagUrl(mark.iso)} alt="" className="h-4 w-6 rounded-[3px] object-cover ring-1 ring-white/25" />
          <span className="whitespace-nowrap text-[11px] font-semibold tabular-nums text-rose-50">{mark.label}</span>
        </div>
        <span className="h-2.5 w-px bg-gradient-to-b from-rose-200/80 to-rose-300/40" />
        <span className="size-2 rounded-full bg-rose-300 shadow-[0_0_10px_rgba(251,113,133,.9)] ring-2 ring-rose-300/30" />
      </div>
    </div>)}
  </div>
}
