import type { MouseEventHandler, ReactNode } from 'react'
import { lazy, Suspense, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { BookOpen, Database, Info, Shield } from 'lucide-react'
import { useCookieConsent } from '@/hooks/useCookieConsent'
import { useTranslation } from '@/i18n'
import { RIVER_GUIDE_PATH, RIVER_GUIDE_QUERY } from '@/pages/games/rivers/riverGameConstants'
import { SILHOUETTE_GUIDE_PATH, SILHOUETTE_GUIDE_QUERY } from '@/pages/games/silhouettes/silhouetteGameConstants'
import { FLAG_GUIDE_PATH, FLAG_GUIDE_QUERY } from '@/pages/games/flags/flagGameConstants'
import { QUIZ_GUIDE_PATH, QUIZ_GUIDE_QUERY } from '@/pages/games/countries-quiz/quizGameConstants'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

const LegalModal = lazy(() => import('@/components/ui/LegalModal').then(module => ({ default: module.LegalModal })))

export function HomeLegalStrip({ guideMode = 'country' }: { guideMode?: 'country' | 'budget' | 'river' | 'silhouette' | 'flag' | 'quiz' }) {
  const { t } = useTranslation()
  const localize = useLocalizedPath()
  const location = useLocation()
  const navigate = useNavigate()
  const cookieAccepted = useCookieConsent()
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const [legalModalActivated, setLegalModalActivated] = useState(false)

  const openLegalModal = (type: 'terms' | 'privacy') => {
    setLegalModalActivated(true)
    setLegalModal(type)
  }

  const guidePath = guideMode === 'quiz' ? QUIZ_GUIDE_PATH : guideMode === 'flag' ? FLAG_GUIDE_PATH : guideMode === 'silhouette' ? SILHOUETTE_GUIDE_PATH : guideMode === 'river' ? RIVER_GUIDE_PATH : guideMode === 'budget' ? '/budget/how-to' : '/how-to'

  const openGuide: MouseEventHandler<HTMLAnchorElement> = (event) => {
    event.preventDefault()
    const params = new URLSearchParams(location.search)
    params.delete('about')
    params.set('guide', guideMode === 'quiz' ? QUIZ_GUIDE_QUERY : guideMode === 'flag' ? FLAG_GUIDE_QUERY : guideMode === 'silhouette' ? SILHOUETTE_GUIDE_QUERY : guideMode === 'river' ? RIVER_GUIDE_QUERY : guideMode === 'budget' ? 'budget' : '1')
    navigate(`${location.pathname}?${params.toString()}`, { replace: true })
  }

  const openAbout: MouseEventHandler<HTMLAnchorElement> = (event) => {
    event.preventDefault()
    const params = new URLSearchParams(location.search)
    params.delete('guide')
    params.set('about', '1')
    navigate(`${location.pathname}?${params.toString()}`, { replace: true })
  }

  return (
    <>
      <div
        className={`pointer-events-none fixed inset-x-0 bottom-[calc(env(safe-area-inset-bottom)+1rem)] z-30 hidden justify-center transition-transform duration-300 ease-out md:flex ${
          cookieAccepted ? 'translate-y-0' : '-translate-y-[5.8rem]'
        }`}
      >
        <div className="pointer-events-auto flex items-center justify-center rounded-full border border-white/[.08] bg-[#0a0c14]/74 px-1.25 py-1.25 shadow-[0_18px_50px_rgba(0,0,0,.26)] backdrop-blur-2xl">
          <div className="flex max-w-full items-center gap-1 overflow-x-auto px-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            <LegalLink
              icon={<BookOpen size={13} />}
              label={t.footer.howToPlay}
              to={localize(guidePath)}
              linkOnClick={openGuide}
            />
            <LegalLink
              icon={<Info size={13} />}
              label={t.footer.aboutLink}
              to="/about"
              linkOnClick={openAbout}
            />
            <LegalLink
              icon={<Shield size={13} />}
              label={t.footer.privacyLink}
              onClick={() => openLegalModal('privacy')}
            />
            <LegalLink
              icon={<Shield size={13} />}
              label={t.footer.termsLink}
              onClick={() => openLegalModal('terms')}
            />
            <LegalLink
              icon={<Database size={13} />}
              label={t.footer.registryLink}
              to="/data-sources"
            />
          </div>
        </div>
        {legalModalActivated && <Suspense fallback={null}>
          <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
        </Suspense>}
      </div>
    </>
  )
}

interface LegalLinkProps {
  icon: ReactNode
  label: string
  onClick?: () => void
  linkOnClick?: MouseEventHandler<HTMLAnchorElement>
  to?: string
}

function LegalLink({ icon, label, linkOnClick, onClick, to }: LegalLinkProps) {
  const content = (
    <>
      {icon}
      <span>{label}</span>
    </>
  )

  if (to) {
    return (
      <Link
        to={to}
        onClick={linkOnClick}
        className="inline-flex min-h-[36px] shrink-0 items-center justify-center gap-2 rounded-full px-3.5 text-[13px] font-medium text-white/64 transition-colors hover:bg-white/[.06] hover:text-white"
        aria-label={label}
      >
        {content}
      </Link>
    )
  }

  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex min-h-[36px] shrink-0 items-center justify-center gap-2 rounded-full px-3.5 text-[13px] font-medium text-white/64 transition-colors hover:bg-white/[.06] hover:text-white"
      aria-label={label}
    >
      {content}
    </button>
  )
}
