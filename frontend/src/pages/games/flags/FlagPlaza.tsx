import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import type { RiverPadding } from '../rivers/globe/riverMercator'
import type { CountryPool } from '../silhouettes/silhouetteWorld'
import type { FlagPlazaRegionKey } from './flagPlazaConstants'
import { layoutFlagPlaza, plazaCountries, regionOfSubregion } from './flagPlazaLayout'
import { FlagPlazaScene, type FlagPlazaFocus } from './FlagPlazaScene'
import { useFlagAtlas } from './useFlagAtlas'

export type FlagPlazaTarget =
  | { kind: 'overview' }
  | { kind: 'region'; subregion: string }
  | { kind: 'country'; iso: string }

interface FlagPlazaProps {
  copy: TranslationKeys['flagGame']['plaza']
  regions: Record<FlagPlazaRegionKey, string>
  language: SilhouetteLocale
  world: CountryPool | null
  solved: string[]
  target: FlagPlazaTarget
  padding: RiverPadding
  className?: string
  onUserMove?: () => void
}

interface PoleSelection {
  index: number
  target: FlagPlazaTarget
}

const OVERVIEW: FlagPlazaFocus = { kind: 'overview' }
const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'
const LABEL_GAP_PX = 12
const HOVER_DWELL_MS = 160

function subscribeVisibility(notify: () => void) {
  document.addEventListener('visibilitychange', notify)
  return () => document.removeEventListener('visibilitychange', notify)
}

function subscribeReducedMotion(notify: () => void) {
  const query = window.matchMedia(REDUCED_MOTION_QUERY)
  query.addEventListener('change', notify)
  return () => query.removeEventListener('change', notify)
}

export function FlagPlaza({ copy, regions, language, world, solved, target, padding, className, onUserMove }: FlagPlazaProps) {
  const layout = useMemo(() => (world ? layoutFlagPlaza(plazaCountries(world)) : null), [world])
  const atlas = useFlagAtlas(layout?.slots ?? null, solved)
  const [hovered, setHovered] = useState<number | null>(null)
  const dwell = useRef<number | null>(null)
  const [selection, setSelection] = useState<PoleSelection | null>(null)
  const hidden = useSyncExternalStore(subscribeVisibility, () => document.hidden, () => false)
  const calm = useSyncExternalStore(subscribeReducedMotion,
    () => window.matchMedia(REDUCED_MOTION_QUERY).matches, () => false)
  const solvedSet = useMemo(() => new Set(solved), [solved])
  const raised = useMemo(() => (layout?.slots ?? []).map(slot => solvedSet.has(slot.country.iso)), [layout, solvedSet])
  const slotByIso = useMemo(() => new Map((layout?.slots ?? []).map(slot => [slot.country.iso, slot.index])), [layout])
  const selected = selection && selection.target === target ? selection.index : null

  const focus = useMemo<FlagPlazaFocus>(() => {
    if (selected !== null) return { kind: 'slot', index: selected }
    if (target.kind === 'country') {
      const index = slotByIso.get(target.iso)
      return index === undefined ? OVERVIEW : { kind: 'slot', index }
    }
    if (target.kind === 'region') return { kind: 'region', region: regionOfSubregion(target.subregion) }
    return OVERVIEW
  }, [selected, target, slotByIso])

  const select = useCallback((index: number) => setSelection({ index, target }), [target])
  const hover = useCallback((index: number | null) => {
    if (dwell.current !== null) window.clearTimeout(dwell.current)
    dwell.current = null
    if (index === null) {
      setHovered(null)
      return
    }
    dwell.current = window.setTimeout(() => setHovered(index), HOVER_DWELL_MS)
  }, [])

  useEffect(() => () => {
    if (dwell.current !== null) window.clearTimeout(dwell.current)
  }, [])
  const labelled = hovered ?? selected
  const labelledSlot = layout && labelled !== null ? layout.slots[labelled] : null

  return <div className={cn(className, hovered !== null && raised[hovered] && 'cursor-pointer')} role="img" aria-label={copy.label}>
    {layout && <FlagPlazaScene layout={layout} raised={raised} regionNames={regions} atlas={atlas} focus={focus} selected={selected} hovered={hovered}
      calm={calm} paused={hidden} padding={padding} onHover={hover} onSelect={select} onUserMove={onUserMove} />}
    {labelledSlot && <div aria-live="polite"
      className="pointer-events-none absolute z-10 -translate-x-1/2 rounded-full border border-white/[.12] bg-black/70 px-3.5 py-1.5 text-xs font-semibold text-white/90 backdrop-blur-md"
      style={{ top: padding.top + LABEL_GAP_PX, left: `calc(50% + ${(padding.left - padding.right) / 2}px)` }}>
      {raised[labelledSlot.index] ? labelledSlot.country.names[language] : copy.unknown}
      <span className="ml-2 font-normal text-white/45">{regions[labelledSlot.country.region]}</span>
    </div>}
  </div>
}
