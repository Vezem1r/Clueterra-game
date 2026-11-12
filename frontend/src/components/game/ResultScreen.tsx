import { useState, type ReactNode } from 'react'
import { motion } from 'framer-motion'
import { Check, LoaderCircle, Sparkles, X } from 'lucide-react'
import type { GuessResult, Country } from '@/types'
import { useTranslation } from '@/i18n'
import { useDelayedBusy } from '@/hooks/useDelayedBusy'
import { getFlagUrlByIso2 } from '@/utils/country'

const RESULT_REVEAL_INITIAL = { opacity: 0.2, y: 16, scale: 0.97 }
const RESULT_REVEAL_ANIMATE = { opacity: 1, y: 0, scale: 1 }
const RESULT_REVEAL_TRANSITION = { type: 'tween' as const, duration: 0.42, ease: 'easeInOut' as const }
const FLAG_REVEAL_TRANSITION = { type: 'tween' as const, duration: 0.34, delay: 0.05, ease: 'easeOut' as const }
const BADGE_REVEAL_TRANSITION = { type: 'tween' as const, duration: 0.24, delay: 0.2, ease: 'easeOut' as const }

interface ResultScreenProps {
  result: GuessResult
  correctCountry: Country | undefined
  guessedCountry: Country | undefined
  repeatCount: number
  onContinue: () => void
  isContinuing: boolean
}

export function ResultScreen({ result, correctCountry, guessedCountry, repeatCount, onContinue, isContinuing }: ResultScreenProps) {
  const { t } = useTranslation()
  const [reactionIdx] = useState(() => ({
    correct: Math.floor(Math.random() * t.play.correctReactions.length),
    wrong: Math.floor(Math.random() * t.play.wrongReactions.length),
    repeat: Math.floor(Math.random() * t.play.repeatReactions.length),
  }))

  const displayCountry = result.correct ? (correctCountry || guessedCountry) : guessedCountry

  const reaction = result.correct
    ? t.play.correctReactions[reactionIdx.correct % t.play.correctReactions.length]
    : guessedCountry
      ? repeatCount >= 2
        ? t.play.repeatReactions[reactionIdx.repeat % t.play.repeatReactions.length]
            .replace('{country}', guessedCountry.nameEn)
        : t.play.wrongReactions[reactionIdx.wrong % t.play.wrongReactions.length]
            .replace('{country}', guessedCountry.nameEn)
      : ''

  if (result.correct) {
    return (
      <motion.div
        initial={RESULT_REVEAL_INITIAL}
        animate={RESULT_REVEAL_ANIMATE}
        transition={RESULT_REVEAL_TRANSITION}
        className="flex flex-col items-center justify-center py-16 sm:py-24"
      >
        <motion.div
          initial={{ opacity: 0, scale: 0.68, rotate: 7 }}
          animate={{ opacity: 1, scale: 1, rotate: 0 }}
          transition={FLAG_REVEAL_TRANSITION}
          className="relative mb-6 transform-gpu"
        >
          <CountryFlag country={displayCountry} />
          <motion.div
            initial={{ opacity: 0, scale: 0.45, rotate: -12 }}
            animate={{ opacity: 1, scale: 1, rotate: 0 }}
            transition={BADGE_REVEAL_TRANSITION}
            className="absolute -bottom-2 -right-2 w-8 h-8 rounded-full bg-success flex items-center justify-center shadow-lg shadow-success/30"
          >
            <Check size={16} className="text-white" strokeWidth={3} />
          </motion.div>
        </motion.div>

        <motion.h2
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'tween', duration: 0.28, delay: 0.14, ease: 'easeOut' }}
          className="text-3xl sm:text-5xl xl:text-6xl font-bold text-center mb-3"
        >
          {displayCountry?.nameEn || '???'}
        </motion.h2>

        <motion.p
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'tween', duration: 0.24, delay: 0.23, ease: 'easeOut' }}
          className="text-sm sm:text-base text-success/60 text-center mb-4 max-w-sm italic"
        >
          {reaction}
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'tween', duration: 0.24, delay: 0.3, ease: 'easeOut' }}
          className="flex items-center gap-2 text-success mb-10"
        >
          <Sparkles size={18} />
          <span className="text-lg font-semibold">+{result.xpEarned} {t.play.points}</span>
        </motion.div>

        <ResultActionReveal delay={0.36}>
          <ResultActionButton
            label={result.gameCompleted ? t.play.seeResults : t.play.continue}
            onClick={onContinue}
            busy={isContinuing}
          />
        </ResultActionReveal>
      </motion.div>
    )
  }

  if (result.countryComplete) {
    return (
      <motion.div
        initial={RESULT_REVEAL_INITIAL}
        animate={RESULT_REVEAL_ANIMATE}
        transition={RESULT_REVEAL_TRANSITION}
        className="flex flex-col items-center justify-center py-16 sm:py-24"
      >
        <motion.div
          initial={{ opacity: 0, scale: 0.68 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={FLAG_REVEAL_TRANSITION}
          className="relative mb-6 transform-gpu"
        >
          <CountryFlag country={correctCountry || guessedCountry} />
          <motion.div
            initial={{ opacity: 0, scale: 0.45 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={BADGE_REVEAL_TRANSITION}
            className="absolute -bottom-2 -right-2 w-8 h-8 rounded-full bg-white/10 border border-white/20 flex items-center justify-center"
          >
            <X size={16} className="text-white/40" strokeWidth={3} />
          </motion.div>
        </motion.div>

        <motion.h2
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'tween', duration: 0.28, delay: 0.14, ease: 'easeOut' }}
          className="text-3xl sm:text-5xl xl:text-6xl font-bold text-center mb-3 text-white/60"
        >
          {correctCountry?.nameEn || guessedCountry?.nameEn || '???'}
        </motion.h2>

        <motion.p
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'tween', duration: 0.24, delay: 0.23, ease: 'easeOut' }}
          className="text-white/35 mb-10"
        >
          {t.play.noAttempts}
        </motion.p>

        <ResultActionReveal delay={0.3}>
          <ResultActionButton
            label={result.gameCompleted ? t.play.seeResults : t.play.continue}
            onClick={onContinue}
            busy={isContinuing}
          />
        </ResultActionReveal>
      </motion.div>
    )
  }

  return (
    <motion.div
      initial={RESULT_REVEAL_INITIAL}
      animate={RESULT_REVEAL_ANIMATE}
      transition={RESULT_REVEAL_TRANSITION}
      className="flex flex-col items-center justify-center py-16 sm:py-24"
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.68, rotate: -7 }}
        animate={{ opacity: 1, scale: 1, rotate: 0 }}
        transition={FLAG_REVEAL_TRANSITION}
        className="relative mb-6 transform-gpu"
      >
        <CountryFlag country={guessedCountry} />
        <motion.div
          initial={{ opacity: 0, scale: 0.45, rotate: 12 }}
          animate={{ opacity: 1, scale: 1, rotate: 0 }}
          transition={BADGE_REVEAL_TRANSITION}
          className="absolute -bottom-2 -right-2 w-8 h-8 rounded-full bg-error/90 flex items-center justify-center shadow-lg shadow-error/30"
        >
          <X size={16} className="text-white" strokeWidth={3} />
        </motion.div>
      </motion.div>

      <motion.h2
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'tween', duration: 0.28, delay: 0.14, ease: 'easeOut' }}
        className="text-2xl sm:text-3xl font-bold text-center mb-3 text-white/60"
      >
        {guessedCountry?.nameEn || '???'}
      </motion.h2>

      <motion.p
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'tween', duration: 0.24, delay: 0.23, ease: 'easeOut' }}
        className="text-sm sm:text-base text-white/30 text-center mb-4 max-w-sm italic"
      >
        {reaction}
      </motion.p>

      <motion.p
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'tween', duration: 0.24, delay: 0.3, ease: 'easeOut' }}
        className="text-xs text-white/25 mb-10"
      >
        {result.attemptsRemaining > 0
          ? t.play.attemptsLeft.replace('{count}', String(result.attemptsRemaining))
          : t.play.noAttempts}
      </motion.p>

      <ResultActionReveal delay={0.36}>
        <ResultActionButton
          label={t.play.tryAgain}
          onClick={onContinue}
          busy={isContinuing}
        />
      </ResultActionReveal>
    </motion.div>
  )
}

