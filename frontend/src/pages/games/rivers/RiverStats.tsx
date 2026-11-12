import { CircleCheck, Compass, Sparkles } from 'lucide-react'
import type { RiverPlayerStats } from '@/api/rivers'
import type { TranslationKeys } from '@/i18n'
import { GlobeGameStats } from '../globe-game/GlobeGameStats'

interface RiverStatsProps {
  copy: TranslationKeys['riverGame']
  stats: RiverPlayerStats | null
}

const EMPTY = '—'

function format(value: number | undefined) {
  return value == null ? EMPTY : value.toLocaleString()
}

export function RiverStats({ copy, stats }: RiverStatsProps) {
  return <GlobeGameStats label={copy.statsLabel} items={[
    { value: format(stats?.attemptedRivers), label: copy.stats.explored, icon: Compass, tone: 'bg-sky-300/12 text-sky-200' },
    { value: format(stats?.solvedRivers), label: copy.stats.guessed, icon: CircleCheck, tone: 'bg-emerald-300/12 text-river-delta' },
    { value: format(stats?.xpAwarded), label: copy.stats.xp, icon: Sparkles, tone: 'bg-amber-300/12 text-river-silt' },
  ]} />
}
