import { useState, type MouseEventHandler } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Globe2, Trophy, User, Shield, LogOut, Menu, X, ChevronDown, Database, BookOpen, Users, LayoutGrid, Info, Landmark } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useGuestProgress } from '@/hooks/useGameQueries'
import { useSettingsStore, type Language } from '@/store/settingsStore'
import { useTranslation, languageNames } from '@/i18n'
import { cn } from '@/utils/cn'
import { basePath, isLocalizedPath, localizedPath } from '@/lib/locale/localePaths'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'
import { RIVER_GUIDE_PATH, RIVER_GUIDE_QUERY } from '@/pages/games/rivers/riverGameConstants'
import { SILHOUETTE_CANONICAL_PATH, SILHOUETTE_GUIDE_PATH, SILHOUETTE_GUIDE_QUERY } from '@/pages/games/silhouettes/silhouetteGameConstants'
import { FLAG_CANONICAL_PATH, FLAG_GUIDE_PATH, FLAG_GUIDE_QUERY } from '@/pages/games/flags/flagGameConstants'
import { QUIZ_CANONICAL_PATH, QUIZ_GUIDE_PATH, QUIZ_GUIDE_QUERY } from '@/pages/games/countries-quiz/quizGameConstants'

const languages: Language[] = ['en', 'ru', 'cs']
const languageFlags: Record<Language, string> = { en: '🇬🇧', ru: '🇷🇺', cs: '🇨🇿' }

