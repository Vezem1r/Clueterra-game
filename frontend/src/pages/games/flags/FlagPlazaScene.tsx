import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { type PerspectiveCamera, Vector3 } from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import {
  FOG_COLOR,
  FOG_FAR,
  FOG_NEAR,
  MAX_CAMERA_DISTANCE,
  MAX_DEVICE_PIXEL_RATIO,
  MAX_POLAR_ANGLE,
  MIN_CAMERA_DISTANCE,
  MIN_POLAR_ANGLE,
  MOON_DIRECTION,
  OVERVIEW_CAMERA,
  OVERVIEW_TARGET_HEIGHT,
  REGION_CAMERA_DISTANCE,
  REGION_TARGET_HEIGHT,
  FLAG_WIDTH,
  MAX_NARROW_ZOOM_OUT,
  SLOT_CAMERA_DISTANCE,
  SLOT_CAMERA_HEIGHT,
  CAMERA_FLOOR,
  FLAG_HEIGHT,
  FLAG_LOWERED_Y,
  FLAG_TOP_Y,
  WIDE_ASPECT,
  type FlagPlazaRegionKey,
} from './flagPlazaConstants'
import { FlagPlazaFlags } from './FlagPlazaFlags'
import { FlagPlazaGrounds } from './FlagPlazaGrounds'
import { FlagPlazaLabels } from './FlagPlazaLabels'
import { FlagPlazaMeadow } from './FlagPlazaMeadow'
import { FlagPlazaMonument } from './FlagPlazaMonument'
import type { FlagPlazaLayout } from './flagPlazaLayout'
import type { RiverPadding } from '../rivers/globe/riverMercator'
import { plazaPoint } from './flagPlazaLayout'
import { FlagPlazaPoles } from './FlagPlazaPoles'
import { AfterFrames, FrameCap, ShaderWarmup } from './FlagPlazaPacing'
import { isLowEndDevice } from './flagPlazaDevice'
import type { FlagAtlas } from './useFlagAtlas'
import { advanceFlight, type CameraFlight, type CameraGoal, planFlight } from './flagPlazaCamera'

export type FlagPlazaFocus =
  | { kind: 'overview' }
  | { kind: 'region'; region: FlagPlazaRegionKey }
  | { kind: 'slot'; index: number }

interface FlagPlazaSceneProps {
  layout: FlagPlazaLayout
  raised: boolean[]
  regionNames: Record<FlagPlazaRegionKey, string>
  atlas: FlagAtlas | null
  focus: FlagPlazaFocus
  selected: number | null
  hovered: number | null
  calm: boolean
  paused: boolean
  padding: RiverPadding
  onHover: (index: number | null) => void
  onSelect: (index: number) => void
  onUserMove?: () => void
}

interface CameraRigProps {
  layout: FlagPlazaLayout
  focus: FlagPlazaFocus
  calm: boolean
  onFlight: (flying: boolean) => void
  onUserMove?: () => void
}

const CAMERA_FOV = 42
const CAMERA_NEAR = 0.5
const CAMERA_FAR = 400
const AMBIENT_INTENSITY = 0.45
const HEMISPHERE_INTENSITY = 1.1
const MOON_INTENSITY = 0.85
const MOON_LIGHT_DISTANCE = 60
const MONUMENT_LIGHT_INTENSITY = 70
const MONUMENT_LIGHT_DISTANCE = 48
const MONUMENT_LIGHT_HEIGHT = 9
const REGION_SIGHT_HEIGHT = 5.5
const LITE_FPS = 30
const DETAIL_DELAY_FRAMES = 2
const SLOT_HEIGHT_ZOOM_POWER = 0.7
const SLOT_SIGHT_HEIGHT = (FLAG_LOWERED_Y + FLAG_TOP_Y) / 2 - FLAG_HEIGHT / 2

