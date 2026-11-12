import { CaseSensitive, Compass, Landmark, type LucideIcon } from 'lucide-react'
import type { FlagClueKind } from '@/api/flags'

export const FLAG_CANONICAL_PATH = '/games/country-flags'
export const FLAG_GUIDE_PATH = '/games/country-flags/how-to'
export const FLAG_GUIDE_QUERY = 'flags'
export const FLAG_SHARE_IMAGE = '/images/share/flags.jpg'
export const FLAG_ROUND_EXAMPLE = { src: '/images/guides/flags/round.webp', width: 1600, height: 920 }
export const FLAG_PLAZA_EXAMPLE = { src: '/images/guides/flags/plaza.webp', width: 1400, height: 958 }
export const FLAG_CLUES_EXAMPLE = { src: '/images/guides/flags/step-clues.webp', width: 784, height: 376 }
export const FLAG_NAME_EXAMPLE = { src: '/images/guides/flags/step-name.webp', width: 784, height: 224 }
export const FLAG_SEO_KEYWORDS =
  'guess the flag, flag quiz, world flags quiz, country flags game, flags of the world, guess the country by flag'
export const FLAG_PERCENT = 100
export const FLAG_SPACE = '#05070d'
export const FLAG_IMAGE_RETRY_MS = 1200
export const FLAG_IMAGE_RETRIES = 2

export const FLAG_HINT_ICONS: Record<FlagClueKind, LucideIcon> = {
  REGION: Compass,
  INITIAL: CaseSensitive,
  CAPITAL: Landmark,
}
