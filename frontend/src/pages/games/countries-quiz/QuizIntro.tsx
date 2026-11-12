import { Car, CircleHelp, Globe2, type LucideIcon } from 'lucide-react'
import type { QuizStop } from '@/api/quiz'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameIntro } from '../globe-game/GlobeGameIntro'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import { hidesFlag } from './quizFacts'
import { QUIZ_GUIDE_PATH, QUIZ_GUIDE_QUERY } from './quizGameConstants'

const STEP_ICONS: LucideIcon[] = [CircleHelp, Car, Globe2]

interface QuizIntroProps {
  copy: TranslationKeys['quizGame']
  legalNotice: string
  language: SilhouetteLocale
  busy: boolean
  ready: boolean
  isGuest: boolean
  complete: boolean
  current: QuizStop | null
  onStart: () => void
  onOpenPrivacy: () => void
  onOpenTerms: () => void
}

export function QuizIntro({
  copy, legalNotice, language, busy, ready, isGuest, complete, current, onStart, onOpenPrivacy, onOpenTerms,
}: QuizIntroProps) {
  const answered = current?.questions.filter(question => question.answer != null).length ?? 0
  const country = current?.country.names[language] ?? ''

  return <GlobeGameIntro accent="quiz" titleId="quiz-title" actions={!complete}
    lead={complete ? copy.completeTitle : copy.lead} detail={complete ? copy.completeBody : copy.leadDetail}
    actionLabel={current ? interpolate(copy.resume, { country }) : copy.start}
    preparingLabel={copy.preparing} howToPlay={copy.howToPlay} guidePath={QUIZ_GUIDE_PATH} guideQuery={QUIZ_GUIDE_QUERY}
    legalNotice={legalNotice} busy={busy} ready={ready} isGuest={isGuest}
    onStart={onStart} onOpenPrivacy={onOpenPrivacy} onOpenTerms={onOpenTerms}
    facts={[
      { question: copy.guide.countriesTitle, answer: copy.guide.countriesBody },
      { question: copy.guide.questionsTitle, answer: copy.guide.questionsBody },
      ...copy.guide.faq,
    ]}
    steps={complete ? [] : copy.steps.map((step, index) => ({
      key: step.title,
      state: 'flowing',
      icon: STEP_ICONS[index],
      content: <>
        <p className="text-sm font-semibold text-white/90">{step.title}</p>
        <p className="text-sm text-white/50">{step.body}</p>
      </>,
    }))}
    resume={current && {
      round: current.stop,
      title: copy.resumeTitle,
      summary: interpolate(copy.resumeSummary, { stop: current.stop, level: current.level, answered, total: current.questions.length }),
      media: <div className="mx-auto mt-5 hidden items-center gap-3 sm:flex [@media(max-height:820px)]:hidden">
        {!hidesFlag(current) && <img src={flagUrl(current.country.countryCode)} alt="" aria-hidden="true"
          className="h-12 w-[4.5rem] rounded-md object-cover shadow-[0_12px_30px_rgba(0,0,0,.5)] ring-1 ring-white/20" />}
        <span className="text-lg font-bold text-white">{country}</span>
      </div>,
    }} />
}
