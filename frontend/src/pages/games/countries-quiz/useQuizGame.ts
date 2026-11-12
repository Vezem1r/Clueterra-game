import { useCallback, useEffect, useRef, useState } from 'react'
import { legalApi } from '@/api/legal'
import { quizApi, type QuizCountryLevel, type QuizSession, type QuizStop, type QuizTravelMode } from '@/api/quiz'
import type { TranslationKeys } from '@/i18n'
import { useAuthStore } from '@/store/authStore'
import { extractApiErrorMessage } from '@/utils/errors'
import { isStaleRound, useRoundSync } from '../globe-game/useRoundSync'
import { QUIZ_SUMMARY_POSITION } from './quizGameConstants'

export interface QuizJourney {
  id: number
  from: string
  to: string
  mode: QuizTravelMode
  route: string[]
}

const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'

function firstOpenPosition(session: QuizSession | null) {
  const questions = session?.current?.questions ?? []
  const open = questions.find(question => question.answer == null)
  return open?.position ?? QUIZ_SUMMARY_POSITION
}

function withLevel(countries: QuizCountryLevel[], stop: QuizStop): QuizCountryLevel[] {
  const code = stop.country.countryCode
  const reached = { countryCode: code, level: stop.level, levels: stop.levels }
  return countries.some(country => country.countryCode === code)
    ? countries.map(country => country.countryCode === code ? { ...country, level: Math.max(country.level, stop.level) } : country)
    : [...countries, reached]
}

export function useQuizGame(copy: TranslationKeys['quizGame']) {
  const { authResolved, isAuthenticated } = useAuthStore()
  const [session, setSession] = useState<QuizSession | null>(null)
  const [position, setPosition] = useState(QUIZ_SUMMARY_POSITION)
  const [resumePending, setResumePending] = useState(false)
  const [journey, setJourney] = useState<QuizJourney | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const arrivalRef = useRef<QuizSession | null>(null)
  const current = session?.current ?? null
  const inGame = journey != null || (current != null && !resumePending)

  useEffect(() => {
    if (!authResolved) return
    let cancelled = false
    quizApi.session()
      .then(loaded => {
        if (cancelled) return
        setSession(loaded)
        setPosition(firstOpenPosition(loaded))
        setResumePending(loaded.current != null)
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

  const currentStop = current?.stop ?? null
  const syncStop = useCallback(() => {
    if (currentStop == null || journey) return
    quizApi.session()
      .then(loaded => {
        setSession(loaded)
        if (loaded.current?.stop === currentStop) return
        setPosition(firstOpenPosition(loaded))
        setError(copy.roundMoved)
      })
      .catch(() => undefined)
  }, [currentStop, journey, copy.roundMoved])

  useRoundSync(authResolved, syncStop)

  function apply(next: QuizSession) {
    setSession(next)
    setPosition(firstOpenPosition(next))
    setResumePending(false)
  }

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError('')
    try {
      await action()
    } catch (actionError) {
      if (isStaleRound(actionError)) {
        await quizApi.session().then(apply).catch(() => undefined)
        setError(copy.roundMoved)
      } else {
        setError(extractApiErrorMessage(actionError, copy.actionError))
      }
    } finally {
      setBusy(false)
    }
  }

  function start() {
    if (current && resumePending) {
      setResumePending(false)
      return
    }
    void run(async () => {
      if (isAuthenticated) {
        apply(await quizApi.startRegistered())
        return
      }
      const legal = await legalApi.getCurrent()
      apply(await quizApi.startGuest(legal.termsVersion, legal.privacyVersion))
    })
  }

  function openMenu() {
    setResumePending(current != null)
  }

  function answer(option: number) {
    const stop = current
    if (!stop || busy) return
    const question = stop.questions.find(item => item.position === position)
    if (!question || question.answer) return
    void run(async () => {
      const result = await quizApi.answer(stop.stop, question.position, option)
      setSession(previous => previous && {
        ...previous,
        current: result.stop,
        stats: result.stats,
        complete: result.complete,
        countries: result.stop.finished ? withLevel(previous.countries, result.stop) : previous.countries,
      })
    })
  }

  function nextQuestion() {
    const stop = current
    if (!stop) return
    const open = stop.questions.find(question => question.answer == null)
    setPosition(open?.position ?? QUIZ_SUMMARY_POSITION)
  }

  function travel() {
    const stop = current
    if (!stop?.next || busy || journey) return
    const destination = stop.next
    void run(async () => {
      const arrived = await quizApi.travel(stop.stop)
      if (window.matchMedia(REDUCED_MOTION_QUERY).matches) {
        apply(arrived)
        return
      }
      arrivalRef.current = arrived
      setJourney({
        id: Date.now(), from: stop.country.countryCode, to: destination.country.countryCode, mode: destination.mode,
        route: destination.route,
      })
    })
  }

  const arrive = useCallback(() => {
    const arrived = arrivalRef.current
    arrivalRef.current = null
    setJourney(null)
    if (arrived) {
      setSession(arrived)
      setPosition(firstOpenPosition(arrived))
    }
  }, [])

  return {
    authResolved, isAuthenticated, session, current, position, resumePending, journey, loading, busy, error, inGame,
    start, openMenu, answer, nextQuestion, travel, arrive,
  }
}
