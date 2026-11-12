import { useEffect, useMemo, useRef } from 'react'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import {
  AdditiveBlending, BackSide, BufferAttribute, BufferGeometry, CatmullRomCurve3, Color, DoubleSide, Group, Line, LineBasicMaterial,
  Mesh, MeshBasicMaterial, MeshStandardMaterial, PerspectiveCamera, Quaternion, ShaderMaterial, Vector3,
} from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { QuizCountryLevel, QuizTravelMode, QuizVisit } from '@/api/quiz'
import type { SilhouettePoint } from '@/api/silhouettes'
import type { RiverPadding } from '../rivers/globe/riverMercator'
import type { SilhouetteWorld } from '../silhouettes/silhouetteWorld'
import type { QuizJourney } from './useQuizGame'
import { QUIZ_COLORS, QUIZ_DRIVE_SECONDS, QUIZ_FLY_SECONDS, QUIZ_LEVEL_PAINT, QUIZ_MAX_LEVEL } from './quizGameConstants'
import { betweenOnGlobe, easeJourney, globeNormal, globePoint, type GeoPoint } from './quizGlobeMath'
import { createQuizGlobeTextures, paintQuizGlobe } from './quizGlobeTexture'
import { globePath, landWaypoints } from './quizLandRoute'
import { QuizCar, QuizPlane } from './QuizVehicles'

export interface QuizGlobeDestination {
  iso: string
  mode: QuizTravelMode
  route: string[]
}

export interface QuizGlobeBurst {
  id: number
  iso: string
  level: number
}

interface QuizGlobeProps {
  world: SilhouetteWorld | null
  countries: QuizCountryLevel[]
  trail: QuizVisit[]
  current: string | null
  next: QuizGlobeDestination | null
  focus: string | null
  journey: QuizJourney | null
  burst: QuizGlobeBurst | null
  overview: boolean
  padding: RiverPadding
  label: string
  className?: string
  onArrive: () => void
  onUserMove?: () => void
}