function cameraGoal(layout: FlagPlazaLayout, focus: FlagPlazaFocus, zoomOut: number): CameraGoal {
  if (focus.kind === 'slot') {
    const slot = layout.slots[focus.index]
    const along = new Vector3(-Math.sin(slot.angle), 0, -Math.cos(slot.angle))
    const flag = new Vector3(slot.x, 0, slot.z).addScaledVector(along, FLAG_WIDTH / 2)
    const reach = Math.hypot(flag.x, flag.z)
    const distance = SLOT_CAMERA_DISTANCE * zoomOut
    const height = SLOT_CAMERA_HEIGHT * zoomOut ** SLOT_HEIGHT_ZOOM_POWER
    const position = flag.multiplyScalar((reach + distance) / reach)
    position.y = height
    const targetHeight = SLOT_SIGHT_HEIGHT - (height - SLOT_SIGHT_HEIGHT) * reach / distance
    return { target: new Vector3(0, targetHeight, 0), position }
  }
  if (focus.kind === 'region') {
    const sector = layout.sectors.find(candidate => candidate.region === focus.region) ?? layout.sectors[0]
    const angle = sector.start + sector.length / 2
    const middle = (sector.innerRadius + sector.outerRadius) / 2
    const reach = sector.outerRadius + REGION_CAMERA_DISTANCE * zoomOut
    const [cameraX, cameraZ] = plazaPoint(reach, angle)
    const height = REGION_TARGET_HEIGHT + (REGION_SIGHT_HEIGHT - REGION_TARGET_HEIGHT) * reach / middle
    return {
      target: new Vector3(0, REGION_TARGET_HEIGHT, 0),
      position: new Vector3(cameraX, height, cameraZ),
    }
  }
  return {
    target: new Vector3(0, OVERVIEW_TARGET_HEIGHT, 0),
    position: new Vector3(...OVERVIEW_CAMERA).multiplyScalar(zoomOut),
  }
}

function CameraRig({ layout, focus, calm, onFlight, onUserMove }: CameraRigProps) {
  const camera = useThree(state => state.camera)
  const element = useThree(state => state.gl.domElement)
  const aspect = useThree(state => state.size.width / Math.max(1, state.size.height))
  const zoomOut = Math.min(MAX_NARROW_ZOOM_OUT, Math.max(1, WIDE_ASPECT / aspect))
  const controls = useRef<OrbitControls | null>(null)
  const goal = useRef<CameraGoal | null>(null)
  const flight = useRef<CameraFlight | null>(null)

  useEffect(() => {
    const orbit = new OrbitControls(camera, element)
    orbit.enableDamping = true
    orbit.enablePan = false
    orbit.minDistance = MIN_CAMERA_DISTANCE
    orbit.maxDistance = MAX_CAMERA_DISTANCE
    orbit.minPolarAngle = MIN_POLAR_ANGLE
    orbit.maxPolarAngle = MAX_POLAR_ANGLE
    const release = () => {
      goal.current = null
      flight.current = null
      onFlight(false)
      onUserMove?.()
    }
    orbit.addEventListener('start', release)
    controls.current = orbit
    return () => {
      orbit.removeEventListener('start', release)
      orbit.dispose()
      controls.current = null
    }
  }, [camera, element, onFlight, onUserMove])

  useEffect(() => {
    goal.current = cameraGoal(layout, focus, zoomOut)
  }, [layout, focus, zoomOut])

  useFrame((state, delta) => {
    const orbit = controls.current
    if (!orbit) return
    if (goal.current) {
      flight.current = planFlight(state.camera.position, orbit.target, goal.current, calm)
      goal.current = null
      onFlight(true)
    }
    if (flight.current && advanceFlight(flight.current, delta, state.camera.position, orbit.target)) {
      flight.current = null
      onFlight(false)
    }
    orbit.update()
    if (state.camera.position.y < CAMERA_FLOOR) {
      state.camera.position.y = CAMERA_FLOOR
      state.camera.lookAt(orbit.target)
    }
  })

  return null
}

