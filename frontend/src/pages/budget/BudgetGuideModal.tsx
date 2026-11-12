import { useCallback, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowLeft, ArrowRight, Landmark, Lightbulb, Scale, SlidersHorizontal } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Modal } from '@/components/ui/Modal'
import { useTranslation } from '@/i18n'
import { BudgetRoundTypes } from '@/pages/public/howto/BudgetRoundTypes'
import { DEMO_FULL_XP_THRESHOLD_PERCENT, useBudgetDemoRound } from '@/pages/public/howto/useBudgetDemoRound'
import { cn } from '@/utils/cn'
import { BudgetAnswerPanel, BudgetHints } from './BudgetAnswerPanel'
import { BudgetProjectStage } from './BudgetProjectStage'

const TOTAL_STEPS = 4
const PROJECT_STEP = 0
const HINTS_STEP = 1
const GUESS_STEP = 2
const TYPES_STEP = 3

interface BudgetGuideModalProps {
  isOpen: boolean
  onClose: () => void
}

export function BudgetGuideModal({ isOpen, onClose }: BudgetGuideModalProps) {
  const { t } = useTranslation()
  const [step, setStep] = useState(PROJECT_STEP)
  const [direction, setDirection] = useState(1)
  const round = useBudgetDemoRound(t.budgetHowTo.demoCountry)

  const goNext = useCallback(() => {
    if (step < TOTAL_STEPS - 1) {
      setDirection(1)
      setStep(current => current + 1)
    }
  }, [step])

  const goBack = useCallback(() => {
    if (step > 0) {
      setDirection(-1)
      setStep(current => current - 1)
    }
  }, [step])

  const handleClose = useCallback(() => {
    setStep(PROJECT_STEP)
    setDirection(1)
    round.reset()
    onClose()
  }, [onClose, round])

  const isLast = step === TOTAL_STEPS - 1

  return (
    <Modal ariaLabel={t.budget.guide.title} isOpen={isOpen} onClose={handleClose} title="" className="max-w-3xl">
      <div className="flex flex-col gap-5">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[.22em] text-emerald-300/60">{t.budget.guide.eyebrow}</p>
            <h2 className="mt-1.5 text-xl font-black tracking-[-.03em] text-white sm:text-2xl">{t.budget.guide.title}</h2>
          </div>
          <div className="flex gap-1.5" aria-label={t.budget.guide.stepOf.replace('{current}', String(step + 1)).replace('{total}', String(TOTAL_STEPS))}>
            {Array.from({ length: TOTAL_STEPS }, (_, index) => (
              <button
                key={index}
                type="button"
                onClick={() => {
                  setDirection(index > step ? 1 : -1)
                  setStep(index)
                }}
                aria-label={t.budget.guide.stepOf.replace('{current}', String(index + 1)).replace('{total}', String(TOTAL_STEPS))}
                className="relative h-2 w-2 rounded-full"
              >
                <span className={cn('absolute inset-0 rounded-full transition-colors', index === step ? 'bg-emerald-300' : 'bg-white/20')} />
              </button>
            ))}
          </div>
        </div>

        <p className="text-[10px] font-semibold uppercase tracking-[.16em] text-white/28">
          {t.budget.guide.stepOf.replace('{current}', String(step + 1)).replace('{total}', String(TOTAL_STEPS))}
        </p>

        <AnimatePresence mode="wait" custom={direction}>
          <motion.div
            key={step}
            custom={direction}
            initial={{ x: direction * 28 }}
            animate={{ x: 0 }}
            exit={{ x: direction * -28 }}
            transition={{ duration: 0.2, ease: [0.25, 0.1, 0.25, 1] }}
            className="will-change-transform"
          >
            {step === PROJECT_STEP && (
              <GuideStep icon={<Landmark size={22} />} title={t.budgetHowTo.projectTitle} body={t.budgetHowTo.projectBody}>
                <BudgetProjectStage question={round.question} answer={round.answer} nameHeadingLevel="h3" />
              </GuideStep>
            )}
            {step === HINTS_STEP && (
              <GuideStep icon={<Lightbulb size={22} />} title={t.budgetHowTo.hintsTitle} body={t.budgetHowTo.hintsBody}>
                <BudgetHints
                  question={round.question}
                  revealing={false}
                  disabled={Boolean(round.answer)}
                  onReveal={round.revealHint}
                />
              </GuideStep>
            )}
            {step === GUESS_STEP && (
              <GuideStep icon={<SlidersHorizontal size={22} />} title={t.budgetHowTo.guessTitle} body={t.budgetHowTo.guessBody}>
                <BudgetAnswerPanel
                  question={round.question}
                  answer={round.answer}
                  submitting={false}
                  error=""
                  completed={false}
                  fullXpThresholdPercent={DEMO_FULL_XP_THRESHOLD_PERCENT}
                  selectedChoice={null}
                  onSubmitValue={round.submitGuess}
                  onSubmitChoice={async () => undefined}
                  onContinue={goNext}
                />
              </GuideStep>
            )}
            {step === TYPES_STEP && (
              <GuideStep icon={<Scale size={22} />} title={t.budgetHowTo.typesTitle} body={t.budgetHowTo.typesBody}>
                <BudgetRoundTypes columns={2} />
                <Link
                  to="/budget/how-to"
                  onClick={handleClose}
                  className="mt-4 inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-200/70 transition-colors hover:text-emerald-100"
                >
                  {t.budgetHowTo.fullGuideCta}
                  <ArrowRight size={13} />
                </Link>
              </GuideStep>
            )}
          </motion.div>
        </AnimatePresence>

        <div className="flex items-center gap-3 border-t border-white/[.06] pt-4">
          {step > 0 && (
            <button type="button" onClick={goBack} className="inline-flex min-h-[44px] items-center gap-2 rounded-2xl border border-white/[.08] bg-white/[.04] px-4 text-sm font-semibold text-white/60 transition hover:bg-white/[.08] hover:text-white">
              <ArrowLeft size={15} /> {t.budget.guide.previous}
            </button>
          )}
          <button
            type="button"
            onClick={isLast ? handleClose : goNext}
            className={cn(
              'ml-auto inline-flex min-h-[44px] items-center gap-2 rounded-2xl px-5 text-sm font-semibold transition',
              isLast ? 'bg-emerald-300 text-[#06100d] hover:bg-emerald-200' : 'border border-white/[.08] bg-white/[.06] text-white hover:bg-white/[.1]',
            )}
          >
            {isLast ? t.budget.guide.done : t.budget.guide.next}
            {!isLast && <ArrowRight size={15} />}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function GuideStep({ icon, title, body, children }: { icon: ReactNode; title: string; body: string; children: ReactNode }) {
  return (
    <div>
      <div className="flex items-start gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-300/10 text-emerald-300">{icon}</span>
        <div>
          <h3 className="text-xl font-black tracking-[-.03em] text-white sm:text-2xl">{title}</h3>
          <p className="mt-2 text-sm leading-6 text-white/48">{body}</p>
        </div>
      </div>
      <div className="mt-5">{children}</div>
    </div>
  )
}
