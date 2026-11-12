import { memo, useMemo, type CSSProperties } from 'react'
import { CheckCircle2, EyeOff, LockKeyhole, MapPin, Target } from 'lucide-react'
import { useBadgeAccent } from '@/components/game/badgeAccent'
import { resolveBadgeColorAccent, type BadgeAccentValues } from '@/constants/badgeColors'
import { Tooltip } from '@/components/ui/Tooltip'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { getFlagUrlByIso2 } from '@/utils/country'
import type { ProfileBadgeDto, ProfileBadgeProgress } from '@/types'

interface BadgeCardProps {
  badge: ProfileBadgeDto
  index?: number
}

const PLACEHOLDER_ICON = (
  <svg viewBox="0 0 24 24" fill="none" className="h-12 w-12 text-white/20 sm:h-16 sm:w-16">
    <path
      d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
)

const PARTICLE_SPECS = [
  { x: '-4.6rem', y: '-3.9rem', delay: '0s', size: '0.9rem', rotate: '-18deg' },
  { x: '-1.6rem', y: '-5.1rem', delay: '.34s', size: '0.76rem', rotate: '14deg' },
  { x: '2.7rem', y: '-4.6rem', delay: '.76s', size: '0.84rem', rotate: '-8deg' },
  { x: '4.9rem', y: '-1.2rem', delay: '.18s', size: '0.72rem', rotate: '22deg' },
  { x: '4.4rem', y: '2.3rem', delay: '1.02s', size: '0.68rem', rotate: '-18deg' },
  { x: '0.9rem', y: '4.7rem', delay: '.58s', size: '0.8rem', rotate: '10deg' },
  { x: '-3.4rem', y: '3.9rem', delay: '1.28s', size: '0.74rem', rotate: '-12deg' },
  { x: '-4.8rem', y: '0.8rem', delay: '.92s', size: '0.7rem', rotate: '18deg' },
]

const PARTICLE_STYLES = PARTICLE_SPECS.map(p => ({
  width: p.size,
  height: p.size,
  animationDelay: p.delay,
  ['--badge-particle-x' as string]: p.x,
  ['--badge-particle-y' as string]: p.y,
  ['--badge-particle-rotate' as string]: p.rotate,
} as CSSProperties))

const STAR_ICON = (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className="h-full w-full">
    <path d="M12 1.8l2.75 5.57 6.15.9-4.45 4.33 1.05 6.12L12 15.82 6.5 18.72l1.05-6.12L3.1 8.27l6.15-.9L12 1.8Z" />
  </svg>
)

function progressCount(progress: ProfileBadgeProgress, compact = false) {
  if (compact) {
    return `${progress.currentDisplay}/${progress.targetDisplay}`
  }
  return `${progress.current.toLocaleString()}/${progress.target.toLocaleString()}`
}

function progressPercent(progress: ProfileBadgeProgress) {
  return Math.max(0, Math.min(100, progress.percent || 0))
}

function BadgeProgressStrip({
  progress,
  accent,
}: {
  progress: ProfileBadgeProgress
  accent: BadgeAccentValues
}) {
  const pct = progressPercent(progress)

  return (
    <div className="relative z-20 mt-1.5 w-full max-w-[8.75rem] px-1 sm:mt-2 sm:max-w-[9.5rem]">
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="min-w-0 truncate text-[9px] font-black tabular-nums tracking-[-0.02em] text-white/62 sm:text-[10px]">
          {progressCount(progress, true)}
        </span>
        <span className="shrink-0 text-[8px] font-black tabular-nums text-white/32 sm:text-[9px]">
          {pct}%
        </span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-white/[.055] shadow-[inset_0_1px_3px_rgba(0,0,0,.45)]">
        <div
          className="h-full rounded-full transition-[width] duration-500"
          style={{
            width: `${pct}%`,
            background: `linear-gradient(90deg, ${accent.solid}, rgba(255,255,255,.78))`,
            boxShadow: `0 0 12px ${accent.glow}`,
          }}
        />
      </div>
    </div>
  )
}

