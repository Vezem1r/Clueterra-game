import { useCallback, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowLeft, ArrowRight, Globe2, Lightbulb, ScanSearch, Search } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { SilhouetteClue, SilhouetteHints, SilhouetteLocale } from '@/api/silhouettes'
import { Modal } from '@/components/ui/Modal'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { SilhouetteClues } from './SilhouetteClues'
import { SilhouetteGuideShot, SilhouetteGuideStage } from './SilhouetteGuideStage'
import {
  SILHOUETTE_GLOBE_EXAMPLE,
  SILHOUETTE_GUIDE_PATH,
  SILHOUETTE_NAME_EXAMPLE,
  SILHOUETTE_SHAPE_EXAMPLE,
} from './silhouetteGameConstants'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

const SHAPE_STEP = 0
const NAME_STEP = 1
const CLUES_STEP = 2
const GLOBE_STEP = 3
const TOTAL_STEPS = 4
const DEMO_FIRST_CLUES = 1
const DEMO_LIGHT_CLUE_XP = 12
const DEMO_FULL_CLUE_XP = 24
const DEMO_CLUES: SilhouetteClue[] = [
  { kind: 'REGION', costXp: DEMO_LIGHT_CLUE_XP },
  { kind: 'LOCATION', costXp: DEMO_LIGHT_CLUE_XP },
  { kind: 'CAPITAL', costXp: DEMO_FULL_CLUE_XP },
]
const DEMO_SUBREGION = 'Southern Europe'
const DEMO_CENTER: [number, number] = [12.5, 42.8]
const DEMO_BOUNDS: [number, number, number, number] = [6.6, 35.5, 18.5, 47.1]
const STEP_ICONS = [<ScanSearch size={22} />, <Search size={22} />, <Lightbulb size={22} />, <Globe2 size={22} />]

interface SilhouetteGuideModalProps {
  isOpen: boolean
  onClose: () => void
}

export function SilhouetteGuideModal({ isOpen, onClose }: SilhouetteGuideModalProps) {
  const { t, language } = useTranslation()
  const copy = t.silhouetteGame
  const localize = useLocalizedPath()
  const guide = copy.guide
  const [step, setStep] = useState(SHAPE_STEP)
  const [direction, setDirection] = useState(1)
  const [demoClues, setDemoClues] = useState(DEMO_FIRST_CLUES)
  const isLast = step === TOTAL_STEPS - 1
  const hints: SilhouetteHints = {
    region: { region: 'Europe', subregion: DEMO_SUBREGION, center: DEMO_CENTER },
    location: demoClues >= 2 ? { center: DEMO_CENTER, bounds: DEMO_BOUNDS } : null,
    capitals: demoClues >= DEMO_CLUES.length ? [guide.demoCapital] : null,
    flagColors: null,
    landmark: null,
  }

  const goTo = useCallback((next: number) => {
    setDirection(next > step ? 1 : -1)
    setStep(next)
  }, [step])

  const handleClose = useCallback(() => {
    setStep(SHAPE_STEP)
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
            <span className={cn('absolute inset-0 rounded-full transition-colors', index === step ? 'bg-cyan-300' : 'bg-white/20')} />
          </button>)}
        </div>
      </div>

      <AnimatePresence mode="wait" custom={direction}>
        <motion.div key={step} custom={direction} initial={{ x: direction * 28 }} animate={{ x: 0 }}
          exit={{ x: direction * -28 }} transition={{ duration: 0.2, ease: [0.25, 0.1, 0.25, 1] }} className="will-change-transform">
          <GuideStep icon={STEP_ICONS[step]} title={guide.howStepTitles[step]} body={guide.howSteps[step]}>
            <SilhouetteGuideStage className={cn(step === CLUES_STEP ? 'min-h-64 p-2 sm:min-h-80 sm:p-6' : 'h-64 sm:h-80',
              (step === SHAPE_STEP || step === NAME_STEP) && 'p-5 sm:p-6')}>
              {step === SHAPE_STEP && <SilhouetteGuideShot image={SILHOUETTE_SHAPE_EXAMPLE} alt={guide.shapeAlt} eager />}
              {step === NAME_STEP && <SilhouetteGuideShot image={SILHOUETTE_NAME_EXAMPLE} alt={guide.nameAlt} eager />}
              {step === CLUES_STEP && <div className="w-full max-w-md rounded-[1.25rem] border border-white/[.1] bg-[#0a0f1b] p-3 shadow-[0_24px_60px_rgba(0,0,0,.6)] sm:p-4">
                <SilhouetteClues copy={copy} language={language as SilhouetteLocale} hints={hints}
                  clues={DEMO_CLUES} hintsUsed={demoClues} busy={false}
                  onReveal={() => setDemoClues(count => Math.min(DEMO_CLUES.length, count + 1))} />
              </div>}
              {step === GLOBE_STEP && <SilhouetteGuideShot image={SILHOUETTE_GLOBE_EXAMPLE} alt={guide.globeAlt} cover eager />}
            </SilhouetteGuideStage>
            {isLast && <Link to={localize(SILHOUETTE_GUIDE_PATH)} onClick={handleClose}
              className="mt-4 inline-flex items-center gap-1.5 text-xs font-semibold text-cyan-200/75 transition-colors hover:text-cyan-100">
              {guide.fullGuide} <ArrowRight size={13} aria-hidden="true" />
            </Link>}
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
            isLast ? 'bg-cyan-300 text-[#03141c] hover:bg-cyan-200' : 'border border-white/[.08] bg-white/[.06] text-white hover:bg-white/[.1]')}>
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
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-cyan-300/10 text-cyan-300">{icon}</span>
      <div>
        <h3 className="text-xl font-black tracking-[-.03em] text-white sm:text-2xl">{title}</h3>
        <p className="mt-2 text-sm leading-6 text-white/50">{body}</p>
      </div>
    </div>
    <div className="mt-5">{children}</div>
  </div>
}
