import { useLayoutEffect } from 'react'

export type TodayStateGame = 'clueterra' | 'budget'
export type TodayStateStatus = 'completed' | 'in_progress'

type TodayStateHint = { date: string } & Partial<Record<TodayStateGame, TodayStateStatus>>

const STORAGE_KEY = 'clueterra:today-state'
const ATTRIBUTE_PREFIX = 'data-today-'
const GAMES: TodayStateGame[] = ['clueterra', 'budget']

function utcToday() {
  return new Date().toISOString().slice(0, 10)
}

function readHint(): TodayStateHint | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as TodayStateHint) : null
  } catch {
    return null
  }
}

function writeHint(hint: TodayStateHint) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(hint))
  } catch {
    return
  }
}

export function rememberTodayState(game: TodayStateGame, status: TodayStateStatus | null) {
  if (typeof window === 'undefined') return

  const today = utcToday()
  const current = readHint()
  const next: TodayStateHint = current?.date === today ? { ...current } : { date: today }
  if (status) {
    next[game] = status
  } else {
    delete next[game]
  }
  writeHint(next)

  const attribute = ATTRIBUTE_PREFIX + game
  if (status) {
    document.documentElement.setAttribute(attribute, status)
  } else {
    document.documentElement.removeAttribute(attribute)
  }
}

export function forgetTodayStates() {
  if (typeof window === 'undefined') return

  GAMES.forEach((game) => document.documentElement.removeAttribute(ATTRIBUTE_PREFIX + game))
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    return
  }
}

export function useTodayStateHint(game: TodayStateGame, status: TodayStateStatus | null | undefined) {
  useLayoutEffect(() => {
    if (status === undefined) return
    rememberTodayState(game, status)
  }, [game, status])
}
