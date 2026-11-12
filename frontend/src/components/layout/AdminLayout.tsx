import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Globe2, Lightbulb, Calendar,
  BarChart3, FileText, LogOut, Zap, Flag, Play,
  PanelLeftClose, PanelLeftOpen, BarChart, Users, Settings, Award, ListFilter,
  Menu, X,
  Sparkles,
  PanelsTopLeft,
} from 'lucide-react'
import { GlobalProgressBanner } from '@/components/admin/GlobalProgressBanner'
import { useAuthStore } from '@/store/authStore'
import { useSettingsStore, type Language } from '@/store/settingsStore'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'

const languages: Language[] = ['en', 'ru', 'cs']
const languageFlags: Record<Language, string> = { en: '🇬🇧', ru: '🇷🇺', cs: '🇨🇿' }

export function AdminLayout() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { user, logout } = useAuthStore()
  const { language, setLanguage } = useSettingsStore()
  const [collapsed, setCollapsed] = useState(false)
  const [isMobileOpen, setIsMobileOpen] = useState(false)

  const navItems = [
    { to: '/admin', icon: LayoutDashboard, label: t.admin.dashboard, end: true },
    { to: '/admin/users', icon: Users, label: t.admin.users },
    { to: '/admin/badges', icon: Award, label: t.admin.badges },
    { to: '/admin/countries', icon: Globe2, label: t.admin.countries },
    { to: '/admin/hints', icon: Lightbulb, label: t.admin.hints },
    { to: '/admin/challenges', icon: Calendar, label: t.admin.challenges },
    { to: '/admin/content/budget', icon: PanelsTopLeft, label: 'Content Studio' },
    { to: '/admin/analytics', icon: BarChart3, label: t.admin.analytics },
    { to: '/admin/reports', icon: Flag, label: t.admin.reports },
    { to: '/admin/logs', icon: FileText, label: t.admin.logs },
    { to: '/admin/operations', icon: Zap, label: t.admin.operations },
    { to: '/admin/ai', icon: Sparkles, label: t.admin.ai },
    { to: '/admin/statistics', icon: BarChart, label: t.admin.statistics },
    { to: '/admin/indicators', icon: ListFilter, label: t.admin.indicators },
    { to: '/admin/settings', icon: Settings, label: t.admin.settings },
  ]

  const handleLogout = async () => {
    await logout()
    navigate('/')
  }

  return (
    <div className="flex h-screen bg-background">
      {isMobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/70 md:hidden"
          onClick={() => setIsMobileOpen(false)}
        />
      )}
      {/* Sidebar */}
      <aside
        className={cn(
          'flex flex-col bg-[#0a0a12] border-r border-white/[.06] transition-all duration-300',
          'md:relative md:z-auto',
          collapsed ? 'md:w-[68px]' : 'md:w-64',
          'max-md:fixed max-md:inset-0 max-md:z-50 max-md:w-full',
          isMobileOpen ? 'max-md:flex' : 'max-md:hidden',
        )}
      >
        {/* Logo */}
        <div className="px-4 py-4 border-b border-white/[.06] flex items-center justify-between">
          {!collapsed && (
            <div className="px-2">
              <h1 className="text-lg font-bold text-white font-display tracking-tight">
                Clue<span className="text-violet-400">terra</span>
              </h1>
              <p className="text-[10px] text-white/30 uppercase tracking-widest">Admin</p>
            </div>
          )}
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="hidden md:flex p-2 rounded-lg text-white/30 hover:text-white/60 hover:bg-white/[.04] transition-colors cursor-pointer"
          >
            {collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
          </button>
          <button
            onClick={() => setIsMobileOpen(false)}
            className="flex md:hidden p-2 rounded-lg text-white/30 hover:text-white/60 hover:bg-white/[.04] transition-colors cursor-pointer"
          >
            <X size={18} />
          </button>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-2.5 py-3 space-y-0.5 overflow-y-auto">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setIsMobileOpen(false)}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm transition-all',
                  collapsed && 'justify-center px-0',
                  isActive
                    ? 'bg-white/[.08] text-white'
                    : 'text-white/45 hover:text-white/70 hover:bg-white/[.04]'
                )
              }
              title={collapsed ? item.label : undefined}
            >
              <item.icon size={18} className="shrink-0" />
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div className="p-2.5 border-t border-white/[.06] space-y-0.5">
          {/* Language */}
          {!collapsed && (
            <div className="flex gap-1 px-1 py-1.5">
              {languages.map((lang) => (
                <button
                  key={lang}
                  onClick={() => setLanguage(lang)}
                  className={cn(
                    'flex-1 py-1.5 text-[10px] rounded-lg transition-colors cursor-pointer text-center',
                    lang === language
                      ? 'bg-white/[.08] text-white'
                      : 'text-white/30 hover:bg-white/[.04]'
                  )}
                >
                  {languageFlags[lang]}
                </button>
              ))}
            </div>
          )}

          <NavLink
            to="/"
            onClick={() => setIsMobileOpen(false)}
            className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-white/45 hover:text-white/70 hover:bg-white/[.04] transition-colors"
            title={collapsed ? t.admin.playGame : undefined}
          >
            <Play size={18} className="shrink-0" />
            {!collapsed && <span>{t.admin.playGame}</span>}
          </NavLink>

          {/* User info */}
          {!collapsed && (
            <div className="flex items-center gap-3 px-3 py-2">
              <div className="w-8 h-8 rounded-full bg-white/[.08] flex items-center justify-center shrink-0">
                <span className="text-sm font-medium text-white/60">
                  {user?.email?.[0].toUpperCase()}
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm text-white truncate">{user?.email}</p>
                <p className="text-[10px] text-white/30">Admin</p>
              </div>
            </div>
          )}

          <button
            onClick={handleLogout}
            className={cn(
              'flex items-center gap-3 w-full px-3 py-2.5 text-sm text-white/40 hover:text-error/70 hover:bg-error/[.04] rounded-xl transition-colors cursor-pointer',
              collapsed && 'justify-center px-0'
            )}
            title={collapsed ? t.nav.signOut : undefined}
          >
            <LogOut size={18} className="shrink-0" />
            {!collapsed && <span>{t.nav.signOut}</span>}
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto flex flex-col min-w-0">
        <GlobalProgressBanner />
        <div className="md:hidden sticky top-0 z-30 flex items-center gap-3 px-4 h-14 bg-[#0a0a12] border-b border-white/[.06] shrink-0">
          <button
            onClick={() => setIsMobileOpen(true)}
            className="p-2 rounded-lg text-white/50 hover:text-white/80 hover:bg-white/[.05] transition-colors cursor-pointer"
          >
            <Menu size={20} />
          </button>
          <div>
            <h1 className="text-base font-bold text-white font-display tracking-tight leading-none">
              Clue<span className="text-violet-400">terra</span>
            </h1>
            <p className="text-[9px] text-white/25 uppercase tracking-widest">Admin</p>
          </div>
        </div>
        <div className="flex-1 min-h-0 overflow-auto">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
