import { Link } from 'react-router-dom'
import { cn } from '@/utils/cn'
import { RIVER_BREADCRUMB_LABEL, RIVER_GAMES_PATH, RIVER_HOME_LABEL, RIVER_HOME_PATH } from './riverGameConstants'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

interface RiverBreadcrumbProps {
  gamesLabel: string
  title: string
  roundLabel?: string
  onOpenMenu?: () => void
  className?: string
}

const CRUMB_LINK = 'shrink-0 transition-colors hover:text-white/80'

export function RiverBreadcrumb({ gamesLabel, title, roundLabel, onOpenMenu, className }: RiverBreadcrumbProps) {
  const localize = useLocalizedPath()
  return <nav aria-label={RIVER_BREADCRUMB_LABEL} className={cn('flex min-w-0 items-center gap-2 overflow-hidden text-xs text-white/48', className)}>
    <Link to={RIVER_HOME_PATH} className={CRUMB_LINK}>{RIVER_HOME_LABEL}</Link>
    <span aria-hidden="true">/</span>
    <Link to={localize(RIVER_GAMES_PATH)} className={CRUMB_LINK}>{gamesLabel}</Link>
    <span aria-hidden="true">/</span>
    {roundLabel && onOpenMenu
      ? <>
        <button type="button" onClick={onOpenMenu} className={CRUMB_LINK}>{title}</button>
        <span aria-hidden="true">/</span>
        <span className="truncate text-white/68" aria-current="page">{roundLabel}</span>
      </>
      : <span className="truncate text-white/68" aria-current="page">{title}</span>}
  </nav>
}
