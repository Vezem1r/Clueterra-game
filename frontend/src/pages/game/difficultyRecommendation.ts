import type { ProfileDto } from '@/types'

export type DifficultyLevel = 'EASY' | 'MEDIUM' | 'HARD'

export function getProfileDifficultyRecommendation(profile: ProfileDto | null): DifficultyLevel | null {
  return profile?.difficultyRecommendation ?? null
}
