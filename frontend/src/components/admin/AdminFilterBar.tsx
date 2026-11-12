import { useState, type ReactNode } from 'react'
import { Search, SlidersHorizontal, X } from 'lucide-react'
import { Input } from '@/components/ui/Input'
import { cn } from '@/utils/cn'
import { MobileFilterSheet } from './MobileFilterSheet'

interface AdminFilterBarProps {
  search?: string
  onSearchChange?: (value: string) => void
  searchPlaceholder?: string
  activeFilterCount: number
  onClearFilters?: () => void
  hasFilters: boolean
  resultCount?: number
  children: ReactNode
}

export function AdminFilterBar({
  search,
  onSearchChange,
  searchPlaceholder = 'Search...',
  activeFilterCount,
  onClearFilters,
  hasFilters,
  resultCount,
  children,
}: AdminFilterBarProps) {
  const [sheetOpen, setSheetOpen] = useState(false)
  const hasSearch = search != null && onSearchChange != null

  return (
    <>
      {/* Desktop: inline filter bar */}
      <div className="hidden md:flex items-center gap-2.5 rounded-xl bg-white/[.03] border border-white/[.06] px-3 py-2.5 overflow-x-auto scrollbar-none">
        {hasSearch && (
          <>
            <Search size={14} className="text-white/25 shrink-0" />
            <Input
              placeholder={searchPlaceholder}
              value={search}
              onChange={e => onSearchChange(e.target.value)}
              containerClassName="flex-1 min-w-[120px]"
              className="h-8 text-xs"
            />
          </>
        )}
        {children}
        {hasFilters && onClearFilters && (
          <button
            type="button"
            onClick={onClearFilters}
            className="flex items-center gap-1 shrink-0 text-xs text-white/30 hover:text-white/60 transition-colors cursor-pointer"
          >
            <X size={12} /> Clear
          </button>
        )}
        {resultCount != null && (
          <span className="shrink-0 text-[11px] text-white/25 tabular-nums">
            {resultCount.toLocaleString()}
          </span>
        )}
      </div>

      {/* Mobile: compact search + filter button */}
      <div className="flex md:hidden items-center gap-2 rounded-xl bg-white/[.03] border border-white/[.06] px-3 py-2">
        {hasSearch ? (
          <>
            <Search size={14} className="text-white/25 shrink-0" />
            <Input
              placeholder={searchPlaceholder}
              value={search}
              onChange={e => onSearchChange(e.target.value)}
              containerClassName="flex-1 min-w-0"
              className="h-8 text-xs"
            />
          </>
        ) : (
          <span className="flex-1 text-xs text-white/30">Filters</span>
        )}
        <button
          type="button"
          onClick={() => setSheetOpen(true)}
          className={cn(
            'flex items-center gap-1.5 shrink-0 h-8 px-2.5 rounded-lg border text-xs transition-colors cursor-pointer',
            activeFilterCount > 0
              ? 'bg-accent/10 border-accent/30 text-accent'
              : 'bg-white/[.04] border-white/[.08] text-white/50 hover:bg-white/[.06]',
          )}
        >
          <SlidersHorizontal size={13} />
          {activeFilterCount > 0 && (
            <span className="flex h-4 min-w-4 items-center justify-center rounded-full bg-accent/25 px-1 text-[10px] font-bold">
              {activeFilterCount}
            </span>
          )}
        </button>
        {resultCount != null && (
          <span className="shrink-0 text-[11px] text-white/25 tabular-nums">
            {resultCount.toLocaleString()}
          </span>
        )}
      </div>

      <MobileFilterSheet
        isOpen={sheetOpen}
        onClose={() => setSheetOpen(false)}
        activeCount={activeFilterCount}
        onClear={onClearFilters}
      >
        {children}
      </MobileFilterSheet>
    </>
  )
}
