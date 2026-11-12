import { useMemo } from 'react'
import { ArrowLeft, ArrowRight, ArrowUpRight, ChevronDown, Database, Globe2, Map as MapIcon, ScanSearch } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Header } from '@/components/layout/Header'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { cn } from '@/utils/cn'
import { SilhouetteGuideShot, SilhouetteGuideStage, type SilhouetteGuideImage } from './silhouettes/SilhouetteGuideStage'
import {
  SILHOUETTE_CANONICAL_PATH,
  SILHOUETTE_CLUES_EXAMPLE,
  SILHOUETTE_GLOBE_EXAMPLE,
  SILHOUETTE_GUIDE_PATH,
  SILHOUETTE_NAME_EXAMPLE,
  SILHOUETTE_ROUND_EXAMPLE,
  SILHOUETTE_SHAPE_EXAMPLE,
  SILHOUETTE_SHARE_IMAGE,
} from './silhouettes/silhouetteGameConstants'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

const DATA_SOURCES_PATH = '/data-sources'
const STEP_NUMBER_DIGITS = 2
const STEP_ALTERNATION = 2

interface GuideStepVisual {
  image: SilhouetteGuideImage
  alt: string
  cover?: boolean
}

export function CountrySilhouettesHowToPage() {
  const { t } = useTranslation()
  const copy = t.silhouetteGame
  const localize = useLocalizedPath()
  const guide = copy.guide
  const visuals: GuideStepVisual[] = [
    { image: SILHOUETTE_SHAPE_EXAMPLE, alt: guide.shapeAlt },
    { image: SILHOUETTE_NAME_EXAMPLE, alt: guide.nameAlt },
    { image: SILHOUETTE_CLUES_EXAMPLE, alt: guide.cluesAlt },
    { image: SILHOUETTE_GLOBE_EXAMPLE, alt: guide.globeAlt, cover: true },
  ]
  const sections = [
    { icon: Globe2, title: guide.countriesTitle, body: guide.countriesBody },
    { icon: MapIcon, title: guide.bordersTitle, body: guide.bordersBody },
    { icon: Database, title: guide.sourcesTitle, body: guide.sourcesBody, link: true },
  ]
  const structuredData = useMemo(() => ({
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: guide.faq.map(item => ({
      '@type': 'Question',
      name: item.question,
      acceptedAnswer: { '@type': 'Answer', text: item.answer },
    })),
  }), [guide.faq])

  useSeoMeta({
    title: `${guide.howTitle} | Clueterra`,
    description: copy.leadDetail,
    canonicalPath: SILHOUETTE_GUIDE_PATH,
    image: SILHOUETTE_SHARE_IMAGE,
    imageAlt: copy.shapeLabel,
    structuredData,
  })

  return <div className="relative isolate min-h-[100dvh] overflow-x-hidden bg-[#04070d] text-white">
    <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[42rem] bg-[radial-gradient(ellipse_at_70%_0%,rgba(103,232,249,.14),transparent_60%)]" />
    <Header />
    <main className="mx-auto max-w-[1200px] px-4 pb-28 pt-8 sm:px-8 sm:pt-12 lg:px-12">
      <section aria-labelledby="silhouette-how-title" className="grid items-center gap-8 lg:grid-cols-[minmax(0,.9fr)_minmax(0,1.1fr)] lg:gap-12">
        <div>
          <Link to={localize(SILHOUETTE_CANONICAL_PATH)} className="inline-flex items-center gap-2 text-sm font-medium text-cyan-100/70 hover:text-cyan-100">
            <ArrowLeft size={15} aria-hidden="true" /> {copy.title}
          </Link>
          <h1 id="silhouette-how-title" className="mt-7 font-display text-4xl font-semibold tracking-[-.045em] text-white sm:text-5xl">{guide.howTitle}</h1>
          <p className="mt-4 max-w-[34rem] text-[15px] leading-7 text-white/70 sm:text-base">{copy.leadDetail}</p>
          <Link to={localize(SILHOUETTE_CANONICAL_PATH)} className="mt-7 inline-flex items-center gap-2 rounded-2xl bg-gradient-to-r from-cyan-300 to-teal-200 px-5 py-3 text-sm font-semibold text-[#03141c] shadow-[0_12px_40px_rgba(103,232,249,.25)] transition hover:brightness-110">
            {copy.start} <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>
        <div className="relative">
          <div aria-hidden="true" className="absolute -inset-6 -z-10 rounded-[3rem] bg-cyan-300/10 blur-3xl" />
          <img src={SILHOUETTE_ROUND_EXAMPLE.src} width={SILHOUETTE_ROUND_EXAMPLE.width} height={SILHOUETTE_ROUND_EXAMPLE.height}
            alt={guide.roundAlt} fetchPriority="high"
            className="h-auto w-full rounded-[1.75rem] border border-cyan-200/15 bg-[#060a12] shadow-[0_30px_80px_rgba(0,0,0,.55)]" />
        </div>
      </section>

      <section aria-labelledby="silhouette-steps" className="mt-20 sm:mt-28">
        <div className="flex items-center gap-3">
          <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-2xl bg-cyan-300/10 text-cyan-200 ring-1 ring-cyan-200/20"><ScanSearch size={20} /></span>
          <h2 id="silhouette-steps" className="font-display text-2xl font-semibold tracking-tight text-white sm:text-3xl">{guide.stepsTitle}</h2>
        </div>
        <ol className="mt-10 grid gap-14 sm:gap-20">
          {guide.howSteps.map((step, index) => {
            const visual = visuals[index]
            return <li key={step} className="grid items-center gap-6 lg:grid-cols-2 lg:gap-14">
              <div className={cn(index % STEP_ALTERNATION === 1 && 'lg:order-2')}>
                <span aria-hidden="true" className="inline-flex size-12 items-center justify-center rounded-2xl bg-cyan-300/10 font-display text-lg font-semibold tabular-nums text-cyan-200 ring-1 ring-cyan-200/20">
                  {String(index + 1).padStart(STEP_NUMBER_DIGITS, '0')}
                </span>
                <h3 className="mt-5 font-display text-2xl font-semibold tracking-tight text-white sm:text-[1.75rem]">{guide.howStepTitles[index]}</h3>
                <p className="mt-3 max-w-[34rem] text-[15px] leading-7 text-white/70">{step}</p>
              </div>
              <SilhouetteGuideStage className={cn('aspect-[4/3]', !visual.cover && 'p-6 sm:p-10')}>
                <SilhouetteGuideShot image={visual.image} alt={visual.alt} cover={visual.cover} />
              </SilhouetteGuideStage>
            </li>
          })}
        </ol>
      </section>

      <div className="mt-20 grid gap-4 sm:mt-28 lg:grid-cols-3">
        {sections.map(section => <section key={section.title} className="rounded-[1.4rem] border border-cyan-200/10 bg-[#081120]/80 p-6 sm:p-7">
          <section.icon size={20} aria-hidden="true" className="text-cyan-200" />
          <h2 className="mt-5 font-display text-xl font-semibold text-white">{section.title}</h2>
          <p className="mt-3 text-[15px] leading-7 text-white/65">{section.body}</p>
          {section.link && <Link to={DATA_SOURCES_PATH} className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-cyan-100 hover:underline">
            {guide.sourcesLink} <ArrowUpRight size={15} aria-hidden="true" />
          </Link>}
        </section>)}
      </div>

      <section aria-labelledby="silhouette-faq" className="mt-16 sm:mt-20">
        <h2 id="silhouette-faq" className="font-display text-2xl font-semibold tracking-tight text-white sm:text-3xl">{guide.faqTitle}</h2>
        <div className="mt-5 grid gap-2.5">
          {guide.faq.map(item => <details key={item.question} className="group rounded-2xl border border-cyan-200/10 bg-[#0a1626]/45 px-5 py-4 open:bg-[#0a1626]/75 sm:px-6">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[15px] font-medium text-white/90 marker:hidden">
              {item.question}<ChevronDown size={17} aria-hidden="true" className="shrink-0 text-cyan-200/70 transition-transform group-open:rotate-180" />
            </summary>
            <p className="mt-3 max-w-4xl border-t border-cyan-200/10 pt-3 text-sm leading-7 text-white/65">{item.answer}</p>
          </details>)}
        </div>
      </section>
    </main>
    <HomeLegalStrip guideMode="silhouette" />
  </div>
}
