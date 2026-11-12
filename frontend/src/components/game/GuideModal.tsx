import type { ReactNode } from 'react'
import { useCallback, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import {
  ArrowLeft,
  ArrowRight,
  Award,
  ChevronDown,
  Flame,
  Globe,
  Leaf,
  Map,
  Compass,
  Search,
  Shield,
  Smartphone,
  Sparkles,
  Target,
  Zap,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { gameApi } from '@/api/game'
import { Modal } from '@/components/ui/Modal'
import { BadgeCard } from '@/components/game/BadgeCard'
import { SecretBadgeCard } from '@/components/game/SecretBadgeCard'
import { DifficultyBadge } from '@/components/game/DifficultyBadge'
import { XpProgressBar } from '@/components/ui/XpProgressBar'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'

const TOTAL_STEPS = 5

interface GuideModalProps {
  isOpen: boolean
  onClose: () => void
}

export function GuideModal({ isOpen, onClose }: GuideModalProps) {
  const { t } = useTranslation()
  const [step, setStep] = useState(0)
  const [direction, setDirection] = useState(1)

  const goNext = useCallback(() => {
    if (step < TOTAL_STEPS - 1) {
      setDirection(1)
      setStep((s) => s + 1)
    }
  }, [step])

  const goBack = useCallback(() => {
    if (step > 0) {
      setDirection(-1)
      setStep((s) => s - 1)
    }
  }, [step])

  const handleClose = useCallback(() => {
    setStep(0)
    setDirection(1)
    onClose()
  }, [onClose])

  const isLast = step === TOTAL_STEPS - 1

  return (
    <Modal ariaLabel={t.guide.title} isOpen={isOpen} onClose={handleClose} title="" className="max-w-2xl">
      <div className="flex flex-col gap-5">
        <div className="flex items-center justify-between">
          <p className="text-[11px] uppercase tracking-[.22em] text-white/28">
            {t.guide.stepOf.replace('{current}', String(step + 1)).replace('{total}', String(TOTAL_STEPS))}
          </p>
          <div className="flex gap-1.5">
            {Array.from({ length: TOTAL_STEPS }, (_, i) => (
              <button
                key={i}
                type="button"
                onClick={() => {
                  setDirection(i > step ? 1 : -1)
                  setStep(i)
                }}
                className="relative h-2 w-2 rounded-full"
              >
                <span
                  className={cn(
                    'absolute inset-0 rounded-full transition-colors duration-200',
                    i === step ? 'bg-white' : 'bg-white/20'
                  )}
                />
                {i === step && (
                  <motion.span
                    layoutId="guide-dot"
                    className="absolute -inset-0.5 rounded-full border border-white/30"
                    transition={{ type: 'spring', stiffness: 400, damping: 30 }}
                  />
                )}
              </button>
            ))}
          </div>
        </div>

        <AnimatePresence mode="wait" custom={direction}>
          <motion.div
            key={step}
            custom={direction}
            initial={{ opacity: 0, x: direction * 40 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: direction * -40 }}
            transition={{ duration: 0.25, ease: [0.25, 0.1, 0.25, 1] }}
          >
            {step === 0 && <StepClues />}
            {step === 1 && <StepSearch />}
            {step === 2 && <StepModes />}
            {step === 3 && <StepStreaks />}
            {step === 4 && <StepResults />}
          </motion.div>
        </AnimatePresence>

        <div className="flex items-center gap-3">
          {step > 0 && (
            <button
              type="button"
              onClick={goBack}
              className="inline-flex min-h-[44px] items-center gap-2 rounded-2xl border border-white/[.08] bg-white/[.04] px-4 text-sm font-medium text-white/60 transition-colors hover:bg-white/[.07] hover:text-white"
            >
              <ArrowLeft size={15} />
              {t.guide.previous}
            </button>
          )}

          <button
            type="button"
            onClick={isLast ? handleClose : goNext}
            className={cn(
              'ml-auto inline-flex min-h-[44px] items-center gap-2 rounded-2xl px-5 text-sm font-semibold transition-colors',
              isLast
                ? 'bg-white text-black hover:bg-white/90'
                : 'border border-white/[.08] bg-white/[.06] text-white hover:bg-white/[.1]'
            )}
          >
            {isLast ? t.guide.gotIt : t.guide.next}
            {!isLast && <ArrowRight size={15} />}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function StepClues() {
  const { t } = useTranslation()

  const clues = [
    { label: t.guide.step1Clue1Label, text: t.guide.step1Clue1, icon: <Flame size={14} /> },
    { label: t.guide.step1Clue2Label, text: t.guide.step1Clue2, icon: <Smartphone size={14} /> },
    { label: t.guide.step1Clue3Label, text: t.guide.step1Clue3, icon: <Leaf size={14} /> },
  ]

  return (
    <div>
      <h3 className="text-xl font-semibold tracking-tight text-white sm:text-2xl">{t.guide.step1Title}</h3>
      <p className="mt-2.5 text-sm leading-relaxed text-white/48">{t.guide.step1Body}</p>

      <div className="mt-5 space-y-2.5">
        {clues.map((clue, i) => (
          <motion.div
            key={clue.label}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.08, duration: 0.35 }}
            className="rounded-2xl border border-white/[.06] bg-black/18 px-4 py-3.5"
          >
            <div className="flex items-center gap-2">
              <span className="inline-flex h-6 w-6 items-center justify-center rounded-lg bg-white/[.06] text-white/50">
                {clue.icon}
              </span>
              <span className="text-[10px] font-semibold uppercase tracking-[.2em] text-white/30">{clue.label}</span>
            </div>
            <p className="mt-2 text-[0.84rem] leading-relaxed text-white/64">{clue.text}</p>
          </motion.div>
        ))}
      </div>
    </div>
  )
}

function StepSearch() {
  const { t } = useTranslation()

  return (
    <div>
      <h3 className="text-xl font-semibold tracking-tight text-white sm:text-2xl">{t.guide.step2Title}</h3>
      <p className="mt-2.5 text-sm leading-relaxed text-white/48">{t.guide.step2Body}</p>

      <div className="mt-5 rounded-2xl border border-white/[.06] bg-black/18 p-4">
        <div className="flex items-center gap-3 rounded-xl border border-white/[.08] bg-white/[.03] px-3.5 py-2.5">
          <Search size={16} className="shrink-0 text-white/32" />
          <span className="text-sm text-white/80">Indone</span>
          <span className="text-sm text-white/24">|</span>
        </div>

        <div className="mt-2 rounded-xl border border-white/[.06] bg-white/[.02] p-1">
          <div className="flex items-center gap-3 rounded-lg bg-white/[.06] px-3 py-2">
            <span className="text-base">🇮🇩</span>
            <span className="text-sm font-medium text-white/90">Indonesia</span>
            <span className="ml-auto text-xs text-white/30">ID</span>
          </div>
          <div className="flex items-center gap-3 px-3 py-2">
            <span className="text-base">🇮🇳</span>
            <span className="text-sm text-white/50">India</span>
            <span className="ml-auto text-xs text-white/20">IN</span>
          </div>
        </div>

        <div className="mt-3 grid grid-cols-2 gap-2.5">
          <MiniStat label={t.guide.step2Attempts} value="✓" accent />
          <MiniStat label={t.guide.step2CluesRevealed} value="2 / 5" />
        </div>
      </div>
    </div>
  )
}

function StepModes() {
  const { t } = useTranslation()

  const modes = [
    { level: 'EASY' as const, label: t.home.easy, desc: t.guide.step3EasyDesc, icon: <Globe size={18} />, accent: '#34d399' },
    { level: 'MEDIUM' as const, label: t.home.normal, desc: t.guide.step3NormalDesc, icon: <Map size={18} />, accent: '#a78bfa' },
    { level: 'HARD' as const, label: t.home.hard, desc: t.guide.step3HardDesc, icon: <Compass size={18} />, accent: '#fb7185' },
  ]

  return (
    <div>
      <h3 className="text-xl font-semibold tracking-tight text-white sm:text-2xl">{t.guide.step3Title}</h3>
      <p className="mt-2.5 text-sm leading-relaxed text-white/48">{t.guide.step3Body}</p>

      <div className="mt-5 grid gap-2.5 sm:grid-cols-3">
        {modes.map((mode, i) => (
          <motion.div
            key={mode.level}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06, duration: 0.35 }}
            className="rounded-2xl border border-white/[.06] bg-black/18 p-4"
          >
            <div className="mb-3 flex items-center gap-2.5">
              <span
                className="inline-flex h-8 w-8 items-center justify-center rounded-xl"
                style={{ backgroundColor: mode.accent + '18', color: mode.accent }}
              >
                {mode.icon}
              </span>
              <DifficultyBadge level={mode.level} size="sm" />
            </div>
            <p className="text-xs leading-relaxed text-white/48">{mode.desc}</p>
          </motion.div>
        ))}
      </div>

      <div className="mt-3 flex items-center gap-2 rounded-xl border border-white/[.06] bg-white/[.02] px-3.5 py-2.5">
        <Sparkles size={14} className="text-amber-300/60" />
        <span className="text-xs text-white/44">{t.guide.step3Daily}</span>
      </div>
    </div>
  )
}

