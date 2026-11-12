import { useCallback, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowLeft, ArrowRight, Flag, Lightbulb, Search, ScanSearch } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { FlagClue, FlagHints } from '@/api/flags'
import type { SilhouetteLocale } from '@/api/silhouettes'
import { Modal } from '@/components/ui/Modal'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { SilhouetteGuideShot, SilhouetteGuideStage } from '../silhouettes/SilhouetteGuideStage'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import { FlagCard } from './FlagCard'
import { FlagClues } from './FlagClues'
import { FLAG_GUIDE_PATH, FLAG_NAME_EXAMPLE, FLAG_PLAZA_EXAMPLE } from './flagGameConstants'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

const FLAG_STEP = 0
const NAME_STEP = 1
const CLUES_STEP = 2
const PLAZA_STEP = 3
const TOTAL_STEPS = 4
const DEMO_FIRST_CLUES = 1
const DEMO_LIGHT_CLUE_XP = 12
const DEMO_FULL_CLUE_XP = 24
const DEMO_FLAG = 'it'
const DEMO_SUBREGION = 'Southern Europe'
const DEMO_CLUES: FlagClue[] = [
  { kind: 'REGION', costXp: DEMO_LIGHT_CLUE_XP },
  { kind: 'INITIAL', costXp: DEMO_LIGHT_CLUE_XP },
  { kind: 'CAPITAL', costXp: DEMO_FULL_CLUE_XP },
]
const STEP_ICONS = [<ScanSearch size={22} />, <Search size={22} />, <Lightbulb size={22} />, <Flag size={22} />]

interface FlagGuideModalProps {
  isOpen: boolean
  onClose: () => void
}

export function FlagGuideModal({ isOpen, onClose }: FlagGuideModalProps) {
  const { t, language } = useTranslation()
  const copy = t.flagGame
  const localize = useLocalizedPath()
  const guide = copy.guide
  const [step, setStep] = useState(FLAG_STEP)
  const [direction, setDirection] = useState(1)
  const [demoClues, setDemoClues] = useState(DEMO_FIRST_CLUES)
  const isLast = step === TOTAL_STEPS - 1
  const hints: FlagHints = {
    region: { region: 'Europe', subregion: DEMO_SUBREGION },
    initials: demoClues >= 2 ? guide.demoInitial : null,
    capitals: demoClues >= DEMO_CLUES.length ? [guide.demoCapital] : null,
  }

  const goTo = useCallback((next: number) => {
    setDirection(next > step ? 1 : -1)
    setStep(next)
  }, [step])

  const handleClose = useCallback(() => {
    setStep(FLAG_STEP)
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
            <span className={cn('absolute inset-0 rounded-full transition-colors', index === step ? 'bg-amber-200' : 'bg-white/20')} />
          </button>)}
        </div>
      </div>

      <AnimatePresence mode="wait" custom={direction}>
        <motion.div key={step} custom={direction} initial={{ x: direction * 28 }} animate={{ x: 0 }}
          exit={{ x: direction * -28 }} transition={{ duration: 0.2, ease: [0.25, 0.1, 0.25, 1] }} className="will-change-transform">
          <GuideStep icon={STEP_ICONS[step]} title={guide.howStepTitles[step]} body={guide.howSteps[step]}>
            <SilhouetteGuideStage className={cn(step === CLUES_STEP ? 'min-h-64 p-2 sm:min-h-80 sm:p-6' : 'h-64 sm:h-80',
              (step === FLAG_STEP || step === NAME_STEP) && 'p-5 sm:p-6')}>
              {step === FLAG_STEP && <FlagCard src={flagUrl(DEMO_FLAG)} alt={copy.flagLabel} eager className="h-40 sm:h-52" />}
              {step === NAME_STEP && <SilhouetteGuideShot image={FLAG_NAME_EXAMPLE} alt={guide.nameAlt} eager />}
              {step === CLUES_STEP && <div className="w-full max-w-md rounded-[1.25rem] border border-white/[.1] bg-[#0a0f1b] p-3 shadow-[0_24px_60px_rgba(0,0,0,.6)] sm:p-4">
                <FlagClues copy={copy} regions={t.silhouetteGame.regions} language={language as SilhouetteLocale} hints={hints}
                  clues={DEMO_CLUES} hintsUsed={demoClues} busy={false}
                  onReveal={() => setDemoClues(count => Math.min(DEMO_CLUES.length, count + 1))} />
              </div>}
              {step === PLAZA_STEP && <SilhouetteGuideShot image={FLAG_PLAZA_EXAMPLE} alt={guide.plazaAlt} cover eager />}
            </SilhouetteGuideStage>
            {isLast && <Link to={localize(FLAG_GUIDE_PATH)} onClick={handleClose}
              className="mt-4 inline-flex items-center gap-1.5 text-xs font-semibold text-amber-200/75 transition-colors hover:text-amber-100">
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
            isLast ? 'bg-amber-200 text-[#1f0b10] hover:bg-amber-100' : 'border border-white/[.08] bg-white/[.06] text-white hover:bg-white/[.1]')}>
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
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-200/10 text-amber-200">{icon}</span>
      <div>
        <h3 className="text-xl font-black tracking-[-.03em] text-white sm:text-2xl">{title}</h3>
        <p className="mt-2 text-sm leading-6 text-white/50">{body}</p>
      </div>
    </div>
    <div className="mt-5">{children}</div>
  </div>
}
