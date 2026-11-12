import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/utils/cn'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements?: number
  onPageChange: (page: number) => void
  className?: string
}

export function Pagination({ page, totalPages, totalElements, onPageChange, className }: PaginationProps) {
  if (totalPages <= 1) return null

  const getPageNumbers = (): (number | 'ellipsis')[] => {
    const pages: (number | 'ellipsis')[] = []
    const maxVisible = 5

    if (totalPages <= maxVisible + 2) {
      for (let i = 0; i < totalPages; i++) pages.push(i)
      return pages
    }

    pages.push(0)

    let start = Math.max(1, page - 1)
    let end = Math.min(totalPages - 2, page + 1)

    if (page <= 2) { start = 1; end = Math.min(3, totalPages - 2) }
    if (page >= totalPages - 3) { start = Math.max(1, totalPages - 4); end = totalPages - 2 }

    if (start > 1) pages.push('ellipsis')
    for (let i = start; i <= end; i++) pages.push(i)
    if (end < totalPages - 2) pages.push('ellipsis')

    pages.push(totalPages - 1)
    return pages
  }

  return (
    <div className={cn('flex items-center justify-between gap-4', className)}>
      {totalElements !== undefined && (
        <span className="text-xs text-white/30 hidden sm:block">
          {totalElements.toLocaleString()} total
        </span>
      )}

      <div className="flex items-center gap-1 ml-auto">
        <button
          onClick={() => onPageChange(Math.max(0, page - 1))}
          disabled={page === 0}
          className="h-8 w-8 rounded-lg flex items-center justify-center text-white/40 hover:text-white/70 hover:bg-white/[.06] disabled:opacity-25 disabled:pointer-events-none transition-all cursor-pointer"
        >
          <ChevronLeft size={16} />
        </button>

        {getPageNumbers().map((p, i) =>
          p === 'ellipsis' ? (
            <span key={`e-${i}`} className="w-8 text-center text-white/20 text-xs">...</span>
          ) : (
            <button
              key={p}
              onClick={() => onPageChange(p)}
              className={cn(
                'h-8 min-w-8 px-2 rounded-lg text-xs font-medium transition-all cursor-pointer',
                p === page
                  ? 'bg-white/[.1] text-white border border-white/[.1]'
                  : 'text-white/40 hover:text-white/70 hover:bg-white/[.04]'
              )}
            >
              {p + 1}
            </button>
          )
        )}

        <button
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="h-8 w-8 rounded-lg flex items-center justify-center text-white/40 hover:text-white/70 hover:bg-white/[.06] disabled:opacity-25 disabled:pointer-events-none transition-all cursor-pointer"
        >
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  )
}
