import type { ReactNode } from 'react'
import { GlobeGameTrail, type GlobeGameTrailStop } from './GlobeGameTrail'
import type { GlobeGameAccent } from './globeGameTheme'

export interface GlobeGameCluesProps {
  accent: GlobeGameAccent
  titleId: string
  title: string
  progress?: string
  stops: GlobeGameTrailStop[]
  children?: ReactNode
}

export function GlobeGameClues({ accent, titleId, title, progress, stops, children }: GlobeGameCluesProps) {
  return <section aria-labelledby={titleId}>
    <div className="mb-3 flex items-baseline justify-between gap-3">
      <h3 id={titleId} className="text-sm font-semibold text-white/85">{title}</h3>
      {progress && <p className="text-xs tabular-nums text-white/45">{progress}</p>}
    </div>
    <GlobeGameTrail accent={accent} stops={stops} />
    {children}
  </section>
}
