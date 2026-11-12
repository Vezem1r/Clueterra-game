import { motion } from 'framer-motion'
import { AlertTriangle, ChevronRight, Sparkles } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from '@/i18n'
import type { GuestProgress } from '@/types'

interface HomeGuestBannerProps {
  stats: GuestProgress
}

export function HomeGuestBanner({ stats }: HomeGuestBannerProps) {
  const navigate = useNavigate()
  const { t } = useTranslation()

  const totalPlayCount = stats.playCount + stats.budgetPlayCount
  if (totalPlayCount < 1) return null

  const isUrgent = totalPlayCount >= 3
  const body = t.results.guestHomeBannerBody
    .replace('{count}', String(totalPlayCount))
    .replace('{xp}', stats.totalXp.toLocaleString())

  return (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      className={`mb-4 flex flex-wrap items-center gap-3 rounded-2xl border px-4 py-3 sm:flex-nowrap ${
        isUrgent
          ? 'border-red-300/20 bg-red-400/[.07]'
          : 'border-amber-300/20 bg-amber-400/[.07]'
      }`}
    >
      {isUrgent ? (
        <AlertTriangle size={16} className="shrink-0 text-red-300" />
      ) : (
        <Sparkles size={16} className="shrink-0 text-amber-300" />
      )}
      <p className={`min-w-[12rem] flex-1 text-xs font-medium leading-relaxed ${
        isUrgent ? 'text-red-100/70' : 'text-amber-100/70'
      }`}>
        {body}
      </p>
      <Link
        to="/profile"
        className="shrink-0 rounded-xl border border-white/10 px-3 py-1.5 text-[11px] font-bold text-white/70 transition hover:bg-white/[.06]"
      >
        {t.guestProfile.viewProfile}
      </Link>
      <button
        type="button"
        onClick={() => navigate('/register', { state: { fromGame: true } })}
        className={`shrink-0 inline-flex items-center gap-1 rounded-xl px-3 py-1.5 text-[11px] font-bold transition-colors cursor-pointer ${
          isUrgent
            ? 'bg-red-500/20 text-red-200 hover:bg-red-500/30'
            : 'bg-amber-500/20 text-amber-200 hover:bg-amber-500/30'
        }`}
      >
        {t.results.createAccount}
        <ChevronRight size={12} />
      </button>
    </motion.div>
  )
}