function StepStreaks() {
  const { t } = useTranslation()

  return (
    <div>
      <h3 className="text-xl font-semibold tracking-tight text-white sm:text-2xl">
        {t.guide.step4StreakTitle}
      </h3>
      <p className="mt-2.5 text-sm leading-relaxed text-white/48">
        {t.guide.step4StreakBody}
      </p>

      <div className="mt-5 space-y-2.5">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0, duration: 0.35 }}
          className="rounded-2xl border border-white/[.06] bg-black/18 px-4 py-3.5"
        >
          <div className="flex items-center gap-3">
            <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-amber-400/15 bg-amber-400/[.08]">
              <Flame size={20} className="text-amber-400" />
            </span>
            <div>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-black tabular-nums text-white">14</span>
                <span className="text-[10px] uppercase tracking-[.2em] text-white/30">day streak</span>
              </div>
              <span className="mt-1 inline-flex items-center gap-1 rounded-full border border-amber-300/15 bg-amber-300/[.07] px-2 py-0.5 text-xs font-semibold text-amber-300/80">
                +25 XP bonus
              </span>
            </div>
          </div>
          <p className="mt-3 text-xs leading-relaxed text-white/40">
            {t.guide.step4StreakBonusLabel}
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.08, duration: 0.35 }}
          className="rounded-2xl border border-white/[.06] bg-black/18 px-4 py-3.5"
        >
          <div className="flex items-center gap-3">
            <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-purple-400/15 bg-purple-400/[.08]">
              <Shield size={20} className="text-purple-400" />
            </span>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-lg font-bold text-white">{t.guide.step4ShieldLabel}</span>
                <span className="inline-flex items-center gap-1 rounded-full border border-purple-400/20 bg-purple-400/[.08] px-2 py-0.5 text-[11px] font-semibold text-purple-300/80">
                  ×3
                </span>
              </div>
            </div>
          </div>
          <p className="mt-3 text-xs leading-relaxed text-white/40">
            {t.guide.step4ShieldBody}
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.16, duration: 0.35 }}
          className="flex items-center gap-2.5 rounded-xl border border-orange-400/15 bg-orange-400/[.05] px-3.5 py-2.5"
        >
          <Flame size={14} className="animate-pulse text-orange-400" />
          <span className="text-xs text-orange-200/70">
            {t.home.streakAtRisk.replace('{count}', '14').replace('{time}', t.home.timeLeftHours.replace('{n}', '2'))}
          </span>
        </motion.div>
      </div>
    </div>
  )
}

