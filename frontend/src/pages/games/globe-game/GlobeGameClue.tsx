import type { ReactNode } from 'react'
import { Plus } from 'lucide-react'
import { cn } from '@/utils/cn'
import { GLOBE_GAME_THEMES, type GlobeGameAccent } from './globeGameTheme'

export interface GlobeGameClueValueProps {
  accent: GlobeGameAccent
  label: string
  children: ReactNode
  animate?: boolean
}

export interface GlobeGameClueLockedProps {
  label: string
  detail: string
}

export interface GlobeGameClueButtonProps {
  accent: GlobeGameAccent
  title: string
  detail: string
  cost?: string
  busy: boolean
  onClick: () => void
}

export function GlobeGameClueValue({ accent, label, children, animate = false }: GlobeGameClueValueProps) {
  return <div className={cn(animate && 'motion-safe:animate-river-reveal')}>
    <p className={cn('text-xs font-medium', GLOBE_GAME_THEMES[accent].label)}>{label}</p>
    <div className="mt-0.5 text-[15px] leading-snug text-white/90">{children}</div>
  </div>
}

export function GlobeGameClueLocked({ label, detail }: GlobeGameClueLockedProps) {
  return <div className="pt-0.5">
    <p className="text-sm font-medium text-white/45">{label}</p>
    <p className="text-xs leading-snug text-white/30">{detail}</p>
  </div>
}

export function GlobeGameClueButton({ accent, title, detail, cost, busy, onClick }: GlobeGameClueButtonProps) {
  const theme = GLOBE_GAME_THEMES[accent]
  return <button type="button" disabled={busy} onClick={onClick}
    className={cn('group -mt-1 flex w-full items-center gap-2.5 rounded-2xl px-3.5 py-2.5 text-left ring-1 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50', theme.clue, theme.focus)}>
    <span className="min-w-0 flex-1">
      <span className={cn('block text-sm font-semibold', theme.clueTitle)}>{title}</span>
      <span className="mt-0.5 block text-xs leading-snug text-white/50 max-[379px]:hidden">{detail}</span>
    </span>
    {cost && <span className="shrink-0 rounded-full bg-amber-300/10 px-2 py-0.5 text-xs font-bold tabular-nums text-river-silt ring-1 ring-amber-200/25">
      {cost}
    </span>}
    <Plus size={16} aria-hidden="true" className={cn('shrink-0 transition-transform group-hover:rotate-90 motion-reduce:transform-none', theme.clueIcon)} />
  </button>
}
