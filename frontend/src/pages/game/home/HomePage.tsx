import { useState, useCallback, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { legalApi } from '@/api/legal'
import { Header } from '@/components/layout/Header'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { useLegalCurrent } from '@/hooks/useLegalCurrent'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { CLUETERRA_SHARE_IMAGE, SITE_ORIGIN } from '@/constants/site'
import { useGameStore } from '@/store/gameStore'
import { useAuthStore } from '@/store/authStore'
import { useTranslation } from '@/i18n'
import { useGuestProgress, useGuestState, useProfile, useTodayChallenges, useTodaySessionSummary } from '@/hooks/useGameQueries'
import { extractApiErrorMessage } from '@/utils/errors'
import { GAME_SESSION_STATUS } from '@/types'
import { GuestGateModal } from './GuestGateModal'
import { HomeCompletedPanel } from './HomeCompletedPanel'
import { HomeContinuePanel } from './HomeContinuePanel'
import { HomeGuestBanner } from './HomeGuestBanner'
import { HomeGuestReacceptPanel } from './HomeGuestReacceptPanel'
import { HomeLegalStrip } from './HomeLegalStrip'
import { HomeStartPanel } from './HomeStartPanel'
import { buildDifficultyOptions } from './difficultyOptions'
import { TodayStateLoadingCard } from '@/components/game/TodayStateLoadingCard'
import { useTodayStateHint } from '@/lib/todayStateHint'
import type { HomeDifficultyOption, HomeDifficultyValue, HomePanelMode } from './types'

const LANGUAGES = ['en', 'ru', 'cs']

interface ApiErrorData {
  message?: string
  error?: string
  code?: string
}

function isGuestPrincipalMissingError(error: unknown): boolean {
  if (!isAxiosError(error)) return false

  const status = error.response?.status
  if (status === 401 || status === 403) return true

  const data = error.response?.data as ApiErrorData | undefined
  const details = `${data?.code ?? ''} ${data?.message ?? ''} ${data?.error ?? ''}`.toLowerCase()
  return status === 400 && details.includes('authentication is required')
}

type TodayState = 'loading' | 'no_session' | 'in_progress' | 'completed'

export function HomePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t } = useTranslation()
  const { phase } = useGameStore()
  const { startGame } = useGameStore()
  const { isAuthenticated, authResolved } = useAuthStore()
  const [diff, setDiff] = useState<HomeDifficultyValue>('EASY')
  const [starting, setStarting] = useState(false)
  const [guestActionError, setGuestActionError] = useState('')
  const [showGateModal, setShowGateModal] = useState(false)
  const [gateDismissed, setGateDismissed] = useState(false)

  const { data: challenges = [] } = useTodayChallenges()
  const { data: legalCurrent, isLoading: legalCurrentLoading } = useLegalCurrent()
  const {
    data: guestState,
    isLoading: guestStateLoading,
    isFetched: guestStateFetched,
  } = useGuestState(authResolved && !isAuthenticated)
  const guestNeedsLegalAcceptance = !isAuthenticated && !!guestState?.needsLegalAcceptance
  const guestNeedsSessionReacceptance =
    !isAuthenticated && !!guestState?.hasTodaySession && guestNeedsLegalAcceptance
  const canLoadSummary =
    authResolved &&
    (isAuthenticated || (!isAuthenticated && guestStateFetched && !!guestState?.hasGuest && !guestNeedsLegalAcceptance))
  const {
    data: todaySummary,
    isLoading: summaryLoading,
  } = useTodaySessionSummary(canLoadSummary)
  const { data: guestProgress } = useGuestProgress(authResolved && !isAuthenticated)

  const todayState: TodayState = useMemo(() => {
    if (!authResolved) return 'loading'
    if (!isAuthenticated && guestStateLoading) return 'loading'
    if (summaryLoading) return 'loading'
    if (!todaySummary) return 'no_session'
    if (todaySummary.status === GAME_SESSION_STATUS.IN_PROGRESS) return 'in_progress'
    return 'completed'
  }, [authResolved, guestStateLoading, isAuthenticated, summaryLoading, todaySummary])

  useTodayStateHint(
    'clueterra',
    todayState === 'loading'
      ? undefined
      : todayState === 'completed'
        ? 'completed'
        : todayState === 'in_progress'
          ? 'in_progress'
          : null,
  )

  useEffect(() => {
    if (!authResolved) {
      return
    }

    setDiff((current) => {
      if (isAuthenticated) {
        return current === 'EASY' ? 'MEDIUM' : current
      }

      return current === 'EASY' ? current : 'EASY'
    })
  }, [authResolved, isAuthenticated])

  useEffect(() => {
    if (!guestNeedsLegalAcceptance) {
      setGuestActionError('')
    }
  }, [guestNeedsLegalAcceptance])

  const { data: profile } = useProfile(
    authResolved && isAuthenticated && (todayState === 'completed' || todayState === 'no_session')
  )

  const showDifficultySelector = todayState === 'no_session' && !guestNeedsSessionReacceptance
  const showContinue = todayState === 'in_progress' || guestNeedsSessionReacceptance
  const showCompleted = todayState === 'completed' && !!todaySummary
  const mode: HomePanelMode = showCompleted ? 'completed' : showContinue ? 'continue' : 'new'
  const activeDifficulty: HomeDifficultyValue = mode === 'new'
    ? diff
    : (todaySummary?.level ?? diff)

  const difficultyOptions = useMemo<HomeDifficultyOption[]>(
    () => buildDifficultyOptions(challenges, isAuthenticated, t),
    [challenges, isAuthenticated, t],
  )

  const guestProgressStats = !isAuthenticated && authResolved && guestProgress?.hasGuest ? guestProgress : null

  const handlePrimaryAction = useCallback(async () => {
    if (!authResolved) {
      return
    }

    setGuestActionError('')

    if (!isAuthenticated && diff !== 'EASY' && !showContinue && !showCompleted) {
      return
    }

    if (showCompleted) {
      navigate('/results')
      return
    }

    if (showContinue) {
      if (isAuthenticated) {
        if (todayState === 'in_progress' && todaySummary) {
          navigate('/play')
        }
        return
      }

      if (guestNeedsSessionReacceptance) {
        if (!legalCurrent) {
          setGuestActionError('Legal documents are still loading. Please try again in a moment.')
          return
        }

        setStarting(true)
        try {
          await legalApi.acceptCurrent(legalCurrent.termsVersion, legalCurrent.privacyVersion)
          await Promise.all([
            queryClient.invalidateQueries({ queryKey: ['guest-state'] }),
            queryClient.invalidateQueries({ queryKey: ['today-session-summary'] }),
            queryClient.invalidateQueries({ queryKey: ['today-results'] }),
            queryClient.invalidateQueries({ queryKey: ['guest-progress'] }),
          ])
          navigate(guestState?.todaySessionStatus === GAME_SESSION_STATUS.COMPLETED ? '/results' : '/play')
        } catch (error) {
          console.error(error)
          if (isGuestPrincipalMissingError(error)) {
            try {
              await startGame('EASY', {
                termsVersion: legalCurrent.termsVersion,
                privacyVersion: legalCurrent.privacyVersion,
              })
              await Promise.all([
                queryClient.invalidateQueries({ queryKey: ['guest-state'] }),
                queryClient.invalidateQueries({ queryKey: ['today-session-summary'] }),
                queryClient.invalidateQueries({ queryKey: ['today-results'] }),
                queryClient.invalidateQueries({ queryKey: ['guest-progress'] }),
              ])
              navigate('/play')
            } catch (startError) {
              console.error(startError)
              setGuestActionError(extractApiErrorMessage(startError, 'Failed to start guest session'))
            }
            return
          }
          setGuestActionError(extractApiErrorMessage(error, 'Failed to record legal acceptance'))
        } finally {
          setStarting(false)
        }
        return
      }

      if (todayState === 'in_progress' && todaySummary) {
        navigate('/play')
      }
      return
    }

    if (!isAuthenticated && !gateDismissed && guestProgressStats && guestProgressStats.playCount >= 3) {
      setShowGateModal(true)
      return
    }

    setStarting(true)
    try {
      if (isAuthenticated) {
        await startGame(diff)
      } else {
        if (!legalCurrent) {
          setGuestActionError('Legal documents are still loading. Please try again in a moment.')
          return
        }

        await startGame('EASY', {
          termsVersion: legalCurrent.termsVersion,
          privacyVersion: legalCurrent.privacyVersion,
        })
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ['guest-state'] }),
          queryClient.invalidateQueries({ queryKey: ['guest-progress'] }),
          queryClient.invalidateQueries({ queryKey: ['today-session-summary'] }),
        ])
      }

      navigate('/play')
    } catch (error) {
      console.error(error)
      if (!isAuthenticated) {
        setGuestActionError(extractApiErrorMessage(error, 'Failed to start guest session'))
      }
    } finally {
      setStarting(false)
    }
  }, [
    diff,
    gateDismissed,
    guestNeedsSessionReacceptance,
    guestProgressStats,
    guestState,
    isAuthenticated,
    authResolved,
    legalCurrent,
    navigate,
    queryClient,
    showCompleted,
    showContinue,
    startGame,
    todayState,
    todaySummary,
  ])

  const structuredData = useMemo(() => ({
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'WebSite',
        name: 'Clueterra',
        url: `${SITE_ORIGIN}/`,
        description: t.home.seoDescription,
        inLanguage: LANGUAGES,
      },
      {
        '@type': 'VideoGame',
        name: 'Clueterra',
        url: `${SITE_ORIGIN}/`,
        description: t.home.seoDescription,
        genre: ['Geography', 'Trivia', 'Puzzle'],
        gamePlatform: 'Web Browser',
        applicationCategory: 'GameApplication',
        operatingSystem: 'Web',
        offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
      },
      {
        '@type': 'FAQPage',
        mainEntity: [
          { '@type': 'Question', name: t.home.faqQ1, acceptedAnswer: { '@type': 'Answer', text: t.home.faqA1 } },
          { '@type': 'Question', name: t.home.faqQ2, acceptedAnswer: { '@type': 'Answer', text: t.home.faqA2 } },
          { '@type': 'Question', name: t.home.faqQ3, acceptedAnswer: { '@type': 'Answer', text: t.home.faqA3 } },
        ],
      },
    ],
  }), [t])

  useSeoMeta({
    title: t.home.seoTitle,
    description: t.home.seoDescription,
    canonicalPath: '/',
    image: CLUETERRA_SHARE_IMAGE,
    keywords: 'daily geography game, geography quiz, guess the country, country guessing game, clue-based geography game',
    structuredData,
  })

  const selectedOption = difficultyOptions.find((option) => option.value === activeDifficulty) ?? difficultyOptions[0]

  return (
    <div className="flex min-h-[100dvh] flex-col overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="hero" />

      <div className="relative z-10 flex min-h-0 flex-1 flex-col">
        <Header />

        <main className="flex min-h-0 flex-1 px-4 pb-6 pt-4 sm:px-6 sm:pb-8 sm:pt-5 lg:px-10 lg:pt-6">
          <div className="mx-auto flex min-h-0 w-full max-w-[58rem] flex-1 flex-col">
            {guestProgressStats && guestProgressStats.playCount >= 2 && mode === 'new' && (
              <HomeGuestBanner stats={guestProgressStats} />
            )}
            <div className="flex min-h-0 flex-1 items-center justify-center py-3 sm:py-4 lg:py-5">
              {todayState === 'loading' ? (
                <>
                  <div data-today-start="clueterra" className="contents">
                    <HomeStartPanel
                      canPrimaryAction={false}
                      difficulty={activeDifficulty}
                      guestActionError=""
                      isAuthenticated={isAuthenticated}
                      isBusy={false}
                      legalReady={false}
                      onDifficultyChange={setDiff}
                      onLogin={() => navigate('/login', { state: { fromGame: true } })}
                      onPrimaryAction={handlePrimaryAction}
                      onRegister={() => navigate('/register', { state: { fromGame: true } })}
                      options={difficultyOptions}
                      profile={null}
                    />
                  </div>
                  <TodayStateLoadingCard placeholderFor="clueterra" />
                </>
              ) : mode === 'new' ? (
                <>
                  <div data-today-start="clueterra" className="contents">
                    <HomeStartPanel
                      canPrimaryAction={showDifficultySelector}
                      difficulty={activeDifficulty}
                      guestActionError={guestActionError}
                      isAuthenticated={isAuthenticated}
                      isBusy={starting || phase === 'loading'}
                      legalReady={!legalCurrentLoading && !!legalCurrent}
                      onDifficultyChange={setDiff}
                      onLogin={() => navigate('/login', { state: { fromGame: true } })}
                      onPrimaryAction={handlePrimaryAction}
                      onRegister={() => navigate('/register', { state: { fromGame: true } })}
                      options={difficultyOptions}
                      profile={profile ?? null}
                    />
                  </div>
                  <TodayStateLoadingCard placeholderFor="clueterra" />
                </>
              ) : mode === 'continue' && guestNeedsSessionReacceptance && !todaySummary ? (
                <HomeGuestReacceptPanel
                  error={guestActionError}
                  isBusy={starting || phase === 'loading'}
                  legalReady={!legalCurrentLoading && !!legalCurrent}
                  onPrimaryAction={handlePrimaryAction}
                />
              ) : mode === 'continue' && todaySummary && selectedOption ? (
                <HomeContinuePanel
                  guestActionError={guestActionError}
                  guestNeedsSessionReacceptance={guestNeedsSessionReacceptance}
                  isAuthenticated={isAuthenticated}
                  isBusy={starting || phase === 'loading'}
                  legalReady={!legalCurrentLoading && !!legalCurrent}
                  onPrimaryAction={handlePrimaryAction}
                  option={selectedOption}
                  summary={todaySummary}
                />
              ) : mode === 'completed' && todaySummary && selectedOption ? (
                <HomeCompletedPanel
                  isAuthenticated={isAuthenticated}
                  onRegister={() => navigate('/register', { state: { fromGame: true } })}
                  option={selectedOption}
                  summaryContext={{ profile: profile ?? null, summary: todaySummary }}
                />
              ) : null}
            </div>

            <HomeLegalStrip />
          </div>
        </main>
      </div>

      {guestProgressStats && (
        <GuestGateModal
          isOpen={showGateModal}
          onClose={() => {
            setShowGateModal(false)
            setGateDismissed(true)
          }}
          stats={guestProgressStats}
        />
      )}
    </div>
  )
}
