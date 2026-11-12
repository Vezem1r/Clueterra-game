import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/utils/cn'
import { GLOBE_GAME_PANEL_WIDTH } from './globeGameTheme'
import { RiverBreadcrumb } from '../rivers/RiverBreadcrumb'

export interface GlobeGameStageHeaderProps {
  icon: LucideIcon
  iconClassName: string
  title: string
  titleId: string
  gamesLabel: string
  roundLabel?: string
  stats: ReactNode
  onOpenMenu?: () => void
}

export function GlobeGameStageHeader({ icon: Icon, iconClassName, title, titleId, gamesLabel, roundLabel, stats, onOpenMenu }: GlobeGameStageHeaderProps) {
  const playing = roundLabel != null

  return <header className="mb-3 flex items-center justify-between gap-6 sm:mb-4">
    <div className="min-w-0">
      <RiverBreadcrumb gamesLabel={gamesLabel} title={title} roundLabel={roundLabel} onOpenMenu={onOpenMenu} />
      <div className={cn('mt-2 flex items-center gap-3 [text-shadow:0_2px_18px_rgba(0,0,0,.6)]', playing && 'max-lg:sr-only')}>
        <span aria-hidden="true" className={cn('flex size-9 shrink-0 items-center justify-center rounded-xl sm:size-10', iconClassName)}>
          <Icon size={20} strokeWidth={2.4} />
        </span>
        <h1 id={titleId} className="text-[1.6rem] font-extrabold leading-none tracking-[-.035em] text-white sm:text-[1.9rem] lg:text-[2.1rem]">
          {title}
        </h1>
      </div>
    </div>
    {playing && <div className={cn('hidden shrink-0 lg:block', GLOBE_GAME_PANEL_WIDTH)}>{stats}</div>}
  </header>
}
