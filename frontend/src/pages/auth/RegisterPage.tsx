import { useState } from 'react'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Mail, Lock, ArrowRight, ArrowLeft, ShieldCheck, Ban, Check, User } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { safeRedirectPath } from '@/lib/auth/safeRedirect'
import { useLegalCurrent } from '@/hooks/useLegalCurrent'
import { useTranslation } from '@/i18n'
import { OAuthButtons } from '@/components/auth/OAuthButtons'
import { useRegistrationStatus } from '@/hooks/useRegistrationStatus'
import { LegalModal } from '@/components/ui/LegalModal'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'

export function RegisterPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const { t } = useTranslation()
  const { register, isLoading, error, clearError } = useAuthStore()
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [validationError, setValidationError] = useState('')
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)

  const { data: regStatus } = useRegistrationStatus()
  const { data: legalCurrent, isLoading: legalLoading } = useLegalCurrent()
  const registrationEnabled = regStatus?.enabled ?? true
  const redirectTo = safeRedirectPath(searchParams.get('redirect'))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setValidationError('')

    if (password !== confirmPassword) {
      setValidationError(t.auth.passwordMismatch)
      return
    }

    if (password.length < 8) {
      setValidationError(t.auth.passwordTooShort)
      return
    }

    if (!termsAccepted) {
      setValidationError(t.auth.mustAcceptTerms)
      return
    }
    if (!legalCurrent) {
      setValidationError('Legal documents are still loading. Please try again in a moment.')
      return
    }

    try {
      await register(email, username, password, legalCurrent.termsVersion, legalCurrent.privacyVersion)
      await queryClient.invalidateQueries({ queryKey: ['profile'] })
      await queryClient.invalidateQueries({ queryKey: ['today-session-summary'] })
      await queryClient.invalidateQueries({ queryKey: ['guest-progress'] })
      navigate(useAuthStore.getState().user?.needsLegalAcceptance ? '/legal/update' : redirectTo)
    } catch {
      // handled by store
    }
  }

  const displayError = validationError || error

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-5 sm:px-8">
      <GameAuroraBackdrop variant="light" />

      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="relative z-10 w-full max-w-sm"
      >
        <div className="text-center mb-10 sm:mb-12">
          <h1 className="font-display text-3xl sm:text-4xl font-bold tracking-tight mb-3">
            {t.auth.registerTitle}
          </h1>
          <p className="text-sm text-white/30">{t.auth.registerSubtitle}</p>
        </div>

        {registrationEnabled === false && (
          <div className="text-center mb-8">
            <motion.div
              initial={{ scale: 0, rotate: -15 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ type: 'spring', stiffness: 300, damping: 20, delay: 0.15 }}
              className="mx-auto w-16 h-16 rounded-2xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center mb-5"
            >
              <Ban size={28} className="text-amber-400" />
            </motion.div>
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: 0.25 }}
              className="p-5 rounded-2xl bg-amber-500/[.06] border border-amber-500/15 mb-6"
            >
              <p className="text-amber-200/70 text-sm leading-relaxed">
                Email registration is currently disabled. Please use a social login option below.
              </p>
            </motion.div>
            <OAuthButtons className="mb-6" />
            <p className="text-center mt-6 text-sm text-white/30">
              {t.auth.hasAccount}{' '}
              <Link to={`/login?redirect=${encodeURIComponent(redirectTo)}`} className="text-white/60 hover:text-white transition-colors">
                {t.nav.signIn}
              </Link>
            </p>
            <div className="mt-8 text-center">
              <Link
                to={redirectTo === '/' ? '/' : redirectTo}
                className="inline-flex items-center gap-2 text-white/25 hover:text-white/50 text-xs transition-colors"
              >
                <ArrowLeft size={12} />
                {t.common.back}
              </Link>
            </div>
          </div>
        )}

        {registrationEnabled !== false && <form onSubmit={handleSubmit} className="space-y-4 sm:space-y-5">
          {displayError && (
            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              className="p-4 rounded-xl bg-error/10 border border-error/20 text-error text-sm"
            >
              {displayError}
            </motion.div>
          )}

          <div className="relative">
            <Mail size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              type="email"
              placeholder={t.auth.email}
              value={email}
              onChange={e => { setEmail(e.target.value); clearError(); setValidationError('') }}
              required
              className="w-full pl-11 pr-4 py-3.5 sm:py-4 rounded-xl
                         bg-white/[.03] border border-white/[.06]
                         text-white text-base sm:text-sm placeholder:text-white/25
                         focus:outline-none focus:border-accent/40 focus:ring-1 focus:ring-accent/20
                         transition-all duration-200"
            />
          </div>

          <div className="relative">
            <User size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              type="text"
              placeholder={t.auth.username}
              value={username}
              onChange={e => { setUsername(e.target.value); clearError(); setValidationError('') }}
              required
              minLength={3}
              maxLength={30}
              pattern="^[a-zA-Z0-9._-]+$"
              className="w-full pl-11 pr-4 py-3.5 sm:py-4 rounded-xl
                         bg-white/[.03] border border-white/[.06]
                         text-white text-base sm:text-sm placeholder:text-white/25
                         focus:outline-none focus:border-accent/40 focus:ring-1 focus:ring-accent/20
                         transition-all duration-200"
            />
          </div>

          <div className="relative">
            <Lock size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              type="password"
              placeholder={t.auth.password}
              value={password}
              onChange={e => { setPassword(e.target.value); clearError(); setValidationError('') }}
              required
              className="w-full pl-11 pr-4 py-3.5 sm:py-4 rounded-xl
                         bg-white/[.03] border border-white/[.06]
                         text-white text-base sm:text-sm placeholder:text-white/25
                         focus:outline-none focus:border-accent/40 focus:ring-1 focus:ring-accent/20
                         transition-all duration-200"
            />
          </div>

          <div className="relative">
            <ShieldCheck size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              type="password"
              placeholder={t.auth.confirmPassword}
              value={confirmPassword}
              onChange={e => { setConfirmPassword(e.target.value); setValidationError('') }}
              required
              className="w-full pl-11 pr-4 py-3.5 sm:py-4 rounded-xl
                         bg-white/[.03] border border-white/[.06]
                         text-white text-base sm:text-sm placeholder:text-white/25
                         focus:outline-none focus:border-accent/40 focus:ring-1 focus:ring-accent/20
                         transition-all duration-200"
            />
          </div>

          <label className="flex items-start gap-3 cursor-pointer group">
            <button
              type="button"
              role="checkbox"
              aria-checked={termsAccepted}
              onClick={() => { setTermsAccepted(!termsAccepted); setValidationError('') }}
              className={`mt-0.5 w-4.5 h-4.5 shrink-0 rounded border transition-all duration-200 flex items-center justify-center cursor-pointer ${
                termsAccepted
                  ? 'bg-accent border-accent'
                  : 'bg-white/[.03] border-white/[.12] group-hover:border-white/25'
              }`}
            >
              {termsAccepted && <Check size={12} className="text-white" strokeWidth={3} />}
            </button>
            <span className="text-xs text-white/40 leading-relaxed select-none">
              {t.auth.agreeToTerms}{' '}
              <button type="button" onClick={() => setLegalModal('terms')} className="text-white/60 hover:text-white underline cursor-pointer">
                {t.auth.termsOfService}
              </button>
              {' '}{t.auth.andAcknowledge}{' '}
              <button type="button" onClick={() => setLegalModal('privacy')} className="text-white/60 hover:text-white underline cursor-pointer">
                {t.auth.privacyPolicy}
              </button>
            </span>
          </label>

          <button
            type="submit"
            disabled={isLoading || legalLoading || !legalCurrent}
            className="w-full flex items-center justify-center gap-2.5
                       py-3.5 sm:py-4 rounded-xl
                       bg-accent hover:bg-accent-hover
                       text-white text-sm font-semibold
                       disabled:opacity-40 disabled:cursor-not-allowed
                       transition-colors duration-200 cursor-pointer"
          >
            {isLoading ? (
              <>
                <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                {t.auth.register}...
              </>
            ) : (
              <>
                {t.auth.register}
                <ArrowRight size={16} />
              </>
            )}
          </button>
        </form>}

        {registrationEnabled !== false && <OAuthButtons className="mt-6" />}

        {registrationEnabled !== false && (
          <>
            <p className="text-center mt-8 text-sm text-white/30">
              {t.auth.hasAccount}{' '}
              <Link to={`/login?redirect=${encodeURIComponent(redirectTo)}`} className="text-white/60 hover:text-white transition-colors">
                {t.nav.signIn}
              </Link>
            </p>

            <div className="mt-10 text-center">
              <Link
                to={redirectTo === '/' ? '/' : redirectTo}
                className="inline-flex items-center gap-2 text-white/25 hover:text-white/50 text-xs transition-colors"
              >
                <ArrowLeft size={12} />
                {t.common.back}
              </Link>
            </div>
          </>
        )}
      </motion.div>

      <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
    </div>
  )
}
