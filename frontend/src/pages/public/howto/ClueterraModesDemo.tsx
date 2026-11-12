import { useMemo, useState } from 'react'

import { useTodayChallenges } from '@/hooks/useGameQueries'
import { useTranslation } from '@/i18n'
import { buildDifficultyOptions } from '@/pages/game/home/difficultyOptions'
import { HomeDifficultySlider } from '@/pages/game/home/HomeDifficultySlider'
import { HomeModeStatsStrip } from '@/pages/game/home/HomeModeStatsStrip'
import type { HomeDifficultyValue } from '@/pages/game/home/types'
import { useAuthStore } from '@/store/authStore'

export function ClueterraModesDemo() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuthStore()
  const { data: challenges = [] } = useTodayChallenges()
  const [value, setValue] = useState<HomeDifficultyValue>('EASY')

  const options = useMemo(
    () => buildDifficultyOptions(challenges, isAuthenticated, t),
    [challenges, isAuthenticated, t],
  )
  const selected = options.find((option) => option.value === value) ?? options[0]

  return (
    <div className="space-y-4">
      <HomeDifficultySlider onChange={setValue} options={options} value={value} />
      <HomeModeStatsStrip option={selected} />
    </div>
  )
}
