import { useCallback, useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowRight, Banknote, Landmark, LoaderCircle, RotateCcw, Scale, SlidersHorizontal, Sparkles, Trophy } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'

import {
  budgetApi,
  type BudgetAnswerChoice,
  type BudgetAnswerResult,
  type BudgetDifficulty,
  type BudgetGameSession,
  type BudgetTodayChallenge,
} from '@/api/budget'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { TodayStateLoadingCard } from '@/components/game/TodayStateLoadingCard'
import { Header } from '@/components/layout/Header'
import { LegalModal } from '@/components/ui/LegalModal'
import { useLegalCurrent } from '@/hooks/useLegalCurrent'
import { HomeGuestLegalNotice } from '@/pages/game/home/HomeGuestLegalNotice'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { HomePanelShell } from '@/pages/game/home/HomePanelShell'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { useTodayStateHint } from '@/lib/todayStateHint'
import { useAuthStore } from '@/store/authStore'
import { cn } from '@/utils/cn'
import { extractApiErrorMessage } from '@/utils/errors'
import { BudgetAnswerPanel, BudgetHints } from './BudgetAnswerPanel'
import { BudgetProjectStage } from './BudgetProjectStage'
import { BudgetResults } from './BudgetResults'
import { budgetImageVariantUrl } from './budgetImageUrls'
import { BUDGET_SHARE_IMAGE } from '@/constants/site'

