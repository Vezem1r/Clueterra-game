import { useCallback, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { useReducedMotion } from 'framer-motion'
import { Info, LocateFixed, Minus, Mountain, Plus } from 'lucide-react'
import type * as MapLibre from 'maplibre-gl'
import type { GeoJSONSource, Map as MapLibreMap } from 'maplibre-gl'
import type { RiverCourse, RiverPlace } from '@/api/rivers'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { GlobeControl } from './GlobeControl'
import {
  RIVER_BORDER_OPACITY,
  RIVER_BORDERS_URL,
  RIVER_COLORS,
  RIVER_DESKTOP_QUERY,
  RIVER_DRAW_DURATION_MS,
  RIVER_FIT_DURATION_MS,
  RIVER_FLARE_HOLD_MS,
  RIVER_FLARE_OPACITY,
  RIVER_FLIGHT_CURVE,
  RIVER_FLIGHT_MS,
  RIVER_GLOBE_IDLE_CENTER,
  RIVER_GLOBE_IDLE_ZOOM,
  RIVER_GLOBE_MAX_ZOOM,
  RIVER_GLOBE_MIN_ZOOM,
  RIVER_GLOBE_SPIN_DEGREES_PER_SECOND,
  RIVER_MAX_PIXEL_RATIO,
  RIVER_MS_PER_SECOND,
  RIVER_ORBIT_DEGREES_PER_SECOND,
  RIVER_ORBIT_RESUME_MS,
  RIVER_RELIEF_MAX_ZOOM,
  RIVER_RELIEF_TILE_SIZE,
  RIVER_RELIEF_TILES,
  RIVER_REVEAL_BEARING,
  RIVER_REVEAL_DIVE_MS,
  RIVER_REVEAL_PITCH,
  RIVER_REVEAL_ZOOM_OFFSET,
  RIVER_STARFIELD,
  RIVER_STARFIELD_SIZE,
  RIVER_TERRAIN_EXAGGERATION,
  RIVER_TERRAIN_MAX_ZOOM,
  RIVER_TERRAIN_PITCH,
  RIVER_TERRAIN_TILE_SIZE,
  RIVER_TERRAIN_TILES,
  RIVER_ZOOM_STEP_DURATION_MS,
} from './riverGlobeConstants'
import {
  buildRiverGlobeStyle,
  RIVER_BORDER_LAYER,
  RIVER_BORDER_SOURCE,
  RIVER_DRAWN,
  RIVER_FLARE_LAYER,
  RIVER_SOURCE,
  RIVER_TERRAIN_SOURCE,
  riverGradients,
} from './riverGlobeStyle'
import { fitRiverCamera, type RiverPadding, type RiverViewport } from './riverMercator'
import { prefetchRiverTiles, type RiverTileTemplate } from './riverTilePrefetch'

export type RiverGlobeMode = 'idle' | 'play' | 'reveal'

const INTERACTION_EVENTS = ['mousedown', 'touchstart', 'wheel'] as const
const SCALE_MAX_WIDTH_PX = 120
const PADDING_REFIT_MS = 500
const TERRAIN_DELTA_ZOOM_SCALE = 2
const IDLE_RETURN_ZOOM_MARGIN = 1
const CHROME_OFFSET_VARIABLE = '--river-globe-chrome'
const CHROME_RIGHT_VARIABLE = '--river-globe-chrome-right'
const EMPTY_RIVER = { type: 'FeatureCollection' as const, features: [] }
const COMPACT_ATTRIBUTION = 'maplibregl-compact'
const COMPACT_ATTRIBUTION_OPEN = 'maplibregl-compact-show'
const MARKER_OFFSET: [number, number] = [-6, 0]
const MARKER_CLASS = 'pointer-events-none'
const MARKER_BODY_CLASS = 'flex items-center gap-2 motion-safe:animate-river-reveal'
const MARKER_DOT_CLASS = 'size-3 shrink-0 rounded-full bg-white ring-4 ring-amber-300/45 shadow-[0_0_16px_rgba(252,211,77,.9)]'
const MARKER_LABEL_CLASS = 'whitespace-nowrap rounded-full bg-[rgba(9,11,21,.8)] px-2.5 py-1 text-xs font-semibold text-white ring-1 ring-white/15 backdrop-blur-md'

const CHROME = 'globe-attribution absolute inset-0 transition-opacity duration-700 [&_.maplibregl-ctrl-bottom-left]:!bottom-[var(--river-globe-chrome)] [&_.maplibregl-ctrl-bottom-right]:!bottom-[var(--river-globe-chrome)] [&_.maplibregl-ctrl-bottom-right]:!z-20 [&_.maplibregl-ctrl-bottom-right]:!right-[var(--river-globe-chrome-right)] max-lg:[&_.maplibregl-ctrl-scale]:hidden [&_.maplibregl-ctrl-scale]:!border-white/60 [&_.maplibregl-ctrl-scale]:!bg-transparent [&_.maplibregl-ctrl-scale]:!text-[11px] [&_.maplibregl-ctrl-scale]:!font-semibold [&_.maplibregl-ctrl-scale]:!text-white/75'

const MAPLIBRE_IDLE_TIMEOUT_MS = 1500

const pageSettled = () => new Promise<void>(resolve => {
  const whenIdle = () => typeof window.requestIdleCallback === 'function'
    ? window.requestIdleCallback(() => resolve(), { timeout: MAPLIBRE_IDLE_TIMEOUT_MS })
    : window.setTimeout(resolve)
  if (document.readyState === 'complete') whenIdle()
  else window.addEventListener('load', whenIdle, { once: true })
})

const loadMapLibre = () => pageSettled().then(() => Promise.all([
  import('maplibre-gl'),
  import('maplibre-gl/dist/maplibre-gl-worker.mjs?url'),
  import('maplibre-gl/dist/maplibre-gl.css'),
]))
const mapLibreRequest = typeof window === 'undefined' ? null : loadMapLibre()

type MapLibreModule = typeof MapLibre

interface RiverGlobeProps {
  copy: TranslationKeys['riverGame']
  label: string
  riverId: number | null
  course: RiverCourse | null
  places: RiverPlace[]
  mode: RiverGlobeMode
  showBorders: boolean
  padding: RiverPadding
  chromeOffset: number
  chromeRight: number
  fallback: ReactNode
  className?: string
  controlsClassName?: string
  controlsStyle?: CSSProperties
  onUserMove?: () => void
}

function placeMarker(name: string) {
  const element = document.createElement('div')
  const body = document.createElement('div')
  const dot = document.createElement('span')
  const label = document.createElement('span')
  element.className = MARKER_CLASS
  body.className = MARKER_BODY_CLASS
  dot.className = MARKER_DOT_CLASS
  label.className = MARKER_LABEL_CLASS
  label.textContent = name
  body.append(dot, label)
  element.append(body)
  return element
}

function easeOut(progress: number) {
  return 1 - (1 - progress) ** 3
}

function viewportOf(map: MapLibreMap, padding: RiverPadding): RiverViewport {
  const element = map.getContainer()
  return { width: element.clientWidth, height: element.clientHeight, padding }
}

function applyTerrain(map: MapLibreMap, on: boolean) {
  if (on === (map.getTerrain() != null)) return
  map.setTerrain(on ? { source: RIVER_TERRAIN_SOURCE, exaggeration: RIVER_TERRAIN_EXAGGERATION } : null)
}

function paintRiver(map: MapLibreMap, progress: number) {
  riverGradients(progress).forEach(line => map.setPaintProperty(line.id, 'line-gradient', line.gradient))
}

export function RiverGlobe({
  copy, label, riverId, course, places, mode, showBorders, padding, chromeOffset, chromeRight, fallback, className, controlsClassName, controlsStyle, onUserMove,
}: RiverGlobeProps) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<MapLibreMap | null>(null)
  const mapLibreRef = useRef<MapLibreModule | null>(null)
  const lastInteraction = useRef(Number.NEGATIVE_INFINITY)
  const userMoved = useRef(false)
  const fittedRiver = useRef<number | null>(null)
  const drawnRiver = useRef<number | null>(null)
  const bordersLoaded = useRef(false)
  const terrainChosenRef = useRef(false)
  const paddingRef = useRef(padding)
  const pendingPadding = useRef(false)
  const courseRef = useRef(course)
  const riverIdRef = useRef(riverId)
  const modeRef = useRef(mode)
  const reducedMotionRef = useRef(false)
  const onUserMoveRef = useRef(onUserMove)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)
  const [terrainChosen, setTerrainChosen] = useState(false)
  const reducedMotion = useReducedMotion() ?? false
  const desktop = useMediaQuery(RIVER_DESKTOP_QUERY, true)
  const terrainOn = terrainChosen || mode === 'reveal'
  const globeCopy = copy.globe
  const placement = desktop ? 'right' : 'top'

  useEffect(() => {
    courseRef.current = course
    riverIdRef.current = riverId
    modeRef.current = mode
    reducedMotionRef.current = reducedMotion
    onUserMoveRef.current = onUserMove
  }, [course, riverId, mode, reducedMotion, onUserMove])

  const settlePadding = useCallback((map: MapLibreMap) => {
    pendingPadding.current = false
    const nextPadding = paddingRef.current
    const current = courseRef.current
    const id = riverIdRef.current
    const duration = reducedMotionRef.current ? 0 : PADDING_REFIT_MS
    if (current && id != null && fittedRiver.current === id && !userMoved.current) {
      const zoomOffset = modeRef.current === 'reveal' ? RIVER_REVEAL_ZOOM_OFFSET : 0
      map.easeTo({ ...fitRiverCamera(current, viewportOf(map, nextPadding), zoomOffset), padding: nextPadding, duration })
      return
    }
    map.easeTo({ padding: nextPadding, duration })
  }, [])

  useEffect(() => {
    let disposed = false
    let map: MapLibreMap | null = null
    void (async () => {
      try {
        const [maplibre, worker] = await (mapLibreRequest ?? loadMapLibre())
        if (disposed || !container.current) return
        maplibre.setWorkerUrl(worker.default)
        mapLibreRef.current = maplibre
        map = new maplibre.Map({
          container: container.current,
          style: buildRiverGlobeStyle(window.location.origin),
          center: RIVER_GLOBE_IDLE_CENTER,
          zoom: RIVER_GLOBE_IDLE_ZOOM,
          minZoom: RIVER_GLOBE_MIN_ZOOM,
          maxZoom: RIVER_GLOBE_MAX_ZOOM,
          attributionControl: false,
          renderWorldCopies: false,
          pixelRatio: Math.min(window.devicePixelRatio, RIVER_MAX_PIXEL_RATIO),
        })
        map.addControl(new maplibre.AttributionControl({ compact: true }), 'bottom-right')
        map.addControl(new maplibre.ScaleControl({ maxWidth: SCALE_MAX_WIDTH_PX }), 'bottom-left')
        INTERACTION_EVENTS.forEach(event => map?.on(event, () => {
          userMoved.current = true
          lastInteraction.current = performance.now()
        }))
        const loadedMap = map
        loadedMap.on('movestart', event => {
          if (event.originalEvent) onUserMoveRef.current?.()
        })
        loadedMap.on('moveend', event => {
          if (event.originalEvent && pendingPadding.current && !loadedMap.isMoving()) settlePadding(loadedMap)
        })
        loadedMap.on('load', () => {
          if (disposed) return
          loadedMap.getContainer().querySelector(`.${COMPACT_ATTRIBUTION}`)?.classList.remove(COMPACT_ATTRIBUTION_OPEN)
          mapRef.current = loadedMap
          setReady(true)
        })
      } catch {
        if (!disposed) setFailed(true)
      }
    })()
    return () => {
      disposed = true
      mapRef.current = null
      map?.remove()
    }
  }, [settlePadding])

  useEffect(() => {
    paddingRef.current = padding
    const map = mapRef.current
    if (!ready || !map) return
    if (map.isMoving()) {
      pendingPadding.current = true
      return
    }
    settlePadding(map)
  }, [ready, padding, settlePadding])

  useEffect(() => {
    const map = mapRef.current
    const maplibre = mapLibreRef.current
    if (!ready || !map || !maplibre) return
    const markers = places.map(place => new maplibre.Marker({ element: placeMarker(place.name), anchor: 'left', offset: MARKER_OFFSET })
      .setLngLat([place.lng, place.lat])
      .addTo(map))
    return () => markers.forEach(marker => marker.remove())
  }, [ready, places])

  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || !showBorders) return
    if (!bordersLoaded.current) {
      bordersLoaded.current = true
      map.getSource<GeoJSONSource>(RIVER_BORDER_SOURCE)?.setData(window.location.origin + RIVER_BORDERS_URL)
    }
    map.setPaintProperty(RIVER_BORDER_LAYER, 'line-opacity', RIVER_BORDER_OPACITY)
    return () => {
      map.setPaintProperty(RIVER_BORDER_LAYER, 'line-opacity', 0)
    }
  }, [ready, showBorders])

  useEffect(() => {
    const map = mapRef.current
    const current = courseRef.current
    if (!ready || !map) return
    let frame = 0
    let flareTimer = 0
    const stopFrame = () => cancelAnimationFrame(frame)
    const viewport = () => viewportOf(map, paddingRef.current)
    const settleIfPending = () => {
      if (pendingPadding.current) settlePadding(map)
    }

    const loop = (turn: (elapsed: number) => void) => {
      let last = performance.now()
      const step = (now: number) => {
        if (now - lastInteraction.current > RIVER_ORBIT_RESUME_MS && !map.isMoving()) turn(now - last)
        last = now
        frame = requestAnimationFrame(step)
      }
      frame = requestAnimationFrame(step)
    }

    if (riverId == null || !current) {
      fittedRiver.current = null
      drawnRiver.current = null
      map.getSource<GeoJSONSource>(RIVER_SOURCE)?.setData(EMPTY_RIVER)
      const flatten = () => applyTerrain(map, terrainChosenRef.current)
      const tilted = map.getPitch() > 0 && !terrainChosenRef.current
      if (map.getZoom() > RIVER_GLOBE_IDLE_ZOOM + IDLE_RETURN_ZOOM_MARGIN || tilted) {
        map.flyTo({ center: map.getCenter(), zoom: Math.min(map.getZoom(), RIVER_GLOBE_IDLE_ZOOM),
          pitch: terrainChosenRef.current ? RIVER_TERRAIN_PITCH : 0, bearing: 0,
          padding: paddingRef.current, duration: reducedMotion ? 0 : RIVER_FIT_DURATION_MS })
      }
      if (map.isMoving()) map.once('moveend', flatten)
      else flatten()
      if (reducedMotion) return () => {
        map.off('moveend', flatten)
      }
      loop(elapsed => {
        const center = map.getCenter()
        map.setCenter([center.lng - RIVER_GLOBE_SPIN_DEGREES_PER_SECOND * elapsed / RIVER_MS_PER_SECOND, center.lat])
      })
      return () => {
        map.off('moveend', flatten)
        stopFrame()
        map.stop()
      }
    }

    if (mode === 'reveal') {
      paintRiver(map, RIVER_DRAWN)
      drawnRiver.current = riverId
      fittedRiver.current = riverId
      userMoved.current = false
      applyTerrain(map, true)
      pendingPadding.current = false
      const revealCamera = {
        ...fitRiverCamera(current, viewport(), RIVER_REVEAL_ZOOM_OFFSET),
        pitch: RIVER_REVEAL_PITCH, bearing: RIVER_REVEAL_BEARING, padding: paddingRef.current,
      }
      if (reducedMotion) {
        map.jumpTo(revealCamera)
        return
      }
      map.setPaintProperty(RIVER_FLARE_LAYER, 'line-opacity', RIVER_FLARE_OPACITY)
      flareTimer = window.setTimeout(() => map.setPaintProperty(RIVER_FLARE_LAYER, 'line-opacity', 0), RIVER_FLARE_HOLD_MS)
      map.easeTo({ ...revealCamera, duration: RIVER_REVEAL_DIVE_MS })
      map.once('moveend', settleIfPending)
      loop(elapsed => map.setBearing(map.getBearing() + RIVER_ORBIT_DEGREES_PER_SECOND * elapsed / RIVER_MS_PER_SECOND))
      return () => {
        window.clearTimeout(flareTimer)
        map.setPaintProperty(RIVER_FLARE_LAYER, 'line-opacity', 0)
        map.off('moveend', settleIfPending)
        stopFrame()
        map.stop()
      }
    }

    if (drawnRiver.current !== riverId) {
      drawnRiver.current = null
      paintRiver(map, 0)
      map.getSource<GeoJSONSource>(RIVER_SOURCE)?.setData({ type: 'Feature', properties: {},
        geometry: { type: 'MultiLineString', coordinates: current.coordinates } })
    }
    const origin = window.location.origin
    const relief: RiverTileTemplate = { url: origin + RIVER_RELIEF_TILES, tileSize: RIVER_RELIEF_TILE_SIZE, maxZoom: RIVER_RELIEF_MAX_ZOOM }
    const terrain: RiverTileTemplate = { url: origin + RIVER_TERRAIN_TILES, tileSize: RIVER_TERRAIN_TILE_SIZE, maxZoom: RIVER_TERRAIN_MAX_ZOOM }
    const camera = fitRiverCamera(current, viewport())
    prefetchRiverTiles(camera, viewport(), [relief, terrain])
    userMoved.current = false
    fittedRiver.current = null
    pendingPadding.current = false
    const target = { ...camera, pitch: terrainChosenRef.current ? RIVER_TERRAIN_PITCH : 0, bearing: 0, padding: paddingRef.current }
    if (reducedMotion) map.jumpTo(target)
    else map.flyTo({ ...target, curve: RIVER_FLIGHT_CURVE, duration: RIVER_FLIGHT_MS })

    const drawIn = () => {
      drawnRiver.current = riverId
      if (reducedMotion) {
        paintRiver(map, RIVER_DRAWN)
        return
      }
      const start = performance.now()
      const step = (now: number) => {
        const progress = Math.min(RIVER_DRAWN, (now - start) / RIVER_DRAW_DURATION_MS)
        paintRiver(map, progress < RIVER_DRAWN ? easeOut(progress) : RIVER_DRAWN)
        if (progress < RIVER_DRAWN) frame = requestAnimationFrame(step)
      }
      frame = requestAnimationFrame(step)
    }
    const arrive = () => {
      fittedRiver.current = riverId
      applyTerrain(map, terrainChosenRef.current)
      settleIfPending()
      if (drawnRiver.current !== riverId) drawIn()
      prefetchRiverTiles(fitRiverCamera(current, viewport(), RIVER_REVEAL_ZOOM_OFFSET), viewport(), [
        { ...terrain, tileSize: RIVER_TERRAIN_TILE_SIZE * TERRAIN_DELTA_ZOOM_SCALE },
      ])
    }
    if (map.isMoving()) map.once('moveend', arrive)
    else arrive()
    return () => {
      map.off('moveend', arrive)
      stopFrame()
      if (drawnRiver.current === riverId) paintRiver(map, RIVER_DRAWN)
    }
  }, [ready, riverId, mode, reducedMotion, settlePadding])

  function zoom(direction: 1 | -1) {
    const map = mapRef.current
    if (!map) return
    userMoved.current = true
    lastInteraction.current = performance.now()
    if (direction > 0) map.zoomIn({ duration: RIVER_ZOOM_STEP_DURATION_MS })
    else map.zoomOut({ duration: RIVER_ZOOM_STEP_DURATION_MS })
  }

  function recenter() {
    const map = mapRef.current
    if (!map) return
    userMoved.current = false
    lastInteraction.current = Number.NEGATIVE_INFINITY
    if (!course) {
      map.easeTo({ center: RIVER_GLOBE_IDLE_CENTER, zoom: RIVER_GLOBE_IDLE_ZOOM, pitch: 0, bearing: 0,
        padding: paddingRef.current, duration: RIVER_FIT_DURATION_MS })
      return
    }
    const revealing = mode === 'reveal'
    map.easeTo({
      ...fitRiverCamera(course, viewportOf(map, paddingRef.current), revealing ? RIVER_REVEAL_ZOOM_OFFSET : 0),
      pitch: revealing ? RIVER_REVEAL_PITCH : terrainOn ? RIVER_TERRAIN_PITCH : 0,
      bearing: revealing ? map.getBearing() : 0,
      padding: paddingRef.current,
      duration: RIVER_FIT_DURATION_MS,
    })
  }

  function toggleTerrain() {
    const map = mapRef.current
    const next = !terrainChosen
    terrainChosenRef.current = next
    setTerrainChosen(next)
    if (!map) return
    applyTerrain(map, next)
    map.easeTo({ pitch: next ? RIVER_TERRAIN_PITCH : 0, duration: RIVER_FIT_DURATION_MS })
  }

  if (failed) return <>{fallback}</>

  return <div data-testid="river-globe" className={cn('relative overflow-hidden', className)}
    style={{ backgroundColor: RIVER_COLORS.space, backgroundImage: RIVER_STARFIELD, backgroundSize: RIVER_STARFIELD_SIZE,
      [CHROME_OFFSET_VARIABLE]: `${chromeOffset}px`, [CHROME_RIGHT_VARIABLE]: `${chromeRight}px` } as CSSProperties}>
    <div ref={container} role="region" aria-label={label} aria-busy={!ready}
      className={cn(CHROME, ready ? 'opacity-100' : 'opacity-0')} />
    <div style={controlsStyle} className={cn('absolute z-10 flex divide-x divide-white/[.08] overflow-hidden rounded-2xl border border-white/[.12] bg-[rgba(9,11,21,.72)] shadow-[0_18px_40px_rgba(0,0,0,.45)] backdrop-blur-xl lg:flex-col lg:divide-x-0 lg:divide-y', controlsClassName)}>
      <GlobeControl icon={Plus} label={globeCopy.zoomIn} placement={placement} disabled={!ready} onClick={() => zoom(1)}
        className="max-lg:hidden" />
      <GlobeControl icon={Minus} label={globeCopy.zoomOut} placement={placement} disabled={!ready} onClick={() => zoom(-1)}
        className="max-lg:hidden" />
      <GlobeControl icon={LocateFixed} label={course ? globeCopy.recenter : globeCopy.recenterIdle}
        hint={course ? globeCopy.recenterHint : undefined} placement={placement} disabled={!ready} onClick={recenter} />
      <GlobeControl icon={Mountain} label={terrainOn ? globeCopy.terrainOff : globeCopy.terrainOn}
        hint={terrainOn ? globeCopy.terrainOffHint : globeCopy.terrainOnHint} placement={placement}
        disabled={!ready || mode === 'reveal'} pressed={terrainOn} onClick={toggleTerrain} />
      <GlobeControl icon={Info} label={copy.mapInfoLabel} hint={copy.mapInfo} placement={placement} touchHint />
    </div>
  </div>
}
