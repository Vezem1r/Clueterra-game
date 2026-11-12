import { useState, useRef, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Check, Clock, Flame, Globe2, Lightbulb, Share2, Target, Trophy } from 'lucide-react'
import { useTranslation } from '@/i18n'
import { DifficultyBadge } from '@/components/game/DifficultyBadge'
import { XpProgressBar } from '@/components/ui/XpProgressBar'
import { LevelUpModal } from '@/components/game/LevelUpModal'
import { StreakBonusChip } from '@/components/game/StreakBonusChip'
import { STREAK_BONUS_REVEAL } from '@/components/game/streakBonusReveal'
import type { GameResults, ProfileDto } from '@/types'
import { formatDurationMs, formatPercent, formatDecimal } from '@/utils/format'
import { shareResult } from '@/utils/shareResult'
import { useToastStore } from '@/store/toastStore'
import { StatCard } from './StatCard'
import { getToneChipClasses } from './constants'
import type { RunInsights } from './useRunInsights'

interface HeroProps {
  results: GameResults
  profile: ProfileDto | null
  insights: RunInsights
}

export function Hero({ results, profile, insights }: HeroProps) {
  const { t } = useTranslation()
  const toast = useToastStore()

  const [celebratingLevel, setCelebratingLevel] = useState<number | null>(null)
  const [copied, setCopied] = useState(false)
  const resolveRef = useRef<(() => void) | null>(null)
  const copiedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const streakBonusXp = Math.max(0, results.streakBonusXp)
  const baseXpEarned = Math.max(0, results.totalXpEarned - streakBonusXp)
  const hasStreakBonus = streakBonusXp > 0
  const canAnimateProfileXp = profile
    ? !results.guest
      && results.xpBefore >= 0
      && results.xpBefore < profile.totalXp
      && Math.abs(results.xpBefore + results.totalXpEarned - profile.totalXp) <= 1
    : false

  const handleLevelUp = useCallback((level: number) => {
    return new Promise<void>(resolve => {
      setCelebratingLevel(level)
      resolveRef.current = resolve
    })
  }, [])

  const handleDismissLevelUp = useCallback(() => {
    setCelebratingLevel(null)
    resolveRef.current?.()
    resolveRef.current = null
  }, [])

  const handleShare = useCallback(async () => {
    const outcome = await shareResult(results)
    if (outcome === 'copied') {
      setCopied(true)
      toast.addToast({ type: 'success', message: t.results.shareCopied })
      if (copiedTimerRef.current) clearTimeout(copiedTimerRef.current)
      copiedTimerRef.current = setTimeout(() => setCopied(false), 2000)
    }
  }, [results, toast, t])

  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      className="relative h-full overflow-hidden rounded-[1.75rem] bg-[linear-gradient(140deg,rgba(255,255,255,.045),rgba(15,23,42,.75))] border border-white/[.08] p-6 sm:p-8 lg:p-9 flex flex-col"
    >
      <div className="absolute -right-24 -top-24 w-60 h-60 rounded-full bg-amber-300/10 blur-3xl pointer-events-none hidden sm:block" />
      <div className="absolute -left-12 bottom-0 w-48 h-48 rounded-full bg-emerald-300/[.08] blur-3xl pointer-events-none hidden sm:block" />

      <div className="relative flex flex-col flex-1">
        <div className="mb-5 flex flex-wrap items-center gap-2.5">
          <div
            className={`inline-flex self-start items-center gap-2 px-3 py-1.5 rounded-full border text-xs font-semibold ${getToneChipClasses('amber')}`}
          >
            <Trophy size={14} />
            {t.results.title}
          </div>
          <DifficultyBadge level={results.level} size="sm" />
          <button
            type="button"
            onClick={handleShare}
            className="ml-auto inline-flex items-center gap-1.5 rounded-full border border-white/[.08] bg-white/[.04] px-3 py-1.5 text-xs font-semibold text-white/40 transition-colors hover:bg-white/[.08] hover:text-white/70 cursor-pointer"
          >
            {copied ? <Check size={13} /> : <Share2 size={13} />}
            {t.results.shareResult}
          </button>
        </div>

        <p className="text-white/38 text-sm leading-relaxed max-w-2xl mb-5">{t.results.subtitle}</p>

        <div className="mb-8 lg:mb-0 lg:flex-1 lg:min-h-0 lg:flex lg:items-center lg:[container-type:size]">
          <div className="text-center sm:text-left">
            <div className="sm:flex sm:items-end sm:flex-wrap sm:gap-x-4 sm:gap-y-2">
              <span
                className="block w-full text-[4.5rem] font-black leading-[.84] tracking-normal sm:inline sm:w-auto sm:text-[6.4rem] lg:text-[clamp(8rem,65cqh,16rem)]"
              >
                {baseXpEarned}
              </span>
              {hasStreakBonus && (
                <motion.div
                  className="relative -mb-0.5 hidden shrink-0 will-change-transform items-center gap-2.5 rounded-[1.15rem] border border-amber-200/15 bg-[linear-gradient(135deg,rgba(251,191,36,.14),rgba(249,115,22,.045))] px-3.5 py-2 shadow-[inset_0_1px_0_rgba(255,255,255,.08),0_0_34px_rgba(251,191,36,.1)] sm:-mb-1 sm:inline-flex"
                  initial={{ opacity: 0, scale: 0.96 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={STREAK_BONUS_REVEAL}
                >
                  <span className="pointer-events-none absolute inset-x-3 top-0 h-px bg-gradient-to-r from-transparent via-amber-100/35 to-transparent" />
                  <span className="relative flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-amber-200/15 bg-amber-200/10 lg:h-[clamp(2rem,13cqh,4rem)] lg:w-[clamp(2rem,13cqh,4rem)]">
                    <span className="absolute inset-0 rounded-xl bg-amber-300/10 blur-md" />
                    <Flame size={16} className="relative text-amber-300" />
                  </span>
                  <span className="flex flex-col leading-none">
                    <span className="flex items-baseline gap-1">
                      <span className="bg-gradient-to-b from-amber-50 to-amber-300 bg-clip-text text-[1.7rem] font-black tabular-nums text-transparent lg:text-[clamp(1.7rem,17cqh,4rem)]">
                        +{streakBonusXp}
                      </span>
                      <span className="text-[10px] font-black uppercase text-amber-200/45">XP</span>
                    </span>
                    <span className="mt-0.5 text-[8px] font-bold uppercase tracking-[.18em] text-amber-200/42">
                      {t.results.streakBonus}
                    </span>
                  </span>
                </motion.div>
              )}
              <span className="hidden pb-3 text-sm uppercase tracking-[.3em] text-white/30 sm:inline">
                {t.play.points}
              </span>
            </div>
            <div className="mt-3 text-[10px] font-bold uppercase tracking-[.2em] text-white/28 sm:hidden">
              {t.play.points}
            </div>
            <StreakBonusChip bonusXp={streakBonusXp} className="mt-4 sm:hidden" />

          </div>
        </div>

        <div className="mt-auto grid sm:grid-cols-2 xl:grid-cols-4 gap-3 sm:gap-4">
          <StatCard
            icon={<Globe2 size={18} />}
            label={t.results.countries}
            value={`${results.guessedCountries}/${results.totalCountries}`}
            tone="emerald"
            layout="header"
          />
          <StatCard
            icon={<Clock size={18} />}
            label={t.results.time}
            value={formatDurationMs(results.activeTimeMs)}
            tone="sky"
            layout="header"
          />
          <StatCard
            icon={<Target size={18} />}
            label={t.results.shotAccuracy}
            value={formatPercent(insights.accuracyRate)}
            tone="amber"
            layout="header"
          />
          <StatCard
            icon={<Lightbulb size={18} />}
            label={t.results.avgCluesRun}
            value={formatDecimal(insights.avgHintsSeen)}
            tone="violet"
            layout="header"
          />
        </div>

        {profile ? (
          <div className="mt-7 pt-6 border-t border-white/[.08]">
            <XpProgressBar
              totalXp={profile.totalXp}
              level={profile.level}
              xpForCurrentLevel={profile.xpForCurrentLevel}
              xpForNextLevel={profile.xpForNextLevel}
              levelDivisor={profile.levelDivisor}
              previousTotalXp={canAnimateProfileXp ? results.xpBefore : undefined}
              previousLevel={canAnimateProfileXp ? results.levelBefore : undefined}
              sessionId={canAnimateProfileXp ? results.id : undefined}
              onLevelUp={handleLevelUp}
            />
          </div>
        ) : null}
      </div>

      <LevelUpModal
        isOpen={celebratingLevel !== null}
        onClose={handleDismissLevelUp}
        newLevel={celebratingLevel ?? 0}
      />
    </motion.div>
  )
}
