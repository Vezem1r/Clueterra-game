import { ArrowUpRight, ChevronDown, Compass, Database, Waves } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { TranslationKeys } from '@/i18n'
import { RIVER_DATA_SOURCES_PATH } from './riverGameConstants'

export function RiverGuide({ copy }: { copy: TranslationKeys['riverGame']['guide'] }) {
  return <div className="space-y-12 sm:space-y-16">
    <section aria-labelledby="river-how-title">
      <div className="flex items-center gap-3">
        <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-2xl bg-river-glacier/10 text-river-glacier ring-1 ring-river-glacier/20"><Compass size={20} /></span>
        <h2 id="river-how-title" className="font-display text-2xl font-semibold tracking-tight text-white sm:text-3xl">{copy.stepsTitle}</h2>
      </div>
      <ol className="mt-6 grid gap-3 md:grid-cols-3">
        {copy.howSteps.map((step, index) => <li key={step} className="rounded-[1.4rem] border border-river-glacier/10 bg-river-channel/45 p-5 sm:p-6">
          <span aria-hidden="true" className="font-display text-3xl font-semibold tabular-nums text-river-glacier/70">0{index + 1}</span>
          <p className="mt-4 text-[15px] leading-7 text-river-mist/80">{step}</p>
        </li>)}
      </ol>
    </section>

    <div className="grid gap-4 md:grid-cols-2">
      <section aria-labelledby="river-catalog-title" className="rounded-[1.4rem] border border-river-glacier/10 bg-river-deep/70 p-6 sm:p-7">
        <Waves size={20} aria-hidden="true" className="text-river-glacier" />
        <h2 id="river-catalog-title" className="mt-5 font-display text-xl font-semibold text-white sm:text-2xl">{copy.riversTitle}</h2>
        <p className="mt-3 text-[15px] leading-7 text-river-mist/70">{copy.riversBody}</p>
      </section>
      <section aria-labelledby="river-sources-title" className="rounded-[1.4rem] border border-river-glacier/10 bg-river-deep/70 p-6 sm:p-7">
        <Database size={20} aria-hidden="true" className="text-river-glacier" />
        <h2 id="river-sources-title" className="mt-5 font-display text-xl font-semibold text-white sm:text-2xl">{copy.sourcesTitle}</h2>
        <p className="mt-3 text-[15px] leading-7 text-river-mist/70">{copy.sourcesBody}</p>
        <Link to={RIVER_DATA_SOURCES_PATH} className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-river-glacier-strong hover:underline">
          {copy.sourcesLink} <ArrowUpRight size={15} aria-hidden="true" />
        </Link>
      </section>
    </div>

    <section aria-labelledby="river-faq-title">
      <h2 id="river-faq-title" className="font-display text-2xl font-semibold tracking-tight text-white sm:text-3xl">{copy.faqTitle}</h2>
      <div className="mt-5 grid gap-2.5">
        {copy.faq.map(item => <details key={item.question} className="group rounded-2xl border border-river-glacier/10 bg-river-channel/35 px-5 py-4 open:bg-river-channel/60 sm:px-6">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[15px] font-medium text-river-mist marker:hidden">
            {item.question}<ChevronDown size={17} aria-hidden="true" className="shrink-0 text-river-glacier/70 transition-transform group-open:rotate-180" />
          </summary>
          <p className="mt-3 max-w-4xl border-t border-river-glacier/10 pt-3 text-sm leading-7 text-river-mist/70">{item.answer}</p>
        </details>)}
      </div>
    </section>
  </div>
}
