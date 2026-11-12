import { AlertTriangle, Award, ChevronRight, Sparkles } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Modal } from '@/components/ui/Modal'
import { useTranslation } from '@/i18n'
import type { GuestProgress } from '@/types'

interface GuestGateModalProps {
  isOpen: boolean
  onClose: () => void
  stats: GuestProgress
}

export function GuestGateModal({ isOpen, onClose, stats }: GuestGateModalProps) {
  const navigate = useNavigate()
  const { t } = useTranslation()

  const title = t.results.guestGateTitle
  const body = t.results.guestGateBody
    .replace('{count}', String(stats.playCount))

  return (
    <Modal isOpen={isOpen} onClose={onClose} ariaLabel={title}>
      <div className="flex flex-col items-center text-center pt-2">
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-amber-300/20 bg-amber-400/[.09]">
          <AlertTriangle size={26} className="text-amber-300" />
        </div>

        <h2 className="mt-5 text-lg font-bold text-white">{title}</h2>
        <p className="mt-2.5 max-w-xs text-sm leading-relaxed text-white/50">{body}</p>

        <div className="mt-6 grid w-full grid-cols-2 gap-3">
          <div className="flex flex-col items-center gap-1.5 rounded-xl border border-white/[.06] bg-black/15 px-3 py-3">
            <Sparkles size={16} className="text-amber-300/70" />
            <p className="text-lg font-black text-white">{stats.totalXp.toLocaleString()}</p>
            <p className="text-[9px] font-bold uppercase tracking-[.14em] text-white/36">
              {t.results.guestStatXp}
            </p>
          </div>
          <div className="flex flex-col items-center gap-1.5 rounded-xl border border-white/[.06] bg-black/15 px-3 py-3">
            <Award size={16} className="text-emerald-300/70" />
            <p className="text-lg font-black text-white">{stats.badgesEarned}</p>
            <p className="text-[9px] font-bold uppercase tracking-[.14em] text-white/36">
              {t.results.guestStatBadges}
            </p>
          </div>
        </div>

        <p className="mt-5 text-[11px] font-medium leading-relaxed text-red-200/55">
          {t.results.guestLossWarning}
        </p>

        <div className="mt-6 flex w-full flex-col gap-2.5">
          <button
            type="button"
            onClick={() => navigate('/register', { state: { fromGame: true } })}
            className="inline-flex min-h-[48px] w-full items-center justify-center gap-2 rounded-2xl bg-amber-500 px-5 font-semibold text-sm text-amber-950 transition-colors hover:bg-amber-400 cursor-pointer"
          >
            {t.results.createAccount}
            <ChevronRight size={16} />
          </button>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex min-h-[44px] w-full items-center justify-center rounded-2xl border border-white/[.08] bg-white/[.04] px-5 text-sm font-medium text-white/50 transition-colors hover:bg-white/[.08] hover:text-white/70 cursor-pointer"
          >
            {t.results.guestGateContinue}
          </button>
        </div>
      </div>
    </Modal>
  )
}