const RADIUS = 3
const GROUND = RADIUS + 0.002
const ROUTE_BASE = RADIUS + 0.008
const SHADOW_RADIUS = 0.34
const SHADOW_OPACITY = 0.35
const MARKER_HEIGHT = RADIUS + 0.06
const UP = new Vector3(0, 1, 0)
const ORIGIN = new Vector3()
const DEFAULT_CENTER: SilhouettePoint = [12, 38]
const ARC_POINTS = 64
const ARC_SEGMENTS = 72
const ARC_RADIAL_SEGMENTS = 5
const ARC_RADIUS = 0.009
const ARC_ACTIVE_RADIUS = 0.016
const FLIGHT_HEIGHT_PER_RADIAN = 0.42
const MIN_FLIGHT_HEIGHT = 0.18
const MAX_FLIGHT_HEIGHT = 0.6
const DRIVE_ARC_HEIGHT = 0.015
const ARC_MIN_OPACITY = 0.05
const ARC_MAX_OPACITY = 0.5
const TRAIL_VISIBLE = 10
const ARC_ACTIVE_OPACITY = 0.95
const TRAVELLER_SCALE = 0.26
const PLANE_MORPH_RATE = 10
const LOOK_AHEAD = 0.01
const BASE_DISTANCE = 7.2
const MIN_FOCUS_DISTANCE = 6
const MAX_FOCUS_DISTANCE = 10.5
const OVERVIEW_DISTANCE = 11
const PAIR_PULL_PER_RADIAN = 2.4
const FLIGHT_PULL_PER_RADIAN = 3.2
const MAX_DISTANCE = 15
const MIN_DISTANCE = 4.3
const FIT_SHARE = 0.2
const DEGREES = Math.PI / 180
const FULL_TURN_DEGREES = 360
const FOLLOW_RATE = 2.6
const TRAVEL_FOLLOW_RATE = 3.4
const ARRIVED_EPSILON = 0.02
const AUTO_ROTATE_SPEED = 0.35
const ROTATE_SPEED = 0.45
const ZOOM_SPEED = 0.7
const DAMPING = 0.08
const MARKER_SEGMENTS = 24
const MARKER_SIZE = 0.026
const PULSE_SPEED = 0.45
const PULSE_GROWTH = 0.7
const PULSE_OPACITY = 0.4
const RING_INNER = 0.08
const RING_OUTER = 0.092
const BURST_RING_OUTER = 0.11
const BURST_SECONDS = 2.2
const BURST_GROWTH = 4
const BURST_OPACITY = 0.45
const SPARKLES_PER_COUNTRY = 9
const SPARKLE_SPREAD_SHARE = 0.3
const SPARKLE_MIN_SPREAD_DEGREES = 0.5
const SPARKLE_MAX_SPREAD_DEGREES = 3
const SPARKLE_MIN_RADIUS_SHARE = 0.15
const SPARKLE_SIZE = 70
const SPARKLE_MIN_SCALE = 0.55
const SPARKLE_SCALE_RANGE = 0.8
const SPARKLE_RISE = 0.14
const SPARKLE_CYCLES_PER_SECOND = 0.2
const SPARKLE_TWINKLE_SPEED = 2.6
const SPARKLE_SEED_PHASE = 3.7
const SPARKLE_SEED_RADIUS = 5.3
const SPARKLE_SEED_SCALE = 7.9
const STAR_COUNT = 1400
const STAR_RADIUS = 45
const STAR_SIZE = 0.09
const STAR_OPACITY = 0.7
const NOISE_SCALE = 43758.5453
const NOISE_LONGITUDE_SEED = 12.9898
const NOISE_LATITUDE_SEED = 78.233
const CONTRAIL_POINTS = 70
const CONTRAIL_MIN_LIFT = 0.04
const ATMOSPHERE_RADIUS = RADIUS * 1.08
const GLOW_BASE = 0.5
const GLOW_SWING = 0.1
const GLOW_SPEED = 0.9
const SPHERE_SEGMENTS = 160
const SPHERE_RINGS = 112
const FIELD_OF_VIEW = 40
const NEAR = 0.1
const FAR = 120
const MAX_PIXEL_RATIO = 1.75
const AMBIENT_INTENSITY = 1.25
const SUN_INTENSITY = 1.6
const SUN_POSITION: [number, number, number] = [6, 7, 9]
const RIM_INTENSITY = 0.6
const RIM_POSITION: [number, number, number] = [-8, -2, -6]
const GLOBE_ROUGHNESS = 0.92
const GLOBE_METALNESS = 0.04
const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'
const COLOR_CHANNELS = 4
const POSITION_CHANNELS = 3

const ATMOSPHERE_VERTEX = `
varying vec3 vNormal;
varying vec3 vView;
void main() {
  vec4 world = modelViewMatrix * vec4(position, 1.0);
  vNormal = normalize(normalMatrix * normal);
  vView = normalize(-world.xyz);
  gl_Position = projectionMatrix * world;
}`

const ATMOSPHERE_FRAGMENT = `
uniform vec3 glowColor;
varying vec3 vNormal;
varying vec3 vView;
void main() {
  float rim = pow(1.0 - abs(dot(vNormal, vView)), 3.0);
  gl_FragColor = vec4(glowColor, rim * 0.55);
}`

const SPARKLE_VERTEX = `
uniform float uTime;
uniform float uSize;
uniform float uRise;
uniform float uCycles;
uniform float uTwinkle;
uniform float uPixelRatio;
attribute float aPhase;
attribute float aScale;
varying float vAlpha;
void main() {
  float life = fract(uTime * uCycles + aPhase);
  float arc = sin(life * 3.14159265);
  vec3 lifted = position + normalize(position) * life * uRise;
  vec4 view = modelViewMatrix * vec4(lifted, 1.0);
  float twinkle = 0.65 + 0.35 * sin(uTime * uTwinkle * aScale + aPhase * 6.2831853);
  vAlpha = arc * twinkle;
  gl_PointSize = uSize * aScale * (0.6 + 0.4 * arc) * uPixelRatio / -view.z;
  gl_Position = projectionMatrix * view;
}`

