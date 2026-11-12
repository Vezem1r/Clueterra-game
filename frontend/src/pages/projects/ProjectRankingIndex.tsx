import { useState } from 'react'
import { ArrowUpRight, ChevronDown, Globe2, Layers3, Search, Trophy } from 'lucide-react'
import { Link } from 'react-router-dom'

import { categoryHubPath, PROJECT_HUB_ROOT_PATH } from '@/features/projects/projectHubs'
import { PROJECT_CATEGORIES, type ProjectHubLink } from '@/features/projects/projectTypes'
import { useTranslation } from '@/i18n'

interface ProjectRankingIndexProps {
  countryHubs: ProjectHubLink[]
}

export function ProjectRankingIndex({ countryHubs }: ProjectRankingIndexProps) {
  const { t } = useTranslation()
  const copy = t.projectsPage
  const [countryQuery, setCountryQuery] = useState('')
  const matchingCountries = countryHubs.filter((hub) =>
    (hub.countryName ?? hub.countryCode ?? '').toLocaleLowerCase().includes(countryQuery.trim().toLocaleLowerCase()),
  )

  return (
    <nav aria-label={copy.rankings} className="border-b border-white/[.08] py-5 sm:py-6">
      <div className="mb-3 flex items-center justify-between gap-4">
        <h2 className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[.18em] text-amber-100/70">
          <Trophy size={15} className="text-amber-200" />
          {copy.rankings}
        </h2>
        <span className="hidden text-xs text-white/30 sm:block">{copy.rankingsHint}</span>
      </div>

      <div className="grid grid-cols-3 gap-2 sm:gap-3">
        <Link
          to={PROJECT_HUB_ROOT_PATH}
          className="group relative flex min-h-[62px] items-center gap-2 overflow-hidden rounded-[1.15rem] border border-amber-200/22 bg-[linear-gradient(115deg,rgba(251,191,36,.12),rgba(251,191,36,.025)_70%)] px-2.5 py-2 transition hover:-translate-y-0.5 hover:border-amber-200/40 hover:bg-amber-200/[.1] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-200 sm:min-h-[82px] sm:gap-3 sm:px-4 sm:py-3"
        >
          <span className="hidden h-10 w-10 shrink-0 place-items-center rounded-xl border border-amber-200/16 bg-amber-200/[.08] text-amber-100 sm:grid sm:h-11 sm:w-11">
            <Trophy size={19} />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-[11px] font-bold leading-tight text-white sm:text-[15px]">{copy.mostExpensive}</span>
            <span className="mt-1 hidden text-[11px] text-amber-100/50 sm:block">{copy.mostExpensiveHint}</span>
          </span>
          <ArrowUpRight size={17} className="hidden shrink-0 text-amber-100/55 transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-amber-100 sm:block" />
        </Link>

        <details className="group rounded-[1.15rem] border border-white/[.09] bg-white/[.025] transition-colors open:col-span-3 open:border-amber-200/22 open:bg-[#101115] hover:border-white/[.16]">
          <summary className="flex min-h-[62px] cursor-pointer list-none items-center gap-1.5 px-2.5 py-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-200 sm:min-h-[82px] sm:gap-3 sm:px-4 sm:py-2.5 [&::-webkit-details-marker]:hidden">
            <span className="hidden h-9 w-9 shrink-0 place-items-center rounded-xl border border-sky-200/12 bg-sky-200/[.055] text-sky-100/75 sm:grid sm:h-11 sm:w-11">
              <Layers3 size={18} />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[11px] font-semibold leading-tight text-white/85 sm:text-[15px]">{copy.byType}</span>
              <span className="mt-1 block text-[9px] text-white/36 sm:text-[11px]">{copy.categoriesCount.replace('{count}', String(PROJECT_CATEGORIES.length))}</span>
            </span>
            <ChevronDown size={13} className="shrink-0 text-white/40 transition-transform duration-200 group-open:rotate-180 sm:size-[15px]" />
          </summary>
          <div className="grid grid-cols-2 gap-2 border-t border-white/[.07] p-3 sm:grid-cols-3 sm:p-4 lg:grid-cols-4">
            {PROJECT_CATEGORIES.map((category) => (
              <Link
                key={category}
                to={categoryHubPath(category)}
                className="group/link flex min-w-0 items-center justify-between gap-2 rounded-xl border border-white/[.06] bg-white/[.025] px-3 py-2.5 text-xs font-medium text-white/65 transition hover:border-amber-200/20 hover:bg-amber-200/[.055] hover:text-amber-100 focus-visible:outline-2 focus-visible:outline-amber-200"
              >
                <span className="min-w-0">{t.budget.categories[category.toLowerCase() as Lowercase<typeof category>]}</span>
                <ArrowUpRight size={13} className="shrink-0 text-white/25 transition group-hover/link:text-amber-100/70" />
              </Link>
            ))}
          </div>
        </details>

        {countryHubs.length > 0 && (
          <details
            className="group rounded-[1.15rem] border border-white/[.09] bg-white/[.025] transition-colors open:col-span-3 open:border-emerald-200/20 open:bg-[#0d1416] hover:border-white/[.16]"
            onToggle={(event) => { if (!event.currentTarget.open) setCountryQuery('') }}
          >
            <summary className="flex min-h-[62px] cursor-pointer list-none items-center gap-1.5 px-2.5 py-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-200 sm:min-h-[82px] sm:gap-3 sm:px-4 sm:py-2.5 [&::-webkit-details-marker]:hidden">
              <span className="hidden h-9 w-9 shrink-0 place-items-center rounded-xl border border-emerald-200/12 bg-emerald-200/[.055] text-emerald-100/75 sm:grid sm:h-11 sm:w-11">
                <Globe2 size={18} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-[11px] font-semibold leading-tight text-white/85 sm:text-[15px]">{copy.byCountry}</span>
                <span className="mt-1 block text-[9px] text-white/36 sm:text-[11px]">{copy.countriesCount.replace('{count}', String(countryHubs.length))}</span>
              </span>
              <ChevronDown size={13} className="shrink-0 text-white/40 transition-transform duration-200 group-open:rotate-180 sm:size-[15px]" />
            </summary>
            <div className="border-t border-white/[.07] p-3 sm:p-4">
              <label className="relative block sm:max-w-xs">
                <span className="sr-only">{copy.findCountryRanking}</span>
                <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/30" />
                <input
                  type="search"
                  value={countryQuery}
                  onChange={(event) => setCountryQuery(event.target.value)}
                  placeholder={copy.findCountry}
                  className="h-10 w-full rounded-xl border border-white/[.08] bg-black/20 pl-9 pr-3 text-xs text-white outline-none transition placeholder:text-white/30 focus:border-emerald-200/35 focus:ring-2 focus:ring-emerald-200/[.07]"
                />
              </label>
              {matchingCountries.length > 0 ? (
                <div className="mt-3 grid max-h-64 grid-cols-2 gap-2 overflow-y-auto pr-1 sm:grid-cols-3 lg:grid-cols-4">
                  {matchingCountries.map((hub) => (
                    <Link
                      key={hub.path}
                      to={hub.path}
                      className="group/link flex min-w-0 items-center justify-between gap-2 rounded-xl border border-white/[.06] bg-white/[.025] px-3 py-2.5 text-xs font-medium text-white/65 transition hover:border-emerald-200/20 hover:bg-emerald-200/[.055] hover:text-emerald-100 focus-visible:outline-2 focus-visible:outline-emerald-200"
                    >
                      <span className="min-w-0 truncate">{hub.countryName ?? hub.countryCode}</span>
                      <span className="shrink-0 tabular-nums text-[10px] text-white/30 transition group-hover/link:text-emerald-100/55">{hub.projectCount}</span>
                    </Link>
                  ))}
                </div>
              ) : (
                <p className="px-1 py-5 text-xs text-white/40">{copy.noCountryMatch}</p>
              )}
            </div>
          </details>
        )}
      </div>
    </nav>
  )
}
