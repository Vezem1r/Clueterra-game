import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { ArrowLeft, Lightbulb, LoaderCircle, Search, Sparkles } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { CountrySearch } from '@/components/game/CountrySearch'
import { HintCard } from '@/components/game/HintCard'
import { HintInfoModal } from '@/components/game/HintInfoModal'
import { ResultScreen } from '@/components/game/ResultScreen'
import { useGuestState } from '@/hooks/useGameQueries'
import { useDelayedBusy } from '@/hooks/useDelayedBusy'
import { ResultsPage } from '@/pages/game/ResultsPage'
import { useGameStore } from '@/store/gameStore'
import { useAuthStore } from '@/store/authStore'
import { useToastStore } from '@/store/toastStore'
import { useTranslation } from '@/i18n'
import type { Country, Hint } from '@/types'

export function PlayPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t } = useTranslation()
  const { isAuthenticated, authResolved } = useAuthStore()
  const {
    data: guestState,
    isFetched: guestStateFetched,
    isLoading: guestStateLoading,
  } = useGuestState(authResolved && !isAuthenticated)
  const {
    session, currentCountry, hints, phase, isRevealingHint, lastGuessResult, error,
    countries, revealHint, submitGuess, nextCountry, restoreSession,
  } = useGameStore()
  const addToast = useToastStore(s => s.addToast)

  const [selectedHint, setSelectedHint] = useState<Hint | null>(null)
  const [showReport, setShowReport] = useState(false)
  const [isContinuing, setIsContinuing] = useState(false)
  const [lastGuessedCountry, setLastGuessedCountry] = useState<Country | null>(null)
  const [guessRepeatCount, setGuessRepeatCount] = useState(0)
  const guessHistoryRef = useRef<Map<string, number>>(new Map())
  const showRevealBusy = useDelayedBusy(isRevealingHint)

  useEffect(() => {
    if (!authResolved) {
      return
    }

    if (phase === 'idle') {
      if (!isAuthenticated) {
        if (guestStateLoading || !guestStateFetched) {
          return
        }
        if (!guestState?.hasTodaySession || guestState.needsLegalAcceptance) {
          navigate('/', { replace: true })
          return
        }
      }
      restoreSession().then(ok => { if (!ok) navigate('/') })
    }
  }, [authResolved, guestState, guestStateFetched, guestStateLoading, isAuthenticated, navigate, phase, restoreSession])

  useEffect(() => {
    if (error) {
      addToast({ type: 'error', message: error })
      useGameStore.setState({ error: null })
    }
  }, [error, addToast])

  useEffect(() => {
    return () => {
      queryClient.invalidateQueries({ queryKey: ['today-session-summary'] })
      queryClient.invalidateQueries({ queryKey: ['guest-state'] })
    }
  }, [queryClient])

  const handleGuess = useCallback((country: Country) => {
    const count = (guessHistoryRef.current.get(country.isoAlpha2) ?? 0) + 1
    guessHistoryRef.current.set(country.isoAlpha2, count)
    setGuessRepeatCount(count)
    setLastGuessedCountry(country)
    submitGuess(country.isoAlpha2)
  }, [submitGuess])

  const handleContinue = async () => {
    if (isContinuing) return

    setIsContinuing(true)
    try {
      if (lastGuessResult?.gameCompleted) {
        if (!isAuthenticated) {
          await Promise.all([
            queryClient.invalidateQueries({ queryKey: ['guest-progress'] }),
            queryClient.invalidateQueries({ queryKey: ['guest-state'] }),
          ])
        }
        useGameStore.setState({ phase: 'completed' })
      } else if (lastGuessResult?.correct || lastGuessResult?.countryComplete) {
        await nextCountry()
      } else {
        useGameStore.setState({ phase: 'playing', lastGuessResult: null })
      }
    } finally {
      setIsContinuing(false)
    }
  }

  if (!authResolved || phase === 'loading') {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex flex-col items-center gap-5">
          <div className="w-10 h-10 border-[3px] border-accent/30 border-t-accent rounded-full animate-spin" />
          <p className="text-white/30 text-sm">{t.play.loadingGame}</p>
        </motion.div>
      </div>
    )
  }

  if (phase === 'completed' && session) {
    return <ResultsPage />
  }

  const correctCountry = lastGuessResult?.correctCountry ||
    (countries.find(c => c.orderIndex === currentCountry?.orderIndex) as unknown as { correctCountry?: Country })?.correctCountry

  const maxHints = currentCountry?.totalHints ?? session?.maxHintsPerCountry ?? 0
  const canRevealMore = currentCountry
    ? currentCountry.hintsRevealed < maxHints && !currentCountry.isGuessed
    : false
  const progress = ((session?.currentCountryIndex ?? 0) / (session?.countryCount || 5)) * 100
  const displayedHints = [...hints].reverse()
  const attemptsIndicator = currentCountry && session?.maxAttempts
    ? (
        <div className="flex items-center gap-3 mb-6">
          <span className="text-xs text-white/35 uppercase tracking-wider font-medium">{t.play.attempts}</span>
          <div className="flex items-center gap-1.5">
            {Array.from({ length: session.maxAttempts }, (_, i) => (
              <div
                key={i}
                className={`w-3 h-3 rounded-full transition-all ${
                  i < currentCountry.attemptsRemaining
                    ? 'bg-accent shadow-[0_0_8px_rgba(124,92,252,.4)]'
                    : 'bg-white/10'
                }`}
              />
            ))}
          </div>
        </div>
      )
    : null

  return (
    <div className="min-h-screen bg-background text-white">
      <GameAuroraBackdrop variant="play" />

      <div className="relative z-10 min-h-screen flex flex-col">
        {/* header */}
        <header className="px-5 sm:px-8 lg:px-12 xl:px-16 py-5 sm:py-6 border-b border-white/[.04]">
          <div className="max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto flex items-center justify-between">
            <button
              onClick={() => navigate('/')}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-white/30 hover:text-white/60 hover:bg-white/[.05] transition-all cursor-pointer"
            >
              <ArrowLeft size={18} />
              <span className="hidden sm:inline text-sm">{t.play.exit}</span>
            </button>

            <div className="flex items-center gap-3 sm:gap-8">
              <div className="flex items-center gap-2 sm:gap-3">
                <span className="text-xs text-white/30">
                  {(session?.currentCountryIndex ?? 0) + 1}/{session?.countryCount || 5}
                </span>
                <div className="w-16 sm:w-28 xl:w-36 h-2 rounded-full bg-white/[.06] overflow-hidden shadow-[0_0_8px_rgba(124,92,252,.1)]">
                  <motion.div
                    initial={{ width: 0 }}
                    animate={{ width: `${progress}%` }}
                    className="h-full rounded-full bg-gradient-to-r from-accent to-success shadow-[0_0_6px_rgba(52,211,153,.3)]"
                  />
                </div>
              </div>

              <div className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 rounded-xl bg-white/[.03] border border-white/[.06] shadow-[0_0_12px_rgba(251,191,36,.12)]">
                <Sparkles size={14} className="text-warning" />
                <span className="text-sm font-semibold">{session?.totalXpEarned || 0}</span>
                <span className="text-white/30 text-xs hidden sm:inline">{t.play.points}</span>
              </div>
            </div>
          </div>
        </header>

        {/* content */}
        <main className="flex-1 px-5 sm:px-8 lg:px-12 xl:px-16 pt-8 sm:pt-14 pb-[calc(10.5rem+env(safe-area-inset-bottom))] sm:pb-[calc(12rem+env(safe-area-inset-bottom))] lg:py-14">
          <div className="max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto">
            {phase === 'result' && lastGuessResult ? (
              <ResultScreen
                result={lastGuessResult}
                correctCountry={correctCountry}
                guessedCountry={lastGuessedCountry ?? undefined}
                repeatCount={guessRepeatCount}
                onContinue={handleContinue}
                isContinuing={isContinuing}
              />
            ) : (
              <div className="flex flex-col lg:flex-row lg:gap-10 xl:gap-14 2xl:gap-16">
                  {/* left column — hints */}
                  <div className="flex-1 lg:flex-[3] mb-10 sm:mb-14 lg:mb-0">
                    <div className="flex flex-col gap-4 mb-6 sm:mb-8 sm:flex-row sm:items-center sm:justify-between">
                      <div className="flex items-center gap-3">
                        <div className="p-2.5 rounded-xl bg-warning/15">
                          <Lightbulb size={18} className="text-warning" />
                        </div>
                        <h2 className="text-base sm:text-lg font-semibold text-white/70">{t.play.clues}</h2>
                      </div>

                      <div className="flex items-center gap-3 sm:gap-4 self-start sm:self-auto">
                        <span className="px-3 py-1.5 rounded-full bg-white/[.03] border border-white/[.06] text-xs text-white/45">
                          {hints.length} / {maxHints}
                        </span>

                        {canRevealMore && (
                          <button
                            type="button"
                            onClick={revealHint}
                            disabled={phase === 'guessing' || isRevealingHint}
                            aria-busy={isRevealingHint}
                            className="px-4 py-2.5 rounded-xl border border-accent/20 bg-accent/[.07]
                                       hover:border-accent/40 hover:bg-accent/[.12]
                                       text-accent/90 disabled:cursor-wait
                                       transition-[background-color,border-color,transform] duration-150
                                       hover:-translate-y-px active:translate-y-0 active:scale-[.98]
                                       flex items-center justify-center gap-2 cursor-pointer"
                          >
                            {showRevealBusy
                              ? <LoaderCircle size={16} className="animate-spin" />
                              : <Lightbulb size={16} />}
                            <span className="text-sm font-medium">{t.play.revealClue}</span>
                          </button>
                        )}
                      </div>
                    </div>

                    <div className="space-y-4 sm:space-y-5">
                      {displayedHints.map((hint, i) => (
                        <HintCard
                          key={hint.id}
                          hint={hint}
                          displayNumber={hints.length - i}
                          isNewest={i === 0}
                          onInfoClick={setSelectedHint}
                        />
                      ))}

                      {hints.length === 0 && phase === 'playing' && maxHints > 0 && (
                        <div className="p-8 sm:p-10 rounded-2xl bg-white/[.02] border border-white/[.05]
                                        flex items-center justify-center">
                          <div className="flex items-center gap-3 text-white/30">
                            <div className="w-4 h-4 border-2 border-white/15 border-t-white/40 rounded-full animate-spin" />
                            <span className="text-sm">{t.play.revealingFirst}</span>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* right column — answer */}
                  <div className="hidden lg:block lg:flex-[2] lg:sticky lg:top-28 lg:self-start pt-8 sm:pt-10 lg:pt-0 border-t lg:border-t-0 border-white/[.05]">
                    <div className="flex items-center gap-3 mb-6 sm:mb-8">
                      <div className="p-2.5 rounded-xl bg-success/15">
                        <Search size={18} className="text-success" />
                      </div>
                      <h2 className="text-base sm:text-lg font-semibold text-white/70">{t.play.yourAnswer}</h2>
                    </div>

                    {attemptsIndicator}

                    <CountrySearch onSelect={handleGuess} disabled={phase === 'guessing'} />
                  </div>
              </div>
            )}
          </div>
        </main>

        {phase !== 'result' && (
          <div className="lg:hidden fixed inset-x-0 bottom-0 z-30 pb-[env(safe-area-inset-bottom)]">
            <div className="pointer-events-none absolute inset-x-0 -top-12 h-12 bg-gradient-to-t from-background via-background/85 to-transparent" />
            <div className="relative w-full rounded-t-[1.25rem] rounded-b-none border border-b-0 border-white/[.08] bg-[linear-gradient(180deg,rgba(12,18,28,.98),rgba(8,10,16,1))] px-4 pt-2.5 pb-3 shadow-[0_-12px_40px_rgba(0,0,0,.32)]">
              <div className="mb-2 flex justify-center">
                <div className="h-1 w-8 rounded-full bg-white/12" />
              </div>

              <div className="flex items-center justify-between mb-2.5">
                <span className="text-[11px] text-white/40 font-medium">{t.play.yourAnswer}</span>
                {currentCountry && session?.maxAttempts && (
                  <div className="flex items-center gap-1.5">
                    <span className="text-[10px] text-white/25 uppercase tracking-wider">{t.play.attempts}</span>
                    <div className="flex items-center gap-1">
                      {Array.from({ length: session.maxAttempts }, (_, i) => (
                        <div
                          key={i}
                          className={`w-2 h-2 rounded-full transition-all ${
                            i < currentCountry.attemptsRemaining
                              ? 'bg-accent shadow-[0_0_6px_rgba(124,92,252,.4)]'
                              : 'bg-white/10'
                          }`}
                        />
                      ))}
                    </div>
                  </div>
                )}
              </div>

              <CountrySearch
                onSelect={handleGuess}
                disabled={phase === 'guessing'}
                dropdownDirection="up"
              />
            </div>
          </div>
        )}
      </div>

      {/* modals */}
      {selectedHint && (
        <HintInfoModal
          hint={selectedHint}
          onClose={() => setSelectedHint(null)}
          isAuthenticated={isAuthenticated}
          onRequestSignIn={() => { setShowReport(true); setSelectedHint(null) }}
        />
      )}

      <Modal isOpen={showReport && !isAuthenticated} onClose={() => setShowReport(false)} title={t.play.reportHint} className="max-w-md">
        <p className="text-white/40 text-sm mb-8">
          {t.play.reportSignIn}
        </p>
        <div className="flex gap-3">
          <button
            onClick={() => setShowReport(false)}
            className="flex-1 py-3.5 rounded-xl bg-white/[.05] hover:bg-white/[.08] text-white/60 font-medium text-sm transition-colors cursor-pointer"
          >
            {t.common.cancel}
          </button>
          <button
            onClick={() => { setShowReport(false); navigate('/login') }}
            className="flex-1 py-3.5 rounded-xl bg-accent hover:bg-accent-hover text-white font-medium text-sm transition-colors cursor-pointer"
          >
            {t.nav.signIn}
          </button>
        </div>
      </Modal>
    </div>
  )
}
