import { cn } from '@/utils/cn'

export interface GlobeGameRoundBarProps {
  round: string
  badge: string
  badgeClassName: string
  xp: string
  xpShare: number
}

export function GlobeGameRoundBar({ round, badge, badgeClassName, xp, xpShare }: GlobeGameRoundBarProps) {
  return <div className="shrink-0">
    <div className="flex items-center justify-between gap-3">
      <p className="flex items-center gap-2.5 text-sm font-semibold text-white/85">
        {round}
        <span className={cn('rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1', badgeClassName)}>{badge}</span>
      </p>
      <p className="text-sm font-bold tabular-nums text-river-silt">{xp}</p>
    </div>
    <div aria-hidden="true" className="mt-3 h-1 overflow-hidden rounded-full bg-white/[.07]">
      <div className="h-full rounded-full bg-gradient-to-r from-amber-300 to-yellow-200 transition-[width] duration-500" style={{ width: `${xpShare}%` }} />
    </div>
  </div>
}
