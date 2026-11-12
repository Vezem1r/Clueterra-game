import type { ReactNode } from 'react'
import { useState, useCallback, useRef } from 'react'
import { ArrowRight, Check, CheckCircle2, Clock, Share2, Sparkles, Target, Trophy, User } from 'lucide-react'
import { motion } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import { DifficultyBadge } from '@/components/game/DifficultyBadge'
import { StreakStatusStrip } from '@/components/game/StreakStatusStrip'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import { formatDurationMs } from '@/utils/format'
import { shareSummary } from '@/utils/shareResult'
import { HomePanelShell } from './HomePanelShell'
import type { HomeDifficultyOption, HomeSummaryContext } from './types'

interface HomeCompletedPanelProps {
  isAuthenticated: boolean
  onRegister: () => void
  option: HomeDifficultyOption
  summaryContext: HomeSummaryContext
}

export function HomeCompletedPanel({
  isAuthenticated,
  onRegister,
  option,
  summaryContext,
}: HomeCompletedPanelProps) {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const toast = useToastStore()
  const { profile, summary } = summaryContext
  const [copied, setCopied] = useState(false)
  const copiedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const handleShare = useCallback(async () => {
    if (!summary) return
    const outcome = await shareSummary(summary)
    if (outcome === 'copied') {
      setCopied(true)
      toast.addToast({ type: 'success', message: t.results.shareCopied })
      if (copiedTimerRef.current) clearTimeout(copiedTimerRef.current)
      copiedTimerRef.current = setTimeout(() => setCopied(false), 2000)
    }
  }, [summary, toast, t])

  if (!summary) {
    return null
  }

  const solvedAll = summary.guessedCountries === summary.totalCountries
  const xpCaption = solvedAll
    ? t.home.resultSolvedAll
    : t.home.resultFieldPartial
        .replace('{guessed}', String(summary.guessedCountries))
        .replace('{total}', String(summary.totalCountries))
  const shellBoxShadow = [
    '0 32px 90px rgba(0,0,0,.48)',
    '0 0 0 1px rgba(255,255,255,.05)',
    `0 -1px 60px ${option.accent}20`,
    `0 1px 80px ${option.accent}16`,
  ].join(', ')
  const topBackground = `linear-gradient(180deg, ${option.accent}36 0%, rgba(7,15,25,.90) 55%, rgba(5,10,18,.95) 100%)`
  const topAccentClouds = `radial-gradient(circle at 18% 18%, ${option.accent}2f 0%, transparent 36%), radial-gradient(circle at 82% 10%, ${option.accent}24 0%, transparent 28%)`
  const bottomBackground = `radial-gradient(circle at 50% 115%, ${option.accent}22 0%, transparent 42%), linear-gradient(180deg, rgba(7,11,19,.94), rgba(4,7,14,.98))`

  return (
    <HomePanelShell
      className="max-w-[50rem] px-0 py-0 sm:px-0 sm:py-0 lg:px-0"
      style={{ boxShadow: shellBoxShadow }}
    >
      <div className="pointer-events-none absolute inset-0 overflow-hidden rounded-[2.4rem] hidden sm:block">
        <div
          className="absolute -left-12 -top-12 h-72 w-72 rounded-full blur-3xl opacity-[.22]"
          style={{ background: option.accent }}
        />
        <div
          className="absolute -right-10 -top-4 h-52 w-52 rounded-full blur-3xl opacity-[.12]"
          style={{ background: option.accent }}
        />
        <div
          className="absolute bottom-4 left-1/3 h-36 w-36 rounded-full blur-2xl opacity-[.07]"
          style={{ background: option.accent }}
        />
      </div>

      <div className="relative flex flex-1 flex-col overflow-hidden rounded-[calc(2.4rem-1px)]">
        <div className="relative overflow-hidden px-4 pb-7 pt-4 sm:px-6 sm:pb-8 sm:pt-5">
          <div className="pointer-events-none absolute inset-0" style={{ background: topBackground }} />
          <div
            className="pointer-events-none absolute inset-x-0 top-0 h-full opacity-70"
            style={{ background: topAccentClouds }}
          />

          <div className="relative flex items-center justify-between gap-3">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/[.08] bg-white/[.04] px-3 py-1.5 text-[11px] uppercase tracking-[.18em] text-white/38 ring-1 ring-white/[.08]">
              <Trophy size={12} />
              {t.home.actionCompletedBadge}
            </div>
            <DifficultyBadge level={summary.level} size="sm" />
          </div>

          <div className="relative mt-7 flex flex-col items-center text-center sm:mt-8">
            <div
              className="pointer-events-none absolute -top-6 left-1/2 h-36 w-2/3 -translate-x-1/2 rounded-full blur-3xl hidden sm:block"
              style={{ background: option.grad, opacity: 0.15 }}
            />

            <p className="relative text-[10px] uppercase tracking-[.22em] text-white/30">
              {t.home.earnedXp}
            </p>

            <motion.div
              className="relative mt-3 flex items-baseline gap-2.5"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.45, ease: 'easeOut' }}
            >
              <span
                className="px-1 text-[4.5rem] font-black leading-none tracking-[-0.06em] sm:text-[5.5rem]"
                style={{
                  background: option.grad,
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                  backgroundClip: 'text',
                }}
              >
                {summary.totalXpEarned}
              </span>
              <Sparkles size={22} className="mb-2 shrink-0 text-white/28" />
            </motion.div>

            <p className="relative mt-2.5 text-[13px] font-medium text-white/50">{xpCaption}</p>

            {profile && (
              <div className="relative mt-4">
                <StreakStatusStrip profile={profile} />
              </div>
            )}
          </div>
        </div>

        <div className="h-px bg-white/[.06]" />

        <div className="relative px-4 pb-4 pt-6 sm:px-6 sm:pb-5 sm:pt-7">
          <div className="pointer-events-none absolute inset-0" style={{ background: bottomBackground }} />
          <div
            className="pointer-events-none absolute inset-x-[15%] bottom-0 h-24 blur-3xl opacity-20 hidden sm:block"
            style={{ background: option.grad }}
          />

          <div className="relative flex items-start justify-center gap-6 sm:gap-10">
            <MiniStat
              icon={<CheckCircle2 size={12} />}
              label={t.home.continueSolvedLabel}
              value={`${summary.guessedCountries}/${summary.totalCountries}`}
            />
            <div className="mt-1 h-8 w-px bg-white/[.07]" />
            <MiniStat
              icon={<Clock size={12} />}
              label={t.home.resultMetricTime}
              value={formatDurationMs(summary.activeTimeMs)}
            />
            <div className="mt-1 h-8 w-px bg-white/[.07]" />
            <MiniStat
              icon={<Target size={12} />}
              label={t.home.continueAttemptsLabel}
              value={String(summary.totalAttemptsUsed)}
            />
          </div>

          <div className="relative mt-6 flex gap-3">
            <div className="relative flex-1">
              <div
                className="animate-glow-breathe pointer-events-none absolute inset-x-8 -bottom-1 top-2 rounded-[1.4rem] blur-2xl hidden sm:block"
                style={{ background: option.grad }}
              />
              <button
                type="button"
                onClick={() => navigate('/results')}
                className="relative inline-flex min-h-[60px] w-full items-center justify-center gap-3 rounded-[1.4rem] px-5 text-base font-semibold text-white transition-all hover:-translate-y-0.5 hover:brightness-110"
                style={{
                  background: option.grad,
                  boxShadow: `${option.shadow}, inset 0 1px 0 rgba(255,255,255,.16)`,
                }}
              >
                <Trophy size={16} />
                {t.home.viewResults}
                <ArrowRight size={16} />
              </button>
            </div>
            <button
              type="button"
              onClick={handleShare}
              className="relative flex min-h-[60px] w-[60px] shrink-0 items-center justify-center rounded-[1.4rem] border border-white/[.10] bg-white/[.06] text-white/50 transition-all hover:-translate-y-0.5 hover:bg-white/[.10] hover:text-white/80"
            >
              {copied ? <Check size={20} /> : <Share2 size={20} />}
            </button>
          </div>

          {!isAuthenticated ? (
            <div className="relative mt-2.5 rounded-[1.2rem] border border-white/[.08] bg-white/[.03] px-4 py-3.5 sm:flex sm:items-center sm:justify-between sm:gap-4">
              <div className="flex items-start gap-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-emerald-400/20 bg-emerald-400/[.07]">
                  <User size={15} className="text-emerald-300/80" />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-white">{t.results.saveProgressTitle}</p>
                  <p className="mt-0.5 text-xs leading-relaxed text-white/42">{t.results.saveProgressBody}</p>
                </div>
              </div>
              <button
                type="button"
                onClick={onRegister}
                className="mt-3 inline-flex min-h-[42px] w-full items-center justify-center rounded-[1.05rem] border border-white/[.10] bg-white/[.06] px-5 text-sm font-semibold text-white/80 transition-colors hover:bg-white/[.10] sm:mt-0 sm:min-w-[10.5rem] sm:w-auto"
              >
                {t.results.createAccount}
              </button>
            </div>
          ) : null}
        </div>
      </div>
    </HomePanelShell>
  )
}

interface MiniStatProps {
  icon: ReactNode
  label: string
  value: string
}

function MiniStat({ icon, label, value }: MiniStatProps) {
  return (
    <div className="flex flex-col items-center gap-1">
      <div className="flex items-center gap-1 text-white/32">{icon}</div>
      <span className="text-[1.1rem] font-bold tracking-[-0.03em] text-white sm:text-[1.2rem]">
        {value}
      </span>
      <span className="text-[9px] uppercase tracking-[.15em] text-white/28">{label}</span>
    </div>
  )
}
