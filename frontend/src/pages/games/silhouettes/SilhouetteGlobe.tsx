import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { useReducedMotion } from 'framer-motion'
import { Flag, Globe2, Minus, Palette, Plus } from 'lucide-react'
import type { GeoJSONSource, Map as MapLibreMap, MapLayerMouseEvent } from 'maplibre-gl'
import type { SilhouetteBounds, SilhouettePoint } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import { cn } from '@/utils/cn'
import { GlobeControl } from '../rivers/globe/GlobeControl'
import { RIVER_DESKTOP_QUERY } from '../rivers/globe/riverGlobeConstants'
import type { RiverPadding } from '../rivers/globe/riverMercator'
import { SILHOUETTE_COLORS, SILHOUETTE_GLOBE, SILHOUETTE_HOVER, SILHOUETTE_MISS, type SilhouettePaintMode } from './silhouetteGameConstants'
import {
  buildSilhouetteGlobeStyle,
  DOT_LAYER,
  DOT_SOURCE,
  FOCUS_FILL_LAYER,
  FOCUS_GLOW_LAYER,
  FOCUS_LAYER,
  HINT_DOT_LAYER,
  HINT_FILL_LAYER,
  HINT_LINE_LAYER,
  HOVER_LAYERS,
  isoFilter,
  isoListFilter,
  MISSED_DOT_LAYER,
  MISSED_FILL_LAYER,
  MISSED_LINE_LAYER,
  PAINTED_EDGE_LAYER,
  PAINTED_GLOW_LAYER,
  PAINTED_LAYER,
  tonedWorld,
  tinyCountryDots,
  DRAPE_BEFORE_LAYER,
  flagPattern,
  STRIPES_LAYER,
  STRIPES_PREFIX,
  REGION_FILL_LAYER,
  REGION_LINE_LAYER,
  regionFilter,
  WORLD_SOURCE,
} from './silhouetteGlobeStyle'
import { drapeFlag } from './silhouetteFlagDrape'
import { angularDistance, missMark, type SilhouetteGlobeMiss, type SilhouetteMissMark } from './silhouetteMissArrow'
import { SilhouetteMissMarks } from './SilhouetteMissMarks'
import type { SilhouetteWorld } from './silhouetteWorld'

export type SilhouetteCamera =
  | { kind: 'idle' }
  | { kind: 'hold' }
  | { kind: 'region', center: SilhouettePoint }
  | { kind: 'country', center: SilhouettePoint, bounds: SilhouetteBounds }
  | { kind: 'location', center: SilhouettePoint, bounds: SilhouetteBounds }

interface SilhouetteGlobeProps {
  copy: TranslationKeys['silhouetteGame']['globe']
  world: SilhouetteWorld | null
  solved: string[]
  focusIso: string | null
  camera: SilhouetteCamera
  hintIso: string | null
  missed: string[]
  misses: SilhouetteGlobeMiss[]
  regionKey: string | null
  padding: RiverPadding
  chromeOffset: number
  chromeRight: number
  renderCard: (iso: string) => ReactNode
  paint: SilhouettePaintToggle
  className?: string
  controlsClassName?: string
  controlsStyle?: CSSProperties
  onUserMove?: () => void
}

export interface SilhouettePaintToggle {
  mode: SilhouettePaintMode
  label: string
  hint: string
  onToggle: () => void
}

interface HoveredCountry {
  iso: string
  x: number
  y: number
}

