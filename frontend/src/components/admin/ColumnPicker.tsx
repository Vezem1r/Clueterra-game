import type { ColumnDef } from '@/hooks/useAdminTable'
import { cn } from '@/utils/cn'

interface ColumnPickerProps {
  columns: ColumnDef[]
  visibleCols: Set<string>
  onToggle: (key: string) => void
  onShowAll: () => void
  onReset: () => void
}

export function ColumnPicker({ columns, visibleCols, onToggle, onShowAll, onReset }: ColumnPickerProps) {
  return (
    <div className="absolute right-0 top-full mt-2 z-40 w-56 rounded-xl border border-white/[.08] bg-[#0e0e1a] shadow-2xl shadow-black/60 p-3">
      <div className="flex items-center justify-between mb-2 px-1">
        <p className="text-[10px] text-white/30 uppercase tracking-widest">Columns</p>
        <div className="flex gap-1">
          <button
            type="button"
            onClick={onShowAll}
            className="text-[10px] text-accent/70 hover:text-accent px-1.5 py-0.5 rounded hover:bg-accent/10 transition-colors"
          >
            All
          </button>
          <button
            type="button"
            onClick={onReset}
            className="text-[10px] text-white/30 hover:text-white/60 px-1.5 py-0.5 rounded hover:bg-white/[.05] transition-colors"
          >
            Reset
          </button>
        </div>
      </div>
      <div className="space-y-0.5">
        {columns.map(col => (
          <label
            key={col.key}
            onClick={() => onToggle(col.key)}
            className="flex items-center gap-2.5 px-2 py-1.5 rounded-lg hover:bg-white/[.04] cursor-pointer select-none"
          >
            <div
              className={cn(
                'w-3.5 h-3.5 rounded border flex items-center justify-center shrink-0 transition-colors',
                visibleCols.has(col.key) ? 'bg-accent border-accent' : 'border-white/20 bg-white/[.04]'
              )}
            >
              {visibleCols.has(col.key) && (
                <svg width="9" height="7" viewBox="0 0 9 7" fill="none">
                  <path d="M1 3.5L3.5 6L8 1" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              )}
            </div>
            <span className="text-sm text-white/60">{col.label}</span>
          </label>
        ))}
      </div>
    </div>
  )
}
