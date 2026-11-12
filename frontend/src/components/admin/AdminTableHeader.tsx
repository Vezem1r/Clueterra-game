import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react'
import type { ColumnDef } from '@/hooks/useAdminTable'
import { cn } from '@/utils/cn'

function SortIcon({ col, sortBy, sortDir }: { col: ColumnDef; sortBy: string; sortDir: 'asc' | 'desc' }) {
  if (!col.sortKey) return null
  const active = sortBy === col.sortKey
  if (!active) return <ChevronsUpDown size={11} className="text-white/15 ml-0.5" />
  return sortDir === 'asc'
    ? <ChevronUp size={11} className="text-accent ml-0.5" />
    : <ChevronDown size={11} className="text-accent ml-0.5" />
}

const ACTIONS_GRAD = 'linear-gradient(to right, transparent, rgb(15,15,20) 28px, rgb(15,15,20))'

interface AdminTableHeaderProps {
  dataCols: ColumnDef[]
  hasActions: boolean
  dataColsTemplate: string
  actionsW: number
  sortBy: string
  sortDir: 'asc' | 'desc'
  onSort: (col: ColumnDef) => void
  onResizeStart: (e: React.MouseEvent, colKey: string, startWidth: number) => void
  getWidth: (col: ColumnDef) => number
  actionsRefCb?: (node: HTMLDivElement | null) => void
  actionsBackground?: string
  centerColumns?: string[]
}

export function AdminTableHeader({
  dataCols,
  hasActions,
  dataColsTemplate,
  actionsW,
  sortBy,
  sortDir,
  onSort,
  onResizeStart,
  getWidth,
  actionsRefCb,
  actionsBackground = ACTIONS_GRAD,
  centerColumns,
}: AdminTableHeaderProps) {
  const centerSet = centerColumns ? new Set(centerColumns) : null

  return (
    <div className="hidden sm:flex items-center min-w-max border-b border-white/[.06] sticky top-0 z-10 bg-[#0e0e14]">
      <div className="grid gap-0 px-3 py-3 flex-1 min-w-0" style={{ gridTemplateColumns: dataColsTemplate }}>
        {dataCols.map((col, i) => (
          <div key={col.key} className="relative flex items-center min-w-0">
            {i > 0 && (
              <div className="absolute left-0 top-1 bottom-1 w-px bg-white/[.07] pointer-events-none" />
            )}
            <button
              onClick={() => onSort(col)}
              className={cn(
                'flex items-center gap-0.5 text-[10px] text-white/25 uppercase tracking-widest font-medium truncate w-full px-2',
                col.sortKey && 'hover:text-white/50 cursor-pointer transition-colors',
                centerSet?.has(col.key) && 'justify-center',
              )}
            >
              {col.label}
              <SortIcon col={col} sortBy={sortBy} sortDir={sortDir} />
            </button>
            <div
              title="Drag to resize"
              className="absolute right-0 top-0 h-full w-2 cursor-col-resize group flex items-center justify-center shrink-0"
              onMouseDown={e => onResizeStart(e, col.key, getWidth(col))}
            >
              <div className="w-px h-3/5 bg-white/20 group-hover:bg-accent/70 group-active:bg-accent transition-colors rounded-full" />
            </div>
          </div>
        ))}
      </div>
      {hasActions && (
        <div
          ref={actionsRefCb}
          className="sticky right-0 z-10 shrink-0 px-3 py-3"
          style={{ width: actionsW, background: actionsBackground }}
        />
      )}
    </div>
  )
}