const SPARKLE_FRAGMENT = `
uniform vec3 uColor;
varying float vAlpha;
void main() {
  vec2 uv = gl_PointCoord - 0.5;
  float core = pow(max(0.0, 1.0 - length(uv) * 2.0), 2.5);
  float rays = max(0.0, 1.0 - abs(uv.x) * 16.0) * max(0.0, 1.0 - abs(uv.y) * 2.0)
    + max(0.0, 1.0 - abs(uv.y) * 16.0) * max(0.0, 1.0 - abs(uv.x) * 2.0);
  float light = core + rays * 0.6;
  if (light * vAlpha < 0.01) discard;
  gl_FragColor = vec4(mix(uColor, vec3(1.0), core * 0.55), light * vAlpha);
}`

function noise(seed: number) {
  const value = Math.sin(seed) * NOISE_SCALE
  return value - Math.floor(value)
}

function centerOf(world: SilhouetteWorld | null, iso: string | null): SilhouettePoint | null {
  return iso ? world?.byIso.get(iso)?.center ?? null : null
}

function sparkleSpread(world: SilhouetteWorld | null, iso: string) {
  const bounds = world?.byIso.get(iso)?.bounds
  if (!bounds) return SPARKLE_MIN_SPREAD_DEGREES
  const [west, south, east, north] = bounds
  const width = (east >= west ? east - west : east - west + FULL_TURN_DEGREES) * Math.cos((south + north) / 2 * DEGREES)
  const span = Math.min(width, north - south)
  return Math.min(SPARKLE_MAX_SPREAD_DEGREES, Math.max(SPARKLE_MIN_SPREAD_DEGREES, span * SPARKLE_SPREAD_SHARE))
}

function focusDistance(world: SilhouetteWorld | null, iso: string | null) {
  const bounds = iso ? world?.byIso.get(iso)?.bounds : null
  if (!bounds) return BASE_DISTANCE
  const [west, south, east, north] = bounds
  const width = (east >= west ? east - west : east - west + FULL_TURN_DEGREES) * Math.cos((south + north) / 2 * DEGREES)
  const span = Math.max(width, north - south) * DEGREES
  return Math.min(MAX_FOCUS_DISTANCE, Math.max(MIN_FOCUS_DISTANCE, RADIUS + span / 2 * RADIUS / FIT_SHARE))
}

function flightHeight(from: GeoPoint, to: GeoPoint) {
  const angle = globeNormal(from).angleTo(globeNormal(to))
  return Math.min(MAX_FLIGHT_HEIGHT, Math.max(MIN_FLIGHT_HEIGHT, angle * FLIGHT_HEIGHT_PER_RADIAN))
}

function routeWaypoints(world: SilhouetteWorld | null, fromIso: string | null, toIso: string | null, mode: QuizTravelMode | null,
  route: string[]): GeoPoint[] | null {
  const from = centerOf(world, fromIso)
  const to = centerOf(world, toIso)
  if (!from || !to || !fromIso || !toIso) return null
  return mode === 'DRIVE' ? landWaypoints(world, from, fromIso, route, toIso, to) : [from, to]
}

function RouteArc({ points, mode, opacity, active = false }: {
  points: GeoPoint[], mode: QuizTravelMode, opacity: number, active?: boolean
}) {
  const curve = useMemo(() => {
    const path = globePath(points)
    const height = mode === 'FLY' ? flightHeight(points[0], points[points.length - 1]) : DRIVE_ARC_HEIGHT
    return new CatmullRomCurve3(Array.from({ length: ARC_POINTS + 1 }, (_, index) => {
      const share = index / ARC_POINTS
      return path.normalAt(share).multiplyScalar(ROUTE_BASE + Math.sin(Math.PI * share) * height)
    }))
  }, [points, mode])

  return <mesh>
    <tubeGeometry args={[curve, ARC_SEGMENTS, active ? ARC_ACTIVE_RADIUS : ARC_RADIUS, ARC_RADIAL_SEGMENTS, false]} />
    <meshBasicMaterial color={active ? QUIZ_COLORS.routeActive : QUIZ_COLORS.route} transparent opacity={opacity}
      depthWrite={false} toneMapped={false} />
  </mesh>
}

