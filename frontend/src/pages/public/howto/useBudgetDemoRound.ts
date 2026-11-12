import { useCallback, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import type {
  BudgetAnsweredQuestion,
  BudgetChallengeQuestion,
  BudgetImage,
  BudgetPromptProject,
  BudgetRevealedHint,
} from '@/api/budget'
import { projectPagesApi } from '@/api/projectPages'

export const DEMO_PROJECT_SLUG = 'allianz-arena'
export const DEMO_FULL_XP_THRESHOLD_PERCENT = 20

const DEMO_PROJECT_STALE_TIME = 1000 * 60 * 60
const DEMO_XP_CURVE_POWER = 0.75
const DEMO_BASE_XP = 200
const DEMO_COST_USD = 422_560_000
const DEMO_COST_EUR = 340_000_000
const DEMO_SOURCE_NAME = 'allianz-arena.com'
const DEMO_SOURCE_URL = 'https://allianz-arena.com/en/arena/facts/general-information'
const DEMO_CAPACITY = 75_024
const DEMO_COMPLETION_YEAR = 2005
const COMPARABLE_PROJECT_NAME = 'State Farm Stadium'
const COMPARABLE_COST_USD = 455_000_000
const RANGE_MINIMUM_USD = 300_000_000
const RANGE_MAXIMUM_USD = 600_000_000
const FIRST_HINT_MULTIPLIER = 85
const SECOND_HINT_MULTIPLIER = 60
const FULL_MULTIPLIER = 100

export interface BudgetDemoRound {
  question: BudgetChallengeQuestion
  answer: BudgetAnsweredQuestion | null
  revealHint: (index: number) => Promise<void>
  submitGuess: (value: number) => Promise<void>
  reset: () => void
}

function demoHint(index: number): BudgetRevealedHint {
  const revealedAt = new Date().toISOString()
  return index === 0
    ? {
        index,
        multiplierPercent: FIRST_HINT_MULTIPLIER,
        revealedAt,
        payload: {
          type: 'COMPARABLE_PROJECT',
          multiplierPercent: FIRST_HINT_MULTIPLIER,
          projectName: COMPARABLE_PROJECT_NAME,
          value: COMPARABLE_COST_USD,
          unit: 'USD',
        },
      }
    : {
        index,
        multiplierPercent: SECOND_HINT_MULTIPLIER,
        revealedAt,
        payload: {
          type: 'ANSWER_RANGE',
          multiplierPercent: SECOND_HINT_MULTIPLIER,
          minimum: RANGE_MINIMUM_USD,
          maximum: RANGE_MAXIMUM_USD,
          unit: 'USD',
        },
      }
}

function rawXpPercent(guess: number, answer: number) {
  const threshold = DEMO_FULL_XP_THRESHOLD_PERCENT / 100
  const relativeError = Math.abs(guess - answer) / answer
  if (relativeError <= threshold) return 100
  const fraction = Math.max(0, 1 - (relativeError - threshold) / (1 - threshold))
  return Math.round(100 * fraction ** DEMO_XP_CURVE_POWER * 100) / 100
}

function useDemoProjectImage(): BudgetImage | null {
  const { data } = useQuery({
    queryKey: ['project-page', DEMO_PROJECT_SLUG],
    queryFn: () => projectPagesApi.getProject(DEMO_PROJECT_SLUG),
    staleTime: DEMO_PROJECT_STALE_TIME,
    retry: false,
  })

  return useMemo(() => {
    const image = data?.project.image
    if (!image?.url) return null
    return {
      url: image.url,
      altText: image.alt,
      authorName: image.author,
      attributionText: image.attribution,
      licenseLabel: image.license,
      licenseUrl: image.licenseUrl,
      sourcePageUrl: image.sourcePageUrl,
    }
  }, [data])
}

export function useBudgetDemoRound(countryName: string): BudgetDemoRound {
  const image = useDemoProjectImage()
  const [revealedHints, setRevealedHints] = useState<BudgetRevealedHint[]>([])
  const [answer, setAnswer] = useState<BudgetAnsweredQuestion | null>(null)

  const project = useMemo<BudgetPromptProject>(() => ({
    name: 'Allianz Arena',
    countryCode: 'DE',
    countryName,
    category: 'STADIUM',
    lifecycleStatus: 'COMPLETED',
    completionYear: DEMO_COMPLETION_YEAR,
  }), [countryName])

  const question = useMemo<BudgetChallengeQuestion>(() => ({
    variantId: 'demo-allianz-arena',
    position: 1,
    type: 'HOW_MUCH',
    difficulty: 'MEDIUM',
    prompt: {
      project,
      metric: { type: 'CAPACITY', value: DEMO_CAPACITY, unit: 'seats' },
    },
    availableHintCount: 2,
    revealedHints,
    primaryImage: image,
    comparisonImage: null,
  }), [image, project, revealedHints])

  const revealHint = useCallback(async (index: number) => {
    setRevealedHints((current) => (current.some((hint) => hint.index === index) ? current : [...current, demoHint(index)]))
  }, [])

  const submitGuess = useCallback(async (value: number) => {
    const raw = rawXpPercent(value, DEMO_COST_USD)
    const multiplier = revealedHints.reduce((lowest, hint) => Math.min(lowest, hint.multiplierPercent), FULL_MULTIPLIER)
    const xpPercent = Math.round(raw * multiplier) / 100
    setAnswer({
      question,
      guessedValue: value,
      guessedChoice: null,
      correctValue: DEMO_COST_USD,
      correctUnit: 'USD',
      correctChoice: null,
      accuracyPercent: Math.round((Math.min(value, DEMO_COST_USD) / Math.max(value, DEMO_COST_USD)) * 10000) / 100,
      rawXpPercent: raw,
      rawXpEarned: Math.round((DEMO_BASE_XP * raw) / 100),
      hintMultiplierPercent: multiplier,
      hintsUsed: revealedHints.length,
      xpPercent,
      xpEarned: Math.round((DEMO_BASE_XP * xpPercent) / 100),
      answeredAt: new Date().toISOString(),
      reveal: {
        project,
        actualCost: { amountUsd: DEMO_COST_USD, sourceName: DEMO_SOURCE_NAME, sourceUrl: DEMO_SOURCE_URL },
        metrics: [{ type: 'CAPACITY', value: DEMO_CAPACITY, unit: 'seats', sourceName: DEMO_SOURCE_NAME, sourceUrl: DEMO_SOURCE_URL }],
        explanation: {
          conversion: {
            sourceAmountLow: DEMO_COST_EUR,
            sourceAmountHigh: DEMO_COST_EUR,
            sourceCurrency: 'EUR',
            amountUsdLow: DEMO_COST_USD,
            amountUsdHigh: DEMO_COST_USD,
          },
        },
      },
    })
  }, [project, question, revealedHints])

  const reset = useCallback(() => {
    setRevealedHints([])
    setAnswer(null)
  }, [])

  return { question, answer, revealHint, submitGuess, reset }
}
