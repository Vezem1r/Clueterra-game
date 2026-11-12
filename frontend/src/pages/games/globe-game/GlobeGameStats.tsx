import type { LucideIcon } from 'lucide-react'
import { cn } from '@/utils/cn'

export interface GlobeGameStat {
  value: string
  label: string
  icon: LucideIcon
  tone: string
}

export interface GlobeGameStatsProps {
  label: string
  items: GlobeGameStat[]
  progress?: number
  className?: string
}

export function GlobeGameStats({ label, items, progress, className }: GlobeGameStatsProps) {
  return <div className={cn('overflow-hidden rounded-2xl border border-white/[.1] bg-[rgba(9,11,21,.72)] shadow-[0_18px_40px_rgba(0,0,0,.35)] backdrop-blur-xl', className)}>
    <dl aria-label={label} className="grid grid-cols-3 divide-x divide-white/[.08]">
      {items.map(item => <div key={item.label} className="relative flex min-w-0 flex-col justify-center px-3 py-2.5 sm:pl-[3.375rem]">
        <dt className="order-2 truncate text-[11px] text-white/50">
          <span aria-hidden="true" className={cn('absolute left-3 top-1/2 hidden size-8 -translate-y-1/2 items-center justify-center rounded-lg sm:flex', item.tone)}><item.icon size={16} /></span>
          {item.label}
        </dt>
        <dd className="order-1 truncate text-lg font-bold leading-tight tabular-nums text-white">{item.value}</dd>
      </div>)}
    </dl>
    {progress != null && <div aria-hidden="true" className="h-1 bg-white/[.05]">
      <div className="h-full bg-gradient-to-r from-cyan-300 via-emerald-300 to-amber-200 transition-[width] duration-700" style={{ width: `${progress}%` }} />
    </div>}
  </div>
}