function PulseMarker({ center, color, pin = true }: { center: SilhouettePoint, color: string, pin?: boolean }) {
  const ring = useRef<Mesh>(null)
  const position = useMemo(() => globePoint(center, MARKER_HEIGHT), [center])
  const orientation = useMemo(() => new Quaternion().setFromUnitVectors(UP, globeNormal(center)), [center])

  useFrame(({ clock }) => {
    if (!ring.current) return
    const pulse = (clock.elapsedTime * PULSE_SPEED) % 1
    ring.current.scale.setScalar(1 + pulse * PULSE_GROWTH)
    const material = ring.current.material as MeshBasicMaterial
    material.opacity = (1 - pulse) * PULSE_OPACITY
  })

  return <group position={position} quaternion={orientation}>
    {pin && <mesh>
      <sphereGeometry args={[MARKER_SIZE, MARKER_SEGMENTS, MARKER_SEGMENTS]} />
      <meshBasicMaterial color={color} toneMapped={false} />
    </mesh>}
    <mesh ref={ring} rotation={[-Math.PI / 2, 0, 0]}>
      <ringGeometry args={[RING_INNER, RING_OUTER, MARKER_SEGMENTS * 2]} />
      <meshBasicMaterial color={color} side={DoubleSide} transparent depthWrite={false} toneMapped={false} />
    </mesh>
  </group>
}

function Burst({ burst, world }: { burst: QuizGlobeBurst, world: SilhouetteWorld | null }) {
  const ring = useRef<Mesh>(null)
  const started = useRef<number | null>(null)
  const center = centerOf(world, burst.iso)
  const position = useMemo(() => center ? globePoint(center, MARKER_HEIGHT) : ORIGIN, [center])
  const orientation = useMemo(() => center ? new Quaternion().setFromUnitVectors(UP, globeNormal(center)) : new Quaternion(), [center])
  const color = QUIZ_LEVEL_PAINT[burst.level]?.edge ?? QUIZ_COLORS.burst

  useFrame(({ clock }) => {
    if (!ring.current) return
    started.current ??= clock.elapsedTime
    const share = Math.min(1, (clock.elapsedTime - started.current) / BURST_SECONDS)
    ring.current.visible = share < 1
    ring.current.scale.setScalar(1 + easeJourney(share) * BURST_GROWTH)
    const material = ring.current.material as MeshBasicMaterial
    material.opacity = (1 - share) * (1 - share) * BURST_OPACITY
  })

  if (!center) return null
  return <group position={position} quaternion={orientation}>
    <mesh ref={ring} rotation={[-Math.PI / 2, 0, 0]}>
      <ringGeometry args={[RING_INNER, BURST_RING_OUTER, MARKER_SEGMENTS * 2]} />
      <meshBasicMaterial color={color} side={DoubleSide} transparent depthWrite={false} toneMapped={false} />
    </mesh>
  </group>
}

interface QuizSparkleCountry {
  center: SilhouettePoint
  spread: number
}

