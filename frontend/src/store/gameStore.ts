import { create } from 'zustand'
import { GAME_SESSION_STATUS, type GameSession, type Hint, type Country, type GuessResult, type GameSessionStatus } from '@/types'
import { gameApi, type GameStateResponse } from '@/api/game'
import { useAuthStore } from '@/store/authStore'
import { extractApiErrorMessage, getApiErrorCode } from '@/utils/errors'

type GamePhase = 'idle' | 'loading' | 'playing' | 'guessing' | 'result' | 'completed'
type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

interface CurrentCountry {
  countryId: string
  orderIndex: number
  totalHints: number
  hintsRevealed: number
  attemptsRemaining: number
  isGuessed: boolean
  xpEarned: number
}

interface SessionCountryData {
  id: string
  orderIndex: number
  totalHints: number
  hintsRevealed: number
  attemptsRemaining: number
  isGuessed: boolean
  xpEarned: number
  hints: Hint[]
  correctCountry?: Country
}

interface GameState {
  session: GameSession | null
  currentCountry: CurrentCountry | null
  hints: Hint[]
  countries: SessionCountryData[]
  phase: GamePhase
  isRevealingHint: boolean
  error: string | null
  lastGuessResult: GuessResult | null
  startGame: (level: Difficulty, legalAcceptance?: { termsVersion: string; privacyVersion: string }) => Promise<void>
  restoreSession: () => Promise<boolean>
  hasActiveSession: () => boolean
  revealHint: () => Promise<void>
  submitGuess: (countryCode: string) => Promise<void>
  resetGame: () => void
  nextCountry: () => Promise<void>
}

const initialState = {
  session: null,
  currentCountry: null,
  hints: [],
  countries: [],
  phase: 'idle' as GamePhase,
  isRevealingHint: false,
  error: null,
  lastGuessResult: null,
}

function mapHint(h: {
  id: string
  text?: string
  hintText?: string
  imageUrl?: string | null
  imageSourcePageUrl?: string | null
  imageAuthorName?: string | null
  imageAttributionText?: string | null
  imageLicenseLabel?: string | null
  imageLicenseUrl?: string | null
  difficulty: number
  category: string
  type: string
  source: string
}): Hint {
  return {
    id: h.id,
    hintText: h.hintText ?? h.text ?? '',
    imageUrl: h.imageUrl,
    imageSourcePageUrl: h.imageSourcePageUrl,
    imageAuthorName: h.imageAuthorName,
    imageAttributionText: h.imageAttributionText,
    imageLicenseLabel: h.imageLicenseLabel,
    imageLicenseUrl: h.imageLicenseUrl,
    difficulty: h.difficulty,
    category: h.category,
    type: h.type,
    source: h.source,
  }
}

function buildCurrentCountry(country: SessionCountryData | undefined): CurrentCountry | null {
  if (!country) return null
  return {
    countryId: country.id,
    orderIndex: country.orderIndex,
    totalHints: country.totalHints,
    hintsRevealed: country.hintsRevealed,
    attemptsRemaining: country.attemptsRemaining,
    isGuessed: country.isGuessed,
    xpEarned: country.xpEarned,
  }
}

function updateCurrentCountryData(
  session: GameSession,
  countries: SessionCountryData[],
  patch: Partial<SessionCountryData>
): Pick<GameState, 'countries' | 'currentCountry' | 'hints'> {
  const updatedCountries = countries.map((country, index) =>
    index === session.currentCountryIndex ? { ...country, ...patch } : country
  )
  const currentCountryData = updatedCountries[session.currentCountryIndex]
  return {
    countries: updatedCountries,
    currentCountry: buildCurrentCountry(currentCountryData),
    hints: currentCountryData?.hints ?? [],
  }
}

function mapResponseToState(response: GameStateResponse): Partial<GameState> {
  const isGuest = response.guest ?? (response.guestToken != null)
  const session: GameSession = {
    id: response.id,
    userId: null,
    guestToken: response.guestToken ?? null,
    guest: isGuest,
    challengeId: '0',
    level: response.level as 'EASY' | 'MEDIUM' | 'HARD',
    status: response.status as GameSessionStatus,
    currentCountryIndex: response.currentCountryIndex,
    startedAt: response.startedAt,
    activeTimeMs: response.activeTimeMs,
    completedAt: response.completedAt,
    totalXpEarned: response.totalXpEarned,
    xpBefore: response.xpBefore,
    levelBefore: response.levelBefore,
    countryCount: response.totalCountries,
    maxHintsPerCountry: response.maxHintsPerCountry ?? 5,
    maxAttempts: response.maxAttempts ?? 5,
  }

  const countries: SessionCountryData[] = (response.countries || []).map(c => {
    const guessed = c.isGuessed ?? (c as unknown as { guessed?: boolean }).guessed ?? false
    const cc = c.correctCountry as unknown as { id: number; code?: string; name?: string; isoAlpha2?: string; nameEn?: string; flagEmoji?: string; flagUrl?: string; region?: string } | undefined
    const correctCountry: Country | undefined = cc ? {
      id: String(cc.id),
      isoAlpha2: cc.code ?? cc.isoAlpha2 ?? '',
      nameEn: cc.name ?? cc.nameEn ?? '',
      flagEmoji: cc.flagEmoji,
      flagUrl: cc.flagUrl,
      region: cc.region ?? '',
    } : undefined

    return {
      id: c.id,
      orderIndex: c.order,
      totalHints: c.totalHints ?? response.maxHintsPerCountry ?? 0,
      hintsRevealed: c.hintsRevealed,
      attemptsRemaining: c.attemptsRemaining,
      isGuessed: guessed,
      xpEarned: c.xpEarned ?? 0,
      hints: (c.hints || []).map(mapHint),
      correctCountry,
    }
  })

  const currentCountryData = countries[response.currentCountryIndex]

  const currentCountry = buildCurrentCountry(currentCountryData)

  const hints = currentCountryData?.hints || []

  const phase = session.status === GAME_SESSION_STATUS.COMPLETED ? 'completed' : 'playing'

  return { session, currentCountry, hints, countries, phase }
}

