import { ArrowUpRight, LockKeyhole } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useTranslation } from '@/i18n'

export function GuestProfileSavePrompt() {
  const { t } = useTranslation()

  return (
    <div className="mt-5 flex min-h-[94px] items-center justify-between gap-3 rounded-2xl border border-emerald-300/20 bg-[linear-gradient(110deg,rgba(52,211,153,.13),rgba(13,20,36,.62)_65%)] p-3 shadow-[inset_0_1px_0_rgba(255,255,255,.06)] sm:mt-7 sm:min-h-[106px] sm:gap-6 sm:p-4">
      <div className="min-w-0">
        <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase leading-4 tracking-[.08em] text-emerald-200/80 sm:gap-2 sm:text-xs sm:tracking-[.12em]">
          <LockKeyhole size={14} className="shrink-0" />{t.guestProfile.saveEyebrow}
        </p>
        <p className="mt-1 hidden max-w-xl text-xs leading-5 text-white/55 sm:block">{t.guestProfile.saveBody}</p>
      </div>
      <Link
        to="/register?redirect=%2Fprofile"
        className="inline-flex shrink-0 items-center justify-center gap-1.5 rounded-xl bg-emerald-300 px-3 py-2.5 text-xs font-bold text-emerald-950 transition hover:bg-emerald-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-300 sm:gap-2 sm:px-4 sm:text-sm"
      >
        {t.auth.register}<ArrowUpRight size={16} />
      </Link>
    </div>
  )
}