function Sparkles({ countries }: { countries: QuizSparkleCountry[] }) {
  const pixelRatio = useThree(state => state.viewport.dpr)
  const geometry = useMemo(() => {
    const total = countries.length * SPARKLES_PER_COUNTRY
    const positions = new Float32Array(total * POSITION_CHANNELS)
    const phases = new Float32Array(total)
    const scales = new Float32Array(total)
    countries.forEach(({ center: [longitude, latitude], spread }, country) => {
      for (let index = 0; index < SPARKLES_PER_COUNTRY; index++) {
        const slot = country * SPARKLES_PER_COUNTRY + index
        const angle = (index / SPARKLES_PER_COUNTRY) * Math.PI * 2 + noise(slot) * Math.PI
        const distance = spread * (SPARKLE_MIN_RADIUS_SHARE + (1 - SPARKLE_MIN_RADIUS_SHARE) * noise(slot * SPARKLE_SEED_RADIUS))
        const point = globePoint([longitude + Math.cos(angle) * distance, latitude + Math.sin(angle) * distance], GROUND)
        positions.set([point.x, point.y, point.z], slot * POSITION_CHANNELS)
        phases[slot] = noise(slot * SPARKLE_SEED_PHASE)
        scales[slot] = SPARKLE_MIN_SCALE + SPARKLE_SCALE_RANGE * noise(slot * SPARKLE_SEED_SCALE)
      }
    })
    const built = new BufferGeometry()
    built.setAttribute('position', new BufferAttribute(positions, POSITION_CHANNELS))
    built.setAttribute('aPhase', new BufferAttribute(phases, 1))
    built.setAttribute('aScale', new BufferAttribute(scales, 1))
    return built
  }, [countries])
  const material = useRef<ShaderMaterial>(null)
  const uniforms = useMemo(() => ({
    uTime: { value: 0 },
    uSize: { value: SPARKLE_SIZE },
    uRise: { value: SPARKLE_RISE },
    uCycles: { value: SPARKLE_CYCLES_PER_SECOND },
    uTwinkle: { value: SPARKLE_TWINKLE_SPEED },
    uPixelRatio: { value: 1 },
    uColor: { value: new Color(QUIZ_LEVEL_PAINT[QUIZ_MAX_LEVEL].edge) },
  }), [])
  useEffect(() => () => geometry.dispose(), [geometry])

  useFrame(({ clock }) => {
    if (!material.current) return
    material.current.uniforms.uTime.value = clock.elapsedTime
    material.current.uniforms.uPixelRatio.value = pixelRatio
  })

  return <points geometry={geometry}>
    <shaderMaterial ref={material} vertexShader={SPARKLE_VERTEX} fragmentShader={SPARKLE_FRAGMENT} uniforms={uniforms}
      transparent depthWrite={false} blending={AdditiveBlending} toneMapped={false} />
  </points>
}

