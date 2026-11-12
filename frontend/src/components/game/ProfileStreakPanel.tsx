import { useMemo, type ReactNode } from 'react'
import { motion } from 'framer-motion'
import { Flame, Info, Shield, Sparkles, Trophy, Zap, type LucideIcon } from 'lucide-react'
import { ProfileGlassPanel } from '@/components/game/profile/ProfileGlassPanel'
import { Tooltip } from '@/components/ui/Tooltip'
import { useTranslation } from '@/i18n'
import type { ProfileDto } from '@/types'
import { cn } from '@/utils/cn'

interface ProfileStreakPanelProps {
  profile: ProfileDto
  isOwnProfile: boolean
}

type ProfileStreakExtras = ProfileDto & {
  streakBonusPercent?: number | null
  freezesAvailable?: number | null
  previousFreezeAtStreak?: number | null
  maxFreezes?: number | null
  nextFreezeAtStreak?: number | null
}

interface StreakMetric {
  icon: LucideIcon
  label: string
  value: string
  accent: string
  highlight?: boolean
  tooltip?: ReactNode
}

interface StreakVisual {
  outerBackground: string
  coreBackground: string
  haloClassName: string
  flameClassName: string
  textClassName: string
  labelClassName: string
  pulseOpacity: number
  pulseScale: number
  pulseDuration: number
}

function formatPercent(value: number): string {
  if (Number.isInteger(value)) return String(value)
  return value.toFixed(1)
}

function getStreakVisual(count: number): StreakVisual {
  if (count <= 0) {
    return {
      outerBackground: 'conic-gradient(from 226deg, rgba(255,255,255,.045), rgba(249,115,22,.16), rgba(255,255,255,.035), rgba(255,255,255,.025))',
      coreBackground: 'radial-gradient(circle at 50% 45%, rgba(255,255,255,.06), rgba(9,12,21,.93) 58%, rgba(2,6,23,.96))',
      haloClassName: 'shadow-[0_0_18px_rgba(255,255,255,.035)] opacity-65',
      flameClassName: 'border-white/[.08] bg-white/[.035] text-white/28 shadow-none',
      textClassName: 'text-white/48',
      labelClassName: 'text-white/30',
      pulseOpacity: 0,
      pulseScale: 1,
      pulseDuration: 4,
    }
  }

  if (count < 7) {
    return {
      outerBackground: 'conic-gradient(from 226deg, rgba(251,191,36,.08), rgba(249,115,22,.52), rgba(251,191,36,.32), rgba(127,29,29,.16), rgba(251,191,36,.08))',
      coreBackground: 'radial-gradient(circle at 50% 45%, rgba(251,146,60,.13), rgba(9,12,21,.92) 57%, rgba(2,6,23,.96))',
      haloClassName: 'shadow-[0_0_24px_rgba(249,115,22,.16)]',
      flameClassName: 'border-amber-200/20 bg-[#17110a] text-amber-300 shadow-[0_0_12px_rgba(251,191,36,.18)]',
      textClassName: 'text-white/84',
      labelClassName: 'text-orange-100/54',
      pulseOpacity: 0.14,
      pulseScale: 1.025,
      pulseDuration: 3.6,
    }
  }

  if (count < 14) {
    return {
      outerBackground: 'conic-gradient(from 226deg, rgba(251,191,36,.1), rgba(249,115,22,.74), rgba(251,191,36,.5), rgba(127,29,29,.24), rgba(251,191,36,.1))',
      coreBackground: 'radial-gradient(circle at 50% 45%, rgba(251,146,60,.2), rgba(9,12,21,.92) 57%, rgba(2,6,23,.96))',
      haloClassName: 'shadow-[0_0_30px_rgba(249,115,22,.22)]',
      flameClassName: 'border-amber-200/28 bg-[#1a130a] text-amber-300 shadow-[0_0_16px_rgba(251,191,36,.28)]',
      textClassName: 'text-white',
      labelClassName: 'text-orange-100/64',
      pulseOpacity: 0.2,
      pulseScale: 1.035,
      pulseDuration: 3.2,
    }
  }

  if (count < 30) {
    return {
      outerBackground: 'conic-gradient(from 226deg, rgba(251,191,36,.12), rgba(249,115,22,.9), rgba(251,191,36,.7), rgba(127,29,29,.28), rgba(251,191,36,.12))',
      coreBackground: 'radial-gradient(circle at 50% 45%, rgba(251,146,60,.24), rgba(9,12,21,.92) 57%, rgba(2,6,23,.96))',
      haloClassName: 'shadow-[0_0_34px_rgba(249,115,22,.3)]',
      flameClassName: 'border-amber-200/35 bg-[#1a130a] text-amber-300 shadow-[0_0_18px_rgba(251,191,36,.35)]',
      textClassName: 'text-white',
      labelClassName: 'text-orange-100/70',
      pulseOpacity: 0.25,
      pulseScale: 1.045,
      pulseDuration: 2.9,
    }
  }

  return {
    outerBackground: 'conic-gradient(from 226deg, rgba(251,191,36,.18), rgba(249,115,22,1), rgba(253,224,71,.9), rgba(190,24,93,.38), rgba(251,191,36,.18))',
    coreBackground: 'radial-gradient(circle at 50% 45%, rgba(253,224,71,.28), rgba(124,45,18,.28) 36%, rgba(9,12,21,.92) 58%, rgba(2,6,23,.96))',
    haloClassName: 'shadow-[0_0_44px_rgba(251,191,36,.38),0_0_76px_rgba(249,115,22,.18)]',
    flameClassName: 'border-yellow-200/42 bg-[#211504] text-yellow-200 shadow-[0_0_22px_rgba(253,224,71,.42)]',
    textClassName: 'text-yellow-50',
    labelClassName: 'text-yellow-100/76',
    pulseOpacity: 0.32,
    pulseScale: 1.055,
    pulseDuration: 2.55,
  }
}

