import { useCallback, useMemo, useState } from 'react'
import { ScanSearch } from 'lucide-react'
import { LegalModal } from '@/components/ui/LegalModal'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameStage } from './globe-game/GlobeGameStage'
import { GlobeGameStageHeader } from './globe-game/GlobeGameStageHeader'
import { GLOBE_GAME_SHEET_SEPARATOR } from './globe-game/globeGameTheme'
import { useGlobeGameStage } from './globe-game/useGlobeGameStage'
import { SILHOUETTE_CANONICAL_PATH, SILHOUETTE_COLORS, SILHOUETTE_SEO_KEYWORDS, SILHOUETTE_SHARE_IMAGE } from './silhouettes/silhouetteGameConstants'
import { SilhouetteGlobe, type SilhouetteCamera } from './silhouettes/SilhouetteGlobe'
import { SilhouetteGuessPanel } from './silhouettes/SilhouetteGuessPanel'
import { SilhouetteIntro } from './silhouettes/SilhouetteIntro'
import { silhouetteMissDistance } from './silhouettes/silhouetteMiss'
import type { SilhouetteGlobeMiss } from './silhouettes/silhouetteMissArrow'
import { SilhouettePassport } from './silhouettes/SilhouettePassport'
import { SilhouetteRevealCard } from './silhouettes/SilhouetteRevealCard'
import { SilhouetteStats } from './silhouettes/SilhouetteStats'
import { useSilhouetteWorld } from './silhouettes/silhouetteWorld'
import { useSilhouetteGame } from './silhouettes/useSilhouetteGame'
import { useSilhouettePaintMode } from './silhouettes/useSilhouettePaintMode'

const IDLE_CAMERA: SilhouetteCamera = { kind: 'idle' }
const HOLD_CAMERA: SilhouetteCamera = { kind: 'hold' }

export function CountrySilhouettesPage() {
  const { t, language } = useTranslation()
  const copy = t.silhouetteGame
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const stage = useGlobeGameStage()
  const { world } = useSilhouetteWorld()
  const game = useSilhouetteGame(copy, world, language)
  const paintMode = useSilhouettePaintMode()
  const paint = useMemo(() => ({
    mode: paintMode.mode,
    label: `${copy.paintLabel}: ${paintMode.mode === 'FLAG' ? copy.paintFlag : copy.paintColors}`,
    hint: copy.paintHint,
    onToggle: paintMode.toggle,
  }), [copy, paintMode.mode, paintMode.toggle])
  const { question, revealed, resumePending, stats, inGame } = game
  const intro = !inGame
  const roundNumber = intro ? null : revealed?.round ?? question?.round ?? null
  const roundLabel = roundNumber == null ? undefined : interpolate(copy.round, { count: roundNumber })

  const location = question && !intro ? question.hints.location : null
  const region = question && !intro ? question.hints.region : null
  const camera = useMemo<SilhouetteCamera>(() => {
    if (revealed) return { kind: 'country', center: revealed.reveal.center, bounds: revealed.reveal.bounds }
    if (location) return { kind: 'location', center: location.center, bounds: location.bounds }
    if (region) return { kind: 'region', center: region.center }
    return intro ? IDLE_CAMERA : HOLD_CAMERA
  }, [revealed, location, region, intro])

  const hintIso = useMemo(() => {
    if (!location || !world) return null
    return world.countries.find(country => country.center[0] === location.center[0] && country.center[1] === location.center[1])?.iso ?? null
  }, [location, world])

  const missed = useMemo(() => intro ? [] : game.wrongGuesses.map(entry => entry.country.iso), [game.wrongGuesses, intro])
  const misses = useMemo<SilhouetteGlobeMiss[]>(() => intro ? [] : game.wrongGuesses.flatMap(({ country, miss }) => miss ? [{
    iso: country.iso,
    center: country.center,
    distanceKm: miss.distanceKm,
    bearingDeg: miss.bearingDeg,
    label: silhouetteMissDistance(copy, language, miss.distanceKm),
  }] : []), [game.wrongGuesses, intro, copy, language])

  const renderCard = useCallback((iso: string) => <SilhouettePassport copy={copy} iso={iso} language={language} />, [copy, language])

  useSeoMeta({
    title: copy.seoTitle,
    description: copy.seoDescription,
    canonicalPath: SILHOUETTE_CANONICAL_PATH,
    keywords: SILHOUETTE_SEO_KEYWORDS,
    image: SILHOUETTE_SHARE_IMAGE,
    imageAlt: copy.shapeLabel,
  })

  const panel = intro
    ? <SilhouetteIntro copy={copy} legalNotice={t.home.guestLegalNotice} busy={game.busy || game.loading}
        ready={game.authResolved && !game.loading && world != null} isGuest={!game.isAuthenticated} complete={game.complete}
        current={resumePending ? question : null} onStart={game.start}
        onOpenPrivacy={() => setLegalModal('privacy')} onOpenTerms={() => setLegalModal('terms')} />
    : revealed
      ? <SilhouetteRevealCard copy={copy} language={language} reveal={revealed.reveal} outcome={revealed.outcome}
          xpAwarded={revealed.xpAwarded} attempts={revealed.attempts} guesses={game.wrongGuesses} busy={game.busy} complete={game.complete} onNext={game.nextCountry} />
      : question && <SilhouetteGuessPanel copy={copy} language={language} question={question} guess={game.guess}
          suggestions={game.suggestions} wrongGuesses={game.wrongGuesses} busy={game.busy}
          onGuessChange={game.setGuess} onPick={game.pick} onReveal={game.revealClue} onSkip={game.skip} onShowAnswer={game.showAnswer} />

  const statsCard = <SilhouetteStats copy={copy} stats={stats} />

  return <>
    <GlobeGameStage stage={stage} copy={t.globeGame} space={SILHOUETTE_COLORS.space} guideMode="silhouette"
      sheet={intro ? 'intro' : revealed ? 'reveal' : 'play'}
      sheetLabel={revealed ? revealed.reveal.names[language] : intro || !roundLabel ? copy.title : [roundLabel, copy.question].join(GLOBE_GAME_SHEET_SEPARATOR)}
      globe={<SilhouetteGlobe copy={copy.globe} world={world} solved={game.solved} focusIso={revealed?.reveal.countryCode ?? null}
        camera={camera} hintIso={hintIso} missed={missed} misses={misses} regionKey={region?.subregion ?? null} padding={stage.padding}
        chromeOffset={stage.chromeOffset} chromeRight={stage.chromeRight} renderCard={renderCard} paint={paint} className="absolute inset-0"
        controlsStyle={stage.controlsStyle} onUserMove={stage.collapse}
        controlsClassName="right-3 lg:left-6 lg:right-auto lg:top-1/2 lg:-translate-y-1/2" />}
      header={<GlobeGameStageHeader icon={ScanSearch} title={copy.title} titleId="silhouette-title" gamesLabel={t.nav.games}
        iconClassName="bg-gradient-to-br from-cyan-200 to-teal-300 text-[#03141c] shadow-[0_0_24px_rgba(103,232,249,.45)]"
        roundLabel={roundLabel} stats={statsCard} onOpenMenu={intro ? undefined : game.openMenu} />}
      stats={statsCard} error={game.error || null}>
      {panel}
    </GlobeGameStage>
    <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
  </>
}