function Stars() {
  const geometry = useMemo(() => {
    const positions = new Float32Array(STAR_COUNT * POSITION_CHANNELS)
    for (let index = 0; index < STAR_COUNT; index++) {
      const longitude = noise(index * NOISE_LONGITUDE_SEED) * FULL_TURN_DEGREES - FULL_TURN_DEGREES / 2
      const latitude = Math.asin(noise(index * NOISE_LATITUDE_SEED) * 2 - 1) / DEGREES
      const point = globePoint([longitude, latitude], STAR_RADIUS)
      positions.set([point.x, point.y, point.z], index * POSITION_CHANNELS)
    }
    const built = new BufferGeometry()
    built.setAttribute('position', new BufferAttribute(positions, POSITION_CHANNELS))
    return built
  }, [])
  useEffect(() => () => geometry.dispose(), [geometry])
  return <points geometry={geometry}>
    <pointsMaterial color={QUIZ_COLORS.contrail} size={STAR_SIZE} transparent opacity={STAR_OPACITY} depthWrite={false} />
  </points>
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

interface TravellerProps {
  world: SilhouetteWorld | null
  current: string | null
  next: QuizGlobeDestination | null
  journey: QuizJourney | null
  journeyPoints: GeoPoint[] | null
  overview: boolean
  onArrive: () => void
  onUserMove?: () => void
}

function Traveller({ world, current, next, journey, journeyPoints, overview, onArrive, onUserMove }: TravellerProps) {
  const camera = useThree(state => state.camera)
  const element = useThree(state => state.gl.domElement)
  const car = useRef<Group>(null)
  const plane = useRef<Group>(null)
  const traveller = useRef<Group>(null)
  const controls = useRef<OrbitControls | null>(null)
  const autopilot = useRef(true)
  const travel = useRef<{ id: number, elapsed: number } | null>(null)
  const arrived = useRef(onArrive)
  const moved = useRef(onUserMove)
  const contrail = useMemo(() => {
    const geometry = new BufferGeometry()
    geometry.setAttribute('position', new BufferAttribute(new Float32Array(CONTRAIL_POINTS * POSITION_CHANNELS), POSITION_CHANNELS))
    geometry.setAttribute('color', new BufferAttribute(new Float32Array(CONTRAIL_POINTS * COLOR_CHANNELS), COLOR_CHANNELS))
    geometry.setDrawRange(0, 0)
    return new Line(geometry, new LineBasicMaterial({ vertexColors: true, transparent: true, depthWrite: false, toneMapped: false }))
  }, [])
  const trailPoints = useRef<Vector3[]>([])
  const white = useMemo(() => new Color(QUIZ_COLORS.contrail), [])
  const goalKey = `${current}|${next?.iso}|${journey?.id}|${overview}`
  const journeyPath = useMemo(() => journeyPoints ? globePath(journeyPoints) : null, [journeyPoints])

  useEffect(() => {
    arrived.current = onArrive
    moved.current = onUserMove
  }, [onArrive, onUserMove])

  useEffect(() => {
    const orbit = new OrbitControls(camera, element)
    orbit.enableDamping = true
    orbit.dampingFactor = DAMPING
    orbit.enablePan = false
    orbit.minDistance = MIN_DISTANCE
    orbit.maxDistance = MAX_DISTANCE
    orbit.rotateSpeed = ROTATE_SPEED
    orbit.zoomSpeed = ZOOM_SPEED
    orbit.autoRotateSpeed = AUTO_ROTATE_SPEED
    const release = () => {
      autopilot.current = false
      moved.current?.()
    }
    orbit.addEventListener('start', release)
    controls.current = orbit
    return () => {
      orbit.removeEventListener('start', release)
      orbit.dispose()
      controls.current = null
    }
  }, [camera, element])

  useEffect(() => {
    autopilot.current = true
  }, [goalKey])

  useEffect(() => {
    travel.current = journey ? { id: journey.id, elapsed: 0 } : null
    trailPoints.current = []
  }, [journey])

  useEffect(() => () => {
    contrail.geometry.dispose()
    const material = contrail.material as LineBasicMaterial
    material.dispose()
  }, [contrail])

  useFrame((_, delta) => {
    const orbit = controls.current
    const home = centerOf(world, journey?.from ?? current) ?? DEFAULT_CENTER
    const away = centerOf(world, journey?.to ?? next?.iso ?? null)
    const reduced = window.matchMedia(REDUCED_MOTION_QUERY).matches
    const flying = journey?.mode === 'FLY'
    const duration = flying ? QUIZ_FLY_SECONDS : QUIZ_DRIVE_SECONDS
    const trip = travel.current
    if (journey && trip) trip.elapsed = Math.min(duration, trip.elapsed + (reduced ? duration : delta))
    const raw = journey && trip ? trip.elapsed / duration : 0
    const share = easeJourney(raw)
    const normal = journey && journeyPath ? journeyPath.normalAt(share) : globeNormal(home)
    const ahead = journey && journeyPath ? journeyPath.normalAt(Math.min(1, share + LOOK_AHEAD))
      : away ? betweenOnGlobe(home, away, LOOK_AHEAD) : normal.clone().add(new Vector3(0, LOOK_AHEAD, 0))
    const forward = ahead.addScaledVector(normal, -ahead.dot(normal)).normalize()
    const height = journey && away && flying ? flightHeight(home, away) : 0
    const lift = flying ? Math.sin(Math.PI * share) * height : 0

    if (traveller.current) {
      traveller.current.visible = current != null || journey != null
      traveller.current.position.copy(normal).multiplyScalar(GROUND + lift)
      const upright = new Quaternion().setFromUnitVectors(UP, normal)
      const local = forward.clone().applyQuaternion(upright.clone().invert())
      traveller.current.quaternion.copy(upright).multiply(new Quaternion().setFromAxisAngle(UP, Math.atan2(local.x, local.z)))
      traveller.current.scale.setScalar(TRAVELLER_SCALE * (camera.position.length() - RADIUS) / (BASE_DISTANCE - RADIUS))
      const planeAmount = flying ? Math.max(0, Math.min(1, raw * PLANE_MORPH_RATE, (1 - raw) * PLANE_MORPH_RATE)) : 0
      car.current?.scale.setScalar(Math.max(Number.EPSILON, 1 - planeAmount))
      plane.current?.scale.setScalar(Math.max(Number.EPSILON, planeAmount))
    }

    if (journey && lift > CONTRAIL_MIN_LIFT && traveller.current) {
      trailPoints.current.push(traveller.current.position.clone())
      if (trailPoints.current.length > CONTRAIL_POINTS) trailPoints.current.shift()
    }
    const points = trailPoints.current
    const positions = contrail.geometry.getAttribute('position') as BufferAttribute
    const colors = contrail.geometry.getAttribute('color') as BufferAttribute
    points.forEach((point, index) => {
      positions.setXYZ(index, point.x, point.y, point.z)
      colors.setXYZW(index, white.r, white.g, white.b, journey ? index / points.length : 0)
    })
    positions.needsUpdate = true
    colors.needsUpdate = true
    contrail.geometry.setDrawRange(0, points.length)

    if (!orbit) return
    orbit.autoRotate = overview && !journey && !reduced && !autopilot.current
    if (autopilot.current) {
      const idleOverview = overview && !journey
      const pairAngle = !journey && away && !idleOverview ? globeNormal(home).angleTo(globeNormal(away)) : 0
      const flightAngle = journey && away ? globeNormal(home).angleTo(globeNormal(away)) : 0
      const anchor = idleOverview ? globeNormal(home)
        : pairAngle > 0 && away ? betweenOnGlobe(home, away, 1 / 2) : normal
      const base = journey
        ? focusDistance(world, journey.from) + (focusDistance(world, journey.to) - focusDistance(world, journey.from)) * share
        : focusDistance(world, current)
      const distance = Math.min(MAX_DISTANCE, idleOverview ? OVERVIEW_DISTANCE
        : base + pairAngle * PAIR_PULL_PER_RADIAN + Math.sin(Math.PI * share) * flightAngle * FLIGHT_PULL_PER_RADIAN)
      const goal = anchor.clone().multiplyScalar(distance)
      const follow = reduced ? 1 : 1 - Math.exp(-delta * (journey ? TRAVEL_FOLLOW_RATE : FOLLOW_RATE))
      const direction = camera.position.clone().normalize().lerp(anchor, follow).normalize()
      const length = camera.position.length() + (distance - camera.position.length()) * follow
      camera.position.copy(direction.multiplyScalar(length))
      camera.lookAt(ORIGIN)
      if (!journey && camera.position.distanceTo(goal) < ARRIVED_EPSILON) autopilot.current = false
    }
    orbit.update()

    if (journey && trip && raw >= 1) {
      travel.current = null
      autopilot.current = true
      arrived.current()
    }
  })

  return <>
    <primitive object={contrail} />
    <group ref={traveller}>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.003, 0]} visible={!journey || journey.mode === 'DRIVE'}>
        <circleGeometry args={[SHADOW_RADIUS, MARKER_SEGMENTS]} />
        <meshBasicMaterial color={QUIZ_COLORS.shadow} transparent opacity={SHADOW_OPACITY} depthWrite={false} />
      </mesh>
      <QuizCar groupRef={car} />
      <QuizPlane groupRef={plane} />
    </group>
  </>
}

