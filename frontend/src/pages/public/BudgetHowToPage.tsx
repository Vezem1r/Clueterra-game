import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'

import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { HowToStep } from '@/components/game/HowToStep'
import { Header } from '@/components/layout/Header'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { BudgetAnswerPanel, BudgetHints } from '@/pages/budget/BudgetAnswerPanel'
import { BudgetProjectStage } from '@/pages/budget/BudgetProjectStage'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { BudgetRoundTypes } from './howto/BudgetRoundTypes'
import { DEMO_FULL_XP_THRESHOLD_PERCENT, useBudgetDemoRound } from './howto/useBudgetDemoRound'
import { BUDGET_SHARE_IMAGE } from '@/constants/site'

export function BudgetHowToPage() {
  const { t } = useTranslation()
  const round = useBudgetDemoRound(t.budgetHowTo.demoCountry)

  useSeoMeta({
    title: t.budgetHowTo.seoTitle,
    description: t.budgetHowTo.seoDescription,
    canonicalPath: '/budget/how-to',
    image: BUDGET_SHARE_IMAGE,
    keywords: 'how to play BudgetGuessr, megaproject cost game, guess the cost game, budget guessing game rules',
  })

  return (
    <div className="min-h-[100dvh] overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="play" />
      <Header />

      <main className="relative z-10 px-4 pb-24 pt-8 sm:px-6 sm:pt-12 md:pb-28 lg:px-10">
        <div className="mx-auto max-w-6xl">
          <div className="max-w-3xl">
            <p className="text-xs font-semibold uppercase tracking-[.24em] text-emerald-300/70">{t.budgetHowTo.eyebrow}</p>
            <h1 className="mt-4 text-balance text-4xl font-bold tracking-[-.035em] text-white sm:text-5xl">
              {t.budgetHowTo.title}
            </h1>
            <p className="mt-5 text-pretty text-base leading-8 text-white/56 sm:text-lg">{t.budgetHowTo.intro}</p>
            <div className="mt-7 flex flex-col gap-3 sm:flex-row">
              <Link
                to="/budget"
                className="inline-flex items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-emerald-300 to-teal-300 px-5 py-3 text-sm font-semibold text-[#06100d] transition hover:brightness-105"
              >
                {t.budgetHowTo.playCta}
                <ArrowRight size={15} />
              </Link>
              <Link
                to="/how-to"
                className="inline-flex items-center justify-center rounded-2xl border border-white/[.1] bg-white/[.04] px-5 py-3 text-sm font-medium text-white/70 transition-colors hover:bg-white/[.08] hover:text-white"
              >
                {t.budgetHowTo.clueterraCta}
              </Link>
            </div>
          </div>

          <div className="mt-14 space-y-16 sm:space-y-20">
            <HowToStep
              index={1}
              accent="emerald"
              title={t.budgetHowTo.projectTitle}
              body={t.budgetHowTo.projectBody}
              tryLabel={t.budgetHowTo.tryLabel}
              tryText={t.budgetHowTo.projectTry}
            >
              <BudgetProjectStage question={round.question} answer={round.answer} nameHeadingLevel="h3" />
            </HowToStep>

            <HowToStep
              index={2}
              accent="emerald"
              wide
              title={t.budgetHowTo.hintsTitle}
              body={t.budgetHowTo.hintsBody}
              tryLabel={t.budgetHowTo.tryLabel}
              tryText={t.budgetHowTo.hintsTry}
            >
              <BudgetHints
                question={round.question}
                revealing={false}
                disabled={Boolean(round.answer)}
                onReveal={round.revealHint}
              />
            </HowToStep>

            <HowToStep
              index={3}
              accent="emerald"
              wide
              title={t.budgetHowTo.guessTitle}
              body={t.budgetHowTo.guessBody}
              tryLabel={t.budgetHowTo.tryLabel}
              tryText={t.budgetHowTo.guessTry}
            >
              <div className={round.answer ? undefined : 'mx-auto max-w-xl'}>
                <BudgetAnswerPanel
                  question={round.question}
                  answer={round.answer}
                  submitting={false}
                  error=""
                  completed={false}
                  fullXpThresholdPercent={DEMO_FULL_XP_THRESHOLD_PERCENT}
                  selectedChoice={null}
                  onSubmitValue={round.submitGuess}
                  onSubmitChoice={async () => undefined}
                  onContinue={round.reset}
                />
              </div>
            </HowToStep>
          </div>

          <section className="mt-20" aria-labelledby="budget-round-types-title">
            <h2 id="budget-round-types-title" className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">
              {t.budgetHowTo.typesTitle}
            </h2>
            <p className="mt-3 max-w-3xl text-[0.95rem] leading-7 text-white/56">{t.budgetHowTo.typesBody}</p>
            <div className="mt-6">
              <BudgetRoundTypes />
            </div>
          </section>
        </div>
      </main>

      <HomeLegalStrip guideMode="budget" />
    </div>
  )
}
