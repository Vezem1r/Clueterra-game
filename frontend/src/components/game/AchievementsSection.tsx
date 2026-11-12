import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { ChevronLeft, ChevronRight, LockKeyhole, Trophy } from 'lucide-react'
import { gameApi } from '@/api/game'
import { HiddenSecretSummaryCard } from '@/components/game/BadgeCard'
import { BadgeGrid, type AchievementGridItem } from '@/components/game/BadgeGrid'
import { ProfileGlassPanel } from '@/components/game/profile/ProfileGlassPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'

interface AchievementsSectionProps {
  mode?: 'own' | 'guest' | 'public'
  username?: string
  className?: string
}

type AchievementFilter = 'earned' | 'locked'

const DESKTOP_PAGE_SIZE = 9
const WIDE_DESKTOP_PAGE_SIZE = 12
const TABLET_PAGE_SIZE = 6
const MOBILE_PAGE_SIZE = 4
const WIDE_DESKTOP_BREAKPOINT_PX = 1536
const WIDE_DESKTOP_MIN_ROWS = 3
const WIDE_DESKTOP_MAX_ROWS = 6

const subscribe = (cb: () => void) => {
  window.addEventListener('resize', cb)
  return () => window.removeEventListener('resize', cb)
}
const getCardsPerPage = () => {
  const w = window.innerWidth
  if (w < 640) return MOBILE_PAGE_SIZE
  if (w < 1024) return TABLET_PAGE_SIZE
  if (w >= WIDE_DESKTOP_BREAKPOINT_PX) return WIDE_DESKTOP_PAGE_SIZE
  return DESKTOP_PAGE_SIZE
}
const getServerCardsPerPage = () => WIDE_DESKTOP_PAGE_SIZE

function useAdaptiveCardsPerPage() {
  const responsiveCardsPerPage = useSyncExternalStore(subscribe, getCardsPerPage, getServerCardsPerPage)
  const gridViewportRef = useRef<HTMLDivElement>(null)
  const [contentGrid, setContentGrid] = useState<HTMLDivElement | null>(null)
  const [heightFittedCardsPerPage, setHeightFittedCardsPerPage] = useState<number | null>(null)
  const contentGridRef = useCallback((node: HTMLDivElement | null) => setContentGrid(node), [])

  useEffect(() => {
    const gridViewport = gridViewportRef.current
    const firstCard = contentGrid?.firstElementChild
    if (!(gridViewport && contentGrid && firstCard instanceof HTMLElement)) return

    const updatePageSize = () => {
      if (window.innerWidth < WIDE_DESKTOP_BREAKPOINT_PX) {
        setHeightFittedCardsPerPage(null)
        return
      }

      const availableHeight = gridViewport.clientHeight
      if (availableHeight <= 0) return

      const gridStyle = window.getComputedStyle(contentGrid)
      const cardHeight = firstCard.getBoundingClientRect().height
      const rowGap = Number.parseFloat(gridStyle.rowGap) || 0
      const columns = gridStyle.gridTemplateColumns.trim().split(/\s+/).length
      if (cardHeight <= 0 || columns <= 0) return

      const fittedRows = Math.floor(
        (availableHeight + rowGap) / (cardHeight + rowGap)
      )
      const rows = Math.max(WIDE_DESKTOP_MIN_ROWS, Math.min(WIDE_DESKTOP_MAX_ROWS, fittedRows))
      const nextPageSize = rows * columns

      setHeightFittedCardsPerPage(current => current === nextPageSize ? current : nextPageSize)
    }

    const resizeObserver = new ResizeObserver(updatePageSize)
    const animationFrame = window.requestAnimationFrame(updatePageSize)
    resizeObserver.observe(gridViewport)
    resizeObserver.observe(contentGrid)
    resizeObserver.observe(firstCard)
    window.addEventListener('resize', updatePageSize)

    return () => {
      window.cancelAnimationFrame(animationFrame)
      resizeObserver.disconnect()
      window.removeEventListener('resize', updatePageSize)
    }
  }, [contentGrid])

  return {
    cardsPerPage: heightFittedCardsPerPage ?? responsiveCardsPerPage,
    contentGridRef,
    gridViewportRef,
    isMobile: responsiveCardsPerPage === MOBILE_PAGE_SIZE,
  }
}

