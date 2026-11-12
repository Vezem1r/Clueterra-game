import { useCallback, useEffect, useState } from 'react'
import { legalApi } from '@/api/legal'
import { riversApi, type RiverCourse, type RiverPlayerStats, type RiverQuestion, type RiverReveal } from '@/api/rivers'
import type { TranslationKeys } from '@/i18n'
import { riverDraftStorage } from '@/lib/browserStorage'
import { useAuthStore } from '@/store/authStore'
import { extractApiErrorMessage } from '@/utils/errors'
import { isStaleRound, useRoundSync } from '../globe-game/useRoundSync'
import type { RiverOutcome } from './RiverRevealCard'
import { RIVER_GUEST_PLAYER } from './riverGameConstants'
import { useRiverNameSuggestions } from './useRiverNameSuggestions'

export interface RevealedRiver {
  questionId: number
  reveal: RiverReveal
  roundNumber: number
  outcome: RiverOutcome
  xpAwarded: number
  attempts: number
  imageUrl: string
  course: RiverCourse
}

export function useRiverGame(copy: TranslationKeys['riverGame']) {
  const { authResolved, isAuthenticated, user } = useAuthStore()
  const [question, setQuestion] = useState<RiverQuestion | null>(null)
  const [revealed, setRevealed] = useState<RevealedRiver | null>(null)
  const [resumePending, setResumePending] = useState(false)
  const [stats, setStats] = useState<RiverPlayerStats | null>(null)
  const [guess, setGuess] = useState('')
  const [wrongGuesses, setWrongGuesses] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const playerId = user?.id ?? RIVER_GUEST_PLAYER
  const questionId = question?.id
  const inGame = revealed != null || (question != null && !resumePending)
  const { suggestions, clearSuggestions } = useRiverNameSuggestions(guess, inGame && revealed == null)

  useEffect(() => {
    if (!authResolved) return
    let cancelled = false
    riversApi.session()
      .then(session => {
        if (!cancelled) {
          setStats(session.stats)
          setQuestion(session.current)
          setResumePending(session.current != null)
        }
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

  useEffect(() => {
    if (questionId != null) setGuess(riverDraftStorage.read(playerId, questionId))
  }, [questionId, playerId])

  function resetGuess() {
    setGuess('')
    setWrongGuesses([])
    clearSuggestions()
  }

  const syncRound = useCallback(() => {
    if (questionId == null) return
    riversApi.session()
      .then(session => {
        setStats(session.stats)
        setQuestion(session.current)
        if (session.current?.id === questionId) return
        setResumePending(false)
        setGuess('')
        setWrongGuesses([])
        setError(copy.roundMoved)
      })
      .catch(() => undefined)
  }, [questionId, copy.roundMoved])

  useRoundSync(authResolved, syncRound)

  async function recover() {
    const session = await riversApi.session()
    setRevealed(null)
    setResumePending(false)
    setStats(session.stats)
    setQuestion(session.current)
    resetGuess()
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

  function moveTo(next: RiverQuestion) {
    setRevealed(null)
    setResumePending(false)
    setQuestion(next)
    resetGuess()
  }

  function start() {
    if (question && resumePending) {
      setResumePending(false)
      return
    }
    void run(async () => {
      if (isAuthenticated) {
        moveTo(await riversApi.startRegistered())
        return
      }
      const legal = await legalApi.getCurrent()
      moveTo(await riversApi.startGuest(legal.termsVersion, legal.privacyVersion))
    })
  }

  function openMenu() {
    setRevealed(null)
    setResumePending(question != null)
    resetGuess()
  }

  function nextRiver() {
    void run(async () => moveTo(await riversApi.next()))
  }

  function changeGuess(value: string) {
    setGuess(value)
    if (question) riverDraftStorage.write(playerId, question.id, value)
  }

  function pickSuggestion(value: string) {
    changeGuess(value)
    clearSuggestions()
  }

  function finish(current: RiverQuestion, river: RevealedRiver, nextStats: RiverPlayerStats) {
    riverDraftStorage.write(playerId, current.id, '')
    setRevealed(river)
    setQuestion(null)
    setStats(nextStats)
    resetGuess()
  }

  function submitGuess() {
    const current = question
    const answer = guess.trim()
    if (!current || !answer || busy) return
    void run(async () => {
      const result = await riversApi.guess(current.id, answer)
      if (result.correct && result.reveal) {
        finish(current, { questionId: current.id, reveal: result.reveal, roundNumber: current.roundNumber, outcome: 'solved',
          xpAwarded: result.xpAwarded, attempts: result.attempts, imageUrl: current.imageUrl, course: current.course }, result.stats)
        return
      }
      setQuestion({ ...current, attempts: result.attempts })
      setStats(result.stats)
      setGuess('')
      setWrongGuesses(list => list.includes(answer) ? list : [...list, answer])
      clearSuggestions()
      riverDraftStorage.write(playerId, current.id, '')
    }, copy.guessError)
  }

  function revealClue() {
    const current = question
    if (!current) return
    void run(async () => {
      setQuestion(await riversApi.hint(current.id))
    })
  }

  function skip() {
    const current = question
    if (!current) return
    void run(async () => {
      riverDraftStorage.write(playerId, current.id, '')
      moveTo(await riversApi.skip(current.id))
    })
  }

  function showAnswer() {
    const current = question
    if (!current) return
    void run(async () => {
      const { reveal, stats: nextStats } = await riversApi.showAnswer(current.id)
      finish(current, { questionId: current.id, reveal, roundNumber: current.roundNumber, outcome: 'shown', xpAwarded: 0,
        attempts: current.attempts, imageUrl: current.imageUrl, course: current.course }, nextStats)
    })
  }

  return {
    authResolved, isAuthenticated, question, revealed, resumePending, stats, guess, wrongGuesses,
    suggestions, loading, busy, error, inGame,
    start, openMenu, nextRiver, changeGuess, pickSuggestion, submitGuess, revealClue, skip, showAnswer,
  }
}
