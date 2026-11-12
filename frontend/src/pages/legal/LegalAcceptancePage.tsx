import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { ArrowRight, FileText, LogOut, ShieldCheck, Check } from 'lucide-react'
import { legalApi } from '@/api/legal'
import { useLegalCurrent } from '@/hooks/useLegalCurrent'
import { useAuthStore } from '@/store/authStore'
import { LegalModal } from '@/components/ui/LegalModal'
import { extractApiErrorMessage } from '@/utils/errors'

export function LegalAcceptancePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user, setUser, logout } = useAuthStore()
  const { data: current, isLoading } = useLegalCurrent()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [accepted, setAccepted] = useState(false)
  const [error, setError] = useState('')
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)

  if (!user) {
    return null
  }

  const handleAccept = async () => {
    if (!current) return
    if (!accepted) {
      setError('You must accept the updated Terms of Service and acknowledge the updated Privacy Policy to continue.')
      return
    }

    setIsSubmitting(true)
    setError('')

    try {
      await legalApi.acceptCurrent(current.termsVersion, current.privacyVersion)

      setUser({
        ...user,
        needsLegalAcceptance: false,
      })

      await queryClient.invalidateQueries({ queryKey: ['profile'] })
      await queryClient.invalidateQueries({ queryKey: ['today-session-summary'] })
      await queryClient.invalidateQueries({ queryKey: ['today-results'] })

      navigate('/', { replace: true })
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Failed to record legal acceptance'))
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleLogout = async () => {
    await logout()
    navigate('/', { replace: true })
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-5 sm:px-8">
      <div className="fixed inset-0 pointer-events-none" aria-hidden>
        <div
          className="absolute rounded-full"
          style={{
            width: 560, height: 560, top: '8%', left: '50%', transform: 'translateX(-50%)',
            background: 'radial-gradient(circle,rgba(52,211,153,.06) 0%,transparent 70%)',
            filter: 'blur(90px)',
          }}
        />
      </div>

      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.45 }}
        className="relative z-10 w-full max-w-xl"
      >
        <div className="gradient-border">
          <div className="gradient-border-inner p-8 sm:p-10">
            <div className="flex items-center justify-between gap-4 mb-8">
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-[.18em] text-white/25 mb-2">
                  Legal Update Required
                </p>
                <h1 className="font-display text-3xl sm:text-4xl font-bold tracking-tight">
                  Review updated terms
                </h1>
              </div>
              <ShieldCheck size={28} className="text-emerald-300/80 shrink-0" />
            </div>

            <div className="space-y-4 text-sm text-white/55 leading-relaxed mb-8">
              <p>
                The current Terms of Service or Privacy Policy changed. You need to accept the current Terms and
                acknowledge the current Privacy Policy before you can continue using authenticated features.
              </p>
              {current && (
                <p className="text-white/35">
                  Effective date: <span className="text-white/65">{current.effectiveDate}</span>
                </p>
              )}
            </div>

            <div className="grid gap-3 sm:grid-cols-2 mb-6">
              <button
                type="button"
                onClick={() => setLegalModal('terms')}
                className="rounded-2xl border border-white/[.08] bg-white/[.03] px-5 py-4 text-left transition-colors hover:bg-white/[.05] cursor-pointer"
              >
                <span className="inline-flex items-center gap-2 text-white/80 text-sm font-medium">
                  <FileText size={16} />
                  Terms of Service
                </span>
                <p className="mt-2 text-xs text-white/35">Read the current usage rules, suspension terms, and liability limits.</p>
              </button>

              <button
                type="button"
                onClick={() => setLegalModal('privacy')}
                className="rounded-2xl border border-white/[.08] bg-white/[.03] px-5 py-4 text-left transition-colors hover:bg-white/[.05] cursor-pointer"
              >
                <span className="inline-flex items-center gap-2 text-white/80 text-sm font-medium">
                  <ShieldCheck size={16} />
                  Privacy Policy
                </span>
                <p className="mt-2 text-xs text-white/35">Read how Clueterra processes account, gameplay, guest, and security data.</p>
              </button>
            </div>

            <label className="flex items-start gap-3 cursor-pointer group mb-6">
              <button
                type="button"
                role="checkbox"
                aria-checked={accepted}
                onClick={() => { setAccepted(!accepted); setError('') }}
                className={`mt-0.5 w-4.5 h-4.5 shrink-0 rounded border transition-all duration-200 flex items-center justify-center cursor-pointer ${
                  accepted
                    ? 'bg-accent border-accent'
                    : 'bg-white/[.03] border-white/[.12] group-hover:border-white/25'
                }`}
              >
                {accepted && <Check size={12} className="text-white" strokeWidth={3} />}
              </button>
              <span className="text-xs text-white/42 leading-relaxed select-none">
                I accept the current Terms of Service and acknowledge that I reviewed the current Privacy Policy.
              </span>
            </label>

            {error && (
              <div className="mb-5 rounded-xl border border-error/20 bg-error/10 px-4 py-3 text-sm text-error">
                {error}
              </div>
            )}

            <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
              <button
                type="button"
                onClick={handleAccept}
                disabled={isSubmitting || isLoading || !current}
                className="h-[52px] rounded-2xl bg-accent hover:bg-accent-hover text-white text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer inline-flex items-center justify-center gap-2.5"
              >
                {isSubmitting ? (
                  <>
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    Accept and Continue
                    <ArrowRight size={16} />
                  </>
                )}
              </button>

              <button
                type="button"
                onClick={handleLogout}
                className="h-[52px] rounded-2xl border border-white/[.08] bg-white/[.03] px-5 text-sm text-white/45 hover:text-white/65 hover:bg-white/[.05] transition-colors inline-flex items-center justify-center gap-2"
              >
                <LogOut size={15} />
                Logout
              </button>
            </div>
          </div>
        </div>
      </motion.div>

      <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
    </div>
  )
}
