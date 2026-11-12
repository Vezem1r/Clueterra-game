import { useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Flag } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { useTranslation } from '@/i18n'
import { useReportHint } from '@/hooks/useReportHint'
import type { Hint } from '@/types'

interface HintInfoModalProps {
  hint: Hint
  onClose: () => void
  isAuthenticated: boolean
  onRequestSignIn?: () => void
}

export function HintInfoModal({
  hint,
  onClose,
  isAuthenticated,
  onRequestSignIn,
}: HintInfoModalProps) {
  const { t } = useTranslation()
  const [reportType, setReportType] = useState('')
  const [description, setDescription] = useState('')
  const [showReportForm, setShowReportForm] = useState(false)
  const reportMutation = useReportHint(onClose)

  const canReport = isAuthenticated || !!onRequestSignIn

  const handleReport = () => {
    if (!reportType) return
    reportMutation.mutate({ hintId: String(hint.id), reportType, description: description || undefined })
  }

  return (
    <Modal isOpen onClose={onClose} title={t.play.hintDetails} className="max-w-md">
      <div className="space-y-0 -mt-2">
        <Row
          label={t.play.category}
          value={<span className="px-3 py-1 rounded-lg bg-white/[.06] text-sm font-medium">{hint.category}</span>}
        />
        <Row
          label={t.play.difficulty}
          value={
            <div className="flex items-center gap-3">
              <div className="w-24 h-1.5 rounded-full bg-white/[.08] overflow-hidden">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-success to-warning"
                  style={{ width: `${hint.difficulty}%` }}
                />
              </div>
              <span className="text-white/60 text-xs">{hint.difficulty}%</span>
            </div>
          }
        />
        {hint.source && (
          <Row label={t.play.source} value={<span className="text-white/70 text-sm">{hint.source}</span>} />
        )}
        {hint.imageUrl && <HintImageAttributionRow hint={hint} />}
      </div>

      <AnimatePresence mode="wait">
        {showReportForm && isAuthenticated ? (
          <motion.div
            key="report-form"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="mt-6 pt-6 border-t border-white/[.06]"
          >
            <p className="text-sm font-medium mb-3">{t.play.reportHint}</p>
            <div className="mb-3">
              <CustomSelect
                value={reportType}
                onChange={setReportType}
                placeholder={t.play.reportTypePlaceholder}
                options={[
                  { value: 'INCORRECT', label: t.play.reportIncorrect },
                  { value: 'MISLEADING', label: t.play.reportMisleading },
                  { value: 'OFFENSIVE', label: t.play.reportOffensive },
                  { value: 'OTHER', label: t.play.reportOther },
                ]}
              />
            </div>
            <textarea
              value={description}
              onChange={event => setDescription(event.target.value)}
              placeholder={t.play.reportDescription}
              rows={2}
              className="w-full mb-4 px-4 py-3 rounded-xl bg-white/[.03] border border-white/[.06] text-sm text-white placeholder:text-white/25 focus:outline-none focus:border-accent/40 resize-none"
            />
            <div className="flex gap-3">
              <button
                onClick={() => setShowReportForm(false)}
                className="flex-1 py-2.5 rounded-xl bg-white/[.05] hover:bg-white/[.08] text-white/60 text-sm font-medium transition-colors cursor-pointer"
              >
                {t.common.cancel}
              </button>
              <button
                onClick={handleReport}
                disabled={!reportType || reportMutation.isPending}
                className="flex-1 py-2.5 rounded-xl bg-error/15 hover:bg-error/25 text-error text-sm font-medium transition-colors disabled:opacity-40 cursor-pointer"
              >
                {reportMutation.isPending ? t.common.loading : t.play.reportHint}
              </button>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      {canReport && (
        <div className="flex items-center justify-between mt-6">
          {isAuthenticated ? (
            <button
              onClick={() => setShowReportForm(true)}
              className="flex items-center gap-2 text-white/30 hover:text-error transition-colors cursor-pointer"
            >
              <Flag size={14} />
              <span className="text-xs">{t.play.reportIssue}</span>
            </button>
          ) : (
            <button
              onClick={onRequestSignIn}
              className="flex items-center gap-2 text-white/30 hover:text-error transition-colors cursor-pointer"
            >
              <Flag size={14} />
              <span className="text-xs">{t.play.reportIssue}</span>
            </button>
          )}
        </div>
      )}
    </Modal>
  )
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between py-4 border-b border-white/[.06] last:border-0">
      <span className="text-white/35 text-sm">{label}</span>
      {value}
    </div>
  )
}

function HintImageAttributionRow({ hint }: { hint: Hint }) {
  const { t } = useTranslation()
  const attributionText = hint.imageAttributionText
    ?? (hint.imageAuthorName ? `${t.play.imageBy} ${hint.imageAuthorName}` : null)

  if (!attributionText && !hint.imageLicenseLabel && !hint.imageSourcePageUrl) {
    return null
  }

  return (
    <div className="flex items-start justify-between gap-4 py-4 border-b border-white/[.06] last:border-0">
      <span className="text-white/35 text-sm shrink-0">{t.play.imageCredit}</span>
      <div className="flex flex-col items-end gap-1 text-xs text-white/50">
        {attributionText && <span>{attributionText}</span>}
        <div className="flex items-center gap-2">
          {hint.imageLicenseLabel && (
            hint.imageLicenseUrl
              ? <a href={hint.imageLicenseUrl} target="_blank" rel="noopener noreferrer" className="underline decoration-white/15 underline-offset-2 hover:text-white/70 transition-colors">{hint.imageLicenseLabel}</a>
              : <span>{hint.imageLicenseLabel}</span>
          )}
          {hint.imageSourcePageUrl && (
            <a href={hint.imageSourcePageUrl} target="_blank" rel="noopener noreferrer" className="underline decoration-white/15 underline-offset-2 hover:text-white/70 transition-colors">{t.play.originalImage}</a>
          )}
        </div>
      </div>
    </div>
  )
}
