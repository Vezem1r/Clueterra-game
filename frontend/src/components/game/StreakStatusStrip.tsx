import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { CheckCircle2, Flame, Shield } from 'lucide-react'
import { useTranslation } from '@/i18n'
import type { ProfileDto } from '@/types'
import { cn } from '@/utils/cn'

const AT_RISK_HOURS = 4
const UPDATE_INTERVAL_MS = 60_000

interface StreakStatusStripProps {
  profile: ProfileDto
  variant?: 'compact' | 'home'
}

type StreakState = 'secured' | 'pending' | 'at_risk' | 'none'

function getHoursUntilReset(resetAtUtc?: string | null): number {
  if (!resetAtUtc) return Number.POSITIVE_INFINITY
  const diff = new Date(resetAtUtc).getTime() - Date.now()
  return Math.max(0, diff / 3_600_000)
}

function formatTimeLeft(hours: number, labels: { hours: string; minutes: string }): string {
  if (!Number.isFinite(hours)) return ''
  if (hours < 1) {
    return labels.minutes.replace('{n}', String(Math.max(1, Math.round(hours * 60))))
  }
  return labels.hours.replace('{n}', String(Math.round(hours)))
}

function getStreakState(profile: ProfileDto): StreakState {
  if (profile.currentStreak <= 0) return 'none'
  if (profile.todayCompleted) return 'secured'
  const hoursLeft = getHoursUntilReset(profile.resetAtUtc)
  if (hoursLeft <= AT_RISK_HOURS) return 'at_risk'
  return 'pending'
}

