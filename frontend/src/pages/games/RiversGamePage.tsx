import { useMemo, useState } from 'react'
import { Waves } from 'lucide-react'
import { LegalModal } from '@/components/ui/LegalModal'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { GlobeGameStage } from './globe-game/GlobeGameStage'
import { GlobeGameStageHeader } from './globe-game/GlobeGameStageHeader'
import { GLOBE_GAME_SHEET_SEPARATOR } from './globe-game/globeGameTheme'
import { useGlobeGameStage } from './globe-game/useGlobeGameStage'
import { RiverGuessPanel } from './rivers/RiverGuessPanel'
import { RiverIntro } from './rivers/RiverIntro'
import { RiverMap } from './rivers/RiverMap'
import { RiverRevealCard } from './rivers/RiverRevealCard'
import { RiverStats } from './rivers/RiverStats'
import {
  RIVER_BORDERS_CLUE,
  RIVER_CANONICAL_PATH,
  RIVER_EXAMPLE_MAP,
  RIVER_SEO_KEYWORDS,
  RIVER_SHARE_IMAGE,
} from './rivers/riverGameConstants'
import { RiverGlobe, type RiverGlobeMode } from './rivers/globe/RiverGlobe'
import { RIVER_COLORS } from './rivers/globe/riverGlobeConstants'
import { useRiverGame } from './rivers/useRiverGame'

export function RiversGamePage() {
  const { t } = useTranslation()
  const copy = t.riverGame
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const stage = useGlobeGameStage()
  const game = useRiverGame(copy)
  const { question, revealed, resumePending, stats, inGame } = game
  const intro = !inGame
  const current = resumePending ? question : null
  const riverId = revealed?.questionId ?? question?.id ?? null
  const course = revealed?.course ?? question?.course ?? null
  const roundNumber = intro ? null : revealed?.roundNumber ?? question?.roundNumber ?? null
  const mode: RiverGlobeMode = revealed ? 'reveal' : question ? 'play' : 'idle'
  const places = useMemo(() => (revealed ? revealed.reveal.facts : question?.revealedHints ?? [])
    .flatMap(item => item.place ? [item.place] : []), [revealed, question])
  const showBorders = revealed != null || (question != null && !intro && question.hintsUsed >= RIVER_BORDERS_CLUE)
  const { padding } = stage
  const roundLabel = roundNumber == null ? undefined : interpolate(copy.round, { count: roundNumber })

  useSeoMeta({
    title: copy.seoTitle,
    description: copy.seoDescription,
    canonicalPath: RIVER_CANONICAL_PATH,
    keywords: RIVER_SEO_KEYWORDS,
    image: RIVER_SHARE_IMAGE,
    imageAlt: copy.exampleAlt,
  })

  const map = revealed
    ? { url: revealed.reveal.mapImageUrl, alt: interpolate(copy.imageAltAnswer, { name: revealed.reveal.name }) }
    : question
      ? { url: question.imageUrl, alt: showBorders ? copy.imageAltBorders : copy.imageAltPlain }
      : { url: RIVER_EXAMPLE_MAP, alt: copy.exampleAlt }
  const label = question || revealed ? map.alt : copy.globe.mapLabel

  const fallback = <div className="absolute flex items-center" style={{ top: padding.top, right: padding.right, bottom: padding.bottom, left: padding.left }}>
    <RiverMap copy={copy} imageUrl={map.url} alt={map.alt} caption={question || revealed ? undefined : copy.exampleCaption} className="w-full" />
  </div>

  const panel = intro
    ? <RiverIntro copy={copy} legalNotice={t.home.guestLegalNotice} busy={game.busy || game.loading}
        ready={game.authResolved && !game.loading} isGuest={!game.isAuthenticated} current={current} draft={game.guess}
        onStart={game.start} onOpenPrivacy={() => setLegalModal('privacy')} onOpenTerms={() => setLegalModal('terms')} />
    : revealed
      ? <RiverRevealCard copy={copy} reveal={revealed.reveal} outcome={revealed.outcome}
          xpAwarded={revealed.xpAwarded} attempts={revealed.attempts} busy={game.busy} onNext={game.nextRiver} />
      : question && <RiverGuessPanel copy={copy} question={question} guess={game.guess} suggestions={game.suggestions}
          wrongGuesses={game.wrongGuesses} busy={game.busy}
          onGuessChange={game.changeGuess} onPickSuggestion={game.pickSuggestion} onSubmit={game.submitGuess}
          onReveal={game.revealClue} onSkip={game.skip} onShowAnswer={game.showAnswer} />

  const statsCard = <RiverStats copy={copy} stats={stats} />

  return <>
    <GlobeGameStage stage={stage} copy={t.globeGame} space={RIVER_COLORS.space} guideMode="river"
      sheet={intro ? 'intro' : revealed ? 'reveal' : 'play'}
      sheetLabel={revealed ? revealed.reveal.name : intro || !roundLabel ? copy.title : [roundLabel, copy.question].join(GLOBE_GAME_SHEET_SEPARATOR)}
      globe={<RiverGlobe copy={copy} label={label} riverId={riverId} course={course} places={places} mode={mode} showBorders={showBorders}
        padding={padding} chromeOffset={stage.chromeOffset} chromeRight={stage.chromeRight} fallback={fallback} className="absolute inset-0"
        controlsStyle={stage.controlsStyle} onUserMove={stage.collapse}
        controlsClassName={cn('right-3 lg:left-6 lg:right-auto lg:top-1/2 lg:-translate-y-1/2', stage.layout.height === 0 && 'invisible')} />}
      header={<GlobeGameStageHeader icon={Waves} title={copy.title} titleId="river-title" gamesLabel={t.nav.games}
        iconClassName="bg-gradient-to-br from-sky-300 to-cyan-400 text-[#04131d] shadow-[0_0_24px_rgba(56,189,248,.45)]"
        roundLabel={roundLabel} stats={statsCard} onOpenMenu={intro ? undefined : game.openMenu} />}
      stats={statsCard} error={game.error || null}>
      {panel}
    </GlobeGameStage>
    <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
  </>
}
