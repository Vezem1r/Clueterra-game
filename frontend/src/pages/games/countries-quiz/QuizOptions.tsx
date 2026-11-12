import { useEffect, type ReactNode } from 'react'
import { motion, useReducedMotion } from 'framer-motion'
import { Check, X } from 'lucide-react'
import type { QuizQuestion } from '@/api/quiz'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { FlagCard } from '../flags/FlagCard'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import type { CountryPool } from '../silhouettes/silhouetteWorld'
import { optionLabel, optionValue } from './quizFacts'
import { QuizShape, type QuizShapeTone } from './QuizShape'
import { QUIZ_OPTION_LETTERS } from './quizGameConstants'

interface QuizOptionsProps {
  copy: TranslationKeys['quizGame']
  question: QuizQuestion
  pool: CountryPool | null
  language: SilhouetteLocale
  busy: boolean
  hideFlags?: boolean
  keyboard?: boolean
  onAnswer: (option: number) => void
}

type OptionState = QuizShapeTone

const STAGGER_SECONDS = 0.05
const ENTER_OFFSET_PX = 10
const SHAKE_PX = [0, -7, 6, -4, 3, 0]
const SHAKE_SECONDS = 0.42
const KEY_OFFSET = 1

const STATE_STYLES: Record<OptionState, string> = {
  idle: 'border-white/[.1] bg-white/[.04] hover:border-orange-200/45 hover:bg-orange-200/[.07]',
  right: 'border-emerald-300/70 bg-emerald-300/[.14] shadow-[0_0_28px_rgba(110,231,183,.22)]',
  wrong: 'border-rose-300/70 bg-rose-400/[.13]',
  missed: 'border-emerald-300/55 bg-emerald-300/[.07]',
  faded: 'border-white/[.06] bg-white/[.02] opacity-45',
}

const LETTER_STYLES: Record<OptionState, string> = {
  idle: 'bg-white/[.07] text-white/70 group-hover:bg-orange-200/20 group-hover:text-orange-100',
  right: 'bg-emerald-300 text-[#04170f]',
  wrong: 'bg-rose-300 text-[#22070b]',
  missed: 'bg-emerald-300/25 text-emerald-100',
  faded: 'bg-white/[.05] text-white/40',
}

function optionState(question: QuizQuestion, index: number): OptionState {
  const answer = question.answer
  if (!answer) return 'idle'
  if (index === answer.answerIndex) return index === answer.selectedIndex ? 'right' : 'missed'
  return index === answer.selectedIndex ? 'wrong' : 'faded'
}

