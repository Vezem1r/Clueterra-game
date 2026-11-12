import { motion, useReducedMotion } from 'framer-motion'
import { Car, CircleCheck, CircleX, Plane, RotateCcw, Sparkles } from 'lucide-react'
import type { QuizStop } from '@/api/quiz'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { GlobeGameReveal } from '../globe-game/GlobeGameReveal'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import type { CountryPool } from '../silhouettes/silhouetteWorld'
import { optionLabel } from './quizFacts'
import { QUIZ_KIND_ICONS, QUIZ_LEVEL_PAINT, QUIZ_MAX_LEVEL } from './quizGameConstants'

interface QuizStopSummaryProps {
  copy: TranslationKeys['quizGame']
  language: SilhouetteLocale
  pool: CountryPool | null
  stop: QuizStop
  complete: boolean
  busy: boolean
  onTravel: () => void
}

const PIP_DELAY_SECONDS = 0.12
const PIP_GLOW_PX = 10
const FIRST_LEVEL = 1
const TINT = '1f'
const RING = '55'

export function QuizStopSummary({ copy, language, pool, stop, complete, busy, onTravel }: QuizStopSummaryProps) {
  const reduced = useReducedMotion()
  const country = stop.country.names[language]
  const correct = stop.questions.filter(question => question.answer?.correct).length
  const xp = stop.questions.reduce((sum, question) => sum + (question.answer?.xpAwarded ?? 0), 0)
  const mastered = stop.level >= stop.levels
  const next = stop.next
  const returning = next != null && next.level > FIRST_LEVEL
  const NextIcon = returning ? RotateCcw : next?.mode === 'FLY' ? Plane : Car
  const nextName = next?.country.names[language] ?? ''
  const paint = QUIZ_LEVEL_PAINT[stop.level]
  const nextPaint = next ? QUIZ_LEVEL_PAINT[next.level] : paint

  return <GlobeGameReveal accent="quiz" testId="quiz-stop-summary" titleId="quiz-summary-title" solved
    status={interpolate(copy.levelReached, { level: stop.level })} name={country} flagUrl={flagUrl(stop.country.countryCode)}
    xp={xp > 0 ? interpolate(copy.correctXp, { xp }) : null}
    summary={interpolate(copy.exploredSummary, { correct, total: stop.questions.length })} photo={null}
    nextLabel={next ? interpolate(copy.travel[next.mode], { country: nextName }) : ''} next={next != null} busy={busy} onNext={onTravel}>
    <div data-testid="quiz-level-reached" className="mt-3 flex items-center gap-3 rounded-2xl px-4 py-2.5 sm:mt-4 sm:py-3"
      style={{ backgroundColor: `${paint.fill}${TINT}`, boxShadow: `inset 0 0 0 1px ${paint.fill}${RING}` }}>
      <span aria-hidden="true" className="flex gap-1.5">
        {Array.from({ length: QUIZ_MAX_LEVEL }, (_, index) => {
          const level = index + FIRST_LEVEL
          const reached = level <= stop.level
          const tone = QUIZ_LEVEL_PAINT[level]
          return <motion.span key={level} className={cn('size-3.5 rounded-full border', level > stop.levels && 'opacity-25')}
            initial={reduced || !reached ? false : { scale: 0.2, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', stiffness: 420, damping: 16, delay: reduced ? 0 : index * PIP_DELAY_SECONDS }}
            style={{
              backgroundColor: reached ? tone.fill : 'transparent',
              borderColor: tone.edge,
              boxShadow: reached && tone.glow > 0 ? `0 0 ${PIP_GLOW_PX * tone.glow}px ${tone.fill}` : undefined,
            }} />
        })}
      </span>
      <p className="min-w-0 text-sm text-white/80">
        <span className="font-bold" style={{ color: paint.edge }}>{copy.levelNames[stop.level]}</span>
        <span className="text-white/55"> · {mastered ? interpolate(copy.masteredNow, { country }) : interpolate(copy.levelUp, { country })}</span>
      </p>
      {mastered && <Sparkles size={18} aria-hidden="true" className="ml-auto shrink-0" style={{ color: paint.edge }} />}
    </div>

    <ol className="mt-2.5 grid grid-cols-2 gap-2 sm:mt-3">
      {stop.questions.map(question => {
        const answer = question.answer
        const KindIcon = QUIZ_KIND_ICONS[question.kind]
        const option = answer ? question.options[answer.answerIndex] : null
        return <li key={question.position} className="flex min-w-0 items-center gap-2.5 rounded-xl bg-white/[.04] px-3 py-2 ring-1 ring-white/[.06] sm:py-2.5">
          <KindIcon size={16} aria-hidden="true" className="shrink-0 text-orange-200/70" />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-xs text-white/45">{copy.kinds[question.kind]}</span>
            <span className="block truncate text-sm font-semibold text-white/90">{option ? optionLabel(option, pool, language) : ''}</span>
          </span>
          {answer?.correct
            ? <CircleCheck size={18} aria-label={copy.correct} className="shrink-0 text-emerald-300" />
            : <CircleX size={18} aria-label={copy.wrong} className="shrink-0 text-rose-300" />}
        </li>
      })}
    </ol>

    {next && <section aria-labelledby="quiz-next-stop" className="mt-3 overflow-hidden rounded-2xl bg-orange-200/[.06] ring-1 ring-orange-200/20 sm:mt-5">
      <div className="flex items-center gap-3 px-4 py-3">
        <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-orange-200/15 text-orange-100">
          <NextIcon size={20} />
        </span>
        <div className="min-w-0 flex-1">
          <h3 id="quiz-next-stop" className="text-xs font-semibold text-orange-100/70">{copy.nextStop}</h3>
          <p className="truncate text-base font-bold text-white">{nextName}</p>
        </div>
        <span className="shrink-0 rounded-full px-2.5 py-1 text-[11px] font-bold"
          style={{ color: nextPaint.edge, backgroundColor: `${nextPaint.fill}${TINT}`, boxShadow: `inset 0 0 0 1px ${nextPaint.fill}${RING}` }}>
          {returning ? interpolate(copy.nextReturn, { level: next.level }) : copy.nextNew}
        </span>
      </div>
      <p className="border-t border-orange-200/10 px-4 py-2.5 text-xs leading-relaxed text-white/55 [@media(max-height:760px)]:hidden">
        {returning ? copy.nextReason.RETURN : copy.nextReason[next.mode]}
      </p>
    </section>}

    {complete && <div className="mt-5 rounded-2xl bg-amber-300/10 px-4 py-3 ring-1 ring-amber-200/25">
      <p className="text-sm font-bold text-amber-100">{copy.completeTitle}</p>
      <p className="mt-1 text-sm text-amber-50/70">{copy.completeBody}</p>
    </div>}
  </GlobeGameReveal>
}
