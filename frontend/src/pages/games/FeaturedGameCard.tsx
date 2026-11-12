import { ArrowUpRight, Banknote, CalendarDays, Infinity as InfinityIcon, Map } from 'lucide-react'
import { Link } from 'react-router-dom'

import { isGameAvailable } from '@/features/games/gameCatalog'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { GameCatalogMotion } from './GameCatalogMotion'
import { BudgetConstructionArtwork } from './BudgetConstructionArtwork'
import { ClueCatalogArtwork } from './CatalogVectorArtwork'

interface FeaturedGameCardProps {
  family: 'clues' | 'budget'
  copy: TranslationKeys['games']
}

export function FeaturedGameCard({ family, copy }: FeaturedGameCardProps) {
  const budget = family === 'budget'
  const dailyPath = budget ? '/budget' : '/'
  const unlimitedPath = budget ? '/games/budgetguessr-unlimited' : '/games/clueterra-unlimited'
  const title = budget ? copy.budgetFeatureTitle : copy.cluesFeatureTitle
  const description = budget ? copy.budgetFeatureDescription : copy.cluesFeatureDescription

  return (
    <article className={cn(
      'relative isolate flex min-h-[282px] flex-col overflow-hidden rounded-[1.35rem] border shadow-[0_20px_55px_rgba(0,0,0,.28)] sm:min-h-[242px] lg:min-h-[246px]',
      budget ? 'border-amber-300/40 bg-[#1d1510]' : 'border-violet-300/40 bg-[#111327]',
    )} data-featured-family={family}>
      {budget
        ? <BudgetConstructionArtwork />
        : <ClueCatalogArtwork />}
      <div className={cn(
        'absolute inset-0',
        budget
          ? 'bg-[linear-gradient(90deg,rgba(7,26,22,.96)_0%,rgba(7,26,22,.83)_55%,rgba(7,26,22,.2)_100%)] sm:bg-[linear-gradient(90deg,rgba(7,26,22,.93)_0%,rgba(7,26,22,.72)_41%,rgba(7,26,22,.1)_72%,transparent_100%)]'
          : 'bg-[linear-gradient(90deg,rgba(13,13,31,.96)_0%,rgba(13,13,31,.83)_55%,rgba(13,13,31,.2)_100%)] sm:bg-[linear-gradient(90deg,rgba(13,13,31,.93)_0%,rgba(13,13,31,.72)_41%,rgba(13,13,31,.1)_72%,transparent_100%)]',
      )} />
      <div className="absolute inset-0 bg-gradient-to-t from-black/25 via-transparent to-transparent" />
      {!budget && <GameCatalogMotion family={family} />}

      <div className="relative flex min-h-[282px] flex-1 flex-col p-5 sm:min-h-[242px] sm:p-6 lg:min-h-[246px]">
        <div className="flex items-start gap-3">
          <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border bg-black/35', budget ? 'border-amber-200/45 text-amber-100' : 'border-violet-200/45 text-violet-100')}>
            {budget ? <Banknote size={21} /> : <Map size={21} />}
          </span>
          <div className="min-w-0 pt-0.5">
            <p className={cn('text-[10px] font-bold uppercase tracking-[.16em]', budget ? 'text-amber-100' : 'text-violet-100')}>{budget ? copy.budgetFeatureLabel : copy.cluesFeatureLabel}</p>
            <h3 className="mt-1 text-2xl font-bold tracking-[-.04em] text-white sm:text-[1.7rem]">{title}</h3>
          </div>
        </div>

        <p className="mt-4 max-w-[21rem] text-sm leading-6 text-white/85">{description}</p>

        <div className="mt-auto flex flex-wrap gap-2 pt-6">
          <Link to={dailyPath} className={cn('inline-flex min-h-10 items-center justify-center gap-2 rounded-xl px-4 text-xs font-bold text-[#101016] shadow-lg transition hover:-translate-y-0.5 hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transform-none', budget ? 'bg-amber-300 shadow-amber-300/20' : 'bg-violet-300 shadow-violet-300/20')}>
            <CalendarDays size={15} />{copy.playDaily}
          </Link>
          {isGameAvailable(budget ? 'budgetUnlimited' : 'cluesUnlimited')
            ? <Link to={unlimitedPath} className={cn('inline-flex min-h-10 items-center justify-center gap-2 whitespace-nowrap rounded-xl border bg-[#0b0d16]/85 px-3 text-xs font-bold text-white transition hover:-translate-y-0.5 hover:bg-[#171b2b] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transform-none sm:px-4', budget ? 'border-amber-300/65' : 'border-violet-300/65')}>
              <InfinityIcon size={16} />{copy.playUnlimited}<ArrowUpRight size={13} />
            </Link>
            : <span className={cn('inline-flex min-h-10 items-center justify-center gap-2 whitespace-nowrap rounded-xl border bg-[#0b0d16]/85 px-3 text-xs font-bold text-white/65 sm:px-4', budget ? 'border-amber-300/30' : 'border-violet-300/30')}>
              <InfinityIcon size={16} className="shrink-0" />{copy.playUnlimited}<span className="ml-1 shrink-0 rounded-full border border-white/20 px-1.5 py-0.5 text-[10px] uppercase tracking-wide sm:px-2">{copy.comingSoon}</span>
            </span>}
        </div>
      </div>
    </article>
  )
}