function CurrentStreakOrb({ count, visual }: { count: number; visual: StreakVisual }) {
  const { t } = useTranslation()
  return (
    <div className="flex w-full justify-center lg:justify-start">
      <div className="relative flex h-[134px] w-[134px] shrink-0 items-center justify-center sm:h-[150px] sm:w-[150px]">
        {visual.pulseOpacity > 0 ? (
          <motion.div
            className="absolute -inset-2 rounded-full border border-orange-300/14"
            animate={{ opacity: [visual.pulseOpacity, visual.pulseOpacity * 0.42, visual.pulseOpacity], scale: [1, visual.pulseScale, 1] }}
            transition={{ duration: visual.pulseDuration, repeat: Infinity, ease: 'easeInOut' }}
          />
        ) : null}
        <div className={cn('absolute inset-0 rounded-full', visual.haloClassName)} style={{ background: visual.outerBackground }} />
        <div className="absolute inset-[5px] rounded-full border border-orange-300/20" style={{ background: visual.coreBackground }} />
        <div className="absolute inset-[12px] rounded-full border border-orange-200/12 shadow-[inset_0_0_24px_rgba(0,0,0,.58)]" />
        <div className={cn('absolute -top-1 left-1/2 flex h-7 w-7 -translate-x-1/2 items-center justify-center rounded-full border', visual.flameClassName)}>
          <Flame size={15} fill="currentColor" />
        </div>
        <div className="pointer-events-none absolute -inset-1 rounded-full border border-orange-400/18" />
        <div className="relative text-center">
          <p className={cn('text-[2.35rem] font-black leading-none drop-shadow-[0_2px_12px_rgba(0,0,0,.72)] sm:text-[2.65rem]', visual.textClassName)}>
            {count}
          </p>
          <p className={cn('mt-2 text-[9px] font-black uppercase tracking-[.16em]', visual.labelClassName)}>
            {t.profile.dayStreak}
          </p>
        </div>
      </div>
    </div>
  )
}

function StreakMetricCell({ metric }: { metric: StreakMetric }) {
  const content = (
    <div
      className={cn(
        'relative flex min-h-[82px] min-w-0 flex-1 flex-col items-center justify-center px-2.5 py-3 text-center sm:min-h-[92px] sm:px-4',
        metric.highlight && 'rounded-l-[1.05rem] border border-amber-300/14 bg-amber-300/[.035]',
      )}
    >
      {metric.highlight ? (
        <div className="pointer-events-none absolute inset-x-4 top-0 h-px bg-gradient-to-r from-transparent via-amber-100/34 to-transparent" />
      ) : null}
      <metric.icon size={18} className={cn('mb-2', metric.accent)} />
      <p className="text-[1.25rem] font-black leading-none text-white drop-shadow-[0_1px_8px_rgba(0,0,0,.45)] sm:text-[1.55rem]">
        {metric.value}
      </p>
      <div className="mt-2 flex min-h-[14px] max-w-full items-center justify-center gap-1 sm:gap-1.5">
        <p className="truncate text-[7px] font-bold uppercase tracking-[.09em] text-white/42 sm:text-[9px] sm:tracking-[.14em]">
          {metric.label}
        </p>
        {metric.tooltip ? <Info size={12} className="shrink-0 text-white/34" /> : null}
      </div>
    </div>
  )

  if (!metric.tooltip) return content

  return (
    <Tooltip content={metric.tooltip} className="min-w-0 flex-1 cursor-help" maxWidth={276}>
      {content}
    </Tooltip>
  )
}

