import { ArrowRight, Banknote } from 'lucide-react'
import { Link } from 'react-router-dom'

export function BudgetGameInvitation() {
  return (
    <div className="relative mt-6 flex flex-wrap items-center gap-x-4 gap-y-2">
      <Link
        to="/budget"
        className="group inline-flex min-h-12 items-center gap-3 rounded-full bg-gradient-to-r from-amber-100 via-amber-200 to-amber-300 py-2 pl-2 pr-5 text-sm font-semibold text-[#151006] shadow-[0_14px_40px_rgba(251,191,36,.2),inset_0_1px_0_rgba(255,255,255,.55)] transition duration-200 hover:-translate-y-0.5 hover:shadow-[0_18px_48px_rgba(251,191,36,.3),inset_0_1px_0_rgba(255,255,255,.55)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-100 focus-visible:ring-offset-2 focus-visible:ring-offset-background"
      >
        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-[#151006] text-amber-200">
          <Banknote size={16} aria-hidden="true" />
        </span>
        Try today’s cost challenge
        <ArrowRight size={16} aria-hidden="true" className="transition-transform duration-200 group-hover:translate-x-0.5" />
      </Link>
      <p className="text-xs text-white/55">Free to play · No account needed</p>
    </div>
  )
}
