import { useEffect } from 'react'
import { animate, motion, useMotionValue, useReducedMotion, useTransform } from 'framer-motion'
import { Car, MapPin, Plane } from 'lucide-react'
import type { QuizTravelMode } from '@/api/quiz'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGamePanel } from '../globe-game/GlobeGamePanel'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import { QUIZ_DRIVE_SECONDS, QUIZ_FLY_SECONDS } from './quizGameConstants'
import { easeJourney } from './quizGlobeMath'

interface QuizTravelCardProps {
  copy: TranslationKeys['quizGame']
  mode: QuizTravelMode
  from: { code: string, name: string }
  to: string
}

const ICON_REM = 2.25
const HALF = 2
const DONE = 1

export function QuizTravelCard({ copy, mode, from, to }: QuizTravelCardProps) {
  const reduced = useReducedMotion()
  const Icon = mode === 'FLY' ? Plane : Car
  const duration = mode === 'FLY' ? QUIZ_FLY_SECONDS : QUIZ_DRIVE_SECONDS
  const progress = useMotionValue(0)
  const left = useTransform(progress, share => `calc(${share} * (100% - ${ICON_REM}rem))`)
  const width = useTransform(progress, share => `calc(${share} * (100% - ${ICON_REM}rem) + ${ICON_REM / HALF}rem)`)

  useEffect(() => {
    if (reduced) {
      progress.set(DONE)
      return
    }
    const controls = animate(progress, DONE, { duration, ease: easeJourney })
    return () => controls.stop()
  }, [progress, duration, reduced])

  return <GlobeGamePanel as="section" data-testid="quiz-travel" aria-live="polite" className="flex-none p-4 motion-safe:animate-river-reveal sm:p-6">
    <p className="text-lg font-bold text-white">{interpolate(copy.travelling[mode], { country: to })}</p>
    <div className="mt-5 flex items-center gap-3">
      <img src={flagUrl(from.code)} alt="" aria-hidden="true" className="h-5 w-7 shrink-0 rounded-[3px] object-cover ring-1 ring-white/25" />
      <div aria-hidden="true" className="relative h-9 flex-1">
        <span className="absolute inset-x-0 top-[calc(50%-1px)] h-0.5 bg-[repeating-linear-gradient(90deg,rgba(254,215,170,.3)_0_6px,transparent_6px_12px)]" />
        <motion.span style={{ width }} className="absolute left-0 top-[calc(50%-1px)] h-0.5 rounded-full bg-gradient-to-r from-orange-200/10 to-orange-200" />
        <motion.span style={{ left }} className="absolute top-0 flex size-9 items-center justify-center rounded-full bg-orange-200 text-[#1b120a] shadow-[0_0_14px_rgba(253,186,116,.35)]">
          <Icon size={18} className={mode === 'FLY' ? 'rotate-45' : undefined} />
        </motion.span>
      </div>
      <MapPin size={20} aria-hidden="true" className="shrink-0 text-orange-200" />
    </div>
    <p className="mt-3 flex justify-between gap-3 text-xs text-white/50">
      <span className="truncate">{from.name}</span>
      <span className="truncate text-right font-semibold text-orange-100/80">{to}</span>
    </p>
  </GlobeGamePanel>
}
