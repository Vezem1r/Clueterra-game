import { Building2, Compass, Flag, GitMerge, MapPin, Ruler, Type, type LucideIcon } from 'lucide-react'
import type { RiverClueSlot, RiverDifficulty, RiverHintKind } from '@/api/rivers'

export const RIVER_CANONICAL_PATH = '/games/rivers'
export const RIVER_GUIDE_PATH = '/games/rivers/how-to'
export const RIVER_GUIDE_QUERY = 'river'
export const RIVER_GAMES_PATH = '/games'
export const RIVER_HOME_PATH = '/'
export const RIVER_HOME_LABEL = 'Clueterra'
export const RIVER_BREADCRUMB_LABEL = 'Breadcrumb'
export const RIVER_PERCENT = 100
export const RIVER_DATA_SOURCES_PATH = '/data-sources'
export const RIVER_SHARE_IMAGE = '/images/share/rivers.jpg'
export const RIVER_EXAMPLE_MAP = '/images/guides/rivers/globe.webp'
export const RIVER_SEO_KEYWORDS =
  'guess the river, guess the river game, rivers of the world quiz, world rivers quiz, river quiz, river map quiz, river geography game'

export const RIVER_CLUE_SLOTS: RiverClueSlot[] = ['GEOGRAPHY', 'PLACE', 'NAME']
export const RIVER_SLOT_ICONS: Record<RiverClueSlot, LucideIcon> = {
  GEOGRAPHY: Compass,
  PLACE: MapPin,
  NAME: Type,
}
export const RIVER_KIND_ICONS: Record<RiverHintKind, LucideIcon> = {
  GEOGRAPHY: Compass,
  CITY: Building2,
  TRIBUTARY: GitMerge,
  COUNTRIES: Flag,
  NAME: Type,
  LENGTH: Ruler,
}
export const RIVER_CLUE_COUNT = RIVER_CLUE_SLOTS.length
export const RIVER_BORDERS_CLUE = 2

export const RIVER_NAME_MIN_QUERY = 2
export const RIVER_NAME_SEARCH_DELAY_MS = 180
export const RIVER_NAME_SUGGESTION_LIMIT = 6

export const RIVER_GUEST_PLAYER = 'guest'

export const RIVER_DIFFICULTY_BADGE: Record<RiverDifficulty, string> = {
  EASY: 'bg-emerald-300/10 text-river-delta ring-emerald-300/25',
  MEDIUM: 'bg-amber-300/10 text-river-silt ring-amber-300/25',
  HARD: 'bg-rose-300/10 text-rose-200 ring-rose-300/25',
}
