import { useCallback, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowLeft, ArrowRight, Car, CircleHelp, Globe2, Lightbulb } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { SilhouetteLocale } from '@/api/silhouettes'
import { Modal } from '@/components/ui/Modal'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { SilhouetteGuideStage } from '../silhouettes/SilhouetteGuideStage'
import { QuizGuideDemo, QUIZ_GUIDE_STEPS } from './QuizGuideDemo'
import { QUIZ_GUIDE_PATH } from './quizGameConstants'

const FIRST_STEP = 0
const SHOT_STEP = 2
const STEP_ICONS = [CircleHelp, Lightbulb, Car, Globe2]

interface QuizGuideModalProps {
  isOpen: boolean
  onClose: () => void
}

export function QuizGuideModal({ isOpen, onClose }: QuizGuideModalProps) {
  const { t, language } = useTranslation()
  const copy = t.quizGame
  const guide = copy.guide
  const localize = useLocalizedPath()
  const [step, setStep] = useState(FIRST_STEP)
  const [direction, setDirection] = useState(1)
  const isLast = step === QUIZ_GUIDE_STEPS - 1
  const Icon = STEP_ICONS[step]

  const goTo = useCallback((next: number) => {
    setDirection(next > step ? 1 : -1)
    setStep(next)
  }, [step])

  const handleClose = useCallback(() => {
    setStep(FIRST_STEP)
    setDirection(1)
    onClose()
  }, [onClose])

  return <Modal ariaLabel={guide.modalTitle} isOpen={isOpen} onClose={handleClose} title="" className="max-w-3xl">
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-xl font-black tracking-[-.03em] text-white sm:text-2xl">{guide.modalTitle}</h2>
        <div className="flex gap-1.5">
          {Array.from({ length: QUIZ_GUIDE_STEPS }, (_, index) => <button key={index} type="button" onClick={() => goTo(index)}
            aria-label={interpolate(guide.stepOf, { current: index + 1, total: QUIZ_GUIDE_STEPS })}
            aria-current={index === step ? 'step' : undefined} className="relative h-2 w-2 rounded-full">
            <span className={cn('absolute inset-0 rounded-full transition-colors', index === step ? 'bg-orange-200' : 'bg-white/20')} />
          </button>)}
        </div>
      </div>

      <AnimatePresence mode="wait" custom={direction}>
        <motion.div key={step} custom={direction} initial={{ x: direction * 28 }} animate={{ x: 0 }}
          exit={{ x: direction * -28 }} transition={{ duration: 0.2, ease: [0.25, 0.1, 0.25, 1] }} className="will-change-transform">
          <div className="flex items-start gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-orange-200/10 text-orange-200"><Icon size={22} /></span>
            <div>
              <h3 className="text-xl font-black tracking-[-.03em] text-white sm:text-2xl">{guide.howStepTitles[step]}</h3>
              <p className="mt-2 text-sm leading-6 text-white/50">{guide.howSteps[step]}</p>
            </div>
          </div>
          <SilhouetteGuideStage className={cn('mt-5 min-h-64 sm:min-h-80', step >= SHOT_STEP ? 'h-64 sm:h-80' : 'p-4 sm:p-6')}>
            <QuizGuideDemo copy={copy} regions={t.silhouetteGame.regions} language={language as SilhouetteLocale} step={step} />
          </SilhouetteGuideStage>
          {isLast && <Link to={localize(QUIZ_GUIDE_PATH)} onClick={handleClose}
            className="mt-4 inline-flex items-center gap-1.5 text-xs font-semibold text-orange-200/75 transition-colors hover:text-orange-100">
            {guide.fullGuide} <ArrowRight size={13} aria-hidden="true" />
          </Link>}
        </motion.div>
      </AnimatePresence>

      <div className="flex items-center gap-3 border-t border-white/[.06] pt-4">
        {step > 0 && <button type="button" onClick={() => goTo(step - 1)}
          className="inline-flex min-h-[44px] items-center gap-2 rounded-2xl border border-white/[.08] bg-white/[.04] px-4 text-sm font-semibold text-white/60 transition hover:bg-white/[.08] hover:text-white">
          <ArrowLeft size={15} aria-hidden="true" /> {guide.previous}
        </button>}
        <button type="button" onClick={isLast ? handleClose : () => goTo(step + 1)}
          className={cn('ml-auto inline-flex min-h-[44px] items-center gap-2 rounded-2xl px-5 text-sm font-semibold transition',
            isLast ? 'bg-orange-200 text-[#1b120a] hover:bg-orange-100' : 'border border-white/[.08] bg-white/[.06] text-white hover:bg-white/[.1]')}>
          {isLast ? guide.done : guide.next}
          {!isLast && <ArrowRight size={15} aria-hidden="true" />}
        </button>
      </div>
    </div>
  </Modal>
}
