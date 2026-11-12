import { Maximize2, RotateCcw, SlidersHorizontal } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ColumnPicker } from './ColumnPicker'
import type { ColumnDef } from '@/hooks/useAdminTable'
import { cn } from '@/utils/cn'

interface TableToolbarButtonsProps {
  showColPicker: boolean
  onToggleColPicker: () => void
  onFitToContainer: () => void
  onResetWidths: () => void
  toggleableColumns: ColumnDef[]
  visibleCols: Set<string>
  onToggleCol: (key: string) => void
  onShowAllCols: () => void
  onResetCols: () => void
}

export function TableToolbarButtons({
  showColPicker,
  onToggleColPicker,
  onFitToContainer,
  onResetWidths,
  toggleableColumns,
  visibleCols,
  onToggleCol,
  onShowAllCols,
  onResetCols,
}: TableToolbarButtonsProps) {
  return (
    <div className="hidden sm:contents">
      <Button variant="ghost" size="sm" onClick={onFitToContainer} title="Fit columns to container width">
        <Maximize2 size={14} />
      </Button>
      <Button variant="ghost" size="sm" onClick={onResetWidths} title="Reset column widths">
        <RotateCcw size={14} />
      </Button>
      <div className="relative">
        <Button
          variant="outline" size="sm"
          onClick={onToggleColPicker}
          className={cn(showColPicker && 'border-accent/40 text-accent')}
        >
          <SlidersHorizontal size={14} />
          Columns
        </Button>
        {showColPicker && (
          <ColumnPicker
            columns={toggleableColumns}
            visibleCols={visibleCols}
            onToggle={onToggleCol}
            onShowAll={onShowAllCols}
            onReset={onResetCols}
          />
        )}
      </div>
    </div>
  )
}
