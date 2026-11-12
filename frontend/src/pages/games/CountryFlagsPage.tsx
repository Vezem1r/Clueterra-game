import { useMemo, useState } from 'react'
import { Flag } from 'lucide-react'
import type { SilhouetteLocale } from '@/api/silhouettes'
import { LegalModal } from '@/components/ui/LegalModal'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { interpolate } from '@/utils/format'
import { FLAG_CANONICAL_PATH, FLAG_SEO_KEYWORDS, FLAG_SHARE_IMAGE, FLAG_SPACE } from './flags/flagGameConstants'
import { FlagGuessPanel } from './flags/FlagGuessPanel'
import { FlagIntro } from './flags/FlagIntro'
import { FlagPlaza, type FlagPlazaTarget } from './flags/FlagPlaza'
import { FlagRevealCard } from './flags/FlagRevealCard'
import { FlagStats } from './flags/FlagStats'
import { useFlagGame } from './flags/useFlagGame'
import { GlobeGameStage } from './globe-game/GlobeGameStage'
import { GlobeGameStageHeader } from './globe-game/GlobeGameStageHeader'
import { GLOBE_GAME_SHEET_SEPARATOR } from './globe-game/globeGameTheme'
import { useGlobeGameStage } from './globe-game/useGlobeGameStage'
import { useCountryPool } from './silhouettes/countryPool'

const OVERVIEW: FlagPlazaTarget = { kind: 'overview' }

export function CountryFlagsPage() {
  const { t, language } = useTranslation()
  const copy = t.flagGame
  const regions = t.silhouetteGame.regions
  const tiers = t.silhouetteGame.tiers
  const locale = language as SilhouetteLocale
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const stage = useGlobeGameStage()
  const { pool: world } = useCountryPool()
  const game = useFlagGame(copy, world, locale)
  const { question, revealed, resumePending, stats, inGame } = game
  const intro = !inGame
  const roundNumber = intro ? null : revealed?.round ?? question?.round ?? null
  const roundLabel = roundNumber == null ? undefined : interpolate(copy.round, { count: roundNumber })
  const raisedIso = revealed?.outcome === 'solved' ? revealed.reveal.countryCode : null
  const regionHint = !intro ? question?.hints.region?.subregion ?? revealed?.regionHint ?? null : null

  const target = useMemo<FlagPlazaTarget>(() => {
    if (raisedIso) return { kind: 'country', iso: raisedIso }
    if (regionHint) return { kind: 'region', subregion: regionHint }
    return OVERVIEW
  }, [raisedIso, regionHint])

  useSeoMeta({
    title: copy.seoTitle,
    description: copy.seoDescription,
    canonicalPath: FLAG_CANONICAL_PATH,
    keywords: FLAG_SEO_KEYWORDS,
    image: FLAG_SHARE_IMAGE,
    imageAlt: copy.flagLabel,
  })

  const panel = intro
    ? <FlagIntro copy={copy} legalNotice={t.home.guestLegalNotice} busy={game.busy || game.loading}
        ready={game.authResolved && !game.loading && world != null} isGuest={!game.isAuthenticated} complete={game.complete}
        current={resumePending ? question : null} onStart={game.start}
        onOpenPrivacy={() => setLegalModal('privacy')} onOpenTerms={() => setLegalModal('terms')} />
    : revealed
      ? <FlagRevealCard copy={copy} regions={regions} language={locale} reveal={revealed.reveal} outcome={revealed.outcome}
          xpAwarded={revealed.xpAwarded} attempts={revealed.attempts} guesses={game.wrongGuesses} busy={game.busy}
          complete={game.complete} onNext={game.nextFlag} />
      : question && <FlagGuessPanel copy={copy} tiers={tiers} regions={regions} language={locale} question={question} guess={game.guess}
          suggestions={game.suggestions} wrongGuesses={game.wrongGuesses} busy={game.busy}
          onGuessChange={game.setGuess} onPick={game.pick} onReveal={game.revealClue} onSkip={game.skip} onShowAnswer={game.showAnswer} />

  const statsCard = <FlagStats copy={copy} stats={stats} />

  return <>
    <GlobeGameStage stage={stage} copy={t.globeGame} space={FLAG_SPACE} guideMode="flag"
      sheet={intro ? 'intro' : revealed ? 'reveal' : 'play'}
      sheetLabel={revealed ? revealed.reveal.names[locale] : intro || !roundLabel ? copy.title : [roundLabel, copy.question].join(GLOBE_GAME_SHEET_SEPARATOR)}
      globe={<FlagPlaza copy={copy.plaza} regions={copy.plazaRegions} language={locale} world={world} solved={game.solved}
        target={target} padding={stage.padding} onUserMove={stage.collapse} className="absolute inset-0" />}
      header={<GlobeGameStageHeader icon={Flag} title={copy.title} titleId="flag-title" gamesLabel={t.nav.games}
        iconClassName="bg-gradient-to-br from-amber-200 to-rose-300 text-[#1f0b10] shadow-[0_0_24px_rgba(251,191,36,.45)]"
        roundLabel={roundLabel} stats={statsCard} onOpenMenu={intro ? undefined : game.openMenu} />}
      stats={statsCard} error={game.error || null}>
      {panel}
    </GlobeGameStage>
    <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
  </>
}
