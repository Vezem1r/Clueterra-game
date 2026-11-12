import { useState } from 'react'
import { ArrowRight, FileText, ShieldCheck } from 'lucide-react'
import { LegalModal } from '@/components/ui/LegalModal'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { HomeGuestLegalNotice } from './HomeGuestLegalNotice'
import { HomePanelShell } from './HomePanelShell'

interface HomeGuestReacceptPanelProps {
  error: string
  isBusy: boolean
  legalReady: boolean
  onPrimaryAction: () => void
}

export function HomeGuestReacceptPanel({
  error,
  isBusy,
  legalReady,
  onPrimaryAction,
}: HomeGuestReacceptPanelProps) {
  const { t } = useTranslation()
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const primaryDisabled = isBusy || !legalReady

  return (
    <HomePanelShell className="max-w-[38rem]">
      <div className="mx-auto flex w-full max-w-[31rem] flex-col items-center py-6 text-center sm:py-8">
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-amber-200/18 bg-amber-300/[.08] text-amber-200 shadow-[0_0_34px_rgba(251,191,36,.08)]">
          <ShieldCheck size={25} />
        </div>

        <div className="mt-5 inline-flex items-center gap-2 rounded-full border border-amber-200/14 bg-amber-300/[.06] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[.18em] text-amber-100/58">
          <FileText size={12} />
          {t.home.heroContinueEyebrow}
        </div>

        <h1 className="mt-4 text-[1.65rem] font-black leading-tight tracking-[-0.03em] text-white sm:text-[2rem]">
          {t.home.heroContinueHeadline}
        </h1>

        <p className="mt-3 text-sm leading-relaxed text-white/46">
          <HomeGuestLegalNotice
            actionLabel={t.home.continueGame}
            onOpenPrivacy={() => setLegalModal('privacy')}
            onOpenTerms={() => setLegalModal('terms')}
            template={t.home.guestLegalNoticeReaccept}
          />
        </p>

        <button
          type="button"
          onClick={onPrimaryAction}
          disabled={primaryDisabled}
          className={cn(
            'mt-6 inline-flex min-h-[56px] w-full items-center justify-center gap-2.5 rounded-[1.25rem] bg-accent px-5 text-sm font-semibold text-white shadow-[0_18px_52px_rgba(124,92,252,.22),inset_0_1px_0_rgba(255,255,255,.18)] transition-all hover:-translate-y-0.5 hover:bg-accent-hover',
            'disabled:cursor-not-allowed disabled:opacity-55 disabled:hover:translate-y-0'
          )}
        >
          {isBusy ? (
            <>
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
              {t.home.starting}
            </>
          ) : (
            <>
              {t.home.continueGame}
              <ArrowRight size={16} />
            </>
          )}
        </button>

        {!legalReady ? (
          <p className="mt-3 text-xs text-white/28">{t.common.loading}</p>
        ) : null}

        {error ? (
          <div className="mt-4 w-full rounded-2xl border border-error/20 bg-error/10 px-4 py-3 text-left text-sm text-error">
            {error}
          </div>
        ) : null}
      </div>

      <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
    </HomePanelShell>
  )
}
