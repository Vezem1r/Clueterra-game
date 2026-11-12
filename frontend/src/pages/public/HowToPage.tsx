import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { HowToStep } from '@/components/game/HowToStep'
import { Header } from '@/components/layout/Header'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { useTranslation } from '@/i18n'
import { ClueterraCluesDemo } from './howto/ClueterraCluesDemo'
import { ClueterraGuessDemo } from './howto/ClueterraGuessDemo'
import { ClueterraModesDemo } from './howto/ClueterraModesDemo'
import { ClueterraResultsDemo } from './howto/ClueterraResultsDemo'
import { useClueterraDemoRound } from './howto/useClueterraDemoRound'
import { CLUETERRA_SHARE_IMAGE } from '@/constants/site'

export function HowToPage() {
  const { t } = useTranslation()
  const round = useClueterraDemoRound(t)

  useSeoMeta({
    title: t.howToPage.seoTitle,
    description: t.howToPage.seoDescription,
    canonicalPath: '/how-to',
    image: CLUETERRA_SHARE_IMAGE,
    keywords: 'how to play Clueterra, geography game guide, country guessing game rules',
  })

  const questions = [
    { question: t.guide.faq1Q, answer: t.guide.faq1A },
    { question: t.guide.faq2Q, answer: t.guide.faq2A },
    { question: t.guide.faq3Q, answer: t.guide.faq3A },
    { question: t.guide.faq4Q, answer: t.guide.faq4A },
  ]

  return (
    <div className="min-h-[100dvh] overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="light" />
      <Header />

      <main className="relative z-10 px-4 pb-24 pt-8 sm:px-6 sm:pt-12 md:pb-28 lg:px-10">
        <div className="mx-auto max-w-6xl">
          <div className="max-w-3xl">
            <p className="text-xs font-semibold uppercase tracking-[.24em] text-sky-300/70">{t.howToPage.eyebrow}</p>
            <h1 className="mt-4 text-balance text-4xl font-bold tracking-[-.035em] text-white sm:text-5xl">
              {t.howToPage.title}
            </h1>
            <p className="mt-5 text-pretty text-base leading-8 text-white/56 sm:text-lg">{t.howToPage.intro}</p>
            <div className="mt-7 flex flex-col gap-3 sm:flex-row">
              <Link
                to="/"
                className="inline-flex items-center justify-center gap-2 rounded-2xl bg-white px-5 py-3 text-sm font-semibold text-black transition-colors hover:bg-white/90"
              >
                {t.howToPage.playCta}
                <ArrowRight size={15} />
              </Link>
              <Link
                to="/budget/how-to"
                className="inline-flex items-center justify-center rounded-2xl border border-white/[.1] bg-white/[.04] px-5 py-3 text-sm font-medium text-white/70 transition-colors hover:bg-white/[.08] hover:text-white"
              >
                {t.howToPage.budgetCta}
              </Link>
            </div>
          </div>

          <div className="mt-14 space-y-16 sm:space-y-20">
            <HowToStep index={1} title={t.howToPage.modeTitle} body={t.howToPage.modeBody} tryLabel={t.howToPage.tryLabel} tryText={t.howToPage.modeTry}>
              <ClueterraModesDemo />
            </HowToStep>
            <HowToStep index={2} title={t.howToPage.cluesTitle} body={t.howToPage.cluesBody} tryLabel={t.howToPage.tryLabel} tryText={t.howToPage.cluesTry}>
              <ClueterraCluesDemo round={round} />
            </HowToStep>
            <HowToStep index={3} title={t.howToPage.guessTitle} body={t.howToPage.guessBody} tryLabel={t.howToPage.tryLabel} tryText={t.howToPage.guessTry}>
              <ClueterraGuessDemo round={round} />
            </HowToStep>
            <HowToStep index={4} title={t.howToPage.resultsTitle} body={t.howToPage.resultsBody} tryLabel={t.howToPage.tryLabel} tryText={t.howToPage.resultsTry}>
              <ClueterraResultsDemo />
            </HowToStep>
          </div>

          <section className="mt-20" aria-labelledby="game-faq-title">
            <h2 id="game-faq-title" className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">
              {t.howToPage.faqLabel}
            </h2>
            <div className="mt-5 grid gap-3">
              {questions.map(({ answer, question }) => (
                <details
                  key={question}
                  className="group rounded-2xl border border-white/[.08] bg-white/[.025] px-5 py-4 open:bg-white/[.04] sm:px-6"
                >
                  <summary className="cursor-pointer list-none pr-6 text-sm font-medium text-white/78 marker:hidden">
                    {question}
                  </summary>
                  <p className="mt-3 max-w-4xl text-sm leading-7 text-white/46">{answer}</p>
                </details>
              ))}
            </div>
          </section>
        </div>
      </main>

      <HomeLegalStrip />
    </div>
  )
}
