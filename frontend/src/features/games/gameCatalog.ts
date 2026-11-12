export type GameId =
  | 'cluesDaily'
  | 'budgetDaily'
  | 'cluesUnlimited'
  | 'budgetUnlimited'
  | 'silhouettes'
  | 'flags'
  | 'rivers'
  | 'countriesQuiz'

export type GameFamily = 'clues' | 'budget' | 'silhouette' | 'flag' | 'river' | 'quiz'
export type GameGroup = 'daily' | 'unlimited' | 'discover'

export interface CatalogGame {
  id: GameId
  path: string
  family: GameFamily
  group: GameGroup
}

export const GAME_CATALOG: readonly CatalogGame[] = [
  { id: 'cluesDaily', path: '/', family: 'clues', group: 'daily' },
  { id: 'budgetDaily', path: '/budget', family: 'budget', group: 'daily' },
  { id: 'cluesUnlimited', path: '/games/clueterra-unlimited', family: 'clues', group: 'unlimited' },
  { id: 'budgetUnlimited', path: '/games/budgetguessr-unlimited', family: 'budget', group: 'unlimited' },
  { id: 'silhouettes', path: '/games/country-silhouettes', family: 'silhouette', group: 'discover' },
  { id: 'flags', path: '/games/country-flags', family: 'flag', group: 'discover' },
  { id: 'rivers', path: '/games/rivers', family: 'river', group: 'discover' },
  { id: 'countriesQuiz', path: '/games/countries-quiz', family: 'quiz', group: 'discover' },
]

const COMING_SOON_GAME_IDS: ReadonlySet<GameId> = new Set([
  'cluesUnlimited', 'budgetUnlimited',
])

export function isGameAvailable(gameId: GameId) {
  return !COMING_SOON_GAME_IDS.has(gameId)
}