function BadgeProgressDetails({
  progress,
  accent,
  progressLabel,
  completeLabel,
  remainingLabel,
  missingCountriesLabel,
  moreMissingLabel,
}: {
  progress: ProfileBadgeProgress
  accent: BadgeAccentValues
  progressLabel: string
  completeLabel: string
  remainingLabel: string
  missingCountriesLabel: string
  moreMissingLabel: string
}) {
  const pct = progressPercent(progress)
  const done = progress.remaining <= 0
  const hasMissingCountries = progress.kind === 'COUNTRY_COLLECTION' && progress.missingItems.length > 0

  return (
    <div
      className="rounded-2xl border bg-[linear-gradient(180deg,rgba(255,255,255,.055),rgba(0,0,0,.16))] p-3 shadow-[inset_0_1px_0_rgba(255,255,255,.05)]"
      style={{
        borderColor: accent.border,
      }}
    >
      <div className="mb-2 flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <span
            className="flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border"
            style={{ borderColor: accent.border, color: accent.solid }}
          >
            {done ? <CheckCircle2 size={13} /> : <Target size={13} />}
          </span>
          <span className="truncate text-[10px] font-black uppercase tracking-[.16em] text-white/42">
            {progressLabel}
          </span>
        </div>
        <span className="shrink-0 text-sm font-black tabular-nums tracking-[-0.02em] text-white/90">
          {progressCount(progress, true)}
        </span>
      </div>

      <div className="h-2 overflow-hidden rounded-full border border-white/[.08] bg-black/28">
        <div
          className="h-full rounded-full"
          style={{
            width: `${pct}%`,
            background: `linear-gradient(90deg, ${accent.solid}, rgba(255,255,255,.88))`,
            boxShadow: `0 0 18px ${accent.glow}`,
          }}
        />
      </div>

      <div className="mt-2 flex items-center justify-between gap-3 text-[11px] leading-snug">
        <span className="text-white/44">{done ? completeLabel : remainingLabel}</span>
        <span className="font-semibold tabular-nums text-white/56">{pct}%</span>
      </div>

      {hasMissingCountries && (
        <div className="mt-3 border-t border-white/[.08] pt-2.5">
          <div className="mb-2 flex items-center gap-1.5 text-[9px] font-black uppercase tracking-[.16em] text-white/34">
            <MapPin size={11} />
            <span>{missingCountriesLabel}</span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {progress.missingItems.map(item => {
              const flagSrc = item.flagUrl || getFlagUrlByIso2(item.code, 'w20')

              return (
                <span
                  key={`${item.code ?? item.name}-${item.name}`}
                  className="inline-flex max-w-full items-center gap-1.5 rounded-lg border border-white/[.08] bg-black/18 px-2 py-1 text-[10px] font-semibold text-white/64"
                >
                  {flagSrc ? (
                    <img
                      src={flagSrc}
                      alt={item.code ?? item.name}
                      loading="lazy"
                      className="h-3 w-4 shrink-0 rounded-[2px] object-cover"
                    />
                  ) : (
                    <span className="shrink-0 font-mono text-[9px] text-white/34">{item.code}</span>
                  )}
                  <span className="truncate">{item.name}</span>
                </span>
              )
            })}
            {progress.hiddenMissingCount > 0 && (
              <span className="inline-flex items-center rounded-lg border border-white/[.08] bg-white/[.05] px-2 py-1 text-[10px] font-black text-white/48">
                {moreMissingLabel}
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export const BadgeCard = memo(function BadgeCard({ badge, index = 0 }: BadgeCardProps) {
  const { t } = useTranslation()
  const configuredAccent = resolveBadgeColorAccent(badge.accentColorKey, 'accent')
  const detectedAccent = useBadgeAccent(
    configuredAccent ? null : badge.imageUrl,
    `${badge.id}:${badge.slug}:${badge.name}`,
  )
  const accent = configuredAccent ?? detectedAccent
  const effectAccent = resolveBadgeColorAccent(badge.effectColorKey, 'effect') ?? accent
  const earnedOnLabel = badge.grantedAt ? new Date(badge.grantedAt).toLocaleDateString() : null
  const progress = badge.progress
  const showProgress = Boolean(progress && !badge.unlocked)
  const progressRemainingLabel = progress
    ? t.achievements.remaining.replace('{count}', progress.remainingDisplay)
    : ''
  const moreMissingLabel = progress
    ? t.achievements.moreMissing.replace('{count}', progress.hiddenMissingCountDisplay)
    : ''

  const cardStyle = useMemo(() => badge.unlocked ? {
    borderColor: accent.border,
    background: `linear-gradient(160deg, rgba(255,255,255,.08), ${accent.surface} 38%, rgba(5,10,20,.92))`,
    boxShadow: `0 26px 56px rgba(0,0,0,.34), 0 0 54px ${accent.softGlow}`,
  } : undefined, [badge.unlocked, accent.border, accent.surface, accent.softGlow])

  const glowStyle = useMemo(() => ({
    background: `radial-gradient(circle, ${effectAccent.glow}, transparent 68%)`,
  }), [effectAccent.glow])

  const conicStyle = useMemo(() => ({
    background: `conic-gradient(from 0deg, transparent, ${accent.glow}, transparent 38%, transparent 62%, ${accent.glow}, transparent)`,
  }), [accent.glow])

  const innerGlowStyle = useMemo(() => ({
    boxShadow: `inset 0 0 0 1px ${accent.border}, inset 0 0 46px ${accent.softGlow}`,
  }), [accent.border, accent.softGlow])

  const imgStyle = useMemo(() => badge.unlocked ? {
    filter: `drop-shadow(0 18px 36px rgba(0,0,0,.56)) drop-shadow(0 0 26px ${effectAccent.glow})`,
    animationDelay: index % 2 === 0 ? '0s' : '-2.75s',
  } : undefined, [badge.unlocked, effectAccent.glow, index])

  const particleInnerStyle = useMemo(() => ({
    color: effectAccent.solid,
    filter: `brightness(1.7) saturate(1.35) drop-shadow(0 0 10px rgba(255,255,255,.75)) drop-shadow(0 0 18px ${effectAccent.glow})`,
  }), [effectAccent.solid, effectAccent.glow])

  const tooltipContent = useMemo(() => (
    <div className="space-y-2 text-left whitespace-normal">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-semibold leading-tight text-white/92">
          {badge.name}
        </p>
        {badge.unlocked && earnedOnLabel ? (
          <span
            className="shrink-0 rounded-full border px-2 py-1 text-[10px] font-semibold uppercase tracking-[.14em]"
            style={{
              color: effectAccent.solid,
              borderColor: effectAccent.border,
              backgroundColor: effectAccent.surface,
            }}
          >
            {earnedOnLabel}
          </span>
        ) : null}
      </div>
      <p className="text-[12px] leading-relaxed text-white/60">
        {badge.description}
      </p>
      {progress && !badge.unlocked && (
        <BadgeProgressDetails
          progress={progress}
          accent={effectAccent}
          progressLabel={t.achievements.progress}
          completeLabel={t.achievements.complete}
          remainingLabel={progressRemainingLabel}
          missingCountriesLabel={t.achievements.missingCountries}
          moreMissingLabel={moreMissingLabel}
        />
      )}
    </div>
  ), [
    badge.name,
    badge.description,
    badge.unlocked,
    progress,
    earnedOnLabel,
    effectAccent,
    t.achievements.progress,
    t.achievements.complete,
    t.achievements.missingCountries,
    progressRemainingLabel,
    moreMissingLabel,
  ])

  return (
    <Tooltip
      content={tooltipContent}
      offset={12}
      placement="bottom"
      maxWidth={296}
      contentClassName="max-w-[296px] rounded-[1.25rem] border-white/[.14] px-4 py-3.5 text-left shadow-[0_30px_72px_rgba(0,0,0,.42)]"
    >
      <div
        className={cn(
          'group relative isolate flex h-[9.2rem] w-full flex-col items-center justify-center overflow-hidden rounded-[1.25rem] border p-2.5 transition-[transform,border-color,box-shadow,background-color] duration-500 will-change-transform sm:h-[11.2rem] sm:rounded-[1.45rem] sm:p-4',
          badge.unlocked
            ? 'bg-[linear-gradient(160deg,rgba(255,255,255,.075),rgba(255,255,255,.03)_36%,rgba(5,10,20,.92))] hover:-translate-y-0.5 hover:scale-[1.01]'
            : 'border-white/[.06] bg-[linear-gradient(180deg,rgba(255,255,255,.03),rgba(5,10,20,.94))]'
        )}
        style={cardStyle}
      >
        <div
          className={cn(
            'pointer-events-none absolute inset-x-[10%] top-3 h-28 rounded-full blur-3xl transition-all duration-500',
            badge.unlocked ? 'opacity-55 group-hover:scale-125 group-hover:opacity-100' : 'opacity-0'
          )}
          style={glowStyle}
        />
        <div
          className={cn(
            'pointer-events-none absolute inset-[14%] top-5 aspect-square rounded-full blur-md transition-opacity duration-500',
            badge.unlocked ? 'opacity-10 group-hover:opacity-70 animate-[spin_7s_linear_infinite]' : 'hidden'
          )}
          style={conicStyle}
        />
        <div
          className={cn(
            'pointer-events-none absolute inset-0 rounded-[inherit] transition-opacity duration-500',
            badge.unlocked ? 'opacity-0 group-hover:opacity-100' : 'opacity-0'
          )}
          style={innerGlowStyle}
        />
        <div className="pointer-events-none absolute inset-0 overflow-hidden rounded-[inherit]">
          <div
            className={cn(
              'absolute inset-y-0 -left-[58%] w-[42%] -skew-x-[20deg] bg-[linear-gradient(100deg,transparent,rgba(255,255,255,.46),transparent)] opacity-0 transition-all duration-700',
              badge.unlocked && 'group-hover:left-[118%] group-hover:opacity-100'
            )}
          />
        </div>
        {badge.unlocked && (
          <div className="pointer-events-none absolute inset-0 z-10 overflow-hidden rounded-[inherit]">
            {PARTICLE_STYLES.map((style, i) => (
              <span key={i} className="animate-badge-particle absolute left-1/2 top-1/2" style={style}>
                <span className="block h-full w-full" style={particleInnerStyle}>
                  {STAR_ICON}
                </span>
              </span>
            ))}
          </div>
        )}

        {!badge.unlocked && (
          <div className="pointer-events-none absolute right-2 top-2 flex h-5 w-5 items-center justify-center rounded-md border border-white/[.08] bg-white/[.04] sm:right-3 sm:top-3 sm:h-6 sm:w-6 sm:rounded-lg">
            <LockKeyhole size={10} className="text-white/30 sm:[&]:h-[11px] sm:[&]:w-[11px]" />
          </div>
        )}

        <div className={cn(
          'relative z-20 flex items-center justify-center',
          showProgress ? 'min-h-[4.85rem] sm:min-h-[6.35rem]' : 'min-h-[6rem] sm:min-h-[7.95rem]'
        )}>
          {badge.imageUrl ? (
            <img
              src={badge.imageUrl}
              alt={badge.name}
              loading="lazy"
              decoding="async"
              className={cn(
                'animate-badge-float object-contain transition-[transform,filter,opacity] duration-500',
                showProgress
                  ? 'h-[4.6rem] w-[4.6rem] sm:h-[6.05rem] sm:w-[6.05rem]'
                  : 'h-[5.5rem] w-[5.5rem] sm:h-[7.35rem] sm:w-[7.35rem]',
                badge.unlocked
                  ? 'group-hover:scale-[1.04]'
                  : 'grayscale opacity-50'
              )}
              style={imgStyle}
            />
          ) : (
            PLACEHOLDER_ICON
          )}
        </div>

        <p className={cn(
          'relative z-20 mt-1 w-full truncate text-center text-[10px] font-semibold leading-tight',
          'sm:hidden',
          badge.unlocked ? 'text-white/70' : 'text-white/30'
        )}>
          {badge.name}
        </p>

        {progress && !badge.unlocked && (
          <BadgeProgressStrip
            progress={progress}
            accent={effectAccent}
          />
        )}
      </div>
    </Tooltip>
  )
})

export const HiddenSecretSummaryCard = memo(function HiddenSecretSummaryCard({ count }: { count: number }) {
  const { t } = useTranslation()

  return (
    <div className="group relative isolate flex w-full items-center gap-3 overflow-hidden rounded-[1.25rem] border border-white/[.06] bg-[linear-gradient(180deg,rgba(12,16,28,.95),rgba(4,8,16,.95))] px-4 py-3 sm:gap-4 sm:rounded-[1.45rem] sm:px-5 sm:py-3.5">
      <div className="pointer-events-none absolute inset-x-[18%] top-0 h-20 rounded-full bg-[radial-gradient(circle,rgba(125,211,252,.06),transparent_72%)] blur-3xl" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(180deg,rgba(255,255,255,.015),transparent_32%,transparent)]" />

      <div className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-white/[.06] bg-[radial-gradient(circle_at_30%_30%,rgba(255,255,255,.08),rgba(255,255,255,.01))] shadow-[inset_0_1px_0_rgba(255,255,255,.06),0_0_24px_rgba(125,211,252,.04)] sm:h-12 sm:w-12 sm:rounded-2xl">
        <EyeOff size={16} className="text-white/36 sm:[&]:h-[18px] sm:[&]:w-[18px]" />
        <div className="absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full border border-sky-300/16 bg-[linear-gradient(135deg,rgba(125,211,252,.18),rgba(255,255,255,.06))] px-1 text-[10px] font-bold tracking-[-0.02em] text-white/90 shadow-[0_0_14px_rgba(125,211,252,.16)] sm:h-5.5 sm:min-w-5.5 sm:px-1.5 sm:text-[11px]">
          {count}
        </div>
      </div>

      <div className="relative min-w-0">
        <p className="text-[11px] font-semibold leading-snug text-white/50 sm:text-xs">
          {t.achievements.hiddenBundle.replace('{count}', String(count))}
        </p>
        <p className="mt-0.5 text-[9px] leading-snug text-white/24 sm:text-[10px]">
          {t.achievements.hiddenHint}
        </p>
      </div>
    </div>
  )
})
