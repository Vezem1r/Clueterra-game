import { useCallback, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowLeft, ArrowRight, Lightbulb, Map as MapIcon, Waves } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { RiverClueSlot, RiverFact, RiverHint, RiverHintKind } from '@/api/rivers'
import { Modal } from '@/components/ui/Modal'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { RiverClues } from './RiverClues'
import { RiverFacts } from './RiverFacts'
import { RIVER_CLUE_COUNT, RIVER_CLUE_SLOTS, RIVER_EXAMPLE_MAP, RIVER_GUIDE_PATH } from './riverGameConstants'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

const COURSE_STEP = 0
const CLUES_STEP = 1
const ANSWER_STEP = 2
const TOTAL_STEPS = 3
const STEP_TEXT_INDEX = [0, 2, 1]
const DEMO_FIRST_CLUES = 1
const DEMO_CLUE_COST_XP = 30
const DEMO_KINDS: Record<RiverClueSlot, RiverHintKind> = { GEOGRAPHY: 'GEOGRAPHY', PLACE: 'CITY', NAME: 'NAME' }

interface RiverGuideModalProps {
  isOpen: boolean
  onClose: () => void
}

export function RiverGuideModal({ isOpen, onClose }: RiverGuideModalProps) {
  const { t } = useTranslation()
  const copy = t.riverGame
  const localize = useLocalizedPath()
  const guide = copy.guide
  const [step, setStep] = useState(COURSE_STEP)
  const [direction, setDirection] = useState(1)
  const [demoClues, setDemoClues] = useState(DEMO_FIRST_CLUES)
  const hints: RiverHint[] = RIVER_CLUE_SLOTS.map((slot, index) => ({ position: index + 1, kind: DEMO_KINDS[slot], text: guide.demoClues[slot] }))
  const facts: RiverFact[] = [
    { kind: 'LENGTH', text: guide.demoFacts.LENGTH },
    { kind: 'COUNTRIES', text: guide.demoFacts.COUNTRIES },
    ...hints.filter(hint => hint.kind !== 'NAME'),
  ]
  const isLast = step === TOTAL_STEPS - 1

  const goTo = useCallback((next: number) => {
    setDirection(next > step ? 1 : -1)
    setStep(next)
  }, [step])

  const handleClose = useCallback(() => {
    setStep(COURSE_STEP)
    setDirection(1)
    setDemoClues(DEMO_FIRST_CLUES)
    onClose()
  }, [onClose])

  return <Modal ariaLabel={guide.modalTitle} isOpen={isOpen} onClose={handleClose} title="" className="max-w-3xl">
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-xl font-black tracking-[-.03em] text-white sm:text-2xl">{guide.modalTitle}</h2>
        <div className="flex gap-1.5">
          {Array.from({ length: TOTAL_STEPS }, (_, index) => <button key={index} type="button" onClick={() => goTo(index)}
            aria-label={interpolate(guide.stepOf, { current: index + 1, total: TOTAL_STEPS })}
            aria-current={index === step ? 'step' : undefined} className="relative h-2 w-2 rounded-full">
            <span className={cn('absolute inset-0 rounded-full transition-colors', index === step ? 'bg-sky-300' : 'bg-white/20')} />
          </button>)}
        </div>
      </div>

      <AnimatePresence mode="wait" custom={direction}>
        <motion.div key={step} custom={direction} initial={{ x: direction * 28 }} animate={{ x: 0 }}
          exit={{ x: direction * -28 }} transition={{ duration: 0.2, ease: [0.25, 0.1, 0.25, 1] }} className="will-change-transform">
          <GuideStep icon={[<MapIcon size={22} />, <Lightbulb size={22} />, <Waves size={22} />][step]}
            title={guide.stepTitles[step]} body={guide.howSteps[STEP_TEXT_INDEX[step]]}>
            {step === COURSE_STEP && <img src={RIVER_EXAMPLE_MAP} alt={copy.exampleAlt}
              className="aspect-[2/1] w-full rounded-[1.25rem] border border-white/[.08] object-cover" />}
            {step === CLUES_STEP && <RiverClues copy={copy} title={copy.cluesTitle} hints={hints.slice(0, demoClues)}
              hintsUsed={demoClues} busy={false} clueCost={DEMO_CLUE_COST_XP}
              onReveal={demoClues < RIVER_CLUE_COUNT ? () => setDemoClues(count => count + 1) : undefined} />}
            {step === ANSWER_STEP && <div className="rounded-[1.25rem] border border-white/[.08] bg-white/[.03] p-4">
              <p className="text-sm font-medium text-river-delta">{copy.solvedTitle}</p>
              <p className="mt-1 text-3xl font-black tracking-[-.03em] text-white">{guide.demoRiver}</p>
              <div className="mt-4">
                <RiverFacts copy={copy} facts={facts} />
              </div>
              <Link to={localize(RIVER_GUIDE_PATH)} onClick={handleClose}
                className="mt-4 inline-flex items-center gap-1.5 text-xs font-semibold text-sky-200/75 transition-colors hover:text-sky-100">
                {guide.fullGuide} <ArrowRight size={13} aria-hidden="true" />
              </Link>
            </div>}
          </GuideStep>
        </motion.div>
      </AnimatePresence>

      <div className="flex items-center gap-3 border-t border-white/[.06] pt-4">
        {step > 0 && <button type="button" onClick={() => goTo(step - 1)}
          className="inline-flex min-h-[44px] items-center gap-2 rounded-2xl border border-white/[.08] bg-white/[.04] px-4 text-sm font-semibold text-white/60 transition hover:bg-white/[.08] hover:text-white">
          <ArrowLeft size={15} aria-hidden="true" /> {guide.previous}
        </button>}
        <button type="button" onClick={isLast ? handleClose : () => goTo(step + 1)}
          className={cn('ml-auto inline-flex min-h-[44px] items-center gap-2 rounded-2xl px-5 text-sm font-semibold transition',
            isLast ? 'bg-sky-300 text-[#04131d] hover:bg-sky-200' : 'border border-white/[.08] bg-white/[.06] text-white hover:bg-white/[.1]')}>
          {isLast ? guide.done : guide.next}
          {!isLast && <ArrowRight size={15} aria-hidden="true" />}
        </button>
      </div>
    </div>
  </Modal>
}

function GuideStep({ icon, title, body, children }: { icon: ReactNode; title: string; body: string; children: ReactNode }) {
  return <div>
    <div className="flex items-start gap-3">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-sky-300/10 text-sky-300">{icon}</span>
      <div>
        <h3 className="text-xl font-black tracking-[-.03em] text-white sm:text-2xl">{title}</h3>
        <p className="mt-2 text-sm leading-6 text-white/50">{body}</p>
      </div>
    </div>
    <div className="mt-5">{children}</div>
  </div>
}
