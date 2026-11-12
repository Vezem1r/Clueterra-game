import { ArrowLeft, CircleDollarSign, Database, Flag, Globe2, Layers3, Menu, Plane, Waves, X } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { cn } from '@/utils/cn'

const contentProducts = [
  {
    to: '/admin/content/budget',
    label: 'BudgetGuessr',
    description: 'Megaproject candidates',
    icon: CircleDollarSign,
  },
  {
    to: '/admin/content/rivers',
    label: 'Rivers',
    description: 'Question maps and clues',
    icon: Waves,
  },
  {
    to: '/admin/content/silhouettes',
    label: 'Silhouettes',
    description: 'Country shapes, clues and play stats',
    icon: Globe2,
  },
  {
    to: '/admin/content/flags',
    label: 'Flags',
    description: 'Flag questions, clues and mix-ups',
    icon: Flag,
  },
  {
    to: '/admin/content/quiz',
    label: 'World quiz',
    description: 'Tour stops, questions and accuracy',
    icon: Plane,
  },
]

export function ContentAdminLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <div className="flex h-screen overflow-hidden bg-[#07080c] text-white">
      {mobileOpen && (
        <button
          aria-label="Close content navigation"
          className="fixed inset-0 z-40 bg-black/70 md:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      <aside
        className={cn(
          'z-50 flex w-[260px] shrink-0 flex-col border-r border-white/[.07] bg-[#0a0c12]',
          'max-md:fixed max-md:inset-y-0 max-md:left-0 max-md:transition-transform',
          mobileOpen ? 'max-md:translate-x-0' : 'max-md:-translate-x-full',
        )}
      >
        <div className="flex h-16 items-center justify-between border-b border-white/[.07] px-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-400/10 text-emerald-300">
              <Layers3 size={18} />
            </div>
            <div>
              <p className="text-sm font-semibold tracking-tight">Content Studio</p>
              <p className="text-[10px] uppercase tracking-[.18em] text-white/30">Clueterra admin</p>
            </div>
          </div>
          <button
            aria-label="Close navigation"
            className="rounded-lg p-2 text-white/40 hover:bg-white/[.05] md:hidden"
            onClick={() => setMobileOpen(false)}
          >
            <X size={17} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-3 py-5">
          <p className="mb-2 px-3 text-[10px] font-semibold uppercase tracking-[.18em] text-white/25">
            Game content
          </p>
          <nav className="space-y-1">
            {contentProducts.map((product) => (
              <NavLink
                key={product.to}
                to={product.to}
                onClick={() => setMobileOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 rounded-xl border px-3 py-3 transition-colors',
                    isActive
                      ? 'border-emerald-400/20 bg-emerald-400/[.08] text-white'
                      : 'border-transparent text-white/50 hover:bg-white/[.04] hover:text-white/80',
                  )
                }
              >
                <product.icon size={18} className="shrink-0" />
                <span className="min-w-0">
                  <span className="block text-sm font-medium">{product.label}</span>
                  <span className="block truncate text-[11px] text-white/30">{product.description}</span>
                </span>
              </NavLink>
            ))}
          </nav>
        </div>

        <div className="border-t border-white/[.07] p-3">
          <NavLink
            to="/admin"
            className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-white/45 transition-colors hover:bg-white/[.04] hover:text-white/75"
          >
            <ArrowLeft size={17} />
            Main admin
          </NavLink>
        </div>
      </aside>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-16 shrink-0 items-center gap-3 border-b border-white/[.07] bg-[#090a0f]/95 px-4 backdrop-blur-xl md:px-6">
          <button
            aria-label="Open content navigation"
            className="rounded-lg p-2 text-white/50 hover:bg-white/[.05] md:hidden"
            onClick={() => setMobileOpen(true)}
          >
            <Menu size={19} />
          </button>
          <Database size={16} className="text-emerald-300" />
          <div>
            <p className="text-sm font-medium">Content Studio</p>
            <p className="text-[11px] text-white/30">Review generated game content</p>
          </div>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
