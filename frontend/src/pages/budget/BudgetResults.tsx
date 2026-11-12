import { useCallback, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import {
  ArrowRight,
  BarChart3,
  CheckCircle2,
  ChevronRight,
  FolderOpen,
  Share2,
  Target,
  Trophy,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import type { BudgetAnsweredQuestion, BudgetGameSession } from '@/api/budget'
import { GuestProgressCta } from '@/components/game/GuestProgressCta'
import { StreakBonusChip } from '@/components/game/StreakBonusChip'
import { useGuestProgress } from '@/hooks/useGameQueries'
import { useTranslation } from '@/i18n'
import { BudgetRevealStage } from './BudgetRevealStage'
import { BudgetShareDialog } from './BudgetShareDialog'
import { BudgetSkyline } from './BudgetSkyline'
import { formatAnswer, formatNumber, questionProject } from './budgetFormatters'
import { budgetImageVariantUrl } from './budgetImageUrls'

interface BudgetResultsProps {
  session: BudgetGameSession
}

export function BudgetResults({ session }: BudgetResultsProps) {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { data: guestProgress } = useGuestProgress(session.guest, true)
  const [shareOpen, setShareOpen] = useState(false)
  const [selectedAnswer, setSelectedAnswer] = useState<BudgetAnsweredQuestion | null>(null)
  const resultsScrollPosition = useRef(0)
  const openReview = useCallback((answer: BudgetAnsweredQuestion) => {
    resultsScrollPosition.current = window.scrollY
    setSelectedAnswer(answer)
    window.requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
  }, [])
  const closeReview = useCallback(() => {
    setSelectedAnswer(null)
    window.requestAnimationFrame(() => window.scrollTo({
      top: resultsScrollPosition.current,
      behavior: 'instant',
    }))
  }, [])
  const averageAccuracy = session.answeredQuestions.length === 0
    ? 0
    : session.answeredQuestions.reduce((sum, answer) => sum + answer.accuracyPercent, 0)
      / session.answeredQuestions.length
  const bestAccuracy = Math.max(0, ...session.answeredQuestions.map(answer => answer.accuracyPercent))
  const fullXpAnswers = session.answeredQuestions.filter(answer => answer.xpPercent >= 100).length

  if (selectedAnswer) {
    return (
      <motion.div
        key={`budget-answer-review-${selectedAnswer.question.variantId}`}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        className="mx-auto w-full max-w-[1500px]"
      >
        <BudgetRevealStage
          answer={selectedAnswer}
          completed
          reviewMode
          fullXpThresholdPercent={session.fullXpThresholdPercent}
          onContinue={closeReview}
        />
      </motion.div>
    )
  }

  return (
    <motion.div
      key="budget-results"
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      className="mx-auto w-full max-w-6xl space-y-5 sm:space-y-6"
    >
      <section className="relative overflow-hidden rounded-[2rem] border border-emerald-300/15 bg-[radial-gradient(circle_at_82%_14%,rgba(250,204,21,.10),transparent_42%),linear-gradient(145deg,rgba(12,31,25,.98),rgba(8,9,16,.98)_52%,rgba(16,10,25,.98))] shadow-[0_34px_120px_rgba(0,0,0,.38)]">
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.018)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.018)_1px,transparent_1px)] bg-[size:30px_30px]" />
        <div className="relative grid gap-3 p-5 sm:p-7 lg:grid-cols-[minmax(0,.82fr)_minmax(420px,1.18fr)] lg:items-end lg:p-9">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full border border-amber-300/15 bg-amber-300/[.07] px-3 py-1.5 text-[10px] font-black uppercase tracking-[.16em] text-amber-100/65">
              <Trophy size={13} />
              {t.budget.session.completedTitle}
            </div>
            <p className="mt-5 text-[10px] font-bold uppercase tracking-[.18em] text-white/30">{t.budget.session.xpEarned}</p>
            <div className="mt-1 flex flex-wrap items-end gap-x-4 gap-y-2">
              <p className="text-6xl font-black tracking-[-.065em] text-emerald-200 sm:text-7xl">
                {session.totalXpEarned}
                <span className="ml-2 text-2xl tracking-[-.02em] text-white/24">/ {session.maximumXp}</span>
              </p>
              <StreakBonusChip bonusXp={session.streakBonusXp} layout="stacked" className="mb-1.5" />
            </div>
            <p className="mt-3 max-w-md text-sm leading-6 text-white/42">{t.budget.session.completedBody}</p>

            <div className="mt-6 grid grid-cols-3 gap-2">
              <ResultMetric label={t.budget.session.averageAccuracy} value={`${formatNumber(averageAccuracy, 1)}%`} />
              <ResultMetric label={t.budget.session.bestEstimate} value={`${formatNumber(bestAccuracy, 1)}%`} />
              <ResultMetric label={t.budget.session.perfectAnswers} value={`${fullXpAnswers}/${session.questionCount}`} />
            </div>

            <div className="mt-6 flex flex-col gap-2 sm:flex-row">
              <button
                type="button"
                onClick={() => setShareOpen(true)}
                className="inline-flex min-h-[52px] flex-1 items-center justify-center gap-2 rounded-2xl bg-emerald-300 px-5 text-sm font-black text-emerald-950 shadow-[0_16px_36px_rgba(52,211,153,.16)] transition hover:-translate-y-0.5 hover:bg-emerald-200"
              >
                <Share2 size={17} />
                {t.budget.session.shareResult}
              </button>
              <button
                type="button"
                onClick={() => navigate('/leaderboard')}
                className="inline-flex min-h-[52px] items-center justify-center gap-2 rounded-2xl border border-white/[.09] bg-white/[.045] px-5 text-sm font-bold text-white/65 transition hover:bg-white/[.08] hover:text-white"
              >
                <BarChart3 size={17} />
                {t.nav.leaderboard}
              </button>
            </div>
          </div>

          <div className="rounded-[1.6rem] border border-white/[.06] bg-black/15 px-3 pb-3 pt-7 sm:px-6 sm:pb-5">
            <p className="text-center text-[9px] font-black uppercase tracking-[.18em] text-amber-200/45">{t.budget.session.costSkyline}</p>
            <BudgetSkyline answers={session.answeredQuestions} challengeDate={session.challengeDate} />
          </div>
        </div>
      </section>

      {session.guest ? (
        <GuestProgressCta
          context="budget"
          fallbackTotalXp={Math.max(0, session.xpBefore) + session.totalXpEarned}
          progress={guestProgress ?? null}
        />
      ) : null}

      <section className="rounded-[2rem] border border-white/[.08] bg-[#0a0c14]/90 p-4 shadow-[0_26px_90px_rgba(0,0,0,.24)] sm:p-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-[10px] font-black uppercase tracking-[.17em] text-emerald-200/45">{t.budget.session.answerSummary}</p>
            <h2 className="mt-2 text-2xl font-black tracking-[-.035em]">{t.budget.session.reviewAnswers}</h2>
            <p className="mt-1 text-sm text-white/38">{t.budget.session.tapToReview}</p>
          </div>
          <button
            type="button"
            onClick={() => navigate('/projects')}
            className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-white/[.08] bg-white/[.035] px-4 text-xs font-bold text-white/55 transition hover:bg-white/[.07] hover:text-white"
          >
            <FolderOpen size={15} />
            {t.budget.session.exploreProjects}
            <ArrowRight size={14} />
          </button>
        </div>

        <div className="mt-5 grid gap-3">
          {session.answeredQuestions.map((answer, index) => (
            <AnswerReviewCard
              key={answer.question.variantId}
              answer={answer}
              index={index}
              onOpen={() => openReview(answer)}
            />
          ))}
        </div>
      </section>

      {shareOpen ? <BudgetShareDialog session={session} onClose={() => setShareOpen(false)} /> : null}
    </motion.div>
  )
}

