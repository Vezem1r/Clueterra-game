import { motion } from 'framer-motion'
import { cn } from '@/utils/cn'

interface Tab {
  id: string
  label: string
  shortLabel?: string
  count?: number
}

interface TabsProps {
  tabs: Tab[]
  activeTab: string
  onChange: (id: string) => void
  className?: string
}

export function Tabs({ tabs, activeTab, onChange, className }: TabsProps) {
  return (
    <div className={cn('relative flex gap-0.5 sm:gap-1 p-1 rounded-xl bg-white/[.03] border border-white/[.06]', className)}>
      {tabs.map((tab) => {
        const isActive = activeTab === tab.id
        return (
          <button
            key={tab.id}
            onClick={() => onChange(tab.id)}
            className={cn(
              'relative z-10 flex-1 flex items-center justify-center gap-1.5 sm:gap-2 px-2 sm:px-4 py-2 rounded-lg text-[11px] sm:text-sm font-medium transition-colors cursor-pointer text-center',
              isActive ? 'text-white' : 'text-white/40 hover:text-white/60'
            )}
          >
            {isActive && (
              <motion.div
                layoutId="tab-indicator"
                className="absolute inset-0 rounded-lg bg-white/[.08] border border-white/[.08]"
                transition={{ type: 'spring', stiffness: 400, damping: 30 }}
              />
            )}
            <span className="relative z-10 sm:hidden">{tab.shortLabel ?? tab.label}</span>
            <span className="relative z-10 hidden sm:inline">{tab.label}</span>
            {tab.count !== undefined && (
              <span
                className={cn(
                  'relative z-10 px-1.5 py-0.5 rounded-md text-[10px] font-semibold',
                  isActive ? 'bg-accent/20 text-accent' : 'bg-white/[.06] text-white/30'
                )}
              >
                {tab.count}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}
