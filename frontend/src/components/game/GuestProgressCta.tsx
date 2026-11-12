import { motion } from 'framer-motion'
import { AlertTriangle, ArrowRight, Flame, Gamepad2, Landmark, Sparkles, Trophy, UserPlus } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { useTranslation } from '@/i18n'
import type { GuestProgress } from '@/types'

export type GuestProgressContext = 'countries' | 'budget'
type GuestProgressTier = 'gentle' | 'warm' | 'urgent'

const WARM_GAME_COUNT = 2
const URGENT_GAME_COUNT = 3

const TIER_STYLES: Record<GuestProgressTier, {
  card: string
  glow: string
  chip: string
  icon: string
  title: string
  body: string
  button: string
  accentStat: string
}> = {
  gentle: {
    card: 'border-emerald-300/15 bg-[linear-gradient(135deg,rgba(16,185,129,.10),rgba(9,12,20,.94)_46%,rgba(251,191,36,.05))] shadow-[0_28px_90px_rgba(0,0,0,.24)]',
    glow: 'bg-emerald-300/10',
    chip: 'border-emerald-300/15 bg-emerald-300/[.07] text-emerald-100/70',
    icon: 'text-emerald-300',
    title: 'text-white',
    body: 'text-white/48',
    button: 'bg-emerald-300 text-emerald-950 shadow-[0_16px_38px_rgba(52,211,153,.18)] hover:bg-emerald-200',
    accentStat: 'border-amber-300/15 bg-amber-300/[.07] text-amber-100/75',
  },
  warm: {
    card: 'border-amber-300/20 bg-[linear-gradient(135deg,rgba(245,158,11,.13),rgba(9,12,20,.95)_46%,rgba(120,53,15,.10))] shadow-[0_28px_90px_rgba(245,158,11,.10)]',
    glow: 'bg-amber-300/10',
    chip: 'border-amber-300/20 bg-amber-300/[.09] text-amber-100/80',
    icon: 'text-amber-300',
    title: 'text-amber-100',
    body: 'text-amber-50/52',
    button: 'bg-amber-400 text-amber-950 shadow-[0_16px_38px_rgba(245,158,11,.20)] hover:bg-amber-300',
    accentStat: 'border-amber-300/18 bg-amber-300/[.08] text-amber-100/80',
  },
  urgent: {
    card: 'border-red-300/20 bg-[linear-gradient(135deg,rgba(239,68,68,.13),rgba(9,12,20,.95)_46%,rgba(127,29,29,.11))] shadow-[0_28px_90px_rgba(239,68,68,.11)]',
    glow: 'bg-red-400/10',
    chip: 'border-red-300/20 bg-red-400/[.09] text-red-100/80',
    icon: 'text-red-300',
    title: 'text-red-100',
    body: 'text-red-50/52',
    button: 'bg-red-500 text-white shadow-[0_16px_38px_rgba(239,68,68,.24)] hover:bg-red-400',
    accentStat: 'border-red-300/18 bg-red-400/[.08] text-red-100/80',
  },
}

interface GuestProgressCtaProps {
  context: GuestProgressContext
  fallbackTotalXp: number
  progress: GuestProgress | null
}

