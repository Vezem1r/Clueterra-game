import { cn } from '@/utils/cn'

interface StatItem {
  label: string
  value: number | string
}

interface AdminStatsProps {
  items: StatItem[]
  className?: string
}

export function AdminStats({ items, className }: AdminStatsProps) {
  return (
    <div className={cn('', className)}>
      {/* Desktop: grid cards */}
      <div className={cn(
        'hidden sm:grid gap-3',
        items.length <= 4 && 'grid-cols-2 lg:grid-cols-4',
        items.length === 5 && 'grid-cols-3 lg:grid-cols-5',
        items.length >= 6 && 'grid-cols-3 lg:grid-cols-6',
      )}>
        {items.map((s, i) => (
          <div key={i} className="rounded-xl bg-white/[.03] border border-white/[.06] px-4 py-3">
            <p className="text-lg font-bold text-white">{typeof s.value === 'number' ? s.value.toLocaleString() : s.value}</p>
            <p className="text-[11px] text-white/35">{s.label}</p>
          </div>
        ))}
      </div>

      {/* Mobile: compact 3-col grid in a single card */}
      <div className="sm:hidden grid grid-cols-3 rounded-xl bg-white/[.03] border border-white/[.06] overflow-hidden">
        {items.map((s, i) => (
          <div key={i} className={cn(
            'px-3 py-2 text-center',
            i >= 3 && 'border-t border-white/[.04]',
            i % 3 !== 0 && 'border-l border-white/[.04]',
          )}>
            <p className="text-[15px] font-bold text-white leading-tight">{typeof s.value === 'number' ? s.value.toLocaleString() : s.value}</p>
            <p className="text-[10px] text-white/30 mt-0.5">{s.label}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
