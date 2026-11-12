import { Banknote, CalendarDays, Gauge, Landmark, Scale, SlidersHorizontal } from 'lucide-react'

import { useTranslation } from '@/i18n'

export function BudgetRoundTypes({ columns = 3 }: { columns?: 2 | 3 }) {
  const { t } = useTranslation()
  const roundTypes = [
    { icon: Banknote, label: t.budget.session.howMuch },
    { icon: Scale, label: t.budget.session.originalBudget },
    { icon: Gauge, label: t.budget.session.budgetOverrun },
    { icon: CalendarDays, label: t.budget.session.howLate },
    { icon: Landmark, label: t.budget.session.higherLower },
    { icon: SlidersHorizontal, label: t.budget.session.costPerUnit },
  ]

  return (
    <div className={columns === 3 ? 'grid gap-3 sm:grid-cols-2 lg:grid-cols-3' : 'grid gap-2.5 sm:grid-cols-2'}>
      {roundTypes.map(({ icon: Icon, label }) => (
        <div
          key={label}
          className="flex min-h-16 items-center gap-3 rounded-2xl border border-white/[.07] bg-white/[.025] px-4 py-3.5"
        >
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-emerald-300/10 text-emerald-300">
            <Icon size={17} />
          </span>
          <span className="text-sm font-semibold leading-5 text-white/70">{label}</span>
        </div>
      ))}
    </div>
  )
}
