import { useCallback, useEffect, useMemo, useState } from 'react'
import { legalApi } from '@/api/legal'
import {
  silhouettesApi,
  type SilhouetteLocale,
  type SilhouetteMiss,
  type SilhouettePlayerStats,
  type SilhouetteQuestion,
  type SilhouetteReveal,
  type SilhouetteSession,
} from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { useAuthStore } from '@/store/authStore'
import { extractApiErrorMessage } from '@/utils/errors'
import { isStaleRound, useRoundSync } from '../globe-game/useRoundSync'
import type { SilhouetteOutcome } from './SilhouetteRevealCard'
import { searchCountries, type SilhouetteCountryOption, type SilhouetteWorld } from './silhouetteWorld'

export interface RevealedCountry {
  reveal: SilhouetteReveal
  outcome: SilhouetteOutcome
  xpAwarded: number
  attempts: number
  round: number
  misses: SilhouetteMiss[]
}

export interface SilhouetteWrongGuess {
  country: SilhouetteCountryOption
  miss: SilhouetteMiss | null
}

export function useSilhouetteGame(copy: TranslationKeys['silhouetteGame'], world: SilhouetteWorld | null, language: SilhouetteLocale) {
  const { authResolved, isAuthenticated } = useAuthStore()
  const [question, setQuestion] = useState<SilhouetteQuestion | null>(null)
  const [revealed, setRevealed] = useState<RevealedCountry | null>(null)
  const [resumePending, setResumePending] = useState(false)
  const [stats, setStats] = useState<SilhouettePlayerStats | null>(null)
  const [solved, setSolved] = useState<string[]>([])
  const [guess, setGuess] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const inGame = revealed != null || (question != null && !resumePending)
  const complete = stats != null && stats.totalCountries > 0 && stats.solvedCountries >= stats.totalCountries
  const suggestions = useMemo(() => searchCountries(world, guess, language), [world, guess, language])
  const misses = revealed ? revealed.misses : question?.misses
  const wrongGuesses = useMemo<SilhouetteWrongGuess[]>(() => (misses ?? []).flatMap(miss => {
    const country = world?.byIso.get(miss.countryCode)
    return country ? [{ country, miss }] : []
  }), [misses, world])

  useEffect(() => {
    if (!authResolved) return
    let cancelled = false
    silhouettesApi.session()
      .then(session => {
        if (cancelled) return
        setStats(session.stats)
        setSolved(session.solved)
        setQuestion(session.current)
        setResumePending(session.current != null)
      })
      .catch(loadError => {
        if (!cancelled) setError(extractApiErrorMessage(loadError, copy.loadError))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [authResolved, isAuthenticated, copy.loadError])

  const currentRound = question?.round ?? null
  const syncRound = useCallback(() => {
    if (currentRound == null) return
    silhouettesApi.session()
      .then(session => {
        setStats(session.stats)
        setSolved(session.solved)
        setQuestion(session.current)
        if (session.current?.round === currentRound) return
        setResumePending(false)
        setGuess('')
        setError(copy.roundMoved)
      })
      .catch(() => undefined)
  }, [currentRound, copy.roundMoved])

  useRoundSync(authResolved, syncRound)

  async function recover() {
    apply(await silhouettesApi.session())
    setError(copy.roundMoved)
  }

  async function run(action: () => Promise<void>, fallback = copy.actionError) {
    setBusy(true)
    setError('')
    try {
      await action()
    } catch (actionError) {
      if (isStaleRound(actionError)) await recover().catch(() => setError(copy.actionError))
      else setError(extractApiErrorMessage(actionError, fallback))
    } finally {
      setBusy(false)
    }
  }

  function apply(session: SilhouetteSession) {
    setRevealed(null)
    setResumePending(false)
    setStats(session.stats)
    setSolved(session.solved)
    setQuestion(session.current)
    setGuess('')
  }

  function start() {
    if (question && resumePending) {
      setResumePending(false)
      return
    }
    void run(async () => {
      if (isAuthenticated) {
        apply(await silhouettesApi.startRegistered())
        return
      }
      const legal = await legalApi.getCurrent()
      apply(await silhouettesApi.startGuest(legal.termsVersion, legal.privacyVersion))
    })
  }

  function openMenu() {
    setRevealed(null)
    setResumePending(question != null)
    setGuess('')
  }

  function nextCountry() {
    void run(async () => apply(await silhouettesApi.next()))
  }

  function finish(country: RevealedCountry, nextStats: SilhouettePlayerStats) {
    setRevealed(country)
    setQuestion(null)
    setStats(nextStats)
    if (country.outcome === 'solved') {
      setSolved(list => list.includes(country.reveal.countryCode) ? list : [...list, country.reveal.countryCode])
    }
    setGuess('')
  }

  function pick(country: SilhouetteCountryOption) {
    const current = question
    if (!current || busy) return
    void run(async () => {
      const result = await silhouettesApi.guess(current.round, country.iso)
      if (result.correct && result.reveal) {
        finish({
          reveal: result.reveal, outcome: 'solved', xpAwarded: result.xpAwarded, attempts: result.attempts,
          round: current.round, misses: result.misses,
        }, result.stats)
        return
      }
      setQuestion({ ...current, attempts: result.attempts, misses: result.misses })
      setStats(result.stats)
      setGuess('')
    }, copy.guessError)
  }

  function revealClue() {
    const current = question
    if (!current) return
    void run(async () => setQuestion(await silhouettesApi.hint(current.round)))
  }

  function skip() {
    const current = question
    if (!current) return
    void run(async () => apply(await silhouettesApi.skip(current.round)))
  }

  function showAnswer() {
    const current = question
    if (!current) return
    void run(async () => {
      const { reveal, misses: shownMisses, stats: nextStats } = await silhouettesApi.showAnswer(current.round)
      finish({ reveal, outcome: 'shown', xpAwarded: 0, attempts: current.attempts, round: current.round, misses: shownMisses }, nextStats)
    })
  }

  return {
    authResolved, isAuthenticated, question, revealed, resumePending, stats, solved, guess, wrongGuesses,
    suggestions, loading, busy, error, inGame, complete,
    start, openMenu, nextCountry, setGuess, pick, revealClue, skip, showAnswer,
  }
}
