import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { HintInfoModal } from '@/components/game/HintInfoModal'
import { GuestProgressCta } from '@/components/game/GuestProgressCta'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useAuthStore } from '@/store/authStore'
import { useGuestProgress, useProfile, useTodayResults } from '@/hooks/useGameQueries'
import { useTranslation } from '@/i18n'
import type { GameResults, Hint, ProfileDto } from '@/types'
import { CountryResultCard } from './results/CountryResultCard'
import { DayPulseSection } from './results/DayPulseSection'
import { Hero } from './results/Hero'
import { ResultsTopBar } from './results/ResultsTopBar'
import { RunBreakdownCard } from './results/RunBreakdownCard'
import { SECTION_DELAY_COUNTRIES } from './results/constants'
import { useRunInsights } from './results/useRunInsights'

export function ResultsPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { isAuthenticated, authResolved } = useAuthStore()
  const canLoadResults = authResolved

  const { data: results, isLoading, isError } = useTodayResults(canLoadResults)
  const { data: profile } = useProfile(isAuthenticated && !!results, { staleTime: 0 })

  useSeoMeta({
    title: `${t.results.title} | Clueterra`,
    description: t.results.subtitle,
    canonicalPath: null,
    robots: 'noindex,nofollow',
  })

  useEffect(() => {
    if (isAuthenticated && results) {
      queryClient.invalidateQueries({ queryKey: ['unacknowledged-badges'] })
    }
  }, [isAuthenticated, results, queryClient])

  useEffect(() => {
    if (!authResolved) {
      return
    }

    if (isError) {
      navigate('/', { replace: true })
    }
  }, [authResolved, isError, navigate])

  if (!authResolved || isLoading || !results) {
    return (
      <div className="min-h-screen bg-background text-white flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-[3px] border-emerald-400/20 border-t-emerald-300 rounded-full animate-spin" />
          <p className="text-sm text-white/30">{t.common.loading}</p>
        </div>
      </div>
    )
  }

  return (
    <ResultsContent
      results={results}
      profile={profile ?? null}
      isAuthenticated={isAuthenticated}
      isGuest={!isAuthenticated}
    />
  )
}

interface ResultsContentProps {
  results: GameResults
  profile: ProfileDto | null
  isAuthenticated: boolean
  isGuest: boolean
}

function ResultsContent({ results, profile, isAuthenticated, isGuest }: ResultsContentProps) {
  const insights = useRunInsights(results)
  const showGuestPrompt = results.guest && isGuest
  const [selectedHint, setSelectedHint] = useState<Hint | null>(null)
  const { data: guestProgress } = useGuestProgress(showGuestPrompt, true)

  return (
    <div className="min-h-screen bg-background text-white overflow-hidden">
      <GameAuroraBackdrop />
      <ResultsTopBar
        level={results.level}
        totalXpEarned={results.totalXpEarned}
        guessedCountries={results.guessedCountries}
        totalCountries={results.totalCountries}
      />

      <div className="relative z-10 px-5 sm:px-8 lg:px-12 pt-24 sm:pt-28 pb-5 sm:pb-7">
        <main className="max-w-6xl mx-auto pb-24 md:pb-28">
          <section className="mb-8 sm:mb-10 grid gap-5 sm:gap-6 lg:grid-cols-[minmax(0,1.32fr)_minmax(300px,.68fr)] items-stretch">
            <Hero results={results} profile={profile} insights={insights} />
            <RunBreakdownCard insights={insights} />
          </section>

          {showGuestPrompt ? (
            <div className="mb-8 sm:mb-10">
              <GuestProgressCta
                context="countries"
                fallbackTotalXp={Math.max(0, results.xpBefore) + results.totalXpEarned}
                progress={guestProgress ?? null}
              />
            </div>
          ) : null}

          <DayPulseSection stats={results.communityStats} />

          <motion.section
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: SECTION_DELAY_COUNTRIES }}
            className="space-y-5"
          >
            {results.countries.map((country, index) => (
              <CountryResultCard
                key={country.id}
                country={country}
                index={index}
                onHintClick={setSelectedHint}
              />
            ))}
          </motion.section>
        </main>
      </div>

      {selectedHint && (
        <HintInfoModal
          hint={selectedHint}
          onClose={() => setSelectedHint(null)}
          isAuthenticated={isAuthenticated}
        />
      )}
    </div>
  )
}
