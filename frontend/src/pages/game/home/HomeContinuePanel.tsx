import type { ReactNode } from 'react'
import { useState } from 'react'
import { ArrowRight, Clock3, Play, Sparkles, Target, Trophy } from 'lucide-react'
import { motion } from 'framer-motion'
import { DifficultyBadge } from '@/components/game/DifficultyBadge'
import { LegalModal } from '@/components/ui/LegalModal'
import { useTranslation } from '@/i18n'
import { formatDurationMs } from '@/utils/format'
import { cn } from '@/utils/cn'
import { HomeGuestLegalNotice } from './HomeGuestLegalNotice'
import { HomePanelShell } from './HomePanelShell'
import type { HomeDifficultyOption } from './types'
import type { GameSessionSummary } from '@/types'

interface HomeContinuePanelProps {
  guestActionError: string
  guestNeedsSessionReacceptance: boolean
  isAuthenticated: boolean
  isBusy: boolean
  legalReady: boolean
  onPrimaryAction: () => void
  option: HomeDifficultyOption
  summary: GameSessionSummary
}

export function HomeContinuePanel({
  guestActionError,
  guestNeedsSessionReacceptance,
  isAuthenticated,
  isBusy,
  legalReady,
  onPrimaryAction,
  option,
  summary,
}: HomeContinuePanelProps) {
  const { t } = useTranslation()
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const primaryDisabled = isBusy || (!isAuthenticated && !legalReady)
  const currentCountryNumber = Math.min(summary.currentCountryIndex + 1, summary.totalCountries)
  const routeFillPercent =
    summary.totalCountries <= 1
      ? 0
      : (summary.guessedCountries / (summary.totalCountries - 1)) * 100
  const routeInsight = getRouteInsight(summary, t)
  const routeSteps = Array.from({ length: summary.totalCountries }, (_, index) => {
    if (index < summary.guessedCountries) return 'solved' as const
    if (index === summary.currentCountryIndex) return 'current' as const
    return 'pending' as const
  })

  return (
    <HomePanelShell className="max-w-[50rem]">
      <div className="flex flex-1 flex-col">
        <div className="flex items-center justify-between gap-3">
          <div className="inline-flex items-center gap-2 rounded-full border border-white/[.08] bg-white/[.04] px-3 py-1.5 text-[11px] uppercase tracking-[.18em] text-white/38 ring-1 ring-white/[.08]">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />
            {t.home.heroContinueEyebrow}
          </div>
          <DifficultyBadge level={summary.level} size="sm" />
        </div>

        <h1 className="mt-3 text-[1.65rem] font-semibold tracking-tight text-white sm:mt-4 sm:text-[2.1rem]">
          {t.home.heroContinueHeadline}
        </h1>

        <div className="relative mt-4 overflow-hidden rounded-[1.85rem] border border-white/[.08] bg-[linear-gradient(180deg,rgba(255,255,255,.03),rgba(9,13,21,.82))] p-4 shadow-[inset_0_1px_0_rgba(255,255,255,.035),0_24px_60px_rgba(0,0,0,.18)] sm:p-5">
          <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(52,211,153,.10),transparent_36%),radial-gradient(circle_at_top_right,rgba(56,189,248,.08),transparent_34%),linear-gradient(180deg,rgba(255,255,255,.03),transparent_32%)]" />
          <div className="pointer-events-none absolute inset-x-10 top-0 h-px bg-[linear-gradient(90deg,transparent,rgba(255,255,255,.16),transparent)]" />

          <div className="relative flex items-start justify-between gap-4">
            <div>
              <p className="text-[10px] uppercase tracking-[.18em] text-white/28">
                {t.home.continueXpFocusLabel}
              </p>
              <div className="mt-1.5 flex items-baseline gap-1.5">
                <span className="text-[2.5rem] font-black leading-none tracking-[-0.06em] text-white sm:text-[3rem]">
                  {summary.totalXpEarned}
                </span>
                <Sparkles size={15} className="mb-0.5 shrink-0 text-emerald-300/55" />
              </div>
              <p className="mt-1.5 text-[11px] text-emerald-100/52">{routeInsight}</p>
            </div>

            <div className="text-right">
              <p className="text-[10px] uppercase tracking-[.18em] text-white/28">
                {t.home.continueCountryFocusLabel}
              </p>
              <div className="mt-1.5 flex items-baseline justify-end gap-1">
                <span className="text-[2.5rem] font-black leading-none tracking-[-0.06em] text-sky-100/90 sm:text-[3rem]">
                  {currentCountryNumber}
                </span>
                <span className="mb-0.5 text-[1.15rem] font-semibold text-white/28">
                  /{summary.totalCountries}
                </span>
              </div>
            </div>
          </div>

          <div className="relative my-4 h-px bg-white/[.06]" />

          <div className="relative">
            <div className="mb-3.5 flex items-center justify-between gap-2 text-[11px]">
              <span className="font-semibold uppercase tracking-[.18em] text-white/28">
                {t.home.progressLabel}
              </span>
              <span className="text-white/40">
                {t.home.continueCheckpointReady.replace('{current}', String(currentCountryNumber))}
              </span>
            </div>

            <div className="relative flex items-center justify-between gap-2">
              <div className="pointer-events-none absolute inset-x-3.5 top-1/2 -translate-y-1/2">
                <div className="absolute inset-0 h-[2px] rounded-full bg-white/[.06]" />
                <motion.div
                  className="absolute left-0 top-0 h-[2px] rounded-full"
                  initial={{ width: 0 }}
                  animate={{ width: `${Math.max(0, Math.min(routeFillPercent, 100))}%` }}
                  transition={{ duration: 0.55, ease: 'easeOut' }}
                  style={{ background: option.grad, boxShadow: option.shadow }}
                />
              </div>
              {routeSteps.map((state, index) => (
                <div
                  key={`route-step-${index}`}
                  className="relative z-10 flex h-7 w-7 shrink-0 items-center justify-center rounded-full"
                >
                  <div
                    className={cn(
                      'relative rounded-full border transition-all',
                      state === 'solved' && 'h-4 w-4 border-transparent',
                      state === 'current' && 'h-7 w-7 border-white/24 bg-white/[.03]',
                      state === 'pending' && 'h-3 w-3 border-white/[.10] bg-[#0d111a]'
                    )}
                    style={{
                      background: state === 'solved' ? option.grad : undefined,
                      boxShadow: state === 'solved' ? option.shadow : undefined,
                    }}
                  >
                    {state === 'current' ? (
                      <>
                        <span
                          className="animate-pulse-glow absolute inset-1 rounded-full"
                          style={{ background: option.grad }}
                        />
                        <span className="absolute inset-[7px] rounded-full bg-white" />
                      </>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="relative mt-4 grid grid-cols-3 gap-2">
            <SummaryStatTile
              icon={<Trophy size={14} />}
              label={t.home.continueSolvedLabel}
              tone="emerald"
              value={`${summary.guessedCountries}/${summary.totalCountries}`}
            />
            <SummaryStatTile
              icon={<Clock3 size={14} />}
              label={t.home.continueTimeLabel}
              tone="sky"
              value={formatDurationMs(summary.activeTimeMs)}
            />
            <SummaryStatTile
              icon={<Target size={14} />}
              label={t.home.continueAttemptsLabel}
              tone="violet"
              value={String(summary.totalAttemptsUsed)}
            />
          </div>
        </div>

        <div className="relative mt-4">
          <div
            className="animate-glow-breathe pointer-events-none absolute inset-x-8 -bottom-1 top-2 rounded-[1.4rem] blur-2xl hidden sm:block"
            style={{ background: option.grad }}
          />
          <button
            type="button"
            onClick={onPrimaryAction}
            disabled={primaryDisabled}
            className={cn(
              'relative inline-flex min-h-[60px] w-full items-center justify-center gap-3 rounded-[1.4rem] px-5 text-base font-semibold text-white transition-all hover:-translate-y-0.5 hover:brightness-110',
              'disabled:cursor-not-allowed disabled:opacity-55 disabled:hover:translate-y-0 disabled:hover:brightness-100'
            )}
            style={{
              background: option.grad,
              boxShadow: `${option.shadow}, inset 0 1px 0 rgba(255,255,255,.16)`,
            }}
          >
            {isBusy ? (
              <>
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                {t.home.starting}
              </>
            ) : (
              <>
                <Play size={16} />
                {t.home.continueGame}
                <ArrowRight size={16} />
              </>
            )}
          </button>
        </div>

        {!isAuthenticated ? (
          <div
            className={cn(
              'mt-3 text-xs leading-relaxed',
              guestNeedsSessionReacceptance ? 'text-amber-100/78' : 'text-white/40'
            )}
          >
            <HomeGuestLegalNotice
              actionLabel={t.home.continueGame}
              onOpenPrivacy={() => setLegalModal('privacy')}
              onOpenTerms={() => setLegalModal('terms')}
              template={
                guestNeedsSessionReacceptance
                  ? t.home.guestLegalNoticeReaccept
                  : t.home.guestLegalNotice
              }
            />
          </div>
        ) : null}

        {guestActionError ? (
          <div className="mt-4 rounded-2xl border border-error/20 bg-error/10 px-4 py-3 text-sm text-error">
            {guestActionError}
          </div>
        ) : null}
      </div>

      <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
    </HomePanelShell>
  )
}

interface SummaryStatTileProps {
  icon: ReactNode
  label: string
  tone: 'emerald' | 'sky' | 'violet'
  value: string
}

function SummaryStatTile({ icon, label, tone, value }: SummaryStatTileProps) {
  return (
    <div
      className={cn(
        'rounded-[1.15rem] border p-3 shadow-[inset_0_1px_0_rgba(255,255,255,.025)]',
        tone === 'emerald' &&
          'border-emerald-300/14 bg-[linear-gradient(135deg,rgba(52,211,153,.10),rgba(8,12,20,.26))]',
        tone === 'sky' &&
          'border-sky-300/14 bg-[linear-gradient(135deg,rgba(56,189,248,.10),rgba(8,12,20,.26))]',
        tone === 'violet' &&
          'border-violet-300/14 bg-[linear-gradient(135deg,rgba(167,139,250,.12),rgba(8,12,20,.26))]'
      )}
    >
      <span
        className={cn(
          'inline-flex h-7 w-7 items-center justify-center rounded-xl border text-white/72',
          tone === 'emerald' && 'border-emerald-300/16 bg-emerald-300/[.10]',
          tone === 'sky' && 'border-sky-300/16 bg-sky-300/[.10]',
          tone === 'violet' && 'border-violet-300/16 bg-violet-300/[.12]'
        )}
      >
        {icon}
      </span>
      <p className="mt-2 text-[1.05rem] font-semibold tracking-[-0.03em] text-white">{value}</p>
      <p className="mt-0.5 text-[10px] uppercase tracking-[.14em] text-white/38">{label}</p>
    </div>
  )
}

function getRouteInsight(
  summary: GameSessionSummary,
  t: ReturnType<typeof useTranslation>['t']
) {
  if (summary.guessedCountries === 0) {
    return t.home.continueInsightFresh
  }
  const extraAttempts = Math.max(0, summary.totalAttemptsUsed - summary.guessedCountries)
  if (extraAttempts === 0) {
    return t.home.continueInsightClean
  }
  return t.home.continueInsightRecovery.replace('{count}', String(extraAttempts))
}