const INTERACTION_EVENTS = ['mousedown', 'touchstart', 'wheel'] as const
const MS_PER_SECOND = 1000
const VISIBLE_ANGLE_DEGREES = 75
const DEGREES_TO_RADIANS = Math.PI / 180
const MIN_LATITUDE_SCALE = 0.2
const MIN_SPAN_DEGREES = 2
const MIN_VIEW_PX = 120
const FULL_LONGITUDE = 360
const WORLD_TILE_PX = 512
const ZOOM_STEP = 1
const CARD_GAP_PX = 18
const CARD_FLIP_PX = 320
const SOLVED_LAYERS = [PAINTED_LAYER, STRIPES_LAYER, PAINTED_EDGE_LAYER, PAINTED_GLOW_LAYER, DOT_LAYER]
const FOCUS_LAYERS = [FOCUS_FILL_LAYER, FOCUS_LAYER, FOCUS_GLOW_LAYER]
const HINT_LAYERS = [HINT_FILL_LAYER, HINT_LINE_LAYER, HINT_DOT_LAYER]
const REGION_LAYERS = [REGION_FILL_LAYER, REGION_LINE_LAYER]
const MISSED_LAYERS = [MISSED_FILL_LAYER, MISSED_LINE_LAYER, MISSED_DOT_LAYER]
const DRAPE_PREFIX = 'drape-'
const NO_MARKS: SilhouetteMissMark[] = []
const DRAPE_OPACITY = 0.96
const DRAPE_FADE_MS = 900
const CHROME_OFFSET_VARIABLE = '--silhouette-globe-chrome'
const CHROME_RIGHT_VARIABLE = '--silhouette-globe-chrome-right'
const COMPACT_ATTRIBUTION = 'maplibregl-compact'
const COMPACT_ATTRIBUTION_OPEN = 'maplibregl-compact-show'
const CHROME = 'globe-attribution absolute inset-0 transition-opacity duration-700 [&_.maplibregl-ctrl-bottom-left]:!bottom-[var(--silhouette-globe-chrome)] [&_.maplibregl-ctrl-bottom-right]:!bottom-[var(--silhouette-globe-chrome)] [&_.maplibregl-ctrl-bottom-right]:!z-20 [&_.maplibregl-ctrl-bottom-right]:!right-[var(--silhouette-globe-chrome-right)]'

const loadMapLibre = () => Promise.all([
  import('maplibre-gl'),
  import('maplibre-gl/dist/maplibre-gl-worker.mjs?url'),
  import('maplibre-gl/dist/maplibre-gl.css'),
])

function boundsCamera([west, south, east, north]: SilhouetteBounds, spread: number, width: number, height: number, padding: RiverPadding) {
  const center: SilhouettePoint = [(west + east) / 2, (south + north) / 2]
  const latitudeScale = Math.max(MIN_LATITUDE_SCALE, Math.cos(center[1] * DEGREES_TO_RADIANS))
  const spanX = Math.max(MIN_SPAN_DEGREES, (east - west) * spread * latitudeScale)
  const spanY = Math.max(MIN_SPAN_DEGREES, (north - south) * spread)
  const availableWidth = Math.max(MIN_VIEW_PX, width - padding.left - padding.right)
  const availableHeight = Math.max(MIN_VIEW_PX, height - padding.top - padding.bottom)
  const zoom = Math.min(
    Math.log2((availableWidth * FULL_LONGITUDE) / (WORLD_TILE_PX * spanX)),
    Math.log2((availableHeight * FULL_LONGITUDE) / (WORLD_TILE_PX * spanY)),
  )
  return { center, zoom: Math.max(SILHOUETTE_GLOBE.minZoom, Math.min(SILHOUETTE_GLOBE.fitMaxZoom, zoom)) }
}