export function AchievementsSection({
  mode = 'own',
  username,
  className,
}: AchievementsSectionProps) {
  const { t } = useTranslation()
  const [filter, setFilter] = useState<AchievementFilter>('earned')
  const [pageState, setPageState] = useState({ scopeKey: '', page: 0 })

  const { cardsPerPage, contentGridRef, gridViewportRef, isMobile } = useAdaptiveCardsPerPage()

  const apiFilter = filter === 'earned' ? 'earned' : 'locked'
  const isOwn = mode !== 'public'
  const isGuest = mode === 'guest'
  const pageScopeKey = `${mode}:${username ?? 'self'}:${cardsPerPage}`
  const page = pageState.scopeKey === pageScopeKey ? pageState.page : 0

  const { data: paginatedData, isLoading: isBadgesLoading } = useQuery({
    queryKey: isOwn
      ? [isGuest ? 'guestProfileBadges' : 'profileBadges', apiFilter, page, cardsPerPage]
      : ['publicProfileBadges', username, page, cardsPerPage],
    queryFn: () => isGuest
      ? gameApi.getGuestBadgesPaginated(apiFilter, page, cardsPerPage)
      : isOwn
        ? gameApi.getBadgesPaginated(apiFilter, page, cardsPerPage)
        : gameApi.getPublicBadgesPaginated(username!, page, cardsPerPage),
    enabled: isOwn || !!username,
    staleTime: 30_000,
    placeholderData: (prev) => prev,
  })

  const hiddenSecretCount = paginatedData?.hiddenSecretCount ?? 0
  const earnedCount = paginatedData?.earnedCount ?? 0
  const notUnlockedCount = (paginatedData?.lockedVisibleCount ?? 0) + hiddenSecretCount
  const totalPages = paginatedData?.totalPages ?? 1
  const activePage = Math.min(page, Math.max(0, totalPages - 1))

  const paginatedItems = useMemo<AchievementGridItem[]>(() => {
    if (!paginatedData) return []
    return paginatedData.content.map(badge => ({
      type: 'badge' as const,
      badge,
    }))
  }, [paginatedData])

  const isLastPage = (paginatedData?.totalPages ?? 0) <= 1 || page === (paginatedData?.totalPages ?? 1) - 1
  const showHiddenSummary = isOwn && filter === 'locked' && hiddenSecretCount > 0 && isLastPage

  if (mode === 'public' && !isBadgesLoading && paginatedItems.length === 0) {
    return null
  }

  const filterButtons = [
    { id: 'earned' as const, label: t.achievements.earned, count: earnedCount, icon: Trophy },
    { id: 'locked' as const, label: t.achievements.notUnlocked, count: notUnlockedCount, icon: LockKeyhole },
  ]

  const handleFilterChange = (id: AchievementFilter) => {
    setFilter(id)
    setPageState({ scopeKey: pageScopeKey, page: 0 })
  }

  const handlePageChange = (nextPage: number) => {
    setPageState({
      scopeKey: pageScopeKey,
      page: Math.max(0, Math.min(totalPages - 1, nextPage)),
    })
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: isGuest ? 0.15 : 0.5 }}
      className={cn('w-full self-start 2xl:h-full', className)}
    >
      <ProfileGlassPanel className="p-3.5 sm:p-5 lg:flex lg:flex-col 2xl:h-full">
        <div className="mb-3 flex items-end justify-between gap-3 sm:mb-3.5">
          <div>
            <p className="mb-0.5 text-[9px] uppercase tracking-[.2em] text-white/26 sm:mb-1 sm:text-[10px]">
              {t.achievements.title}
            </p>
            <h3 className="text-lg font-semibold text-white/88 sm:text-xl">
              {t.achievements.milestones}
            </h3>
          </div>

          {isOwn && totalPages > 1 && (
            <div className="flex items-center gap-1 sm:hidden">
              <button
                onClick={() => handlePageChange(activePage - 1)}
                disabled={activePage === 0}
                aria-label={t.common.previous}
                className="flex h-7 w-7 items-center justify-center rounded-lg border border-white/[.08] bg-white/[.03] text-white/40 transition-colors disabled:opacity-25 cursor-pointer"
              >
                <ChevronLeft size={14} />
              </button>
              <span className="min-w-[2.5rem] text-center text-[10px] tabular-nums text-white/30">
                {activePage + 1}/{totalPages}
              </span>
              <button
                onClick={() => handlePageChange(activePage + 1)}
                disabled={activePage >= totalPages - 1}
                aria-label={t.common.next}
                className="flex h-7 w-7 items-center justify-center rounded-lg border border-white/[.08] bg-white/[.03] text-white/40 transition-colors disabled:opacity-25 cursor-pointer"
              >
                <ChevronRight size={14} />
              </button>
            </div>
          )}
        </div>

        {isOwn && (
          <div className="mb-3 flex gap-2">
            {filterButtons.map(button => {
              const active = filter === button.id
              const Icon = button.icon

              return (
                <button
                  key={button.id}
                  type="button"
                  onClick={() => handleFilterChange(button.id)}
                  className={cn(
                    'group relative isolate flex-1 overflow-hidden rounded-[1rem] border px-2.5 py-2 text-left transition-all duration-300 cursor-pointer sm:rounded-[1.15rem] sm:px-3 sm:py-2.5',
                    active
                      ? 'border-sky-200/24 bg-[linear-gradient(135deg,rgba(125,211,252,.14),rgba(255,255,255,.06))] text-white shadow-[0_16px_36px_rgba(56,189,248,.12)]'
                      : 'border-white/[.08] bg-[linear-gradient(135deg,rgba(255,255,255,.045),rgba(255,255,255,.02))] text-white/56 hover:border-white/[.14] hover:bg-[linear-gradient(135deg,rgba(125,211,252,.08),rgba(255,255,255,.03))] hover:text-white/82'
                  )}
                >
                  <div className="pointer-events-none absolute inset-0 overflow-hidden rounded-[inherit]">
                    <div className={cn(
                      'absolute inset-y-0 -left-[55%] w-[38%] -skew-x-[20deg] bg-[linear-gradient(100deg,transparent,rgba(255,255,255,.26),transparent)] opacity-0 transition-all duration-700',
                      active ? 'group-hover:left-[115%] group-hover:opacity-100' : 'group-hover:left-[108%] group-hover:opacity-80'
                    )} />
                  </div>

                  <div className="relative flex min-w-0 items-center gap-2 sm:gap-3">
                    <div className={cn(
                      'hidden h-8.5 w-8.5 shrink-0 items-center justify-center rounded-xl border text-white/60 transition-colors sm:flex',
                      active
                        ? 'border-white/[.12] bg-black/16 text-white/84'
                        : 'border-white/[.08] bg-black/12 group-hover:text-white/76'
                    )}>
                      <Icon size={14} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <span className="flex items-center gap-1.5">
                        <Icon size={12} className="shrink-0 sm:hidden" />
                        <span className="block text-[9px] font-semibold uppercase tracking-[.14em] text-white/36 sm:tracking-[.18em]">{button.label}</span>
                      </span>
                      <span className="mt-0.5 block text-[1.05rem] font-black tracking-[-0.05em] text-white sm:text-[1.15rem]">{button.count}</span>
                    </div>
                    <span className={cn(
                      'h-2 w-2 shrink-0 rounded-full sm:h-2.5 sm:w-2.5',
                      active ? 'bg-sky-200/80' : 'bg-white/18'
                    )} />
                  </div>
                </button>
              )
            })}
          </div>
        )}

        <div className="flex min-h-0 flex-1 flex-col overflow-hidden 2xl:min-h-0">
          <div
            ref={gridViewportRef}
            className="max-h-[34rem] overflow-y-auto pr-1 sm:max-h-[36.5rem] lg:max-h-[39.5rem] 2xl:max-h-none 2xl:min-h-0 2xl:flex-1"
          >
            {isBadgesLoading && paginatedItems.length === 0 ? (
              <div
                ref={contentGridRef}
                className="grid auto-rows-fr grid-cols-2 gap-2.5 sm:grid-cols-2 sm:gap-3 lg:grid-cols-3"
              >
                {Array.from({ length: cardsPerPage }).map((_, i) => (
                  <Skeleton key={i} variant="rect" height={isMobile ? 148 : 179} className="rounded-[1.25rem] sm:rounded-[1.45rem]" />
                ))}
              </div>
            ) : paginatedItems.length > 0 || showHiddenSummary ? (
              <>
                {paginatedItems.length > 0 && (
                  <BadgeGrid items={paginatedItems} containerRef={contentGridRef} />
                )}
                {showHiddenSummary && (
                  <div className={paginatedItems.length > 0 ? 'mt-2.5 sm:mt-3' : ''}>
                    <HiddenSecretSummaryCard count={hiddenSecretCount} />
                  </div>
                )}
              </>
            ) : (
              <div className="flex min-h-[10rem] items-center justify-center rounded-[1.25rem] border border-dashed border-white/[.08] bg-white/[.02] px-5 text-center text-[13px] leading-relaxed text-white/34 sm:min-h-[12rem] sm:rounded-[1.5rem] sm:px-6 sm:text-sm">
                {filter === 'earned' ? t.achievements.emptyEarned : t.achievements.emptyLocked}
              </div>
            )}
          </div>

          {totalPages > 1 && (
            <div className="mt-2.5 hidden items-center justify-between gap-4 border-t border-white/[.06] pt-2.5 sm:mt-3 sm:flex sm:pt-3">
              {paginatedData?.totalElements !== undefined && (
                <span className="text-xs text-white/30">
                  {paginatedData.totalElements.toLocaleString()} total
                </span>
              )}
              <div className="ml-auto flex items-center gap-1">
                <button
                  onClick={() => handlePageChange(activePage - 1)}
                  disabled={activePage === 0}
                  aria-label={t.common.previous}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-white/40 transition-all hover:bg-white/[.06] hover:text-white/70 disabled:pointer-events-none disabled:opacity-25 cursor-pointer"
                >
                  <ChevronLeft size={16} />
                </button>
                {Array.from({ length: totalPages }).map((_, i) => (
                  <button
                    key={i}
                    onClick={() => handlePageChange(i)}
                    className={cn(
                      'h-8 min-w-8 rounded-lg px-2 text-xs font-medium transition-all cursor-pointer',
                      i === activePage
                        ? 'border border-white/[.1] bg-white/[.1] text-white'
                        : 'text-white/40 hover:bg-white/[.04] hover:text-white/70'
                    )}
                  >
                    {i + 1}
                  </button>
                ))}
                <button
                  onClick={() => handlePageChange(activePage + 1)}
                  disabled={activePage >= totalPages - 1}
                  aria-label={t.common.next}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-white/40 transition-all hover:bg-white/[.06] hover:text-white/70 disabled:pointer-events-none disabled:opacity-25 cursor-pointer"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}
        </div>
      </ProfileGlassPanel>
    </motion.div>
  )
}
