import { useCallback, useMemo, useState } from 'react'

import type { TranslationKeys } from '@/i18n/en'
import type { Hint } from '@/types'

export const DEMO_ANSWER_ISO = 'PT'

const DEMO_CATEGORIES = ['DEMOGRAPHICS', 'ECONOMY', 'GEOGRAPHY', 'MUSIC', 'LANDMARKS'] as const
const DEMO_DIFFICULTY_START = 20
const DEMO_DIFFICULTY_STEP = 15

export interface ClueterraDemoRound {
  hints: Hint[]
  opened: number
  solved: boolean
  openNext: () => void
  markSolved: () => void
  reset: () => void
}

export function useClueterraDemoRound(t: TranslationKeys): ClueterraDemoRound {
  const [opened, setOpened] = useState(1)
  const [solved, setSolved] = useState(false)

  const hints = useMemo<Hint[]>(() => {
    const texts = [
      t.howToPage.demoClue1,
      t.howToPage.demoClue2,
      t.howToPage.demoClue3,
      t.howToPage.demoClue4,
      t.howToPage.demoClue5,
    ]
    return texts.map((hintText, index) => ({
      id: `demo-${index + 1}`,
      hintText,
      category: DEMO_CATEGORIES[index],
      difficulty: DEMO_DIFFICULTY_START + index * DEMO_DIFFICULTY_STEP,
    }))
  }, [t])

  const openNext = useCallback(() => setOpened((current) => Math.min(hints.length, current + 1)), [hints.length])
  const markSolved = useCallback(() => setSolved(true), [])
  const reset = useCallback(() => {
    setOpened(1)
    setSolved(false)
  }, [])

  return { hints, opened, solved, openNext, markSolved, reset }
}
