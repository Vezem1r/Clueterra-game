import { HomeDifficultyDesktopRail } from './HomeDifficultyDesktopRail'
import { HomeDifficultyMobileCarousel } from './HomeDifficultyMobileCarousel'
import type { HomeDifficultyOption, HomeDifficultyValue } from './types'

interface HomeDifficultySliderProps {
  onChange: (value: HomeDifficultyValue) => void
  options: HomeDifficultyOption[]
  recommendedValue?: HomeDifficultyValue | null
  value: HomeDifficultyValue
}

export function HomeDifficultySlider({
  onChange,
  options,
  recommendedValue,
  value,
}: HomeDifficultySliderProps) {
  return (
    <div>
      <HomeDifficultyMobileCarousel
        onChange={onChange}
        options={options}
        recommendedValue={recommendedValue}
        value={value}
      />
      <HomeDifficultyDesktopRail
        onChange={onChange}
        options={options}
        recommendedValue={recommendedValue}
        value={value}
      />
    </div>
  )
}
