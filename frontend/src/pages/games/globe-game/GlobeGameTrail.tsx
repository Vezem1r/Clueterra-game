import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/utils/cn'
import { GLOBE_GAME_THEMES, type GlobeGameAccent } from './globeGameTheme'

export type GlobeGameTrailState = 'flowing' | 'next' | 'dry'

export interface GlobeGameTrailStop {
  key: string
  state: GlobeGameTrailState
  icon: LucideIcon
  content: ReactNode
  hiddenWhenShort?: boolean
}

export interface GlobeGameTrailProps {
  accent: GlobeGameAccent
  stops: GlobeGameTrailStop[]
  stretch?: boolean
  className?: string
}

const MEANDERS = ['M20 0 C 44 30, -4 70, 20 100', 'M20 0 C -4 30, 44 70, 20 100']
const DRY_NODE = 'border-white/10 bg-[#0b0d16] text-white/30'

export function GlobeGameTrail({ accent, stops, stretch = false, className }: GlobeGameTrailProps) {
  const theme = GLOBE_GAME_THEMES[accent]
  const nodeTone: Record<GlobeGameTrailState, string> = { flowing: theme.nodeFlowing, next: theme.nodeNext, dry: DRY_NODE }

  return <ol className={cn('relative', stretch && 'flex flex-col', className)}>
    {stops.map((stop, index) => {
      const Icon = stop.icon
      const next = stops[index + 1]
      const flowing = next?.state === 'flowing'
      return <li key={stop.key} className={cn('relative flex items-start gap-3.5 pb-4 last:pb-0', stretch && 'max-h-32 flex-1 last:flex-none', stop.hiddenWhenShort && 'max-lg:hidden [@media(max-height:700px)]:hidden')}>
        {next && <svg aria-hidden="true" viewBox="0 0 40 100" preserveAspectRatio="none" className="pointer-events-none absolute left-0 top-5 h-full w-10 overflow-visible">
          <path d={MEANDERS[index % MEANDERS.length]} fill="none" vectorEffect="non-scaling-stroke" strokeLinecap="round"
            className={cn('transition-[stroke] duration-500', flowing ? theme.stream : 'stroke-white/12 [stroke-dasharray:3_5]')}
            strokeWidth={flowing ? 2.5 : 1.5} />
        </svg>}
        <span className={cn('relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full border transition-colors', nodeTone[stop.state])}>
          <Icon size={17} aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1 pt-0.5">{stop.content}</div>
      </li>
    })}
  </ol>
}