export function SilhouetteGlobe({
  copy, world, solved, focusIso, camera, hintIso, missed, misses, regionKey, padding, chromeOffset, chromeRight, renderCard, paint, className, controlsClassName, controlsStyle, onUserMove,
}: SilhouetteGlobeProps) {
  const desktop = useMediaQuery(RIVER_DESKTOP_QUERY, true)
  const placement = desktop ? 'right' : 'top'
  const paintModeRef = useRef(paint.mode)
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<MapLibreMap | null>(null)
  const lastInteraction = useRef(Number.NEGATIVE_INFINITY)
  const cameraRef = useRef(camera)
  const paddingRef = useRef(padding)
  const worldRef = useRef(world)
  const solvedRef = useRef(solved)
  const onUserMoveRef = useRef(onUserMove)
  const missesRef = useRef(misses)
  const drapeUrls = useRef(new Set<string>())
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)
  const [hovered, setHovered] = useState<HoveredCountry | null>(null)
  const [missMarks, setMissMarks] = useState<SilhouetteMissMark[]>([])
  const reducedMotion = useReducedMotion() ?? false

  useEffect(() => {
    cameraRef.current = camera
    paddingRef.current = padding
    worldRef.current = world
    solvedRef.current = solved
    onUserMoveRef.current = onUserMove
    missesRef.current = misses
  }, [camera, padding, world, solved, onUserMove, misses])

  useEffect(() => {
    let disposed = false
    let map: MapLibreMap | null = null
    void (async () => {
      try {
        const [maplibre, worker] = await loadMapLibre()
        if (disposed || !container.current) return
        maplibre.setWorkerUrl(worker.default)
        map = new maplibre.Map({
          container: container.current,
          style: buildSilhouetteGlobeStyle(),
          center: SILHOUETTE_GLOBE.idleCenter,
          zoom: SILHOUETTE_GLOBE.idleZoom,
          minZoom: SILHOUETTE_GLOBE.minZoom,
          maxZoom: SILHOUETTE_GLOBE.maxZoom,
          attributionControl: false,
          renderWorldCopies: false,
          pixelRatio: Math.min(window.devicePixelRatio, SILHOUETTE_GLOBE.maxPixelRatio),
        })
        map.addControl(new maplibre.AttributionControl({ compact: true }), 'bottom-right')
        const loaded = map
        INTERACTION_EVENTS.forEach(event => loaded.on(event, () => {
          lastInteraction.current = performance.now()
        }))
        loaded.on('movestart', event => {
          if (event.originalEvent) onUserMoveRef.current?.()
        })
        loaded.on('load', () => {
          if (disposed) return
          loaded.getContainer().querySelector(`.${COMPACT_ATTRIBUTION}`)?.classList.remove(COMPACT_ATTRIBUTION_OPEN)
          mapRef.current = loaded
          setReady(true)
        })
      } catch {
        if (!disposed) setFailed(true)
      }
    })()
    const urls = drapeUrls.current
    return () => {
      disposed = true
      mapRef.current = null
      map?.remove()
      urls.forEach(url => URL.revokeObjectURL(url))
      urls.clear()
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || !world) return
    map.getSource<GeoJSONSource>(WORLD_SOURCE)?.setData(tonedWorld(world))
    map.getSource<GeoJSONSource>(DOT_SOURCE)?.setData(tinyCountryDots(world.countries))
  }, [ready, world])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || !world) return
    solved.filter(iso => !map.hasImage(STRIPES_PREFIX + iso)).forEach(iso => {
      const country = world.byIso.get(iso)
      if (country) map.addImage(STRIPES_PREFIX + iso, flagPattern(country.colors), { pixelRatio: SILHOUETTE_GLOBE.patternPixelRatio })
    })
    SOLVED_LAYERS.forEach(layer => map.setFilter(layer, isoListFilter(solved)))
  }, [ready, world, solved])

  useEffect(() => {
    paintModeRef.current = paint.mode
    const map = mapRef.current
    if (!ready || !map) return
    const draped = paint.mode === 'FLAG' ? 'visible' : 'none'
    map.setLayoutProperty(STRIPES_LAYER, 'visibility', paint.mode === 'STRIPES' ? 'visible' : 'none')
    map.getLayersOrder().filter(layer => layer.startsWith(DRAPE_PREFIX))
      .forEach(layer => map.setLayoutProperty(layer, 'visibility', draped))
  }, [ready, paint.mode])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || !world || paint.mode !== 'FLAG') return
    let cancelled = false
    solved.filter(iso => !map.getSource(DRAPE_PREFIX + iso)).forEach(iso => {
      const country = world.byIso.get(iso)
      const feature = world.collection.features.find(item => item.properties.iso === iso && item.properties.pool)
      if (!country || !feature?.geometry.coordinates.length) return
      void drapeFlag(iso, feature.geometry, country.bounds).then(drape => {
        if (cancelled || map.getSource(DRAPE_PREFIX + iso)) {
          URL.revokeObjectURL(drape.url)
          return
        }
        drapeUrls.current.add(drape.url)
        map.addSource(DRAPE_PREFIX + iso, { type: 'image', url: drape.url, coordinates: drape.coordinates })
        map.addLayer({
          id: DRAPE_PREFIX + iso, type: 'raster', source: DRAPE_PREFIX + iso,
          layout: { visibility: paintModeRef.current === 'FLAG' ? 'visible' : 'none' },
          paint: { 'raster-opacity': 0, 'raster-fade-duration': 0, 'raster-opacity-transition': { duration: DRAPE_FADE_MS, delay: 0 } },
        }, DRAPE_BEFORE_LAYER)
        requestAnimationFrame(() => map.getLayer(DRAPE_PREFIX + iso) && map.setPaintProperty(DRAPE_PREFIX + iso, 'raster-opacity', DRAPE_OPACITY))
      }).catch(() => undefined)
    })
    return () => {
      cancelled = true
    }
  }, [ready, world, solved, paint.mode])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    REGION_LAYERS.forEach(layer => map.setFilter(layer, regionFilter(regionKey)))
  }, [ready, regionKey])

  const highlightIso = hovered?.iso ?? focusIso
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    FOCUS_LAYERS.forEach(layer => map.setFilter(layer, isoFilter(highlightIso)))
  }, [ready, highlightIso])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    HINT_LAYERS.forEach(layer => map.setFilter(layer, isoFilter(hintIso)))
  }, [ready, hintIso])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    MISSED_LAYERS.forEach(layer => map.setFilter(layer, isoListFilter(missed)))
  }, [ready, missed])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || misses.length === 0) return
    const update = () => setMissMarks(misses.flatMap((miss, index) => missMark(map, miss, index === misses.length - 1) ?? []))
    const frame = requestAnimationFrame(update)
    map.on('move', update)
    map.on('resize', update)
    return () => {
      cancelAnimationFrame(frame)
      map.off('move', update)
      map.off('resize', update)
    }
  }, [ready, misses])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    const duration = reducedMotion ? 0 : SILHOUETTE_GLOBE.flightMs
    if (camera.kind === 'region') {
      map.flyTo({ center: camera.center, zoom: SILHOUETTE_GLOBE.regionZoom, padding: paddingRef.current, duration })
    } else if (camera.kind === 'country' || camera.kind === 'location') {
      const spread = camera.kind === 'location' ? SILHOUETTE_GLOBE.locationSpread : SILHOUETTE_GLOBE.countrySpread
      const element = map.getContainer()
      map.flyTo({
        ...boundsCamera(camera.bounds, spread, element.clientWidth, element.clientHeight, paddingRef.current),
        padding: paddingRef.current,
        duration: reducedMotion ? 0 : SILHOUETTE_GLOBE.fitMs,
      })
    } else {
      map.easeTo({ zoom: SILHOUETTE_GLOBE.idleZoom, pitch: 0, bearing: 0, padding: paddingRef.current, duration })
    }
  }, [ready, camera, reducedMotion])

  const latestMissIso = misses.at(-1)?.iso ?? null
  useEffect(() => {
    const map = mapRef.current
    const latestMiss = missesRef.current.at(-1)
    if (!ready || !map || !latestMissIso || !latestMiss) return
    const view = map.getCenter()
    if (angularDistance([view.lng, view.lat], latestMiss.center) <= SILHOUETTE_MISS.revealAngle) return
    map.easeTo({
      center: latestMiss.center,
      zoom: Math.min(map.getZoom(), SILHOUETTE_GLOBE.idleZoom),
      padding: paddingRef.current,
      duration: reducedMotion ? 0 : SILHOUETTE_GLOBE.fitMs,
    })
  }, [ready, latestMissIso, reducedMotion])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || reducedMotion) return
    let frame = 0
    let last = performance.now()
    const step = (now: number) => {
      const idle = cameraRef.current.kind === 'idle'
      if (idle && now - lastInteraction.current > SILHOUETTE_GLOBE.resumeSpinMs && !map.isMoving()) {
        const center = map.getCenter()
        map.jumpTo({ center: [center.lng + ((now - last) / MS_PER_SECOND) * SILHOUETTE_GLOBE.spinDegreesPerSecond, center.lat] })
      }
      last = now
      frame = requestAnimationFrame(step)
    }
    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
  }, [ready, reducedMotion])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    let pending: string | null = null
    let shown: string | null = null
    let lastShown: string | null = null
    let hiddenAt = Number.NEGATIVE_INFINITY
    let intentTimer = 0
    let hideTimer = 0
    let last: { x: number, y: number, time: number } | null = null
    const place = (iso: string) => {
      const country = worldRef.current?.byIso.get(iso)
      if (!country) return null
      const center = map.getCenter()
      if (angularDistance([center.lng, center.lat], country.center) > VISIBLE_ANGLE_DEGREES) return null
      const point = map.project(country.center)
      return { iso, x: Math.round(point.x), y: Math.round(point.y) }
    }
    const solvedIso = (event: MapLayerMouseEvent) => {
      const iso = event.features?.[0]?.properties?.iso
      return typeof iso === 'string' && solvedRef.current.includes(iso) ? iso : null
    }
    const cancelIntent = () => {
      window.clearTimeout(intentTimer)
      pending = null
    }
    const showNow = (iso: string) => {
      cancelIntent()
      window.clearTimeout(hideTimer)
      shown = iso
      lastShown = iso
      setHovered(previous => previous?.iso === iso ? previous : place(iso))
    }
    const hideCard = () => {
      window.clearTimeout(hideTimer)
      if (shown) hiddenAt = performance.now()
      shown = null
      setHovered(null)
    }
    const hideNow = () => {
      cancelIntent()
      hideCard()
    }
    const hideSoon = () => {
      window.clearTimeout(hideTimer)
      hideTimer = window.setTimeout(hideCard, SILHOUETTE_HOVER.hideMs)
    }
    const hover = (event: MapLayerMouseEvent) => {
      const iso = solvedIso(event)
      if (!iso) return
      map.getCanvas().style.cursor = 'pointer'
      const time = event.originalEvent.timeStamp
      const speed = last ? Math.hypot(event.point.x - last.x, event.point.y - last.y) / Math.max(1, time - last.time) : 0
      last = { x: event.point.x, y: event.point.y, time }
      if (event.originalEvent.buttons > 0 || map.isMoving()) {
        cancelIntent()
        return
      }
      if (iso === shown) {
        cancelIntent()
        window.clearTimeout(hideTimer)
        return
      }
      if (iso === lastShown && performance.now() - hiddenAt < SILHOUETTE_HOVER.returnMs) {
        showNow(iso)
        return
      }
      if (shown) hideSoon()
      if (iso === pending && speed <= SILHOUETTE_HOVER.maxSpeedPxPerMs) return
      window.clearTimeout(intentTimer)
      pending = iso
      intentTimer = window.setTimeout(() => showNow(iso), SILHOUETTE_HOVER.intentMs)
    }
    const tap = (event: MapLayerMouseEvent) => {
      const iso = solvedIso(event)
      if (iso) showNow(iso)
    }
    const leave = () => {
      map.getCanvas().style.cursor = ''
      cancelIntent()
      hideSoon()
    }
    const userMove = (event: { originalEvent?: unknown }) => {
      if (event.originalEvent) hideNow()
    }
    const follow = () => setHovered(previous => previous ? place(previous.iso) : previous)
    const dismiss = (event: MapLayerMouseEvent) => {
      if (!map.queryRenderedFeatures(event.point, { layers: HOVER_LAYERS }).length) hideNow()
    }
    HOVER_LAYERS.forEach(layer => {
      map.on('mousemove', layer, hover)
      map.on('click', layer, tap)
      map.on('mouseleave', layer, leave)
    })
    map.on('dragstart', userMove)
    map.on('zoomstart', userMove)
    map.on('move', follow)
    map.on('click', dismiss)
    return () => {
      window.clearTimeout(intentTimer)
      window.clearTimeout(hideTimer)
      HOVER_LAYERS.forEach(layer => {
        map.off('mousemove', layer, hover)
        map.off('click', layer, tap)
        map.off('mouseleave', layer, leave)
      })
      map.off('dragstart', userMove)
      map.off('zoomstart', userMove)
      map.off('move', follow)
      map.off('click', dismiss)
    }
  }, [ready])

  function zoomBy(delta: number) {
    lastInteraction.current = performance.now()
    mapRef.current?.easeTo({ zoom: (mapRef.current?.getZoom() ?? SILHOUETTE_GLOBE.idleZoom) + delta })
  }

  function showWholeGlobe() {
    lastInteraction.current = performance.now()
    mapRef.current?.easeTo({ zoom: SILHOUETTE_GLOBE.idleZoom, pitch: 0, bearing: 0, padding: paddingRef.current })
  }

  return <div className={cn('overflow-hidden', className)}
    style={{ backgroundColor: SILHOUETTE_COLORS.space, [CHROME_OFFSET_VARIABLE]: `${chromeOffset}px`, [CHROME_RIGHT_VARIABLE]: `${chromeRight}px` } as CSSProperties}>
    <div ref={container} role="region" aria-label={copy.label} className={cn(CHROME, ready ? 'opacity-100' : 'opacity-0')} />
    <SilhouetteMissMarks marks={misses.length > 0 ? missMarks : NO_MARKS} />
    {failed && <p className="absolute inset-x-0 top-1/2 text-center text-sm text-white/50">{copy.unavailable}</p>}
    {hovered && <div key={hovered.iso} className="pointer-events-none absolute z-10 w-[19rem] [perspective:1100px]"
      style={hovered.y < CARD_FLIP_PX
        ? { left: hovered.x, top: hovered.y + CARD_GAP_PX, transform: 'translateX(-50%)' }
        : { left: hovered.x, top: hovered.y - CARD_GAP_PX, transform: 'translate(-50%, -100%)' }}>
      {renderCard(hovered.iso)}
    </div>}
    <div style={controlsStyle} className={cn('absolute z-10 flex divide-x divide-white/[.08] overflow-hidden rounded-2xl border border-white/[.12] bg-[rgba(9,11,21,.72)] shadow-[0_18px_40px_rgba(0,0,0,.45)] backdrop-blur-xl lg:flex-col lg:divide-x-0 lg:divide-y', controlsClassName)}>
      <GlobeControl icon={Plus} label={copy.zoomIn} placement={placement} disabled={!ready} onClick={() => zoomBy(ZOOM_STEP)}
        className="max-lg:hidden" />
      <GlobeControl icon={Minus} label={copy.zoomOut} placement={placement} disabled={!ready} onClick={() => zoomBy(-ZOOM_STEP)}
        className="max-lg:hidden" />
      <GlobeControl icon={Globe2} label={copy.wholeGlobe} hint={copy.wholeGlobeHint} placement={placement} disabled={!ready}
        onClick={showWholeGlobe} />
      <GlobeControl icon={paint.mode === 'FLAG' ? Flag : Palette} label={paint.label} hint={paint.hint} placement={placement}
        onClick={paint.onToggle} />
    </div>
  </div>
}