export function StreakStatusStrip({ profile, variant = 'compact' }: StreakStatusStripProps) {
  const { t } = useTranslation()
  const [, setTick] = useState(0)

  const state = getStreakState(profile)

  useEffect(() => {
    if (state === 'pending' || state === 'at_risk') {
      const interval = setInterval(() => setTick(t => t + 1), UPDATE_INTERVAL_MS)
      return () => clearInterval(interval)
    }
  }, [state])

  if (state === 'none') return null

  const hoursLeft = getHoursUntilReset(profile.resetAtUtc)
  const timeLabel = formatTimeLeft(hoursLeft, { hours: t.home.timeLeftHours, minutes: t.home.timeLeftMinutes })
  const shieldCount = profile.freezesAvailable ?? 0
  const streakText = state === 'secured'
    ? t.home.streakSecured.replace('{count}', String(profile.currentStreak))
    : state === 'at_risk'
      ? t.home.streakAtRisk
          .replace('{count}', String(profile.currentStreak))
          .replace('{time}', timeLabel)
      : t.home.streakPending
          .replace('{count}', String(profile.currentStreak))

  if (variant === 'home') {
    return (
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35, ease: 'easeOut' }}
        className={cn(
          'group relative isolate w-full max-w-[30rem] overflow-hidden rounded-[1.15rem] border px-3.5 py-3 text-left shadow-[0_18px_42px_rgba(0,0,0,.26)]',
          state === 'secured' && 'border-emerald-300/20 bg-[linear-gradient(135deg,rgba(16,185,129,.13),rgba(10,14,25,.78)_50%,rgba(139,92,246,.08))]',
          state === 'pending' && 'border-amber-300/22 bg-[linear-gradient(135deg,rgba(245,158,11,.14),rgba(10,14,25,.79)_50%,rgba(139,92,246,.09))]',
          state === 'at_risk' && 'border-orange-300/30 bg-[linear-gradient(135deg,rgba(249,115,22,.19),rgba(10,14,25,.80)_48%,rgba(139,92,246,.11))]',
        )}
      >
        <div className="pointer-events-none absolute inset-0 rounded-[inherit] bg-[linear-gradient(135deg,rgba(255,255,255,.08),transparent_34%,rgba(255,255,255,.025))]" />
        <div
          className={cn(
            'pointer-events-none absolute inset-x-3 bottom-0 h-px',
            state === 'secured' && 'bg-gradient-to-r from-transparent via-emerald-300/35 to-transparent',
            state === 'pending' && 'bg-gradient-to-r from-transparent via-amber-300/35 to-transparent',
            state === 'at_risk' && 'bg-gradient-to-r from-transparent via-orange-300/45 to-transparent',
          )}
        />

        <div className="relative grid min-w-0 grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3">
          <div
            className={cn(
              'relative flex h-12 w-12 shrink-0 items-center justify-center rounded-full border',
              state === 'secured' && 'border-emerald-300/30 bg-emerald-400/[.08] text-emerald-200',
              state === 'pending' && 'border-amber-300/35 bg-amber-400/[.10] text-amber-200',
              state === 'at_risk' && 'border-orange-300/45 bg-orange-400/[.13] text-orange-200',
            )}
          >
            {state !== 'secured' ? (
              <motion.span
                className={cn(
                  'absolute inset-0 rounded-full',
                  state === 'pending' && 'bg-amber-300/10',
                  state === 'at_risk' && 'bg-orange-400/16',
                )}
                animate={{ opacity: [0.4, 0.72, 0.4], scale: [1, 1.08, 1] }}
                transition={{ duration: state === 'at_risk' ? 1.9 : 3, repeat: Infinity, ease: 'easeInOut' }}
              />
            ) : null}
            <span className="absolute inset-[4px] rounded-full border border-white/[.07]" />
            <Flame size={19} className={cn('relative', state === 'at_risk' && 'animate-pulse')} />
            <span className="absolute -bottom-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full border border-black/40 bg-black/70 px-1 text-[10px] font-black leading-none text-white shadow-[0_5px_14px_rgba(0,0,0,.35)]">
              {profile.currentStreak}
            </span>
          </div>

          <div className="min-w-0">
            <div className="flex min-w-0 items-center gap-2">
              <span
                className={cn(
                  'h-1.5 w-1.5 shrink-0 rounded-full',
                  state === 'secured' && 'bg-emerald-300',
                  state === 'pending' && 'bg-amber-300',
                  state === 'at_risk' && 'bg-orange-300',
                )}
              />
              <p
                className={cn(
                  'min-w-0 text-[13px] font-bold leading-snug text-white/88',
                  state === 'at_risk' && 'text-orange-100/95',
                )}
              >
                {streakText}
              </p>
            </div>
          </div>

          {shieldCount > 0 ? (
            <div className="inline-flex shrink-0 items-center gap-1.5 rounded-full border border-violet-300/20 bg-violet-400/[.09] px-2.5 py-1.5 text-[11px] font-bold text-violet-200/80">
              <Shield size={12} />
              {shieldCount}
            </div>
          ) : null}
        </div>
      </motion.div>
    )
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className={cn(
        'inline-flex items-center gap-2.5 rounded-full border px-4 py-2',
        state === 'secured' && 'border-emerald-400/20 bg-emerald-400/[.07]',
        state === 'pending' && 'border-amber-300/20 bg-amber-300/[.07]',
        state === 'at_risk' && 'border-orange-400/25 bg-orange-400/[.09]',
      )}
    >
      {state === 'secured' && (
        <>
          <CheckCircle2 size={13} className="text-emerald-400/80" />
          <span className="text-[12px] font-semibold text-emerald-200/80">
            {streakText}
          </span>
        </>
      )}

      {state === 'pending' && (
        <>
          <Flame size={13} className="text-amber-300/80" />
          <span className="text-[12px] font-semibold text-amber-100/80">
            {streakText}
          </span>
          {timeLabel ? <span className="text-[10px] text-white/30">{timeLabel} left</span> : null}
        </>
      )}

      {state === 'at_risk' && (
        <>
          <Flame size={13} className="animate-pulse text-orange-400" />
          <span className="text-[12px] font-semibold text-orange-200/90">
            {streakText}
          </span>
        </>
      )}

      {shieldCount > 0 && (
        <span className="inline-flex items-center gap-1 text-[11px] text-purple-300/60">
          <Shield size={11} />
          {shieldCount}
        </span>
      )}
    </motion.div>
  )
}