export function Header() {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated, authResolved, user, logout } = useAuthStore()
  const { data: guestProgress } = useGuestProgress(authResolved && !isAuthenticated)
  const hasGuestProfile = !isAuthenticated && !!guestProgress?.hasGuest
    && guestProgress.playCount + guestProgress.budgetPlayCount > 0
  const { language, setLanguage } = useSettingsStore()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [langOpen, setLangOpen] = useState(false)

  const isAdmin = user?.role === 'ADMIN'
  const localize = useLocalizedPath()
  const currentPath = basePath(location.pathname)
  const isActive = (path: string) => currentPath === path
  const chooseLanguage = (lang: Language) => {
    setLanguage(lang)
    if (isLocalizedPath(location.pathname)) {
      navigate({ pathname: localizedPath(location.pathname, lang), search: location.search, hash: location.hash })
    }
  }

  const navItems = [
    { path: '/leaderboard', label: t.nav.leaderboard, icon: Trophy },
    { path: '/friends', label: t.nav.friends, icon: Users },
    ...(isAdmin ? [{ path: '/admin', label: t.nav.admin, icon: Shield }] : []),
  ]
  const mobileResourceItems = [
    { path: '/projects', label: t.nav.projects, icon: Landmark },
    { path: '/about', label: t.footer.aboutLink, icon: Info },
    { path: '/data-sources', label: t.footer.registryLink, icon: Database },
    { path: '/privacy', label: t.footer.privacyLink, icon: Shield },
    { path: '/terms', label: t.footer.termsLink, icon: Shield },
  ]

  const riverPage = currentPath.startsWith('/games/rivers')
  const silhouettePage = currentPath.startsWith(SILHOUETTE_CANONICAL_PATH)
  const flagPage = currentPath.startsWith(FLAG_CANONICAL_PATH)
  const quizPage = currentPath.startsWith(QUIZ_CANONICAL_PATH)
  const guidePath = quizPage ? QUIZ_GUIDE_PATH : flagPage ? FLAG_GUIDE_PATH : silhouettePage ? SILHOUETTE_GUIDE_PATH : riverPage ? RIVER_GUIDE_PATH : location.pathname.startsWith('/budget') ? '/budget/how-to' : '/how-to'

  const openGuide: MouseEventHandler<HTMLAnchorElement> = (event) => {
    if (currentPath.startsWith(FLAG_GUIDE_PATH) || currentPath.startsWith(SILHOUETTE_GUIDE_PATH) || currentPath.startsWith(RIVER_GUIDE_PATH)) {
      setMobileOpen(false)
      return
    }
    event.preventDefault()
    setMobileOpen(false)
    const nextParams = new URLSearchParams(location.search)
    nextParams.delete('about')
    nextParams.set('guide', quizPage ? QUIZ_GUIDE_QUERY : flagPage ? FLAG_GUIDE_QUERY : silhouettePage ? SILHOUETTE_GUIDE_QUERY : riverPage ? RIVER_GUIDE_QUERY : location.pathname.startsWith('/budget') ? 'budget' : '1')
    navigate({ pathname: location.pathname, search: `?${nextParams.toString()}` })
  }

  const openAbout: MouseEventHandler<HTMLAnchorElement> = (event) => {
    event.preventDefault()
    setMobileOpen(false)
    const nextParams = new URLSearchParams(location.search)
    nextParams.delete('guide')
    nextParams.set('about', '1')
    navigate({ pathname: location.pathname, search: `?${nextParams.toString()}` })
  }

  const handleLogout = async () => {
    await logout()
    setMobileOpen(false)
    navigate('/')
  }

  return (
    <>
      <header className="sticky top-0 z-40 w-full">
        {/* Glass surface */}
        <div className="absolute inset-0 bg-[#08080c]/94 md:bg-[#08080c]/80 md:backdrop-blur-2xl border-b border-white/[.04]" />

        <nav className="relative flex items-center justify-between h-14 sm:h-16 px-5 sm:px-8 lg:px-12">
          {/* Logo */}
          <Link
            to="/"
            className="flex items-center gap-2.5 group"
            onClick={() => setMobileOpen(false)}
          >
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-violet-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-violet-500/20 group-hover:shadow-violet-500/30 transition-shadow">
              <Globe2 size={16} className="text-white" />
            </div>
            <span className="text-base font-bold tracking-tight text-white">
              Clue<span className="text-violet-400">terra</span>
            </span>
          </Link>

          {/* Desktop nav */}
          <div className="hidden xl:flex items-center gap-1">
            <Link
              to={localize('/games')}
              className={cn(
                'relative mr-1.5 flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-xs font-bold transition-all',
                isActive('/games')
                  ? 'border-violet-300/35 bg-violet-300/15 text-violet-100 shadow-[0_0_22px_rgba(167,139,250,.12)]'
                  : 'border-violet-300/15 bg-violet-300/[.06] text-violet-200/75 hover:border-violet-300/30 hover:bg-violet-300/[.1] hover:text-violet-100',
              )}
            >
              <LayoutGrid size={14} />
              {t.nav.games}
            </Link>

            <Link
              to="/projects"
              className="relative flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-sm text-white/40 transition-all hover:text-white/70"
            >
              <Landmark size={15} />
              {t.nav.projects}
            </Link>

            {navItems.map((item) => {
              const Icon = item.icon
              return (
                <Link
                  key={item.path}
                  to={localize(item.path)}
                  className={cn(
                    'relative flex items-center gap-1.5 px-3.5 py-2 text-sm rounded-lg transition-all',
                    isActive(item.path)
                      ? 'text-white'
                      : 'text-white/40 hover:text-white/70'
                  )}
                >
                  <Icon size={15} />
                  {item.label}
                  {isActive(item.path) && (
                    <span aria-hidden="true" className="pointer-events-none absolute inset-0 rounded-lg border border-white/[.06] bg-white/[.06]" />
                  )}
                </Link>
              )
            })}

            {/* Language switcher */}
            <div className="relative ml-1">
              <button
                onClick={() => setLangOpen(!langOpen)}
                className="flex items-center gap-1 px-2.5 py-2 text-xs text-white/40 hover:text-white/70 rounded-lg hover:bg-white/[.04] transition-all cursor-pointer"
              >
                <span>{languageFlags[language]}</span>
                <ChevronDown size={12} className={cn('transition-transform', langOpen && 'rotate-180')} />
              </button>

              <div className={cn('fixed inset-0 z-40', !langOpen && 'pointer-events-none')} onClick={() => setLangOpen(false)} />
              <div
                inert={!langOpen}
                aria-hidden={!langOpen}
                className={cn(
                  'absolute right-0 top-full z-50 mt-1.5 w-36 origin-top-right overflow-hidden rounded-xl border border-white/[.08] bg-[#0c0c14] shadow-2xl transition-[opacity,transform] duration-150',
                  langOpen ? 'scale-100 translate-y-0 opacity-100' : 'pointer-events-none -translate-y-1 scale-95 opacity-0',
                )}
              >
                      {languages.map((lang) => (
                        <button
                          key={lang}
                          onClick={() => { chooseLanguage(lang); setLangOpen(false) }}
                          className={cn(
                            'w-full flex items-center gap-2.5 px-3.5 py-2.5 text-sm transition-colors cursor-pointer',
                            lang === language
                              ? 'bg-white/[.06] text-white'
                              : 'text-white/45 hover:bg-white/[.04] hover:text-white/70'
                          )}
                        >
                          <span>{languageFlags[lang]}</span>
                          <span>{languageNames[lang]}</span>
                        </button>
                      ))}
              </div>
            </div>

            {/* Separator */}
            <div className="w-px h-5 bg-white/[.06] mx-1.5" />

            {/* Auth */}
            {isAuthenticated ? (
              <div className="flex items-center gap-1">
                <Link
                  to="/profile"
                  className={cn(
                    'flex items-center gap-1.5 px-3.5 py-2 text-sm rounded-lg transition-all relative',
                    isActive('/profile')
                      ? 'text-white'
                      : 'text-white/40 hover:text-white/70'
                  )}
                >
                  <User size={15} />
                  {t.nav.profile}
                  {isActive('/profile') && (
                    <span aria-hidden="true" className="pointer-events-none absolute inset-0 rounded-lg border border-white/[.06] bg-white/[.06]" />
                  )}
                </Link>
                <button
                  onClick={handleLogout}
                  className="flex items-center gap-1.5 px-2.5 py-2 text-sm text-white/30 hover:text-error/80 rounded-lg hover:bg-error/[.06] transition-all cursor-pointer"
                  title={t.nav.signOut}
                >
                  <LogOut size={15} />
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                {hasGuestProfile && <Link to="/profile" className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-emerald-100/75 transition hover:bg-white/[.05] hover:text-emerald-100"><User size={15} />{t.guestProfile.viewProfile}</Link>}
                <Link
                  to="/login"
                  className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-black bg-white rounded-lg hover:bg-white/90 transition-all shadow-[0_0_16px_rgba(255,255,255,0.06)]"
                >
                  {t.nav.signIn}
                </Link>
              </div>
            )}
          </div>

          {/* Mobile hamburger */}
          <button
            type="button"
            onClick={() => setMobileOpen(!mobileOpen)}
            aria-label={mobileOpen ? t.common.close : t.nav.menu}
            aria-expanded={mobileOpen}
            aria-controls="mobile-navigation"
            className="flex h-9 w-9 items-center justify-center rounded-lg text-white/50 transition-all hover:bg-white/[.06] hover:text-white xl:hidden cursor-pointer"
          >
            {mobileOpen ? <X size={18} /> : <Menu size={18} />}
          </button>
        </nav>
      </header>

      {/* Mobile slide-out */}
      <div
        aria-hidden="true"
        className={cn(
          'fixed inset-0 z-30 bg-black/70 transition-opacity duration-300 xl:hidden',
          mobileOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
        onClick={() => setMobileOpen(false)}
      />
      <div
        id="mobile-navigation"
        inert={!mobileOpen}
        aria-hidden={!mobileOpen}
        className={cn(
          'fixed bottom-0 right-0 top-0 z-30 w-72 overflow-y-auto border-l border-white/[.06] bg-[#0a0a12] transition-transform duration-300 ease-out xl:hidden',
          mobileOpen ? 'translate-x-0' : 'translate-x-full',
        )}
      >
              <div className="flex flex-col p-5 pt-20 gap-1">
                {/* Nav links */}
                <Link
                  to={localize('/games')}
                  onClick={() => setMobileOpen(false)}
                  className={cn(
                    'flex items-center gap-3 rounded-xl border px-4 py-3 text-sm font-semibold transition-all',
                    isActive('/games')
                      ? 'border-violet-300/25 bg-violet-300/[.12] text-violet-100'
                      : 'border-violet-300/10 bg-violet-300/[.05] text-violet-200/75 hover:text-violet-100',
                  )}
                >
                  <LayoutGrid size={18} />
                  {t.nav.games}
                </Link>

                <Link
                  to="/"
                  onClick={() => setMobileOpen(false)}
                  className={cn(
                    'flex items-center gap-3 px-4 py-3 rounded-xl text-sm transition-all',
                    isActive('/') ? 'bg-white/[.06] text-white' : 'text-white/45 hover:text-white/70',
                  )}
                >
                  <Globe2 size={18} />
                  {t.nav.home}
                </Link>

                {navItems.map((item) => {
                  const Icon = item.icon
                  return (
                    <Link
                      key={item.path}
                      to={localize(item.path)}
                      onClick={() => setMobileOpen(false)}
                      className={cn(
                        'flex items-center gap-3 px-4 py-3 rounded-xl text-sm transition-all',
                        isActive(item.path)
                          ? 'bg-white/[.06] text-white'
                          : 'text-white/45 hover:text-white/70'
                      )}
                    >
                      <Icon size={18} />
                      {item.label}
                    </Link>
                  )
                })}

                <div className="h-px bg-white/[.06] my-3" />

                <Link
                  to={localize(guidePath)}
                  onClick={openGuide}
                  className="flex w-full items-center gap-3 rounded-xl px-4 py-3 text-left text-sm text-white/45 transition-all hover:text-white/70"
                >
                  <BookOpen size={18} />
                  {t.footer.howToPlay}
                </Link>
                {mobileResourceItems.map((item) => {
                  const Icon = item.icon
                  return (
                    <Link
                      key={item.path}
                      to={localize(item.path)}
                      onClick={item.path === '/about' ? openAbout : () => setMobileOpen(false)}
                      className={cn(
                        'flex items-center gap-3 px-4 py-3 rounded-xl text-sm transition-all',
                        isActive(item.path)
                          ? 'bg-white/[.06] text-white'
                          : 'text-white/45 hover:text-white/70'
                      )}
                    >
                      <Icon size={18} />
                      {item.label}
                    </Link>
                  )
                })}
                {/* Separator */}
                <div className="h-px bg-white/[.06] my-3" />

                {/* Language selector */}
                <p className="px-4 text-[10px] uppercase tracking-widest text-white/20 mb-1">
                  {t.common.language}
                </p>
                <div className="flex gap-1.5 px-4 mb-2">
                  {languages.map((lang) => (
                    <button
                      key={lang}
                      onClick={() => chooseLanguage(lang)}
                      className={cn(
                        'flex-1 py-2 text-xs rounded-lg transition-all cursor-pointer text-center',
                        lang === language
                          ? 'bg-white/[.08] text-white border border-white/[.08]'
                          : 'text-white/35 hover:bg-white/[.04] border border-transparent'
                      )}
                    >
                      {languageFlags[lang]} {lang.toUpperCase()}
                    </button>
                  ))}
                </div>

                <div className="h-px bg-white/[.06] my-3" />

                {/* Auth */}
                {isAuthenticated ? (
                  <>
                    <Link
                      to="/profile"
                      onClick={() => setMobileOpen(false)}
                      className={cn(
                        'flex items-center gap-3 px-4 py-3 rounded-xl text-sm transition-all',
                        isActive('/profile')
                          ? 'bg-white/[.06] text-white'
                          : 'text-white/45 hover:text-white/70'
                      )}
                    >
                      <User size={18} />
                      {t.nav.profile}
                    </Link>
                    <button
                      onClick={handleLogout}
                      className="flex items-center gap-3 px-4 py-3 rounded-xl text-sm text-error/60 hover:text-error hover:bg-error/[.06] transition-all cursor-pointer"
                    >
                      <LogOut size={18} />
                      {t.nav.signOut}
                    </button>
                  </>
                ) : (
                  <>
                    {hasGuestProfile && <Link to="/profile" onClick={() => setMobileOpen(false)} className="flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold text-emerald-100/80 hover:bg-white/[.06]"><User size={18} />{t.guestProfile.viewProfile}</Link>}
                    <Link
                      to="/login"
                      onClick={() => setMobileOpen(false)}
                      className="flex items-center justify-center gap-2 mx-4 mt-2 px-4 py-3 text-sm font-medium text-black bg-white rounded-xl hover:bg-white/90 transition-all"
                    >
                      {t.nav.signIn}
                    </Link>
                  </>
                )}
              </div>
      </div>
    </>
  )
}
