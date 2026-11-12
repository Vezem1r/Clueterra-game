import { useState } from 'react'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Mail, Lock, ArrowRight, ArrowLeft } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { safeRedirectPath } from '@/lib/auth/safeRedirect'
import { useTranslation } from '@/i18n'
import { OAuthButtons } from '@/components/auth/OAuthButtons'
import { PinInput } from '@/components/ui/PinInput'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'

export function LoginPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { t } = useTranslation()
  const { login, verifyTotp, isLoading, error, clearError, totpPending, clearTotpPending } = useAuthStore()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const oauthErrorCode = searchParams.get('error')
  const oauthError = oauthErrorCode ? oauthErrorMessage(oauthErrorCode) : ''
  const redirectTo = safeRedirectPath(searchParams.get('redirect'))

  const clearOauthErrorParam = () => {
    if (!oauthErrorCode) {
      return
    }
    const nextSearchParams = new URLSearchParams(searchParams)
    nextSearchParams.delete('error')
    setSearchParams(nextSearchParams, { replace: true })
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await login(email, password)
      if (!useAuthStore.getState().totpPending) {
        navigate(useAuthStore.getState().user?.needsLegalAcceptance ? '/legal/update' : redirectTo)
      }
    } catch {
      // handled by store
    }
  }

  const handleTotpSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault()
    try {
      await verifyTotp(totpCode)
      navigate(useAuthStore.getState().user?.needsLegalAcceptance ? '/legal/update' : redirectTo)
    } catch {
      // handled by store
    }
  }

  const handleTotpComplete = async (code: string) => {
    try {
      await verifyTotp(code)
      navigate(useAuthStore.getState().user?.needsLegalAcceptance ? '/legal/update' : redirectTo)
    } catch {
      // handled by store
    }
  }

  if (totpPending) {
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
            <h1 className="font-display text-2xl sm:text-3xl font-bold tracking-tight mb-3">
              Two-Factor Authentication
            </h1>
            <p className="text-sm text-white/30">
              Enter the 6-digit code from your authenticator app
            </p>
          </div>

          <form onSubmit={handleTotpSubmit} className="space-y-6">
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                className="p-4 rounded-xl bg-error/10 border border-error/20 text-error text-sm"
              >
                {error}
              </motion.div>
            )}

            <PinInput
              value={totpCode}
              onChange={v => { setTotpCode(v); clearError() }}
              onComplete={handleTotpComplete}
              disabled={isLoading}
              error={!!error}
            />

            <button
              type="submit"
              disabled={isLoading || totpCode.length !== 6}
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
                  Verifying...
                </>
              ) : (
                <>
                  Verify
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>

          <div className="mt-10 text-center">
            <button
              onClick={() => { clearTotpPending(); clearError(); setTotpCode('') }}
              className="inline-flex items-center gap-2 text-white/25 hover:text-white/50 text-xs transition-colors cursor-pointer"
            >
              <ArrowLeft size={12} />
              {t.common.back}
            </button>
          </div>
        </motion.div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-5 sm:px-8">
      <GameAuroraBackdrop variant="light" />

      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="relative z-10 w-full max-w-sm"
      >
        {/* header */}
        <div className="text-center mb-10 sm:mb-12">
          <h1 className="font-display text-3xl sm:text-4xl font-bold tracking-tight mb-3">
            {t.auth.loginTitle}
          </h1>
          <p className="text-sm text-white/30">{t.auth.loginSubtitle}</p>
        </div>

        {/* form */}
        <form onSubmit={handleSubmit} className="space-y-4 sm:space-y-5">
          {(oauthError || error) && (
            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              className="p-4 rounded-xl bg-error/10 border border-error/20 text-error text-sm"
            >
              {oauthError || error}
            </motion.div>
          )}

          <div className="relative">
            <Mail size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              type="email"
              placeholder={t.auth.email}
              value={email}
              onChange={e => { setEmail(e.target.value); clearError(); clearOauthErrorParam() }}
              required
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
              onChange={e => { setPassword(e.target.value); clearError(); clearOauthErrorParam() }}
              required
              className="w-full pl-11 pr-4 py-3.5 sm:py-4 rounded-xl
                         bg-white/[.03] border border-white/[.06]
                         text-white text-base sm:text-sm placeholder:text-white/25
                         focus:outline-none focus:border-accent/40 focus:ring-1 focus:ring-accent/20
                         transition-all duration-200"
            />
          </div>

          <button
            type="submit"
            disabled={isLoading}
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
                {t.auth.login}...
              </>
            ) : (
              <>
                {t.auth.login}
                <ArrowRight size={16} />
              </>
            )}
          </button>
        </form>

        <OAuthButtons className="mt-6" />

        {/* links */}
        <p className="text-center mt-8 text-sm text-white/30">
          {t.auth.noAccount}{' '}
          <Link to={`/register?redirect=${encodeURIComponent(redirectTo)}`} className="text-white/60 hover:text-white transition-colors">
            {t.nav.register}
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
      </motion.div>
    </div>
  )
}

function oauthErrorMessage(code: string): string {
  switch (code) {
    case 'oauth_email_not_verified':
      return 'Your social account does not provide a verified email address.'
    case 'oauth_account_exists':
      return 'An account with this email already exists. Sign in with your existing method first.'
    case 'oauth_account_disabled':
      return 'This account is disabled.'
    case 'oauth_use_password_login':
      return 'This account requires your primary sign-in method.'
    case 'oauth_failed':
      return 'Social login failed. Please try again.'
    default:
      return 'Social login failed. Please try again.'
  }
}
