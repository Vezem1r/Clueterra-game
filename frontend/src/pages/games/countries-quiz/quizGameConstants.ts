import {
  ArrowLeftRight, Banknote, Building2, Car, CircleSlash, Coins, Flag, Globe, Hash, HeartPulse, Landmark,
  Mountain, MountainSnow, Palmtree, Plane, Ruler, Shapes, Sprout, TreePine, Trees, Users, Waves, type LucideIcon,
} from 'lucide-react'
import type { QuizKind } from '@/api/quiz'

export const QUIZ_CANONICAL_PATH = '/games/countries-quiz'
export const QUIZ_GUIDE_PATH = '/games/countries-quiz/how-to'
export const QUIZ_GUIDE_QUERY = 'countries-quiz'
export const QUIZ_SHARE_IMAGE = '/images/share/countries-quiz.jpg'
export const QUIZ_ROUND_EXAMPLE = { src: '/images/guides/quiz/round.webp', width: 1600, height: 920 }
export const QUIZ_TRAVEL_EXAMPLE = { src: '/images/guides/quiz/step-travel.webp', width: 1000, height: 750 }
export const QUIZ_GOLD_EXAMPLE = { src: '/images/guides/quiz/step-gold.webp', width: 1000, height: 750 }
export const QUIZ_SEO_KEYWORDS =
  'countries of the world quiz, geography quiz, world quiz, capitals quiz, country quiz game, world tour geography game'
export const QUIZ_SPACE = '#04161c'
export const QUIZ_PERCENT = 100
export const QUIZ_QUESTIONS_PER_STOP = 2
export const QUIZ_MAX_LEVEL = 3
export const QUIZ_SUMMARY_POSITION = 0
export const QUIZ_OPTION_LETTERS = ['A', 'B', 'C', 'D'] as const
export const QUIZ_DRIVE_SECONDS = 2.7
export const QUIZ_FLY_SECONDS = 5.6

export const QUIZ_KIND_ICONS: Record<QuizKind, LucideIcon> = {
  FLAG: Flag,
  CAPITAL: Landmark,
  NEIGHBOUR: ArrowLeftRight,
  CURRENCY: Banknote,
  POPULATION: Users,
  AREA: Ruler,
  LEFT_DRIVING: Car,
  DOMAIN: Globe,
  LANDLOCKED: Waves,
  OUTLINE: Shapes,
  COASTLINE: Palmtree,
  TOURISTS: Plane,
  PEAK: MountainSnow,
  NEIGHBOUR_CAPITAL: Building2,
  HIGHEST_POINT: Mountain,
  NOT_NEIGHBOUR: CircleSlash,
  LIFE_EXPECTANCY: HeartPulse,
  WEALTH: Coins,
  HERITAGE: Trees,
  NEIGHBOUR_COUNT: Hash,
  FOREST: TreePine,
  MEDIAN_AGE: Sprout,
}

export interface QuizLevelPaint {
  fill: string
  edge: string
  glow: number
}

export const QUIZ_LEVEL_PAINT: Record<number, QuizLevelPaint> = {
  1: { fill: '#d9b48a', edge: '#f3d6b3', glow: 0 },
  2: { fill: '#e8865a', edge: '#ffc49e', glow: 0.45 },
  3: { fill: '#f2c14e', edge: '#fff2b8', glow: 1 },
}

export const QUIZ_LEVEL_BADGES: Record<number, string> = {
  1: 'bg-[#d9b48a]/15 text-[#f3d6b3] ring-[#d9b48a]/35',
  2: 'bg-[#e8865a]/15 text-[#ffc49e] ring-[#e8865a]/40',
  3: 'bg-[#f2c14e]/15 text-[#fff2b8] ring-[#f2c14e]/45',
}

export const QUIZ_COLORS = {
  ocean: '#0d4250',
  oceanDeep: '#08303b',
  grid: 'rgba(156, 210, 207, .08)',
  land: '#6a9b8f',
  landEdge: '#4a7a72',
  territory: '#4c706a',
  current: '#9fe0b5',
  currentEdge: '#cdeedb',
  next: '#8fc9e8',
  nextEdge: '#dff3ff',
  route: '#ffc9a0',
  routeActive: '#ffe7a3',
  contrail: '#ffffff',
  shadow: '#02141a',
  dust: '#e8cfa6',
  burst: '#fff2b8',
  car: '#e8634a',
  carTrim: '#2a2f38',
  carRoof: '#fff1d6',
  plane: '#f7f4ec',
  planeStripe: '#e8634a',
  planeWing: '#d9d4c7',
  atmosphere: '#8fd3c8',
  sunlight: '#fff4dc',
  carGlass: '#2b5f6c',
  tyre: '#15181d',
  headlight: '#fff3c4',
  taillight: '#ff5a4a',
  cockpit: '#2f6f80',
} as const