function StepResults() {
  const { t } = useTranslation()
  const { data: samples = [] } = useQuery({
    queryKey: ['guide-badge-samples'],
    queryFn: () => gameApi.getBadgeSamples(),
    staleTime: 60_000,
  })
  const [secretRevealed, setSecretRevealed] = useState(false)

  const unlockedBadge = samples[0] ? { ...samples[0], unlocked: true } : null
  const lockedBadge = samples[1] ? { ...samples[1], unlocked: false, grantedAt: null } : null
  const secretBadge = samples[2] ?? null

  return (
    <div>
      <h3 className="text-xl font-semibold tracking-tight text-white sm:text-2xl">{t.guide.step4Title}</h3>
      <p className="mt-2.5 text-sm leading-relaxed text-white/48">{t.guide.step4Body}</p>

      <div className="mt-5 rounded-2xl border border-white/[.06] bg-black/18 p-4">
        <XpProgressBar
          totalXp={2240}
          level={5}
          xpForCurrentLevel={1800}
          xpForNextLevel={2500}
          levelDivisor={100}
          showCurrentLevelMarker
          showNextLevelMarker
        />

        <div className="mt-4 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          <MiniStat label={t.guide.step4Xp} value="240" accent />
          <MiniStat label={t.guide.step4Accuracy} value="83%" />
          <MiniStat label={t.guide.step4Time} value="04:18" />
          <MiniStat label={t.guide.step4Streak} value="7" />
        </div>
      </div>

      <div className="mt-4 rounded-2xl border border-white/[.06] bg-black/18 p-4">
        <div className="flex items-center gap-2 mb-3">
          <Award size={14} className="text-amber-300/70" />
          <p className="text-[11px] uppercase tracking-[.2em] text-white/28">{t.guide.step4BadgesLabel}</p>
        </div>
        <p className="text-xs leading-relaxed text-white/40 mb-3">{t.guide.step4BadgesBody}</p>
        <div className="grid grid-cols-3 gap-2">
          {unlockedBadge ? (
            <BadgeCard badge={unlockedBadge} />
          ) : (
            <BadgePreview icon={<Zap size={14} />} name={t.guide.step4Badge1} desc={t.guide.step4Badge1Desc} color="#34d399" />
          )}
          {lockedBadge ? (
            <BadgeCard badge={lockedBadge} />
          ) : (
            <BadgePreview icon={<Target size={14} />} name={t.guide.step4Badge2} desc={t.guide.step4Badge2Desc} color="#a78bfa" />
          )}
          <SecretBadgeCard badge={secretBadge} revealed={secretRevealed} onReveal={() => setSecretRevealed(true)} />
        </div>
      </div>

      <div className="mt-5">
        <p className="mb-3 text-[11px] uppercase tracking-[.2em] text-white/28">{t.guide.faqSection}</p>
        <div className="divide-y divide-white/[.06] rounded-2xl border border-white/[.06] bg-black/18">
          <FaqItem question={t.guide.faq1Q} answer={t.guide.faq1A} />
          <FaqItem question={t.guide.faq2Q} answer={t.guide.faq2A} />
          <FaqItem question={t.guide.faq3Q} answer={t.guide.faq3A} />
          <FaqItem
            question={t.guide.faq4Q}
            answer={t.guide.faq4A}
            links={[
              { to: '/privacy', label: t.footer.privacyLink },
              { to: '/terms', label: t.footer.termsLink },
              { to: '/data-sources', label: t.footer.registryLink },
            ]}
          />
        </div>
      </div>
    </div>
  )
}