function ViewOffset({ padding }: { padding: RiverPadding }) {
  const camera = useThree(state => state.camera) as PerspectiveCamera
  const width = useThree(state => state.size.width)
  const height = useThree(state => state.size.height)

  useEffect(() => {
    camera.setViewOffset(width, height, (padding.right - padding.left) / 2, (padding.bottom - padding.top) / 2, width, height)
    return () => camera.clearViewOffset()
  }, [camera, width, height, padding.top, padding.right, padding.bottom, padding.left])

  return null
}

export function FlagPlazaScene(props: FlagPlazaSceneProps) {
  const { layout, raised, regionNames, atlas, focus, selected, hovered, calm, paused, padding, onHover, onSelect, onUserMove } = props
  const shares = useMemo(() => layout.sectors.map(sector =>
    sector.slotIndexes.filter(index => raised[index]).length / sector.slotIndexes.length), [layout, raised])
  const moon = useMemo(() => MOON_DIRECTION.map(axis => axis * MOON_LIGHT_DISTANCE) as [number, number, number], [])
  const flying = useRef(false)
  const setFlying = useCallback((value: boolean) => {
    flying.current = value
  }, [])
  const isFlying = useCallback(() => flying.current, [])
  const [lite] = useState(isLowEndDevice)
  const [warm, setWarm] = useState(false)
  const [details, setDetails] = useState(false)
  const markWarm = useCallback(() => setWarm(true), [])
  const showDetails = useCallback(() => setDetails(true), [])
  const running = warm && !paused
  const frameloop = !running ? 'never' : lite ? 'demand' : 'always'
  return (
    <Canvas
      dpr={[1, MAX_DEVICE_PIXEL_RATIO]}
      frameloop={frameloop}
      camera={{ position: OVERVIEW_CAMERA, fov: CAMERA_FOV, near: CAMERA_NEAR, far: CAMERA_FAR }}
      gl={{ antialias: true, powerPreference: 'high-performance' }}
      onPointerMissed={() => onHover(null)}
    >
      <color attach="background" args={[FOG_COLOR]} />
      <fog attach="fog" args={[FOG_COLOR, FOG_NEAR, FOG_FAR]} />
      <ambientLight intensity={AMBIENT_INTENSITY} color="#a5b4fc" />
      <hemisphereLight args={['#3b4f7a', '#0f2414', HEMISPHERE_INTENSITY]} />
      <directionalLight position={moon} intensity={MOON_INTENSITY} color="#c7d2fe" />
      <pointLight
        position={[0, MONUMENT_LIGHT_HEIGHT, 0]}
        intensity={MONUMENT_LIGHT_INTENSITY}
        distance={MONUMENT_LIGHT_DISTANCE}
        color="#fbbf24"
      />
      <ViewOffset padding={padding} />
      <CameraRig layout={layout} focus={focus} calm={calm} onFlight={setFlying} onUserMove={onUserMove} />
      <FlagPlazaMeadow plazaRadius={layout.radius} calm={calm} details={details} lite={lite} />
      <FlagPlazaGrounds layout={layout} shares={shares} details={details} lite={lite} />
      <FlagPlazaMonument sectors={layout.sectors} shares={shares} calm={calm} />
      <FlagPlazaLabels sectors={layout.sectors} raised={raised} names={regionNames} />
      <FlagPlazaPoles slots={layout.slots} raised={raised} selected={selected} hovered={hovered} isFlying={isFlying}
        onHover={onHover} onSelect={onSelect} />
      {atlas ? (
        <FlagPlazaFlags slots={layout.slots} raised={raised} atlas={atlas} calm={calm} isFlying={isFlying} />
      ) : null}
      <ShaderWarmup onReady={markWarm} />
      {!details && <AfterFrames frames={DETAIL_DELAY_FRAMES} onReach={showDetails} />}
      {running && lite && <FrameCap fps={LITE_FPS} />}
    </Canvas>
  )
}
