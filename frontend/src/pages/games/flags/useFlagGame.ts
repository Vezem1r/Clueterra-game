import { useCallback, useEffect, useMemo, useState } from 'react'
import { legalApi } from '@/api/legal'
import {
  flagsApi,
  type FlagPlayerStats,
  type FlagQuestion,
  type FlagReveal,
  type FlagSession,
} from '@/api/flags'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { useAuthStore } from '@/store/authStore'
import { extractApiErrorMessage } from '@/utils/errors'
import { isStaleRound, useRoundSync } from '../globe-game/useRoundSync'
import { searchCountries, type CountryPool, type SilhouetteCountryOption } from '../silhouettes/silhouetteWorld'

export type FlagOutcome = 'solved' | 'shown'

export interface RevealedFlag {
  reveal: FlagReveal
  outcome: FlagOutcome
  xpAwarded: number
  attempts: number
  round: number
  misses: string[]
  regionHint: string | null
}

export function useFlagGame(copy: TranslationKeys['flagGame'], world: CountryPool | null, language: SilhouetteLocale) {
  const { authResolved, isAuthenticated } = useAuthStore()
  const [question, setQuestion] = useState<FlagQuestion | null>(null)
  const [revealed, setRevealed] = useState<RevealedFlag | null>(null)
  const [resumePending, setResumePending] = useState(false)
  const [stats, setStats] = useState<FlagPlayerStats | null>(null)
  const [solved, setSolved] = useState<string[]>([])
  const [guess, setGuess] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const inGame = revealed != null || (question != null && !resumePending)
  const complete = stats != null && stats.totalFlags > 0 && stats.solvedFlags >= stats.totalFlags
  const suggestions = useMemo(() => searchCountries(world, guess, language), [world, guess, language])
  const misses = revealed ? revealed.misses : question?.misses
  const wrongGuesses = useMemo<SilhouetteCountryOption[]>(() => (misses ?? []).flatMap(iso => {
    const country = world?.byIso.get(iso)
    return country ? [country] : []
  }), [misses, world])

  useEffect(() => {
    if (!authResolved) return
    let cancelled = false
    flagsApi.session()
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
    flagsApi.session()
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
    apply(await flagsApi.session())
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

  function apply(session: FlagSession) {
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
        apply(await flagsApi.startRegistered())
        return
      }
      const legal = await legalApi.getCurrent()
      apply(await flagsApi.startGuest(legal.termsVersion, legal.privacyVersion))
    })
  }

  function openMenu() {
    setRevealed(null)
    setResumePending(question != null)
    setGuess('')
  }

  function nextFlag() {
    void run(async () => apply(await flagsApi.next()))
  }

  function finish(flag: RevealedFlag, nextStats: FlagPlayerStats) {
    setRevealed(flag)
    setQuestion(null)
    setStats(nextStats)
    if (flag.outcome === 'solved') {
      setSolved(list => list.includes(flag.reveal.countryCode) ? list : [...list, flag.reveal.countryCode])
    }
    setGuess('')
  }

  function pick(country: SilhouetteCountryOption) {
    const current = question
    if (!current || busy) return
    void run(async () => {
      const result = await flagsApi.guess(current.round, country.iso)
      if (result.correct && result.reveal) {
        finish({
          reveal: result.reveal, outcome: 'solved', xpAwarded: result.xpAwarded, attempts: result.attempts,
          round: current.round, misses: result.misses, regionHint: current.hints.region?.subregion ?? null,
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
    void run(async () => setQuestion(await flagsApi.hint(current.round)))
  }

  function skip() {
    const current = question
    if (!current) return
    void run(async () => apply(await flagsApi.skip(current.round)))
  }

  function showAnswer() {
    const current = question
    if (!current) return
    void run(async () => {
      const { reveal, misses: shownMisses, stats: nextStats } = await flagsApi.showAnswer(current.round)
      finish({ reveal, outcome: 'shown', xpAwarded: 0, attempts: current.attempts, round: current.round, misses: shownMisses,
        regionHint: current.hints.region?.subregion ?? null }, nextStats)
    })
  }

  return {
    authResolved, isAuthenticated, question, revealed, resumePending, stats, solved, guess, wrongGuesses,
    suggestions, loading, busy, error, inGame, complete,
    start, openMenu, nextFlag, setGuess, pick, revealClue, skip, showAnswer,
  }
}
