import { ArrowRight, Globe2, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'

import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { Header } from '@/components/layout/Header'
import { GAME_CATALOG } from '@/features/games/gameCatalog'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { FeaturedGameCard } from './FeaturedGameCard'
import { GameCatalogCard } from './GameCatalogCard'
import { QUIZ_CANONICAL_PATH } from './countries-quiz/quizGameConstants'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

const GAMES_PATH = '/games'
const GAMES_KEYWORDS = 'geography games, flag quiz, country shapes quiz, rivers quiz, guess the country, daily geography games'
const GAMES_SHARE_IMAGE = '/images/share/games.jpg'
const exploreLinkClass = 'font-medium text-white/80 underline decoration-white/20 underline-offset-[3px] transition-colors hover:text-emerald-200'

export function GamesPage() {
  const { t } = useTranslation()
  const copy = t.games
  const localize = useLocalizedPath()

  useSeoMeta({
    title: copy.seoTitle,
    description: copy.seoDescription,
    canonicalPath: GAMES_PATH,
    keywords: GAMES_KEYWORDS,
    image: GAMES_SHARE_IMAGE,
  })

  return (
    <div className="relative flex min-h-[100dvh] flex-col overflow-x-clip bg-background bg-[radial-gradient(ellipse_65%_45%_at_72%_85%,rgba(14,165,233,.11),transparent_100%)] text-white">
      <GameAuroraBackdrop variant="hero" className="!absolute" />
      <Header />

      <main className="relative z-10 flex-1 px-4 pb-12 pt-8 sm:px-7 sm:pt-12 md:pb-28 xl:px-10">
        <div className="mx-auto max-w-[1500px]">
          <header className="max-w-[51rem]">
            <p className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[.2em] text-emerald-200/80"><Sparkles size={13} />{copy.eyebrow}</p>
            <h1 className="mt-3 text-balance text-4xl font-semibold tracking-[-.05em] text-white sm:text-5xl lg:text-[3.25rem]" id="games-title">
              {copy.titleLead}<span className="bg-gradient-to-r from-cyan-200 via-sky-300 to-violet-300 bg-clip-text text-transparent">{copy.titleAccent}</span>
            </h1>
            <p className="mt-3 max-w-[43rem] text-pretty text-sm leading-6 text-white/70 sm:text-base sm:leading-7">{copy.intro}</p>
          </header>

          <section className="mt-5 sm:mt-6" aria-labelledby="featured-games-heading">
            <h2 id="featured-games-heading" className="mb-3 text-xl font-semibold tracking-[-.035em] text-white sm:text-2xl">{copy.featuredTitle}</h2>
            <div className="grid gap-3.5 md:grid-cols-2">
              <FeaturedGameCard family="clues" copy={copy} />
              <FeaturedGameCard family="budget" copy={copy} />
            </div>
          </section>

          <section className="mt-6" aria-labelledby="quick-games-heading">
            <h2 id="quick-games-heading" className="mb-3 text-xl font-semibold tracking-[-.035em] text-white sm:text-2xl">{copy.quickTitle}</h2>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
              {GAME_CATALOG.filter(game => game.group === 'discover').map(game => <GameCatalogCard key={game.id} game={game} copy={copy} />)}
            </div>
          </section>

          <section className="relative mt-8 w-full overflow-hidden rounded-[1.6rem] border border-white/[.09] bg-[linear-gradient(115deg,rgba(13,42,43,.7),rgba(12,17,27,.9)_65%)] p-5 shadow-[0_24px_70px_rgba(0,0,0,.18)] sm:mt-9 sm:p-7" aria-labelledby="explore-games-heading">
            <div aria-hidden="true" className="pointer-events-none absolute -right-16 -top-28 h-56 w-56 rounded-full bg-emerald-300/[.06] blur-3xl" />
            <div className="relative flex items-center gap-3">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-emerald-200/20 bg-emerald-200/[.06] text-emerald-200"><Globe2 size={18} strokeWidth={1.7} aria-hidden="true" /></span>
              <h2 id="explore-games-heading" className="text-lg font-semibold tracking-[-.025em] text-white sm:text-xl">{copy.exploreTitle}</h2>
            </div>
            <p className="relative mt-3 text-sm leading-6 text-white/65">
              {copy.exploreAvailableLead}
              <Link to="/" className={exploreLinkClass}>{copy.exploreGuessCountry}</Link>
              {copy.exploreAvailableJoin}
              <Link to={localize('/games/country-silhouettes')} className={exploreLinkClass}>{copy.exploreShapes}</Link>
              {copy.exploreAfterShapes}
              <Link to={localize('/games/country-flags')} className={exploreLinkClass}>{copy.exploreFlags}</Link>
              {copy.exploreAfterFlags}
              <Link to={localize('/games/rivers')} className={exploreLinkClass}>{copy.exploreRivers}</Link>
              {copy.exploreAvailableTail}
              <Link to={localize(QUIZ_CANONICAL_PATH)} className={exploreLinkClass}>{copy.exploreQuiz}</Link>
              {copy.exploreQuizTail}
            </p>
            <Link to="/" className="relative mt-4 inline-flex items-center gap-2 rounded-full border border-violet-300/60 bg-violet-400/25 px-3.5 py-2 text-xs font-semibold text-white shadow-[0_0_24px_rgba(167,139,250,.22)] transition-[background-color,border-color] hover:border-violet-200/80 hover:bg-violet-400/35">
              {copy.exploreCta}<ArrowRight size={14} aria-hidden="true" />
            </Link>
          </section>
        </div>
      </main>

      <HomeLegalStrip />
    </div>
  )
}