function ResultActionReveal({ children, delay }: { children: ReactNode; delay: number }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'tween', duration: 0.24, delay, ease: 'easeOut' }}
    >
      {children}
    </motion.div>
  )
}

function ResultActionButton({
  label,
  onClick,
  busy,
}: {
  label: string
  onClick: () => void
  busy: boolean
}) {
  const showSpinner = useDelayedBusy(busy)

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={busy}
      aria-busy={busy}
      className="relative min-w-36 rounded-2xl border border-white/[.06] bg-white/[.06] px-10 py-4 text-sm font-semibold transition-[background-color,transform] duration-150 hover:-translate-y-px hover:bg-white/[.1] active:translate-y-0 active:scale-[.98] disabled:cursor-wait disabled:transform-none"
    >
      <span className={showSpinner ? 'invisible' : undefined}>{label}</span>
      {showSpinner && (
        <span className="absolute inset-0 flex items-center justify-center" aria-hidden>
          <LoaderCircle size={18} className="animate-spin" />
        </span>
      )}
    </button>
  )
}

function CountryFlag({ country }: { country: Country | undefined }) {
  if (!country) {
    return (
      <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-2xl bg-white/[.06] border border-white/[.1] flex items-center justify-center">
        <X size={32} className="text-white/20" strokeWidth={3} />
      </div>
    )
  }

  const flagSrc = getFlagUrlByIso2(country.isoAlpha2, 'w160')

  if (flagSrc) {
    return (
      <img
        src={flagSrc}
        alt={country.nameEn}
        className="w-20 h-14 sm:w-24 sm:h-16 rounded-lg object-cover border border-white/10 shadow-lg shadow-black/30"
      />
    )
  }

  return (
    <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-2xl bg-white/[.06] border border-white/[.1] flex items-center justify-center text-2xl font-bold text-white/30">
      {country.isoAlpha2}
    </div>
  )
}