function AnswerReviewCard({
  answer,
  index,
  onOpen,
}: {
  answer: BudgetAnsweredQuestion
  index: number
  onOpen: () => void
}) {
  const { t } = useTranslation()
  const project = questionProject(answer.question)
  const image = answer.question.primaryImage
  const userAnswer = answer.question.type === 'HIGHER_LOWER'
    ? choiceProjectName(answer, answer.guessedChoice)
    : formatAnswer(answer.guessedValue, answer.correctUnit, true, t.budget.session.answerUnitMonths)
  const correctAnswer = answer.question.type === 'HIGHER_LOWER'
    ? choiceProjectName(answer, answer.correctChoice)
    : formatAnswer(answer.correctValue, answer.correctUnit, true, t.budget.session.answerUnitMonths)
  const strong = answer.xpPercent >= 100

  return (
    <button
      type="button"
      onClick={onOpen}
      className="group grid w-full gap-3 rounded-[1.35rem] border border-white/[.07] bg-white/[.025] p-3 text-left transition hover:-translate-y-0.5 hover:border-white/[.14] hover:bg-white/[.045] sm:grid-cols-[76px_minmax(0,1fr)_auto] sm:items-center sm:p-4"
    >
      <div className="relative hidden aspect-square overflow-hidden rounded-xl bg-white/[.04] sm:block">
        {image ? (
          <img
                src={budgetImageVariantUrl(image.url, 480)}
            alt=""
            loading="lazy"
            decoding="async"
            className="h-full w-full object-cover"
          />
        ) : (
          <span className="flex h-full items-center justify-center text-white/18"><Target size={22} /></span>
        )}
        <span className="absolute left-1.5 top-1.5 rounded-md bg-black/65 px-1.5 py-1 text-[9px] font-black text-white/70">{index + 1}</span>
      </div>
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          {strong ? <CheckCircle2 size={14} className="shrink-0 text-emerald-300" /> : <Target size={14} className="shrink-0 text-amber-300" />}
          <span className="truncate text-sm font-black text-white/82">{project?.name ?? questionTypeLabel(answer, t)}</span>
        </div>
        <p className="mt-1 text-[10px] font-bold uppercase tracking-[.12em] text-white/28">{questionTypeLabel(answer, t)}</p>
        <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-white/42">
          <span>{t.budget.yours}: <b className="text-white/68">{userAnswer}</b></span>
          <span>{t.budget.session.correctAnswer}: <b className="text-white/68">{correctAnswer}</b></span>
        </div>
      </div>
      <div className="flex items-center justify-between gap-4 border-t border-white/[.06] pt-3 sm:border-0 sm:pt-0">
        <div className="text-right">
          <p className={`text-lg font-black ${answer.accuracyPercent <= 0 ? 'text-red-300' : 'text-emerald-300'}`}>
            {formatNumber(answer.accuracyPercent, 1)}%
          </p>
          <p className="text-[10px] font-bold uppercase tracking-[.1em] text-white/30">+{answer.xpEarned} XP</p>
        </div>
        <ChevronRight size={18} className="text-white/22 transition group-hover:translate-x-0.5 group-hover:text-white/55" />
      </div>
    </button>
  )
}

function ResultMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-white/[.07] bg-white/[.035] px-3 py-3">
      <p className="truncate text-[8px] font-bold uppercase tracking-[.11em] text-white/30">{label}</p>
      <p className="mt-1 text-lg font-black tabular-nums text-white/82">{value}</p>
    </div>
  )
}

function questionTypeLabel(answer: BudgetAnsweredQuestion, t: ReturnType<typeof useTranslation>['t']) {
  switch (answer.question.type) {
    case 'ORIGINAL_BUDGET': return t.budget.session.originalBudget
    case 'BUDGET_OVERRUN': return t.budget.session.budgetOverrun
    case 'HOW_LATE': return t.budget.session.howLate
    case 'HIGHER_LOWER': return t.budget.session.higherLower
    case 'COST_PER_UNIT': return t.budget.session.costPerUnit
    case 'CURRENT_ESTIMATE': return t.budget.session.currentEstimate
    case 'SPENT_SO_FAR': return t.budget.session.spentSoFar
    default: return t.budget.session.howMuch
  }
}

function choiceProjectName(
  answer: BudgetAnsweredQuestion,
  choice: BudgetAnsweredQuestion['guessedChoice'],
) {
  if (choice === 'PRIMARY') return answer.question.prompt.primaryProject?.name ?? 'A'
  if (choice === 'COMPARISON') return answer.question.prompt.comparisonProject?.name ?? 'B'
  return '—'
}
