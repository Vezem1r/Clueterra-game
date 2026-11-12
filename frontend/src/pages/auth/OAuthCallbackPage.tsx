import { useState, useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { User, ArrowRight, AlertCircle, Check } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useAuthStore } from '@/store/authStore'
import { authApi } from '@/api/auth'
import { authResponseToUser } from '@/utils/auth'
import { oauthRedirectStorage } from '@/lib/browserStorage'
import { useLegalCurrent } from '@/hooks/useLegalCurrent'
import { useTranslation } from '@/i18n'
import { LegalModal } from '@/components/ui/LegalModal'
import type { ApiError } from '@/types'

export function OAuthCallbackPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const { setUser } = useAuthStore()

  const authStatus = searchParams.get('status')
  const code = searchParams.get('code')
  const pendingToken = searchParams.get('pending')
  const oauthError = searchParams.get('error')

  const { t } = useTranslation()
  const [username, setUsername] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const handledRef = useRef<string | null>(null)
  const { data: legalCurrent, isLoading: legalLoading } = useLegalCurrent()

  useEffect(() => {
    if (oauthError === 'access_denied') {
      navigate('/login', { replace: true })
      return
    }
    if (oauthError) {
      navigate(`/login?error=${encodeURIComponent(oauthError)}`, { replace: true })
      return
    }
    const authMarker = authStatus === 'authenticated' ? 'authenticated' : code
    if (!authMarker || handledRef.current === authMarker) return
    handledRef.current = authMarker

    const authPromise = authStatus === 'authenticated'
      ? authApi.refresh()
      : authApi.exchangeOAuthCode(code!)

    authPromise.then(async response => {
      const nextUser = authResponseToUser(response)
      setUser(nextUser)
      await queryClient.invalidateQueries({ queryKey: ['profile'] })
      await queryClient.invalidateQueries({ queryKey: ['today-session-summary'] })
      await queryClient.invalidateQueries({ queryKey: ['guest-progress'] })
      await queryClient.invalidateQueries({ queryKey: ['friends'] })
      navigate(nextUser.needsLegalAcceptance ? '/legal/update' : oauthRedirectStorage.consume(), { replace: true })
    }).catch(() => {
      navigate('/login?error=oauth_failed', { replace: true })
    })
  }, [authStatus, code, oauthError, navigate, queryClient, setUser])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!pendingToken) return

    if (!termsAccepted) {
      setError(t.auth.mustAcceptTerms)
      return
    }
    if (!legalCurrent) {
      setError('Legal documents are still loading. Please try again in a moment.')
      return
    }

    setError('')
    setIsLoading(true)
    try {
      const response = await authApi.completeOAuthRegistration(
        pendingToken,
        username,
        legalCurrent.termsVersion,
        legalCurrent.privacyVersion
      )
      const nextUser = authResponseToUser(response)
      setUser(nextUser)
      await queryClient.invalidateQueries({ queryKey: ['profile'] })
      await queryClient.invalidateQueries({ queryKey: ['today-session-summary'] })
      await queryClient.invalidateQueries({ queryKey: ['guest-progress'] })
      await queryClient.invalidateQueries({ queryKey: ['friends'] })
      navigate(nextUser.needsLegalAcceptance ? '/legal/update' : oauthRedirectStorage.consume(), { replace: true })
    } catch (err: unknown) {
      if (isAxiosError(err)) {
        if (err.response?.status === 409) {
          setError('This username is already taken. Please choose another.')
        } else if (err.response?.status === 404) {
          setError('Registration session expired. Please try signing in again.')
        } else {
          const data = err.response?.data as ApiError | undefined
          setError(data?.message ?? data?.error ?? 'Registration failed')
        }
      } else {
        setError(err instanceof Error ? err.message : 'An unexpected error occurred')
      }
    } finally {
      setIsLoading(false)
    }
  }

  if (authStatus === 'authenticated' || code || oauthError) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
      </div>
    )
  }

  if (!pendingToken) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center px-5">
        <div className="text-center">
          <AlertCircle size={48} className="text-error/50 mx-auto mb-4" />
          <p className="text-white/40 text-sm">Invalid OAuth callback. Please try again.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-5 sm:px-8">
      <div className="fixed inset-0 pointer-events-none" aria-hidden>
        <div
          className="absolute rounded-full"
          style={{
            width: 500, height: 500, top: '10%', left: '50%', transform: 'translateX(-50%)',
            background: 'radial-gradient(circle,rgba(52,211,153,.06) 0%,transparent 70%)',
            filter: 'blur(80px)',
          }}
        />
      </div>

      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="relative z-10 w-full max-w-sm"
      >
        <div className="text-center mb-10 sm:mb-12">
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ type: 'spring', delay: 0.1, stiffness: 200 }}
            className="mx-auto w-14 h-14 rounded-2xl bg-success/10 border border-success/20 flex items-center justify-center mb-5"
          >
            <User size={24} className="text-success" />
          </motion.div>
          <h1 className="font-display text-2xl sm:text-3xl font-bold tracking-tight mb-3">
            {t.auth.chooseUsernameTitle}
          </h1>
          <p className="text-sm text-white/30">
            {t.auth.chooseUsernameBody}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 sm:space-y-5">
          {error && (
            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              className="p-4 rounded-xl bg-error/10 border border-error/20 text-error text-sm"
            >
              {error}
            </motion.div>
          )}

          <div className="relative">
            <User size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              type="text"
              placeholder={t.auth.username}
              value={username}
              onChange={e => { setUsername(e.target.value.replace(/[^a-zA-Z0-9_-]/g, '')); setError('') }}
              minLength={3}
              maxLength={30}
              autoFocus
              required
              className="w-full pl-11 pr-4 py-3.5 sm:py-4 rounded-xl
                         bg-white/[.03] border border-white/[.06]
                         text-white text-sm placeholder:text-white/25
                         focus:outline-none focus:border-accent/40 focus:ring-1 focus:ring-accent/20
                         transition-all duration-200"
            />
          </div>

          <p className="text-[11px] text-white/20 px-1">
            3-30 characters. Letters, numbers, underscores and hyphens only.
          </p>

          <label className="flex items-start gap-3 cursor-pointer group">
            <button
              type="button"
              role="checkbox"
              aria-checked={termsAccepted}
              onClick={() => { setTermsAccepted(!termsAccepted); setError('') }}
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
            disabled={isLoading || legalLoading || !legalCurrent || username.length < 3}
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
                Creating account...
              </>
            ) : (
              <>
                Continue
                <ArrowRight size={16} />
              </>
            )}
          </button>
        </form>
      </motion.div>

      <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
    </div>
  )
}
