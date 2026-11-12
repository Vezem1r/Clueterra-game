import { ArrowUpRight, Banknote, CarFront, Flag, Map, ScanSearch, Waves } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { CatalogGame, GameFamily } from '@/features/games/gameCatalog'
import { isGameAvailable } from '@/features/games/gameCatalog'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'
import { GameCatalogMotion } from './GameCatalogMotion'
import { RiverAerialArtwork } from './RiverAerialArtwork'
import { BudgetConstructionArtwork } from './BudgetConstructionArtwork'
import { ClueCatalogArtwork, FlagCatalogArtwork, QuizCatalogArtwork, SilhouetteCatalogArtwork } from './CatalogVectorArtwork'

interface GameCatalogCardProps {
  game: CatalogGame
  copy: TranslationKeys['games']
  size?: 'quick' | 'mini'
}

const FAMILY_STYLE: Record<GameFamily, {
  border: string
  accent: string
  button: string
  icon: typeof Map
}> = {
  clues: {
    border: 'border-violet-300/45',
    accent: 'text-violet-100',
    button: 'bg-violet-300 text-[#130e20]',
    icon: Map,
  },
  budget: {
    border: 'border-amber-300/45',
    accent: 'text-amber-100',
    button: 'bg-amber-300 text-[#1d1409]',
    icon: Banknote,
  },
  silhouette: {
    border: 'border-cyan-300/55',
    accent: 'text-cyan-100',
    button: 'bg-cyan-300 text-[#08212a]',
    icon: ScanSearch,
  },
  flag: {
    border: 'border-rose-300/55',
    accent: 'text-rose-100',
    button: 'bg-rose-300 text-[#290c17]',
    icon: Flag,
  },
  river: {
    border: 'border-blue-300/55',
    accent: 'text-sky-100',
    button: 'bg-blue-400 text-[#071427]',
    icon: Waves,
  },
  quiz: {
    border: 'border-amber-200/55',
    accent: 'text-amber-100',
    button: 'bg-amber-200 text-[#173741]',
    icon: CarFront,
  },
}

function CatalogArtwork({ family }: { family: GameFamily }) {
  switch (family) {
    case 'clues': return <ClueCatalogArtwork />
    case 'budget': return <BudgetConstructionArtwork />
    case 'silhouette': return <SilhouetteCatalogArtwork />
    case 'flag': return <FlagCatalogArtwork />
    case 'river': return <RiverAerialArtwork />
    case 'quiz': return <QuizCatalogArtwork />
  }
}

export function GameCatalogCard({ game, copy, size = 'quick' }: GameCatalogCardProps) {
  const localize = useLocalizedPath()
  const text = copy.entries[game.id]
  const style = FAMILY_STYLE[game.family]
  const Icon = style.icon
  const mini = size === 'mini'
  const available = isGameAvailable(game.id)
  const cardClass = cn(
    'group relative isolate flex min-w-0 flex-col overflow-hidden rounded-[1.2rem] border bg-[#101720] text-left shadow-[0_15px_40px_rgba(0,0,0,.22)]',
    available && 'transition-[border-color,transform,box-shadow] duration-300 hover:-translate-y-1 hover:border-white/70 hover:shadow-[0_24px_50px_rgba(0,0,0,.35)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-200 motion-reduce:transform-none',
    style.border,
    mini ? 'h-[170px] w-[220px] shrink-0 snap-start' : 'h-[188px] sm:h-[270px]',
  )
  const content = <>
    <div className={cn('absolute inset-0 overflow-hidden', available && 'transition-transform duration-500 group-hover:scale-[1.04] motion-reduce:transform-none')}>
      <CatalogArtwork family={game.family} />
      {game.family !== 'budget' && game.family !== 'river' && <GameCatalogMotion family={game.family} />}
    </div>
    <div className={cn('absolute inset-0', mini
      ? 'bg-[linear-gradient(0deg,rgba(5,11,19,.96)_0%,rgba(5,11,19,.66)_35%,transparent_82%)]'
      : 'bg-[linear-gradient(0deg,rgba(5,11,19,.98)_0%,rgba(5,11,19,.91)_22%,rgba(5,11,19,.2)_66%,transparent_100%)]')} />
    <div className={cn('relative flex h-full flex-col', mini ? 'p-3.5' : 'p-4 sm:p-5')}>
      <span className={cn('flex items-center justify-center rounded-lg border border-white/35 bg-[#071018]/75', style.accent, mini ? 'h-8 w-8' : 'h-9 w-9')}>
        <Icon size={mini ? 16 : 18} strokeWidth={1.8} />
      </span>
      {available && !mini && <span aria-hidden="true" className={cn('absolute right-4 top-4 flex h-9 w-9 items-center justify-center rounded-full border border-white/40 bg-[#071018]/75 sm:hidden', style.accent)}><ArrowUpRight size={18} /></span>}
      {!available && <span className="absolute right-3 top-3 rounded-full border border-white/25 bg-[#071018]/85 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wide text-white/85">{copy.comingSoon}</span>}
      <div className="mt-auto">
        <p className={cn('font-bold uppercase tracking-[.13em]', style.accent, mini ? 'text-[9px]' : 'text-[10px]')}>{text.meta}</p>
        <h3 className={cn('mt-1 font-bold leading-tight tracking-[-.03em] text-white', mini ? 'line-clamp-2 text-base' : 'text-lg sm:text-xl')}>{text.name}</h3>
        {!mini && <p className="mt-1.5 line-clamp-2 max-w-[78%] text-xs leading-5 text-white/85 sm:max-w-[25rem]">{text.description}</p>}
        {mini ? available && <ArrowUpRight aria-hidden="true" size={16} className={cn('absolute bottom-3 right-3', style.accent)} /> : (
          <span className={cn('mt-3 hidden min-h-9 w-full items-center justify-center gap-2 rounded-xl text-xs font-bold shadow-lg sm:inline-flex', available ? style.button : 'border border-white/20 bg-[#0b141e]/85 text-white/65')}>
            {available ? copy.playNow : copy.comingSoon}{available && <ArrowUpRight size={14} aria-hidden="true" />}
          </span>
        )}
      </div>
    </div>
  </>

  return available
    ? <Link to={localize(game.path)} data-game-id={game.id} className={cardClass}>{content}</Link>
    : <article data-game-id={game.id} className={cardClass}>{content}</article>
}