export const useGameStore = create<GameState>((set, get) => ({
  ...initialState,

  hasActiveSession: () => {
    return get().session != null
  },

  startGame: async (level: Difficulty, legalAcceptance) => {
    set({ phase: 'loading', error: null })
    try {
      const response = useAuthStore.getState().isAuthenticated
        ? await gameApi.startUserSession(level)
        : await gameApi.startGuestSession(
          legalAcceptance?.termsVersion ?? '',
          legalAcceptance?.privacyVersion ?? '',
        )
      const state = mapResponseToState(response)
      set(state as Partial<GameState>)
    } catch (error) {
      console.error('Failed to start game:', error)
      set({
        error: getApiErrorCode(error) === 'CHALLENGE_GENERATING'
          ? 'Challenge is being generated. Please try again in a few seconds.'
          : extractApiErrorMessage(error, 'Failed to start game'),
        phase: 'idle',
      })
      throw error
    }
  },

  restoreSession: async () => {
    set({ phase: 'loading' })
    try {
      const response = await gameApi.getTodaySession()
      if (!response) {
        set({ phase: 'idle' })
        return false
      }
      const state = mapResponseToState(response)

      if (state.session?.status === GAME_SESSION_STATUS.COMPLETED) {
        set(state as Partial<GameState>)
        return true
      }

      set(state as Partial<GameState>)
      return true
    } catch {
      set({ phase: 'idle' })
      return false
    }
  },

  revealHint: async () => {
    const { session, countries, isRevealingHint } = get()
    if (!session || isRevealingHint) return

    set({ isRevealingHint: true })
    try {
      const result = await gameApi.revealHint(session.id)
      const currentHints = countries[session.currentCountryIndex]?.hints ?? []
      const newHint = result.hint

      set({
        ...updateCurrentCountryData(session, countries, {
          hintsRevealed: result.hintsRevealed,
          attemptsRemaining: result.attemptsRemaining,
          hints: [...currentHints, newHint],
        }),
      })
    } catch (error) {
      set({ error: extractApiErrorMessage(error, 'Failed to reveal hint') })
    } finally {
      set({ isRevealingHint: false })
    }
  },

  submitGuess: async (countryCode: string) => {
    const { session, countries } = get()
    if (!session) return

    const guessedAt = new Date().toISOString()
    set({ phase: 'guessing' })
    try {
      const result = await gameApi.submitGuess(session.id, countryCode)
      const currentCountryData = countries[session.currentCountryIndex]

      const updatedHints = result.nextHint
        ? [...(currentCountryData?.hints ?? []), result.nextHint]
        : (currentCountryData?.hints ?? [])

      set({
        ...updateCurrentCountryData(session, countries, {
          hintsRevealed: result.hintsRevealed,
          attemptsRemaining: result.attemptsRemaining,
          isGuessed: result.correct || currentCountryData?.isGuessed || false,
          xpEarned: result.xpEarned || currentCountryData?.xpEarned || 0,
          hints: updatedHints,
          correctCountry: result.correctCountry ?? currentCountryData?.correctCountry,
        }),
        lastGuessResult: result,
        phase: 'result',
        session: {
          ...session,
          totalXpEarned: session.totalXpEarned + (result.xpEarned || 0),
          ...(result.gameCompleted ? { completedAt: guessedAt, status: GAME_SESSION_STATUS.COMPLETED } : {}),
        },
      })

    } catch (error) {
      set({
        error: extractApiErrorMessage(error, 'Failed to submit guess'),
        phase: 'playing',
      })
    }
  },

  resetGame: () => {
    set(initialState)
  },

  nextCountry: async () => {
    const { session, lastGuessResult } = get()
    if (!session || !lastGuessResult) return

    if (lastGuessResult.gameCompleted) {
      set({ phase: 'completed' })
      return
    }

    try {
      const response = await gameApi.getSession(session.id)
      const nextState = mapResponseToState(response)
      set({
        ...(nextState as Partial<GameState>),
        lastGuessResult: null,
      })
    } catch (error) {
      set({
        error: extractApiErrorMessage(error, 'Failed to load next country'),
        phase: 'result',
      })
    }
  },
}))
