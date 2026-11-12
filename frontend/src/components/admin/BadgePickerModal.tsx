import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, Award, Check, ChevronLeft, ChevronRight } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { adminApi } from '@/api/admin'
import { useDebounce } from '@/hooks/useDebounce'
import type { BadgeAdminDto } from '@/types'

const PAGE_SIZE = 16

interface Props {
  isOpen: boolean
  onClose: () => void
  alreadyGrantedIds: Set<string>
  onGrant: (badge: BadgeAdminDto) => void
}

export function BadgePickerModal({ isOpen, onClose, alreadyGrantedIds, onGrant }: Props) {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const debouncedSearch = useDebounce(search)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-badges-picker', debouncedSearch, page],
    queryFn: () => adminApi.getBadges({ page, size: PAGE_SIZE, search: debouncedSearch || undefined, isActive: true }),
    enabled: isOpen,
  })

  const handleSelect = (badge: BadgeAdminDto) => {
    if (alreadyGrantedIds.has(badge.id)) return
    onGrant(badge)
    onClose()
  }

  const handleClose = () => {
    setSearch('')
    setPage(0)
    onClose()
  }

  const totalPages = data?.totalPages ?? 0

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Assign Badge" className="max-w-xl">
      <div className="space-y-4">
        <div className="flex items-center gap-2 rounded-lg bg-white/[.04] border border-white/[.08] px-3">
          <Search size={14} className="text-white/25 shrink-0" />
          <Input
            placeholder="Search badges..."
            value={search}
            onChange={e => { setSearch(e.target.value); setPage(0) }}
            className="flex-1 h-9 text-sm border-0 bg-transparent focus:ring-0 px-0"
          />
        </div>

        {isLoading ? (
          <div className="grid grid-cols-4 gap-2">
            {Array.from({ length: PAGE_SIZE }).map((_, i) => (
              <div key={i} className="h-20 rounded-lg bg-white/[.04] animate-pulse" />
            ))}
          </div>
        ) : !data?.content?.length ? (
          <div className="py-10 text-center text-white/25 text-sm">No badges found</div>
        ) : (
          <div className="grid grid-cols-4 gap-2">
            {data.content.map((badge: BadgeAdminDto) => {
              const granted = alreadyGrantedIds.has(badge.id)
              return (
                <button
                  key={badge.id}
                  onClick={() => handleSelect(badge)}
                  disabled={granted}
                  className={`relative flex flex-col items-center gap-1.5 rounded-lg border p-2 text-center transition-colors cursor-pointer ${
                    granted
                      ? 'border-white/[.04] bg-white/[.02] opacity-40 cursor-not-allowed'
                      : 'border-white/[.08] bg-white/[.04] hover:bg-white/[.08] hover:border-white/20'
                  }`}
                >
                  {granted && (
                    <div className="absolute top-1 right-1 w-4 h-4 rounded-full bg-emerald-500/70 flex items-center justify-center">
                      <Check size={10} className="text-white" />
                    </div>
                  )}
                  <div className="w-8 h-8 rounded-lg bg-white/[.06] flex items-center justify-center overflow-hidden">
                    {badge.imageUrl ? (
                      <img src={badge.imageUrl} alt="" className="w-6 h-6 object-contain" />
                    ) : (
                      <Award size={14} className="text-white/20" />
                    )}
                  </div>
                  <span className="text-[10px] text-white/60 leading-tight line-clamp-2 w-full">
                    {badge.translations?.en?.name || badge.slug}
                  </span>
                </button>
              )
            })}
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-between pt-1">
            <Button variant="ghost" size="sm" onClick={() => setPage(p => p - 1)} disabled={page === 0}>
              <ChevronLeft size={14} />
            </Button>
            <span className="text-xs text-white/30">
              {page + 1} / {totalPages}
            </span>
            <Button variant="ghost" size="sm" onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>
              <ChevronRight size={14} />
            </Button>
          </div>
        )}
      </div>
    </Modal>
  )
}
