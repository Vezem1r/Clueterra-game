import { useMemo } from 'react'
import type { GameResults, ResultCountry } from '@/types'

export interface RunInsights {
  accuracyRate: number
  totalAttempts: number
  avgHintsSeen: number
  avgAttemptsPerCountry: number
  firstTryHits: number
  missedCountries: number
  bestCountry: ResultCountry | null
  toughestCountry: ResultCountry | null
}

const DIFFICULTY_ATTEMPT_WEIGHT = 3
const DIFFICULTY_MISS_WEIGHT = 3

function scoreDifficulty(country: ResultCountry): number {
  return (
    country.attempts.length * DIFFICULTY_ATTEMPT_WEIGHT +
    country.hintsSeen +
    (country.guessed ? 0 : DIFFICULTY_MISS_WEIGHT)
  )
}

function isBetterBest(candidate: ResultCountry, current: ResultCountry): boolean {
  if (candidate.xpEarned !== current.xpEarned) return candidate.xpEarned > current.xpEarned
  if (candidate.guessed !== current.guessed) return candidate.guessed
  if (candidate.hintsSeen !== current.hintsSeen) return candidate.hintsSeen < current.hintsSeen
  return candidate.attempts.length < current.attempts.length
}

function isHarder(candidate: ResultCountry, current: ResultCountry): boolean {
  const candidateScore = scoreDifficulty(candidate)
  const currentScore = scoreDifficulty(current)
  if (candidateScore !== currentScore) return candidateScore > currentScore
  if (candidate.attempts.length !== current.attempts.length) return candidate.attempts.length > current.attempts.length
  if (candidate.hintsSeen !== current.hintsSeen) return candidate.hintsSeen > current.hintsSeen
  return !candidate.guessed && current.guessed
}

export function useRunInsights(results: GameResults): RunInsights {
  return useMemo(() => {
    let totalAttempts = 0
    let correctAttempts = 0
    let totalHintsSeen = 0
    let firstTryHits = 0
    let bestCountry: ResultCountry | null = null
    let toughestCountry: ResultCountry | null = null

    for (const country of results.countries) {
      totalAttempts += country.attempts.length
      totalHintsSeen += country.hintsSeen
      for (const attempt of country.attempts) {
        if (attempt.correct) correctAttempts += 1
      }
      if (country.guessed && country.attempts[0]?.correct) firstTryHits += 1
      if (!bestCountry || isBetterBest(country, bestCountry)) bestCountry = country
      if (!toughestCountry || isHarder(country, toughestCountry)) toughestCountry = country
    }

    const totalCountries = results.totalCountries
    const avgHintsSeen = totalCountries > 0 ? totalHintsSeen / totalCountries : 0
    const avgAttemptsPerCountry = totalCountries > 0 ? totalAttempts / totalCountries : 0
    const accuracyRate = totalAttempts > 0 ? (correctAttempts / totalAttempts) * 100 : 0
    const missedCountries = totalCountries - results.guessedCountries

    return {
      accuracyRate,
      totalAttempts,
      avgHintsSeen,
      avgAttemptsPerCountry,
      firstTryHits,
      missedCountries,
      bestCountry,
      toughestCountry,
    }
  }, [results])
}
