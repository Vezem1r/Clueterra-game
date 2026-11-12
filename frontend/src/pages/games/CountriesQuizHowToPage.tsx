import { useMemo } from 'react'
import { ArrowLeft, ArrowRight, ArrowUpRight, ChevronDown, CircleHelp, Database, Globe2, Route } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { SilhouetteLocale } from '@/api/silhouettes'
import { Header } from '@/components/layout/Header'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { cn } from '@/utils/cn'
import { QuizGuideDemo } from './countries-quiz/QuizGuideDemo'
import { QUIZ_CANONICAL_PATH, QUIZ_GUIDE_PATH, QUIZ_ROUND_EXAMPLE, QUIZ_SHARE_IMAGE } from './countries-quiz/quizGameConstants'
import { SilhouetteGuideStage } from './silhouettes/SilhouetteGuideStage'

const DATA_SOURCES_PATH = '/data-sources'
const STEP_NUMBER_DIGITS = 2
const STEP_ALTERNATION = 2
const SHOT_STEP = 2

export function CountriesQuizHowToPage() {
  const { t, language } = useTranslation()
  const copy = t.quizGame
  const guide = copy.guide
  const localize = useLocalizedPath()
  const locale = language as SilhouetteLocale
  const sections = [
    { icon: Globe2, title: guide.countriesTitle, body: guide.countriesBody },
    { icon: CircleHelp, title: guide.questionsTitle, body: guide.questionsBody },
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
    canonicalPath: QUIZ_GUIDE_PATH,
    image: QUIZ_SHARE_IMAGE,
    imageAlt: copy.globe.label,
    structuredData,
  })

  return <div className="relative isolate min-h-[100dvh] overflow-x-hidden bg-[#05090b] text-white">
    <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[42rem] bg-[radial-gradient(ellipse_at_70%_0%,rgba(253,186,116,.12),transparent_60%)]" />
    <Header />
    <main className="mx-auto max-w-[1200px] px-4 pb-28 pt-8 sm:px-8 sm:pt-12 lg:px-12">
      <section aria-labelledby="quiz-how-title" className="grid items-center gap-8 lg:grid-cols-[minmax(0,.9fr)_minmax(0,1.1fr)] lg:gap-12">
        <div>
          <Link to={localize(QUIZ_CANONICAL_PATH)} className="inline-flex items-center gap-2 text-sm font-medium text-orange-100/70 hover:text-orange-100">
            <ArrowLeft size={15} aria-hidden="true" /> {copy.title}
          </Link>
          <h1 id="quiz-how-title" className="mt-7 font-display text-4xl font-semibold tracking-[-.045em] text-white sm:text-5xl">{guide.howTitle}</h1>
          <p className="mt-4 max-w-[34rem] text-[15px] leading-7 text-white/70 sm:text-base">{copy.leadDetail}</p>
          <Link to={localize(QUIZ_CANONICAL_PATH)} className="mt-7 inline-flex items-center gap-2 rounded-2xl bg-gradient-to-r from-orange-200 to-emerald-200 px-5 py-3 text-sm font-semibold text-[#10140b] shadow-[0_12px_40px_rgba(253,186,116,.25)] transition hover:brightness-110">
            {copy.start} <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>
        <div className="relative">
          <div aria-hidden="true" className="absolute -inset-6 -z-10 rounded-[3rem] bg-orange-300/10 blur-3xl" />
          <img src={QUIZ_ROUND_EXAMPLE.src} width={QUIZ_ROUND_EXAMPLE.width} height={QUIZ_ROUND_EXAMPLE.height}
            alt={guide.roundAlt} fetchPriority="high"
            className="h-auto w-full rounded-[1.75rem] border border-orange-200/15 bg-[#05090b] shadow-[0_30px_80px_rgba(0,0,0,.55)]" />
        </div>
      </section>

      <section aria-labelledby="quiz-steps" className="mt-20 sm:mt-28">
        <div className="flex items-center gap-3">
          <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-2xl bg-orange-200/10 text-orange-200 ring-1 ring-orange-200/20"><Route size={20} /></span>
          <h2 id="quiz-steps" className="font-display text-2xl font-semibold tracking-tight text-white sm:text-3xl">{guide.stepsTitle}</h2>
        </div>
        <ol className="mt-10 grid gap-14 sm:gap-20">
          {guide.howSteps.map((step, index) => <li key={step} className="grid items-center gap-6 lg:grid-cols-2 lg:gap-14">
            <div className={cn(index % STEP_ALTERNATION === 1 && 'lg:order-2')}>
              <span aria-hidden="true" className="inline-flex size-12 items-center justify-center rounded-2xl bg-orange-200/10 font-display text-lg font-semibold tabular-nums text-orange-200 ring-1 ring-orange-200/20">
                {String(index + 1).padStart(STEP_NUMBER_DIGITS, '0')}
              </span>
              <h3 className="mt-5 font-display text-2xl font-semibold tracking-tight text-white sm:text-[1.75rem]">{guide.howStepTitles[index]}</h3>
              <p className="mt-3 max-w-[34rem] text-[15px] leading-7 text-white/70">{step}</p>
            </div>
            <SilhouetteGuideStage className={cn('aspect-[4/3]', index < SHOT_STEP && 'p-6 sm:p-10')}>
              <QuizGuideDemo copy={copy} regions={t.silhouetteGame.regions} language={locale} step={index} />
            </SilhouetteGuideStage>
          </li>)}
        </ol>
      </section>

      <div className="mt-20 grid gap-4 sm:mt-28 lg:grid-cols-3">
        {sections.map(section => <section key={section.title} className="rounded-[1.4rem] border border-orange-200/10 bg-[#120f0a]/80 p-6 sm:p-7">
          <section.icon size={20} aria-hidden="true" className="text-orange-200" />
          <h2 className="mt-5 font-display text-xl font-semibold text-white">{section.title}</h2>
          <p className="mt-3 text-[15px] leading-7 text-white/65">{section.body}</p>
          {section.link && <Link to={localize(DATA_SOURCES_PATH)} className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-orange-100 hover:underline">
            {guide.sourcesLink} <ArrowUpRight size={15} aria-hidden="true" />
          </Link>}
        </section>)}
      </div>

      <section aria-labelledby="quiz-faq" className="mt-16 sm:mt-20">
        <h2 id="quiz-faq" className="font-display text-2xl font-semibold tracking-tight text-white sm:text-3xl">{guide.faqTitle}</h2>
        <div className="mt-5 grid gap-2.5">
          {guide.faq.map(item => <details key={item.question} className="group rounded-2xl border border-orange-200/10 bg-[#16110a]/45 px-5 py-4 open:bg-[#16110a]/75 sm:px-6">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[15px] font-medium text-white/90 marker:hidden">
              {item.question}<ChevronDown size={17} aria-hidden="true" className="shrink-0 text-orange-200/70 transition-transform group-open:rotate-180" />
            </summary>
            <p className="mt-3 max-w-4xl border-t border-orange-200/10 pt-3 text-sm leading-7 text-white/65">{item.answer}</p>
          </details>)}
        </div>
      </section>
    </main>
    <HomeLegalStrip guideMode="quiz" />
  </div>
}