function ShieldTooltip({
  shields,
  currentStreak,
  previousShieldAt,
  maxShields,
  nextShieldAt,
}: {
  shields: number
  currentStreak: number
  previousShieldAt: number | null
  maxShields: number | null
  nextShieldAt: number | null
}) {
  const hasMaxReserve = maxShields !== null && maxShields > 0 && shields >= maxShields
  const hasNextShield = !hasMaxReserve && nextShieldAt !== null && nextShieldAt > 0
  const previous = hasNextShield ? Math.max(0, previousShieldAt ?? 0) : null
  const daysToShield = hasNextShield ? Math.max(0, nextShieldAt - currentStreak) : null
  const cycleLength = hasNextShield ? Math.max(1, nextShieldAt - (previous ?? 0)) : 1
  const cycleProgress = hasNextShield ? Math.max(0, Math.min(cycleLength, currentStreak - (previous ?? 0))) : 0
  const progress = cycleProgress / cycleLength
  const { t } = useTranslation()
  const copy = t.profile

  return (
    <div className="w-full min-w-0 text-left">
      <div className="flex items-center gap-2">
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-purple-400/20 bg-purple-400/[.09] text-purple-300">
          <Shield size={15} />
        </span>
        <div>
          <p className="text-xs font-black text-white">{copy.shieldsTitle}</p>
          <p className="mt-0.5 text-[10px] font-semibold uppercase tracking-[.12em] text-purple-200/60">
            {maxShields !== null && maxShields > 0
              ? copy.shieldsAvailableOf.replace('{count}', String(shields)).replace('{max}', String(maxShields))
              : copy.shieldsAvailable.replace('{count}', String(shields))}
          </p>
        </div>
      </div>

      <p className="mt-3 text-xs leading-relaxed text-white/62">
        {copy.shieldsBody}
      </p>

      {hasMaxReserve ? (
        <div className="mt-3 rounded-xl border border-purple-300/12 bg-purple-400/[.06] p-2.5">
          <div className="flex items-center justify-between gap-3">
            <span className="text-[10px] font-bold uppercase tracking-[.13em] text-white/36">
              {copy.shieldReserve}
            </span>
            <span className="text-[11px] font-black text-purple-200">
              {copy.shieldMax}
            </span>
          </div>
          <p className="mt-2 text-[11px] leading-relaxed text-white/48">
            {copy.shieldMaxBody}
          </p>
        </div>
      ) : hasNextShield ? (
        <div className="mt-3 rounded-xl border border-white/[.07] bg-black/18 p-2.5">
          <div className="mb-2 flex items-center justify-between gap-3">
            <span className="text-[10px] font-bold uppercase tracking-[.13em] text-white/36">
              {copy.nextShield}
            </span>
            <span className="text-[11px] font-black text-purple-200">
              {daysToShield === 0 ? copy.shieldReady : copy.daysShort.replace('{count}', String(daysToShield))}
            </span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-white/[.08]">
            <div
              className="h-full rounded-full bg-[linear-gradient(90deg,#8b5cf6,#c084fc,#f59e0b)]"
              style={{ width: `${Math.max(5, progress * 100)}%` }}
            />
          </div>
          <div className="mt-1.5 flex justify-between text-[10px] font-semibold text-white/32">
            <span>{previous}</span>
            <span>{nextShieldAt}</span>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function ProtectedCallout({ shields }: { shields: number }) {
  const isProtected = shields > 0
  const { t } = useTranslation()

  return (
    <div className="relative overflow-hidden rounded-[1.05rem] border border-orange-300/14 bg-[linear-gradient(135deg,rgba(251,146,60,.07),rgba(255,255,255,.025))] px-3.5 py-3.5">
      <div className="pointer-events-none absolute inset-x-5 top-0 h-px bg-gradient-to-r from-transparent via-orange-100/24 to-transparent" />
      <div className="flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-amber-300/18 bg-amber-300/[.075] text-amber-300">
          <Sparkles size={17} />
        </div>
        <div className="min-w-0">
          <p className="whitespace-nowrap text-[12px] font-black leading-snug text-orange-200">
            {isProtected ? t.profile.streakProtected : t.profile.noShieldReserve}
          </p>
          <p className="mt-1.5 text-[11px] font-medium leading-relaxed text-orange-100/58">
            {isProtected ? t.profile.protectedBody : t.profile.unprotectedBody}
          </p>
        </div>
      </div>
    </div>
  )
}

export function ProfileStreakPanel({ profile, isOwnProfile }: ProfileStreakPanelProps) {
  const { t } = useTranslation()
  const streakProfile = profile as ProfileStreakExtras
  const currentStreak = Math.max(0, profile.currentStreak ?? 0)
  const bestStreak = Math.max(currentStreak, profile.bestStreak ?? 0)
  const streakBonusPercent = Math.max(0, streakProfile.streakBonusPercent ?? 0)
  const shields = Math.max(0, streakProfile.freezesAvailable ?? 0)
  const previousShieldAt = streakProfile.previousFreezeAtStreak ?? null
  const maxShields = streakProfile.maxFreezes ?? null
  const nextShieldAt = streakProfile.nextFreezeAtStreak ?? null
  const streakVisual = useMemo(() => getStreakVisual(currentStreak), [currentStreak])

  const metrics = useMemo<StreakMetric[]>(() => {
    const items: StreakMetric[] = [
      {
        icon: Trophy,
        label: t.profile.bestStreak,
        value: String(bestStreak),
        accent: 'text-amber-300',
        highlight: true,
        tooltip: t.profile.bestStreakTooltip,
      },
    ]

    items.push(
      {
        icon: Zap,
        label: t.profile.streakBonus,
        value: `+${formatPercent(streakBonusPercent)}%`,
        accent: 'text-orange-300',
        tooltip: t.profile.streakBonusTooltip,
      },
      {
        icon: Shield,
        label: t.profile.shields,
        value: String(shields),
        accent: 'text-purple-400',
        tooltip: (
          <ShieldTooltip
            shields={shields}
            currentStreak={currentStreak}
            previousShieldAt={previousShieldAt}
            maxShields={maxShields}
            nextShieldAt={nextShieldAt}
          />
        ),
      },
    )

    return items
  }, [bestStreak, currentStreak, maxShields, nextShieldAt, previousShieldAt, shields, streakBonusPercent, t])

  if (!isOwnProfile && currentStreak === 0 && bestStreak === 0) return null

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.24 }}
    >
      <ProfileGlassPanel className="rounded-[1.55rem] border-orange-400/22 bg-[linear-gradient(180deg,rgba(251,146,60,.075),rgba(12,13,20,.96))] p-4 shadow-[0_20px_62px_rgba(3,7,18,.3),0_0_0_1px_rgba(249,115,22,.08)] sm:p-5">
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(115deg,rgba(249,115,22,.07),rgba(0,0,0,0)_42%,rgba(168,85,247,.025))]" />
        <div className="relative">
          <div className="mb-3 flex items-center gap-2 text-white/62">
            <Flame size={18} className="text-orange-500" fill="currentColor" />
            <p className="text-sm font-black uppercase tracking-[.08em]">{t.profile.streakZone}</p>
          </div>

          <div className="grid gap-4 lg:grid-cols-[150px_minmax(0,1fr)_214px] lg:items-center">
            <CurrentStreakOrb count={currentStreak} visual={streakVisual} />

            <div className="overflow-hidden rounded-[1.05rem] border border-white/[.075] bg-black/22 shadow-[inset_0_1px_0_rgba(255,255,255,.035)]">
              <div className="grid grid-cols-3 divide-x divide-white/[.07]">
                {metrics.map(metric => (
                  <StreakMetricCell key={metric.label} metric={metric} />
                ))}
              </div>
            </div>

            <ProtectedCallout shields={shields} />
          </div>
        </div>
      </ProfileGlassPanel>
    </motion.div>
  )
}