export function QuizOptions({ copy, question, pool, language, busy, hideFlags = false, keyboard = true, onAnswer }: QuizOptionsProps) {
  const reduced = useReducedMotion()
  const answered = question.answer != null
  const visual = question.options.some(option => option.type === 'FLAG' || option.type === 'SHAPE')
  const tiles = question.options.every(option => option.type === 'NUMBER' || option.type === 'TEXT')

  useEffect(() => {
    if (answered || !keyboard) return
    const onKey = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return
      const target = event.target as HTMLElement | null
      if (target?.closest('input, textarea, [contenteditable="true"]')) return
      const byNumber = Number(event.key) - KEY_OFFSET
      const byLetter = QUIZ_OPTION_LETTERS.indexOf(event.key.toUpperCase() as typeof QUIZ_OPTION_LETTERS[number])
      const index = Number.isInteger(byNumber) && byNumber >= 0 ? byNumber : byLetter
      if (index < 0 || index >= question.options.length) return
      event.preventDefault()
      onAnswer(index)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [answered, keyboard, question.options.length, onAnswer])

  return <ul className={cn('grid gap-2', visual || tiles ? 'grid-cols-2' : 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-1')}>
    {question.options.map((option, index) => {
      const state = optionState(question, index)
      const letter = QUIZ_OPTION_LETTERS[index]
      const label = optionLabel(option, pool, language)
      const value = optionValue(copy, question.answer?.fact, index, language)
      const mark = state === 'right' || state === 'missed' ? <Check size={14} strokeWidth={3} /> : state === 'wrong' ? <X size={14} strokeWidth={3} /> : letter
      const shake = state === 'wrong' && !reduced
      const ariaLabel = option.type === 'FLAG'
        ? label ? interpolate(copy.flagOf, { name: label }) : interpolate(copy.flagOption, { letter })
        : option.type === 'SHAPE'
          ? label ? interpolate(copy.shapeOf, { name: label }) : interpolate(copy.shapeOption, { letter })
          : undefined

      return <motion.li key={`${question.position}-${index}`}
        initial={reduced ? false : { opacity: 0, y: ENTER_OFFSET_PX }}
        animate={shake ? { opacity: 1, y: 0, x: SHAKE_PX } : { opacity: 1, y: 0, x: 0 }}
        transition={shake ? { duration: SHAKE_SECONDS } : { delay: index * STAGGER_SECONDS, duration: 0.24, ease: 'easeOut' }}>
        <button type="button" disabled={answered || busy} onClick={() => onAnswer(index)} aria-label={ariaLabel}
          aria-pressed={answered ? index === question.answer?.selectedIndex : undefined}
          aria-keyshortcuts={answered ? undefined : `${index + KEY_OFFSET} ${letter}`}
          className={cn('group relative flex w-full items-center gap-3 rounded-2xl border text-left transition-[background-color,border-color,opacity,box-shadow] duration-300',
            'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-orange-200 disabled:cursor-default',
            visual ? 'flex-col gap-2 p-2.5' : tiles ? 'min-h-16 justify-center gap-3 px-3 py-3' : 'min-h-[3.25rem] px-3 py-2.5',
            STATE_STYLES[state], busy && !answered && 'cursor-wait')}>
          {!visual && <OptionMark state={state} mark={mark} />}

          {option.type === 'FLAG' && option.flagUrl && <span className="flex h-20 w-full items-center justify-center pt-1 sm:h-24 [@media(max-height:760px)]:h-16">
            <FlagCard src={option.flagUrl} alt="" eager className="max-h-full max-w-full" />
          </span>}
          {option.type === 'SHAPE' && option.shape && <QuizShape shapePath={option.shape} tone={state}
            className="h-20 w-full sm:h-24 [@media(max-height:760px)]:h-16" />}
          {tiles && <span className={cn('text-xl font-black tabular-nums text-white', option.type === 'TEXT' && 'font-mono tracking-tight')}>{label}</span>}

          {(option.type === 'COUNTRY' || option.type === 'PLACE' || option.type === 'CURRENCY') &&
            <span className="flex min-w-0 flex-1 items-center gap-2">
              {option.type === 'COUNTRY' && option.countryCode && !hideFlags && <img src={flagUrl(option.countryCode)} alt="" aria-hidden="true"
                className="h-4 w-6 shrink-0 rounded-[3px] object-cover ring-1 ring-white/20" />}
              <span className="min-w-0">
                <span className="block text-[15px] font-semibold leading-snug text-white">{label}</span>
                {value && <span className="mt-0.5 block text-xs font-semibold tabular-nums text-orange-100/75">{value}</span>}
              </span>
            </span>}

          {visual && <span className="flex w-full min-w-0 items-center gap-2">
            <OptionMark state={state} mark={mark} />
            {answered && label && <span className="min-w-0 truncate text-xs font-semibold text-white/80">{label}</span>}
          </span>}
        </button>
      </motion.li>
    })}
  </ul>
}

function OptionMark({ state, mark }: { state: OptionState, mark: ReactNode }) {
  return <span aria-hidden="true" className={cn('flex size-7 shrink-0 items-center justify-center rounded-lg text-xs font-black transition-colors', LETTER_STYLES[state])}>{mark}</span>
}