export function BudgetGamePage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { authResolved, isAuthenticated } = useAuthStore()
  const { data: legalCurrent, isLoading: legalLoading } = useLegalCurrent()
  const [session, setSession] = useState<BudgetGameSession | null>(null)
  const [existingSession, setExistingSession] = useState<BudgetGameSession | null>(null)
  const [challenge, setChallenge] = useState<BudgetTodayChallenge | null>(null)
  const [result, setResult] = useState<BudgetAnswerResult | null>(null)
  const [selectedChoice, setSelectedChoice] = useState<BudgetAnswerChoice | null>(null)
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [revealingHint, setRevealingHint] = useState(false)
  const [error, setError] = useState('')
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const requestSequence = useRef(0)

  const legalUnavailable = authResolved && !legalLoading && !legalCurrent

  const openTodaySession = useCallback(async () => {
    if (
      !authResolved
      || legalLoading
      || !legalCurrent
    ) return

    const sequence = ++requestSequence.current
    setStarting(true)
    setError('')
    setResult(null)

    try {
      const todayChallenge = challenge ?? await budgetApi.getTodayChallenge()
      if (!todayChallenge.available) {
        if (sequence === requestSequence.current) {
          setChallenge(todayChallenge)
          setSession(null)
        }
        return
      }

      const nextSession = isAuthenticated
        ? await budgetApi.startUserSession()
        : await budgetApi.startGuestSession(
          legalCurrent.termsVersion,
          legalCurrent.privacyVersion,
        )
      if (sequence === requestSequence.current) {
        setChallenge(todayChallenge)
        setSession(nextSession)
      }
    } catch (loadError) {
      if (sequence !== requestSequence.current) return
      setSession(null)
      setError(extractApiErrorMessage(loadError, t.budget.loadError))
    } finally {
      if (sequence === requestSequence.current) setStarting(false)
    }
  }, [authResolved, challenge, isAuthenticated, legalCurrent, legalLoading, t.budget.loadError])

  useEffect(() => {
    if (!authResolved || legalLoading || challenge || error) return

    let cancelled = false
    setLoading(true)
    void Promise.all([
      budgetApi.getTodayChallenge(),
      budgetApi.getTodaySession().catch(() => null),
    ])
      .then(([todayChallenge, todaySession]) => {
        if (cancelled) return
        setChallenge(todayChallenge)
        setExistingSession(todaySession)
      })
      .catch(loadError => {
        if (!cancelled) setError(extractApiErrorMessage(loadError, t.budget.loadError))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [authResolved, challenge, error, legalLoading, t.budget.loadError])

  useEffect(() => {
    if (!result) return
    const frame = window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: 'auto' })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [result])

  useEffect(() => {
    if (!result || !session?.currentQuestion) return
    const images = [
      session.currentQuestion.primaryImage,
      session.currentQuestion.comparisonImage,
    ].filter(image => image !== null)
    images.forEach(image => {
      const preload = new Image()
      preload.decoding = 'async'
      preload.src = budgetImageVariantUrl(image.url, 960)
    })
  }, [result, session?.currentQuestion])

  useEffect(() => {
    setSelectedChoice(null)
  }, [session?.currentQuestion?.variantId])

  const submitValue = async (value: number) => submitAnswer({ value })
  const submitChoice = async (choice: BudgetAnswerChoice) => submitAnswer({ choice })

  const submitAnswer = async (
    answer: { value: number; choice?: never } | { choice: BudgetAnswerChoice; value?: never },
  ) => {
    const question = session?.currentQuestion
    if (!session || !question || submitting) return

    setSubmitting(true)
    setError('')
    try {
      const nextResult = await budgetApi.submitAnswer(session.id, question.variantId, answer)
      setSession(nextResult.session)
      setResult(nextResult)
      if (nextResult.session.guest && nextResult.session.status === 'COMPLETED') {
        await queryClient.invalidateQueries({ queryKey: ['guest-progress'] })
      }
      if (isAuthenticated) {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ['profile'] }),
          queryClient.invalidateQueries({ queryKey: ['unacknowledged-badges'] }),
        ])
      }
    } catch (submitError) {
      setError(extractApiErrorMessage(submitError, t.budget.guessError))
    } finally {
      setSubmitting(false)
    }
  }

  const revealHint = async (hintIndex: number) => {
    const question = session?.currentQuestion
    if (!session || !question || submitting || revealingHint) return

    setRevealingHint(true)
    setError('')
    try {
      const hintResult = await budgetApi.revealHint(session.id, question.variantId, hintIndex)
      setSession(hintResult.session)
    } catch (hintError) {
      setError(extractApiErrorMessage(hintError, t.budget.session.hintError))
    } finally {
      setRevealingHint(false)
    }
  }

  const continueAfterReveal = () => {
    setError('')
    setResult(null)
  }

  useSeoMeta({
    title: t.budget.seoTitle,
    description: t.budget.seoDescription,
    canonicalPath: '/budget',
    image: BUDGET_SHARE_IMAGE,
    keywords: 'megaproject cost guessing game, infrastructure costs, budget quiz',
  })

  const displayedQuestion = result?.answer.question ?? session?.currentQuestion ?? null
  const displayedPosition = displayedQuestion?.position ?? session?.questionCount ?? 0
  const answeredCount = session?.answeredQuestions.length ?? 0
  const showCompleted = session?.status === 'COMPLETED' && !result
  const waitingForSessionPrerequisites = !authResolved || legalLoading
  const retryStart = () => {
    setError('')
    setExistingSession(null)
    if (challenge) void openTodaySession()
  }
  const refreshAvailability = () => {
    setError('')
    setExistingSession(null)
    setChallenge(null)
  }
  const retryStartScreen = () => {
    if (legalUnavailable) {
      void queryClient.invalidateQueries({ queryKey: ['legal-current'] })
      return
    }
    if (error) {
      retryStart()
      return
    }
    refreshAvailability()
  }
  const resumableSession = !session && existingSession?.status === 'IN_PROGRESS' ? existingSession : null
  const completedSession = !session && existingSession?.status === 'COMPLETED' ? existingSession : null
  const startScreenChecking = !legalUnavailable
    && !error
    && (waitingForSessionPrerequisites || loading || !challenge)
  const startScreenAvailable = !legalUnavailable
    && !error
    && Boolean(challenge && (challenge.available || resumableSession))
  const startScreenMessage = legalUnavailable ? t.budget.loadError : error || undefined
  const todayCompleted = session?.status === 'COMPLETED' || existingSession?.status === 'COMPLETED'

  useTodayStateHint('budget', todayCompleted ? 'completed' : startScreenChecking ? undefined : null)

  return (
    <div className="relative flex min-h-[100dvh] flex-col overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="play" />
      <Header />

      <main
        className={cn(
          'relative z-10 mx-auto flex min-h-0 w-full max-w-[1600px] flex-1 flex-col px-4 py-5 sm:px-7 sm:py-7 lg:px-8',
          session || completedSession ? 'pb-24 sm:pb-28 lg:pb-20' : 'pb-6 sm:pb-8',
        )}
      >
        <h1 className="sr-only">{t.budget.seoTitle}</h1>
        {session && displayedQuestion && !showCompleted && (
          <ChallengeStatusBar
            current={displayedPosition}
            total={session.questionCount}
            answered={answeredCount}
            earnedXp={session.totalXpEarned}
            maximumXp={session.maximumXp}
            difficulty={displayedQuestion.difficulty}
          />
        )}

        <AnimatePresence mode="wait">
          {completedSession ? (
            <BudgetResults session={completedSession} />
          ) : !session ? (
            <div className="flex w-full flex-1 items-center justify-center py-4 sm:py-8">
              <div data-today-start="budget" className="contents">
                <BudgetStartScreen
                  available={startScreenAvailable}
                  checking={startScreenChecking}
                  unavailableMessage={startScreenMessage}
                  existingSession={resumableSession}
                  isAuthenticated={isAuthenticated}
                  starting={starting}
                  onStart={() => void openTodaySession()}
                  onRefresh={retryStartScreen}
                  onOpenPrivacy={() => setLegalModal('privacy')}
                  onOpenTerms={() => setLegalModal('terms')}
                />
              </div>
              <TodayStateLoadingCard placeholderFor="budget" />
            </div>
          ) : showCompleted && session ? (
            <BudgetResults session={session} />
          ) : displayedQuestion && session ? (
            <motion.div
              key={`${displayedQuestion.variantId}-${result ? 'reveal' : 'question'}`}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="w-full"
            >
              {result ? (
                <BudgetAnswerPanel
                  key={displayedQuestion.variantId}
                  question={displayedQuestion}
                  answer={result.answer}
                  submitting={submitting}
                  error={error}
                  completed={session.status === 'COMPLETED'}
                  fullXpThresholdPercent={session.fullXpThresholdPercent}
                  selectedChoice={selectedChoice}
                  onSubmitValue={submitValue}
                  onSubmitChoice={submitChoice}
                  onContinue={continueAfterReveal}
                />
              ) : displayedQuestion.type === 'HIGHER_LOWER' ? (
                <section className="w-full">
                  <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-white/[.07] bg-black/20 px-4 py-3.5 sm:px-5">
                    <div className="flex items-center gap-3">
                      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-300/[.09] text-emerald-300">
                        <Scale size={19} />
                      </span>
                      <div>
                        <h2 className="text-lg font-black tracking-[-.025em] sm:text-xl">{t.budget.session.higherLower}</h2>
                        <p className="mt-0.5 text-xs text-white/40">{t.budget.session.chooseProject}</p>
                      </div>
                    </div>
                  </div>
                  <BudgetProjectStage
                    question={displayedQuestion}
                    selectedChoice={selectedChoice}
                    onSelectChoice={setSelectedChoice}
                  />
                  <BudgetAnswerPanel
                    key={displayedQuestion.variantId}
                    question={displayedQuestion}
                    answer={null}
                    submitting={submitting}
                    error={error}
                    completed={false}
                    fullXpThresholdPercent={session.fullXpThresholdPercent}
                    selectedChoice={selectedChoice}
                    onSubmitValue={submitValue}
                    onSubmitChoice={submitChoice}
                    onContinue={continueAfterReveal}
                  />
                </section>
              ) : (
                <section className="space-y-4">
                  <div className="grid grid-cols-[minmax(0,1fr)] items-stretch gap-4 lg:grid-cols-[minmax(0,1.55fr)_minmax(390px,.7fr)] xl:gap-5">
                    <BudgetProjectStage question={displayedQuestion} />
                    <BudgetAnswerPanel
                      key={displayedQuestion.variantId}
                      question={displayedQuestion}
                      answer={null}
                      submitting={submitting}
                      error={error}
                      completed={false}
                      fullXpThresholdPercent={session.fullXpThresholdPercent}
                      selectedChoice={selectedChoice}
                      onSubmitValue={submitValue}
                      onSubmitChoice={submitChoice}
                      onContinue={continueAfterReveal}
                    />
                  </div>
                  <BudgetHints
                    question={displayedQuestion}
                    revealing={revealingHint}
                    disabled={submitting}
                    onReveal={revealHint}
                  />
                </section>
              )}
            </motion.div>
          ) : (
            <ErrorState message={t.budget.loadError} retryLabel={t.budget.retry} onRetry={retryStart} />
          )}
        </AnimatePresence>

      </main>
      <HomeLegalStrip guideMode="budget" />
      <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
    </div>
  )
}

