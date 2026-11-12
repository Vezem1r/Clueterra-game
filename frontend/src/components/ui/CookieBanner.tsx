import { consentStorage } from '@/lib/browserStorage'
import { useTranslation } from '@/i18n'
import { useCookieConsent } from '@/hooks/useCookieConsent'

export function CookieBanner() {
  const { t } = useTranslation()
  const accepted = useCookieConsent()
  const handleAccept = () => {
    consentStorage.accept()
  }

  if (accepted) return null

  return (
    <aside aria-label={t.cookies.label}className="cookie-banner-enter pointer-events-none fixed bottom-0 inset-x-0 z-50 p-2 pb-[max(.5rem,env(safe-area-inset-bottom))] sm:p-6">
      <div className="pointer-events-auto max-w-xl mx-auto flex items-center gap-3 sm:gap-6
                      px-3 py-1 sm:px-6 sm:py-4 rounded-xl sm:rounded-2xl
                      bg-[#10121c]/95 backdrop-blur-xl border border-white/[.12]
                      shadow-2xl">
        <p className="flex-1 text-xs sm:text-sm text-white/75 leading-relaxed">
          {t.cookies.message}
        </p>
        <button
          onClick={handleAccept}
          className="shrink-0 px-3 sm:px-5 py-2 rounded-lg sm:rounded-xl
                     bg-white/[.1] hover:bg-white/[.15]
                     text-xs sm:text-sm font-medium text-white/90
                     transition-colors cursor-pointer"
        >
          {t.cookies.accept}
        </button>
      </div>
    </aside>
  )
}
