import { useMemo, useState } from 'react'
import { Globe2 } from 'lucide-react'
import type { SilhouetteLocale } from '@/api/silhouettes'
import { LegalModal } from '@/components/ui/LegalModal'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { interpolate } from '@/utils/format'
import { answerCountryCode } from './countries-quiz/quizFacts'
import {
  QUIZ_CANONICAL_PATH,
  QUIZ_SEO_KEYWORDS,
  QUIZ_SHARE_IMAGE,
  QUIZ_SPACE,
  QUIZ_SUMMARY_POSITION,
} from './countries-quiz/quizGameConstants'
import { QuizGlobe, type QuizGlobeBurst, type QuizGlobeDestination } from './countries-quiz/QuizGlobe'
import { QuizIntro } from './countries-quiz/QuizIntro'
import { QuizQuestionPanel } from './countries-quiz/QuizQuestionPanel'
import { QuizStats } from './countries-quiz/QuizStats'
import { QuizStopSummary } from './countries-quiz/QuizStopSummary'
import { QuizTravelCard } from './countries-quiz/QuizTravelCard'
import { useQuizGame } from './countries-quiz/useQuizGame'
import { GlobeGameStage } from './globe-game/GlobeGameStage'
import { GlobeGameStageHeader } from './globe-game/GlobeGameStageHeader'
import { GLOBE_GAME_SHEET_SEPARATOR } from './globe-game/globeGameTheme'
import { useGlobeGameStage } from './globe-game/useGlobeGameStage'
import { useSilhouetteWorld } from './silhouettes/silhouetteWorld'

export function CountriesQuizPage() {
  const { t, language } = useTranslation()
  const copy = t.quizGame
  const regions = t.silhouetteGame.regions
  const locale = language as SilhouetteLocale
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const stage = useGlobeGameStage()
  const { world } = useSilhouetteWorld()
  const game = useQuizGame(copy)
  const { session, current, position, journey, inGame } = game
  const intro = !inGame
  const summary = current != null && position === QUIZ_SUMMARY_POSITION
  const question = current?.questions.find(item => item.position === position) ?? null
  const stopLabel = intro || !current ? undefined : interpolate(copy.stop, { count: current.stop })
  const countryName = (iso: string) => world?.byIso.get(iso)?.names[locale] ?? iso

  const next = useMemo<QuizGlobeDestination | null>(() =>
    summary && current?.next ? { iso: current.next.country.countryCode, mode: current.next.mode, route: current.next.route } : null, [summary, current])
  const burst = useMemo<QuizGlobeBurst | null>(() => current?.finished
    ? { id: current.stop, iso: current.country.countryCode, level: current.level }
    : null, [current])
  const focusCode = question ? answerCountryCode(question) : null
  const focus = focusCode && focusCode !== current?.country.countryCode ? focusCode : null

  useSeoMeta({
    title: copy.seoTitle,
    description: copy.seoDescription,
    canonicalPath: QUIZ_CANONICAL_PATH,
    keywords: QUIZ_SEO_KEYWORDS,
    image: QUIZ_SHARE_IMAGE,
    imageAlt: copy.globe.label,
  })

  const panel = intro
    ? <QuizIntro copy={copy} legalNotice={t.home.guestLegalNotice} language={locale} busy={game.busy || game.loading}
        ready={game.authResolved && !game.loading} isGuest={!game.isAuthenticated} complete={session?.complete ?? false}
        current={game.resumePending ? current : null} onStart={game.start}
        onOpenPrivacy={() => setLegalModal('privacy')} onOpenTerms={() => setLegalModal('terms')} />
    : journey
      ? <QuizTravelCard copy={copy} mode={journey.mode}
          from={{ code: journey.from, name: countryName(journey.from) }} to={countryName(journey.to)} />
      : current && summary
        ? <QuizStopSummary copy={copy} language={locale} pool={world} stop={current} complete={session?.complete ?? false}
            busy={game.busy} onTravel={game.travel} />
        : current && question && <QuizQuestionPanel copy={copy} regions={regions} language={locale} pool={world}
            stop={current} question={question} busy={game.busy} onAnswer={game.answer} onNext={game.nextQuestion} />

  const statsCard = <QuizStats copy={copy} stats={session?.stats ?? null} />
  const sheetTitle = current ? current.country.names[locale] : copy.title

  return <>
    <GlobeGameStage stage={stage} copy={t.globeGame} space={QUIZ_SPACE} guideMode="quiz"
      sheet={intro ? 'intro' : summary || journey ? 'reveal' : 'play'}
      sheetLabel={intro || !stopLabel ? copy.title : [stopLabel, sheetTitle].join(GLOBE_GAME_SHEET_SEPARATOR)}
      globe={<QuizGlobe world={world} countries={session?.countries ?? []} trail={session?.trail ?? []}
        current={current?.country.countryCode ?? null} next={next} focus={focus} journey={journey} burst={burst} overview={intro}
        padding={stage.padding} label={copy.globe.label} className="absolute inset-0 touch-none" onArrive={game.arrive}
        onUserMove={stage.collapse} />}
      header={<GlobeGameStageHeader icon={Globe2} title={copy.title} titleId="quiz-title" gamesLabel={t.nav.games}
        iconClassName="bg-gradient-to-br from-orange-200 to-emerald-200 text-[#10140b] shadow-[0_0_24px_rgba(253,186,116,.45)]"
        roundLabel={stopLabel} stats={statsCard} onOpenMenu={intro ? undefined : game.openMenu} />}
      stats={statsCard} error={game.error || null}>
      {panel}
    </GlobeGameStage>
    <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
  </>
}