function Atmosphere() {
  const material = useMemo(() => new ShaderMaterial({
    vertexShader: ATMOSPHERE_VERTEX,
    fragmentShader: ATMOSPHERE_FRAGMENT,
    uniforms: { glowColor: { value: new Color(QUIZ_COLORS.atmosphere) } },
    side: BackSide,
    transparent: true,
    depthWrite: false,
    blending: AdditiveBlending,
  }), [])
  useEffect(() => () => material.dispose(), [material])
  return <mesh material={material}>
    <sphereGeometry args={[ATMOSPHERE_RADIUS, SPHERE_SEGMENTS / 2, SPHERE_RINGS / 2]} />
  </mesh>
}

function GlobeWorld(props: Omit<QuizGlobeProps, 'label' | 'className'>) {
  const { world, countries, trail, current, next, focus, journey, burst, overview, padding, onArrive, onUserMove } = props
  const textures = useMemo(() => createQuizGlobeTextures(), [])
  const globe = useRef<MeshStandardMaterial>(null)
  const levels = useMemo(() => new Map(countries.map(country => [country.countryCode, country.level])), [countries])
  const arcs = useMemo(() => trail.slice(-TRAIL_VISIBLE).flatMap((visit, index, recent) => {
    const points = routeWaypoints(world, visit.fromCountryCode, visit.countryCode, visit.mode, visit.route)
    if (!points || !visit.mode) return []
    const age = recent.length > 1 ? index / (recent.length - 1) : 1
    return [{ key: `${visit.stop}`, points, mode: visit.mode, opacity: ARC_MIN_OPACITY + (ARC_MAX_OPACITY - ARC_MIN_OPACITY) * age }]
  }), [trail, world])
  const golden = useMemo(() => countries.filter(country => country.level >= QUIZ_MAX_LEVEL)
    .flatMap(country => {
      const center = centerOf(world, country.countryCode)
      return center ? [{ center, spread: sparkleSpread(world, country.countryCode) }] : []
    }), [countries, world])
  const currentCenter = centerOf(world, journey ? null : current)
  const nextCenter = centerOf(world, journey ? null : next?.iso ?? null)
  const nextPoints = useMemo(() => journey || !next ? null : routeWaypoints(world, current, next.iso, next.mode, next.route),
    [world, current, next, journey])
  const journeyPoints = useMemo(() => journey ? routeWaypoints(world, journey.from, journey.to, journey.mode, journey.route) : null,
    [world, journey])

  useEffect(() => () => {
    textures.color.dispose()
    textures.glow.dispose()
  }, [textures])
  useEffect(() => {
    if (world) paintQuizGlobe(textures, world, { levels, current: journey?.to ?? current, next: journey ? null : next?.iso ?? null, focus })
  }, [textures, world, levels, current, next, focus, journey])

  useFrame(({ clock }) => {
    if (globe.current) globe.current.emissiveIntensity = GLOW_BASE + Math.sin(clock.elapsedTime * GLOW_SPEED) * GLOW_SWING
  })

  return <>
    <ViewOffset padding={padding} />
    <ambientLight intensity={AMBIENT_INTENSITY} />
    <directionalLight position={SUN_POSITION} intensity={SUN_INTENSITY} color={QUIZ_COLORS.sunlight} />
    <directionalLight position={RIM_POSITION} intensity={RIM_INTENSITY} color={QUIZ_COLORS.atmosphere} />
    <Stars />
    <mesh>
      <sphereGeometry args={[RADIUS, SPHERE_SEGMENTS, SPHERE_RINGS]} />
      <meshStandardMaterial ref={globe} map={textures.color} emissiveMap={textures.glow} emissive={QUIZ_COLORS.contrail}
        roughness={GLOBE_ROUGHNESS} metalness={GLOBE_METALNESS} />
    </mesh>
    <Atmosphere />
    {arcs.map(arc => <RouteArc key={arc.key} points={arc.points} mode={arc.mode} opacity={arc.opacity} />)}
    {nextPoints && next && <RouteArc points={nextPoints} mode={next.mode} opacity={ARC_ACTIVE_OPACITY} active />}
    {journey && journeyPoints && <RouteArc points={journeyPoints} mode={journey.mode} opacity={ARC_ACTIVE_OPACITY} active />}
    {golden.length > 0 && <Sparkles countries={golden} />}
    {currentCenter && <PulseMarker center={currentCenter} color={QUIZ_COLORS.currentEdge} pin={false} />}
    {nextCenter && <PulseMarker center={nextCenter} color={QUIZ_COLORS.nextEdge} />}
    {burst && <Burst key={burst.id} burst={burst} world={world} />}
    <Traveller world={world} current={current} next={next} journey={journey} journeyPoints={journeyPoints} overview={overview}
      onArrive={onArrive} onUserMove={onUserMove} />
  </>
}

export function QuizGlobe({ label, className, ...props }: QuizGlobeProps) {
  return <div className={className} role="img" aria-label={label}>
    <Canvas dpr={[1, MAX_PIXEL_RATIO]} camera={{ position: [0, 2, OVERVIEW_DISTANCE], fov: FIELD_OF_VIEW, near: NEAR, far: FAR }}
      gl={{ antialias: true, powerPreference: 'high-performance' }}>
      <GlobeWorld {...props} />
    </Canvas>
  </div>
}