export function GuestProgressCta({ context, fallbackTotalXp, progress }: GuestProgressCtaProps) {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const isBudget = context === 'budget'
  const progressData = progress?.hasGuest ? progress : null
  const countryPlayCount = progressData?.playCount ?? (isBudget ? 0 : 1)
  const budgetPlayCount = progressData?.budgetPlayCount ?? (isBudget ? 1 : 0)
  const tierPlayCount = Math.max(countryPlayCount, budgetPlayCount)
  const totalXp = Math.max(progressData?.totalXp ?? 0, fallbackTotalXp)
  const level = progressData?.currentLevel ?? 1
  const bestStreak = Math.max(progressData?.bestStreak ?? 0, progressData?.currentStreak ?? 0)
  const tier = guestProgressTier(tierPlayCount)
  const styles = TIER_STYLES[tier]
  const TierIcon = tier === 'urgent' ? AlertTriangle : tier === 'warm' ? Flame : Sparkles
  const title = tier === 'urgent'
    ? t.results.guestUrgentTitle
    : tier === 'warm'
      ? t.results.guestWarmTitle
      : t.results.saveProgressTitle
  const body = tier === 'urgent'
    ? t.results.guestUrgentBody
        .replace('{count}', String(tierPlayCount))
        .replace('{xp}', totalXp.toLocaleString())
    : tier === 'warm'
      ? t.results.guestWarmBody.replace('{xp}', totalXp.toLocaleString())
      : isBudget
        ? t.results.saveBudgetProgressBody
        : t.results.saveCountryProgressBody
  const redirect = isBudget ? '/budget' : '/results'

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.16 }}
      className={`relative overflow-hidden rounded-[1.8rem] border transition-colors ${styles.card}`}
    >
      <div className={`pointer-events-none absolute -left-20 -top-24 h-56 w-56 rounded-full blur-3xl ${styles.glow}`} />
      <div className="relative grid gap-6 p-5 sm:p-7 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
        <div className="min-w-0">
          <div className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-[10px] font-bold uppercase tracking-[.16em] ${styles.chip}`}>
            <TierIcon size={13} className={styles.icon} />
            {t.results.guestEarnedXp.replace('{xp}', totalXp.toLocaleString())}
          </div>
          <h2 className={`mt-4 text-2xl font-black tracking-[-.035em] sm:text-3xl ${styles.title}`}>
            {title}
          </h2>
          <p className={`mt-2 max-w-2xl text-sm leading-6 ${styles.body}`}>
            {body}
          </p>

          <div className="mt-5 flex flex-wrap gap-2">
            <CompactStat icon={<Trophy size={14} />} label={t.results.guestStatLevel} value={String(level)} />
            <CompactStat icon={<Flame size={14} />} label={t.results.guestStatBestStreak} value={`${bestStreak}d`} />
            <CompactStat
              icon={<Gamepad2 size={14} />}
              label={t.results.countryGames}
              value={String(countryPlayCount)}
              toneClass={styles.accentStat}
            />
            <CompactStat
              icon={<Landmark size={14} />}
              label={t.results.budgetGames}
              value={String(budgetPlayCount)}
              toneClass={styles.accentStat}
            />
          </div>
        </div>

        <div className="lg:w-64">
          <button
            type="button"
            onClick={() => navigate(`/register?redirect=${encodeURIComponent(redirect)}`)}
            className={`group grid min-h-[66px] w-full grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 rounded-2xl px-4 text-left transition hover:-translate-y-0.5 ${styles.button}`}
          >
            <UserPlus size={18} />
            <span className="min-w-0">
              <span className="block text-sm font-black">{t.results.createAccount}</span>
              <span className="mt-0.5 block text-[10px] font-bold opacity-60">
                {t.results.guestSaveAssurance.replace('{xp}', totalXp.toLocaleString())}
              </span>
            </span>
            <ArrowRight size={15} className="transition-transform group-hover:translate-x-0.5" />
          </button>
          <Link
            to="/profile"
            className="mt-2.5 flex min-h-11 w-full items-center justify-center gap-2 rounded-xl border border-white/[.12] bg-white/[.04] px-4 text-xs font-bold text-white/75 transition hover:border-white/20 hover:bg-white/[.08] hover:text-white"
          >
            {t.guestProfile.viewProfile}
            <ArrowRight size={14} />
          </Link>
        </div>
      </div>
    </motion.section>
  )
}

function CompactStat({
  icon,
  label,
  value,
  toneClass,
}: {
  icon: ReactNode
  label: string
  value: string
  toneClass?: string
}) {
  return (
    <div className={`inline-flex items-center gap-2 rounded-xl border px-3 py-2 ${toneClass ?? 'border-white/[.07] bg-white/[.035] text-white/55'}`}>
      {icon}
      <span className="text-[10px] font-semibold uppercase tracking-[.1em]">{label}</span>
      <span className="text-sm font-black text-white">{value}</span>
    </div>
  )
}

function guestProgressTier(totalPlayCount: number): GuestProgressTier {
  if (totalPlayCount >= URGENT_GAME_COUNT) return 'urgent'
  if (totalPlayCount >= WARM_GAME_COUNT) return 'warm'
  return 'gentle'
}