function FaqItem({
  answer,
  links,
  question,
}: {
  answer: string
  links?: { to: string; label: string }[]
  question: string
}) {
  const [open, setOpen] = useState(false)

  return (
    <div className="px-4">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex w-full items-center justify-between gap-3 py-3.5 text-left"
      >
        <span className="text-sm font-medium text-white/72">{question}</span>
        <ChevronDown
          size={15}
          className={cn('shrink-0 text-white/28 transition-transform duration-200', open && 'rotate-180')}
        />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <p className="pb-3.5 text-sm leading-relaxed text-white/44">{answer}</p>
            {links && (
              <div className="flex flex-wrap gap-2 pb-3.5">
                {links.map((link) => (
                  <Link
                    key={link.to}
                    to={link.to}
                    className="rounded-lg border border-white/[.06] bg-white/[.03] px-3 py-1.5 text-xs text-white/50 transition-colors hover:bg-white/[.06] hover:text-white/72"
                  >
                    {link.label}
                  </Link>
                ))}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function BadgePreview({
  color,
  desc,
  icon,
  name,
}: {
  color: string
  desc: string
  icon: ReactNode
  name: string
}) {
  return (
    <div className="flex items-start gap-2.5 rounded-xl border border-white/[.06] bg-white/[.02] px-3 py-2.5">
      <span
        className="mt-0.5 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
        style={{ backgroundColor: color + '18', color }}
      >
        {icon}
      </span>
      <div className="min-w-0">
        <p className="text-xs font-semibold text-white/72">{name}</p>
        <p className="mt-0.5 text-[11px] leading-snug text-white/32">{desc}</p>
      </div>
    </div>
  )
}

function MiniStat({ accent, label, value }: { accent?: boolean; label: string; value: string }) {
  return (
    <div className="rounded-xl border border-white/[.06] bg-white/[.02] px-3 py-2.5">
      <p className="text-[10px] uppercase tracking-[.18em] text-white/25">{label}</p>
      <p className={cn('mt-1.5 text-lg font-semibold', accent ? 'text-white' : 'text-white/72')}>{value}</p>
    </div>
  )
}
