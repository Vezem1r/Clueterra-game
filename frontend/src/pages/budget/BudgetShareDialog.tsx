import { useEffect, useState } from 'react'
import { Check, Copy, Download, Share2, X } from 'lucide-react'

import type { BudgetGameSession } from '@/api/budget'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import {
  buildBudgetShareText,
  downloadBudgetShareCard,
  shareBudgetResult,
} from '@/utils/shareResult'
import { formatNumber } from './budgetFormatters'
import { BudgetSkyline } from './BudgetSkyline'

interface BudgetShareDialogProps {
  session: BudgetGameSession
  onClose: () => void
}

export function BudgetShareDialog({ session, onClose }: BudgetShareDialogProps) {
  const { t } = useTranslation()
  const toast = useToastStore()
  const [busy, setBusy] = useState<'share' | 'copy' | 'download' | null>(null)
  const [copied, setCopied] = useState(false)
  const averageAccuracy = session.answeredQuestions.length === 0
    ? 0
    : session.answeredQuestions.reduce((sum, answer) => sum + answer.accuracyPercent, 0)
      / session.answeredQuestions.length

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const share = async () => {
    setBusy('share')
    try {
      const outcome = await shareBudgetResult(session)
      if (outcome === 'copied') toast.addToast({ type: 'success', message: t.results.shareCopied })
    } catch {
      toast.addToast({ type: 'error', message: t.budget.session.shareError })
    } finally {
      setBusy(null)
    }
  }

  const copy = async () => {
    setBusy('copy')
    try {
      await navigator.clipboard.writeText(buildBudgetShareText(session))
      setCopied(true)
      toast.addToast({ type: 'success', message: t.results.shareCopied })
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      toast.addToast({ type: 'error', message: t.budget.session.shareError })
    } finally {
      setBusy(null)
    }
  }

  const download = async () => {
    setBusy('download')
    try {
      await downloadBudgetShareCard(session)
    } catch {
      toast.addToast({ type: 'error', message: t.budget.session.shareError })
    } finally {
      setBusy(null)
    }
  }

  return (
    <div
      className="fixed inset-0 z-[80] flex items-end justify-center bg-black/75 p-0 backdrop-blur-md sm:items-center sm:p-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="budget-share-title"
      onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}
    >
      <section className="max-h-[95dvh] w-full overflow-y-auto rounded-t-[2rem] border border-white/[.1] bg-[#090b13] p-5 shadow-[0_30px_120px_rgba(0,0,0,.65)] sm:max-w-3xl sm:rounded-[2rem] sm:p-7">
        <header className="flex items-start justify-between gap-4">
          <div>
            <p className="text-[10px] font-black uppercase tracking-[.18em] text-amber-200/55">{t.budget.session.shareEyebrow}</p>
            <h2 id="budget-share-title" className="mt-2 text-2xl font-black tracking-[-.035em] sm:text-3xl">
              {t.budget.session.shareTitle}
            </h2>
            <p className="mt-2 text-sm text-white/42">{t.budget.session.shareBody}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={t.common.close}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-white/[.08] bg-white/[.04] text-white/45 transition hover:bg-white/[.08] hover:text-white"
          >
            <X size={18} />
          </button>
        </header>

        <div className="relative mt-6 overflow-hidden rounded-[1.6rem] border border-amber-300/15 bg-[radial-gradient(circle_at_75%_20%,rgba(250,204,21,.12),transparent_42%),linear-gradient(145deg,#07140f,#090914_52%,#110b17)] p-5 sm:p-7">
          <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.025)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.025)_1px,transparent_1px)] bg-[size:24px_24px]" />
          <div className="relative flex items-center justify-between gap-3 text-[10px] font-bold uppercase tracking-[.14em] text-white/35">
            <span>Clueterra / BudgetGuessr</span>
            <span>{session.challengeDate}</span>
          </div>
          <div className="relative mt-5 grid items-end gap-3 sm:grid-cols-[minmax(0,.7fr)_minmax(300px,1.3fr)]">
            <div>
              <p className="text-[9px] font-black uppercase tracking-[.18em] text-amber-200/55">{t.budget.session.costSkyline}</p>
              <p className="mt-2 text-4xl font-black tracking-[-.045em] text-white sm:text-5xl">
                {formatNumber(session.totalXpEarned, 0)} <span className="text-[.48em] tracking-[-.01em] text-emerald-200/70">XP</span>
              </p>
              <p className="mt-3 text-sm font-bold text-white/45">
                {t.budget.session.averageAccuracy} <span className="text-emerald-300">{formatNumber(averageAccuracy, 1)}%</span>
              </p>
            </div>
            <BudgetSkyline answers={session.answeredQuestions} challengeDate={session.challengeDate} compact />
          </div>
          <p className="relative mt-4 text-center text-xs font-black text-emerald-200/60">clueterra.com/budget</p>
        </div>

        <div className="mt-5 grid gap-2 sm:grid-cols-[1fr_auto_auto]">
          <button
            type="button"
            disabled={busy !== null}
            onClick={() => void share()}
            className="inline-flex min-h-[52px] items-center justify-center gap-2 rounded-2xl bg-emerald-300 px-5 text-sm font-black text-emerald-950 transition hover:bg-emerald-200 disabled:opacity-50"
          >
            <Share2 size={17} />
            {t.budget.session.shareResult}
          </button>
          <button
            type="button"
            disabled={busy !== null}
            onClick={() => void copy()}
            className="inline-flex min-h-[52px] items-center justify-center gap-2 rounded-2xl border border-white/[.09] bg-white/[.045] px-5 text-sm font-bold text-white/65 transition hover:bg-white/[.08] hover:text-white disabled:opacity-50"
          >
            {copied ? <Check size={17} /> : <Copy size={17} />}
            {t.budget.session.copyResult}
          </button>
          <button
            type="button"
            disabled={busy !== null}
            onClick={() => void download()}
            className="inline-flex min-h-[52px] items-center justify-center gap-2 rounded-2xl border border-white/[.09] bg-white/[.045] px-5 text-sm font-bold text-white/65 transition hover:bg-white/[.08] hover:text-white disabled:opacity-50"
          >
            <Download size={17} />
            {t.budget.session.downloadCard}
          </button>
        </div>
      </section>
    </div>
  )
}