function BudgetStartScreen({
  available,
  checking,
  unavailableMessage,
  existingSession,
  isAuthenticated,
  starting,
  onStart,
  onRefresh,
  onOpenPrivacy,
  onOpenTerms,
}: {
  available: boolean
  checking: boolean
  unavailableMessage?: string
  existingSession: BudgetGameSession | null
  isAuthenticated: boolean
  starting: boolean
  onStart: () => void
  onRefresh: () => void
  onOpenPrivacy: () => void
  onOpenTerms: () => void
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const isResuming = existingSession?.status === 'IN_PROGRESS'
  const currentQuestion = existingSession?.currentQuestion ?? null
  const mechanics = [
    { icon: Banknote, label: t.budget.session.howMuch },
    { icon: Landmark, label: t.budget.session.higherLower },
    { icon: SlidersHorizontal, label: t.budget.session.costPerUnit },
  ] as const

  return (
    <HomePanelShell className="mx-0 max-w-[54rem] sm:mx-auto">
      <div className="mx-auto flex w-full max-w-[43rem] flex-1 flex-col">
        <div className="pb-5 text-center">
          <div className="flex flex-wrap items-center justify-center gap-1.5">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-200/15 bg-amber-300/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[.16em] text-amber-100/80">
              <Sparkles size={12} /> {t.nav.budget} · {t.budget.session.daily}
            </span>
          </div>
          <p className="mt-5 text-[10px] font-bold uppercase tracking-[.22em] text-emerald-300/65">
            {isResuming ? t.home.heroContinueEyebrow : t.budget.eyebrow}
          </p>
          <h2 className="mt-2 text-[1.65rem] font-black leading-[1.07] tracking-[-.03em] sm:text-[2.2rem]">{t.budget.title}</h2>
          <p className="mx-auto mt-3 max-w-[36rem] text-sm leading-relaxed text-white/48 sm:text-[.95rem]">{t.budget.subtitle}</p>
        </div>

        {isResuming ? (
          <ResumeSessionSummary session={existingSession} currentQuestion={currentQuestion} />
        ) : (
          <div className="mt-4 grid gap-2 sm:grid-cols-3">
            {mechanics.map(({ icon: Icon, label }) => (
              <div key={label} className="flex items-center gap-2.5 rounded-2xl border border-white/[.07] bg-white/[.03] px-3 py-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-emerald-300/10 text-emerald-300"><Icon size={15} /></span>
                <span className="text-xs font-semibold leading-4 text-white/65">{label}</span>
              </div>
            ))}
          </div>
        )}

        <nav aria-label={t.budget.title} className="mt-4 flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-xs font-semibold text-white/48">
          <Link to="/projects" className="transition-colors hover:text-emerald-200">
            {t.nav.projects}
          </Link>
          <Link
            to="/budget/how-to"
            onClick={(event) => {
              event.preventDefault()
              navigate('/budget?guide=budget')
            }}
            className="transition-colors hover:text-emerald-200"
          >
            {t.footer.howToPlay}
          </Link>
          <Link to="/data-sources" className="transition-colors hover:text-emerald-200">
            {t.footer.registryLink}
          </Link>
        </nav>

        {!available && !checking && (
          <div className="mt-5 rounded-2xl border border-amber-200/15 bg-amber-300/[.06] px-4 py-3 text-center text-sm text-amber-100/70">
            {unavailableMessage ?? t.budget.session.unavailable}
          </div>
        )}

        <button
          type="button"
          onClick={available ? onStart : onRefresh}
          disabled={starting || checking}
          className="relative mt-5 inline-flex min-h-[60px] w-full items-center justify-center gap-3 rounded-[1.4rem] bg-gradient-to-r from-emerald-300 to-teal-300 px-5 text-[1.05rem] font-bold text-[#06100d] shadow-[0_18px_52px_rgba(52,211,153,.16),inset_0_1px_0_rgba(255,255,255,.28)] transition hover:-translate-y-0.5 hover:brightness-105 active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0"
        >
          {starting || checking
            ? <LoaderCircle size={17} className="animate-spin" />
            : available
              ? <ArrowRight size={17} />
              : <RotateCcw size={17} />}
          {starting
            ? t.budget.checking
            : checking
              ? t.budget.loading
            : !available
              ? t.budget.retry
              : isResuming
                ? t.home.continueGame
                : t.home.playNow}
        </button>

        {(available || checking) && !isAuthenticated && (
          <p
            data-guest-only
            aria-hidden={checking || undefined}
            className={`mt-2.5 text-center text-xs leading-relaxed text-white/36 ${checking ? 'invisible' : ''}`}
          >
            <HomeGuestLegalNotice
              actionLabel={isResuming ? t.home.continueGame : t.home.playNow}
              onOpenPrivacy={onOpenPrivacy}
              onOpenTerms={onOpenTerms}
              template={t.home.guestLegalNotice}
            />
          </p>
        )}
      </div>
    </HomePanelShell>
  )
}

function ResumeSessionSummary({
  session,
  currentQuestion,
}: {
  session: BudgetGameSession
  currentQuestion: BudgetGameSession['currentQuestion']
}) {
  const { t } = useTranslation()
  const current = Math.min(session.questionCount, Math.max(1, session.currentPosition))
  const projectName = currentQuestion?.prompt.project?.name
    ?? currentQuestion?.prompt.primaryProject?.name
    ?? t.budget.session.nextQuestion

  return (
    <div className="mt-5 grid gap-3 sm:grid-cols-[1.15fr_.85fr]">
      <div className="rounded-2xl border border-emerald-300/15 bg-emerald-300/[.055] p-4 sm:p-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[.16em] text-emerald-200/60">{t.home.continueGame}</p>
            <p className="mt-2 text-lg font-black tracking-[-.025em] text-white">
              {replaceTokens(t.budget.session.questionProgress, {
                current: String(current),
                total: String(session.questionCount),
              })}
            </p>
          </div>
          <span className="rounded-full bg-black/20 px-2.5 py-1 text-[10px] font-black text-emerald-200/75">
            {session.answeredQuestions.length}/{session.questionCount}
          </span>
        </div>
        <div className="mt-4 grid gap-1.5" style={{ gridTemplateColumns: `repeat(${session.questionCount}, minmax(0, 1fr))` }}>
          {Array.from({ length: session.questionCount }, (_, index) => (
            <span
              key={index}
              className={`h-1.5 rounded-full ${index < session.answeredQuestions.length
                ? 'bg-emerald-300'
                : index === current - 1
                  ? 'bg-amber-300 shadow-[0_0_16px_rgba(252,211,77,.32)]'
                  : 'bg-white/[.1]'}`}
            />
          ))}
        </div>
      </div>

      <div className="rounded-2xl border border-white/[.07] bg-white/[.03] p-4 sm:p-5">
        <div className="flex items-center justify-between gap-3">
          <p className="text-[10px] font-bold uppercase tracking-[.16em] text-white/38">{t.budget.session.xpEarned}</p>
          <Trophy size={16} className="text-amber-200/70" />
        </div>
        <p className="mt-2 text-2xl font-black tracking-[-.04em] text-emerald-200">
          {replaceTokens(t.budget.session.xpProgress, {
            earned: String(session.totalXpEarned),
            maximum: String(session.maximumXp),
          })}
        </p>
        <p className="mt-2 truncate text-xs font-semibold text-white/45">{projectName}</p>
      </div>
    </div>
  )
}

function ChallengeStatusBar({
  current,
  total,
  answered,
  earnedXp,
  maximumXp,
  difficulty,
}: {
  current: number
  total: number
  answered: number
  earnedXp: number
  maximumXp: number
  difficulty: BudgetDifficulty
}) {
  const { t } = useTranslation()

  return (
    <section className="mb-4 grid gap-3 rounded-2xl border border-white/[.08] bg-black/25 px-4 py-3.5 backdrop-blur-xl sm:grid-cols-[auto_minmax(14rem,1fr)_auto] sm:items-center sm:gap-5 sm:px-5">
      <div className="flex items-center justify-between gap-4 sm:contents">
        <div className="inline-flex w-fit items-center gap-2 rounded-full bg-amber-300/[.09] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[.16em] text-amber-200/80">
          <Sparkles size={12} /> {t.budget.session.daily}
        </div>
        <div className="text-right sm:order-3">
          <p className="text-lg font-black leading-none tracking-[-.03em] text-emerald-300">
            {earnedXp}<span className="ml-1 text-[11px] tracking-normal text-emerald-300/60">/ {maximumXp} XP</span>
          </p>
        </div>
      </div>

      <div className="min-w-0 sm:order-2">
        <div className="flex items-center justify-between gap-3">
          <p className="text-sm font-bold tracking-[-.01em] text-white/80">
            {replaceTokens(t.budget.session.questionProgress, {
              current: String(current),
              total: String(total),
            })}
          </p>
          <div className="flex shrink-0 items-center gap-2">
            <span className="rounded-full border border-white/[.08] bg-white/[.035] px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.12em] text-white/50">
              {difficulty === 'EASY' ? t.budget.easy : difficulty === 'MEDIUM' ? t.budget.medium : t.budget.hard}
            </span>
            <span className="text-[10px] font-semibold text-white/30">{answered}/{total}</span>
          </div>
        </div>
        <div
          className="mt-2 grid gap-1.5"
          style={{ gridTemplateColumns: `repeat(${total}, minmax(0, 1fr))` }}
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={total}
          aria-valuenow={answered}
        >
          {Array.from({ length: total }, (_, index) => {
            const position = index + 1
            const complete = position <= answered
            const active = position === current && !complete
            return (
              <span
                key={position}
                className={`h-1.5 rounded-full transition-colors duration-300 ${complete
                  ? 'bg-emerald-300'
                  : active
                    ? 'bg-amber-300 shadow-[0_0_18px_rgba(252,211,77,.3)]'
                    : 'bg-white/[.09]'}`}
              />
            )
          })}
        </div>
      </div>
    </section>
  )
}

function ErrorState({ message, retryLabel, onRetry }: {
  message: string
  retryLabel: string
  onRetry: () => void
}) {
  return (
    <motion.div
      key="error"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="flex min-h-[25rem] flex-col items-center justify-center rounded-[2rem] border border-red-400/15 bg-red-400/[.04] p-8 text-center"
    >
      <p className="max-w-lg text-sm text-red-200/70">{message}</p>
      <button type="button" onClick={onRetry} className="mt-5 inline-flex items-center gap-2 rounded-xl bg-white px-4 py-2.5 text-sm font-bold text-black">
        <RotateCcw size={15} /> {retryLabel}
      </button>
    </motion.div>
  )
}

function replaceTokens(template: string, values: Record<string, string>) {
  return Object.entries(values).reduce(
    (result, [key, value]) => result.replace(`{${key}}`, value),
    template,
  )
}
