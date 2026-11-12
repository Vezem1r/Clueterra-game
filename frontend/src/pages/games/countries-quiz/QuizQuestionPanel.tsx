import { useEffect, useRef } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { ArrowRight, CircleCheck, CircleX, Lightbulb } from 'lucide-react'
import type { QuizQuestion, QuizStop } from '@/api/quiz'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { GlobeGamePanel } from '../globe-game/GlobeGamePanel'
import { GlobeGameRoundBar } from '../globe-game/GlobeGameRoundBar'
import { GLOBE_GAME_THEMES } from '../globe-game/globeGameTheme'
import type { CountryPool } from '../silhouettes/silhouetteWorld'
import { factText, hidesFlag, optionLabel } from './quizFacts'
import { QUIZ_KIND_ICONS, QUIZ_LEVEL_BADGES, QUIZ_PERCENT, QUIZ_QUESTIONS_PER_STOP } from './quizGameConstants'
import { QuizOptions } from './QuizOptions'
import { QuizStopHeading } from './QuizStopHeading'

interface QuizQuestionPanelProps {
  copy: TranslationKeys['quizGame']
  regions: Record<string, string>
  language: SilhouetteLocale
  pool: CountryPool | null
  stop: QuizStop
  question: QuizQuestion
  busy: boolean
  onAnswer: (option: number) => void
  onNext: () => void
}

const TITLE_ID = 'quiz-stop-title'
const PROMPT_ID = 'quiz-question-prompt'

export function QuizQuestionPanel({
  copy, regions, language, pool, stop, question, busy, onAnswer, onNext,
}: QuizQuestionPanelProps) {
  const reduced = useReducedMotion()
  const nextButton = useRef<HTMLButtonElement>(null)
  const theme = GLOBE_GAME_THEMES.quiz
  const answer = question.answer
  const answered = stop.questions.filter(item => item.answer != null).length
  const KindIcon = QUIZ_KIND_ICONS[question.kind]
  const country = stop.country.names[language]
  const prompt = interpolate(copy.prompts[question.kind], { country, subject: question.subject?.names[language] ?? '' })
  const correctOption = answer ? question.options[answer.answerIndex] : null
  const correctLabel = correctOption ? optionLabel(correctOption, pool, language) : null
  const lastQuestion = stop.questions.every(item => item.answer != null)

  useEffect(() => {
    if (answer) nextButton.current?.focus({ preventScroll: true })
  }, [answer])

  return <GlobeGamePanel as="section" data-testid="quiz-question-panel" aria-labelledby={TITLE_ID} className="p-4 sm:p-6">
    <GlobeGameRoundBar round={interpolate(copy.stop, { count: stop.stop })}
      badge={interpolate(copy.levelOf, { level: stop.level, levels: stop.levels })} badgeClassName={QUIZ_LEVEL_BADGES[stop.level]}
      xp={interpolate(copy.worthXp, { xp: stop.answerXp })} xpShare={answered / QUIZ_QUESTIONS_PER_STOP * QUIZ_PERCENT} />

    <QuizStopHeading copy={copy} regions={regions} stop={stop} pool={pool} language={language} titleId={TITLE_ID} />

    <div className="-mx-4 mt-4 min-h-0 flex-1 overflow-y-auto px-4 pb-1 sm:-mx-6 sm:px-6 max-sm:mt-3">
      <AnimatePresence mode="wait" initial={false}>
        <motion.div key={question.position} initial={reduced ? false : { opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }}
          exit={reduced ? undefined : { opacity: 0, x: -24 }} transition={{ duration: 0.22, ease: 'easeOut' }}>
          <div className="flex items-center justify-between gap-3">
            <span className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1', theme.badge)}>
              <KindIcon size={13} aria-hidden="true" /> {copy.kinds[question.kind]}
            </span>
            <span className="flex items-center gap-2 text-xs font-medium text-white/50">
              {interpolate(copy.questionOf, { current: question.position, total: QUIZ_QUESTIONS_PER_STOP })}
              <span aria-hidden="true" className="flex gap-1">
                {stop.questions.map(item => <span key={item.position} className={cn('h-1.5 w-4 rounded-full transition-colors duration-500',
                  item.answer ? item.answer.correct ? 'bg-emerald-300' : 'bg-rose-300'
                    : item.position === question.position ? 'bg-orange-200' : 'bg-white/15')} />)}
              </span>
            </span>
          </div>
          <h3 id={PROMPT_ID} className="mt-3 text-balance text-lg font-bold leading-snug text-white sm:text-xl">{prompt}</h3>
          <div role="group" aria-labelledby={PROMPT_ID} className="mt-4">
            <QuizOptions copy={copy} question={question} pool={pool} language={language} busy={busy}
              hideFlags={hidesFlag(stop)} onAnswer={onAnswer} />
          </div>

          <AnimatePresence>
            {answer && <motion.div key="feedback" role="status" initial={reduced ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.28, ease: 'easeOut', delay: reduced ? 0 : 0.12 }}
              className={cn('mt-4 rounded-2xl px-4 py-3 ring-1', answer.correct ? 'bg-emerald-300/[.08] ring-emerald-200/20' : 'bg-rose-400/[.08] ring-rose-200/20')}>
              <div className="flex items-center justify-between gap-3">
                <p className={cn('flex items-center gap-1.5 text-sm font-bold', answer.correct ? 'text-emerald-100' : 'text-rose-100')}>
                  {answer.correct ? <CircleCheck size={16} aria-hidden="true" /> : <CircleX size={16} aria-hidden="true" />}
                  {answer.correct ? copy.correct : copy.wrong}
                </p>
                {answer.xpAwarded > 0 && <motion.span initial={reduced ? false : { scale: 0.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 18, delay: reduced ? 0 : 0.2 }}
                  className="rounded-full bg-amber-300/15 px-2.5 py-0.5 text-sm font-black tabular-nums text-river-silt ring-1 ring-amber-200/30">
                  {interpolate(copy.correctXp, { xp: answer.xpAwarded })}
                </motion.span>}
              </div>
              {!answer.correct && correctLabel && <p className="mt-1 text-sm text-white/75">{interpolate(copy.answerWas, { answer: correctLabel })}</p>}
              <p className="mt-2 flex gap-2 text-sm leading-relaxed text-white/60">
                <Lightbulb size={15} aria-hidden="true" className="mt-0.5 shrink-0 text-orange-200/70" />
                {factText(copy, regions, answer.fact, country, language, pool)}
              </p>
            </motion.div>}
          </AnimatePresence>
        </motion.div>
      </AnimatePresence>
    </div>

    {answer && <div className="shrink-0 pt-4">
      <button ref={nextButton} type="button" onClick={onNext} disabled={busy}
        className={cn('flex min-h-12 w-full items-center justify-center gap-2 rounded-2xl font-bold transition hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50', theme.primary, theme.focus)}>
        {lastQuestion ? copy.nextStop : copy.nextQuestion} <ArrowRight size={18} aria-hidden="true" />
      </button>
    </div>}
  </GlobeGamePanel>
}
