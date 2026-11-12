import { useEffect, useLayoutEffect, useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import {
  BufferAttribute,
  BufferGeometry,
  CanvasTexture,
  Color,
  ConeGeometry,
  DoubleSide,
  CylinderGeometry,
  IcosahedronGeometry,
  type InstancedMesh,
  Object3D,
  SRGBColorSpace,
} from 'three'
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js'
import {
  BUSH_BELT_DEPTH,
  BUSH_BELT_START,
  BUSH_COUNT,
  MEADOW_RADIUS,
  MOON_DIRECTION,
  SKY_RADIUS,
  STAR_COUNT,
  STAR_RADIUS,
  TREE_BELT_DEPTH,
  TREE_BELT_START,
  TREE_COUNT,
} from './flagPlazaConstants'
import { seededShare } from './flagPlazaLayout'
import { advanceFireflyTime, createFireflyMaterial, createMeadowMaterial, createSkyMaterial } from './flagPlazaShaders'

const GROUND_TILT = -Math.PI / 2
const MEADOW_SEGMENTS = 64
const MEADOW_DEPTH = -0.14
const SKY_SEGMENTS = 32
const STAR_SIZE = 0.9
const STAR_MIN_ELEVATION = 0.22
const STAR_SEED = 7
const STAR_COLOR = '#dbe4ff'
const MOON_DISTANCE = 240
const MOON_SIZE = 26
const MOON_TEXTURE_SIZE = 128
const TREE_SEED = 11
const TREE_MIN_SCALE = 0.8
const TREE_SCALE_RANGE = 1.1
const TREE_SPREAD_POWER = 0.7
const TREE_SIDES = 7
const TREE_COLOR = '#2c6141'
const TRUNK_COLOR = '#5a4230'
const BUSH_SEED = 23
const BUSH_RADIUS = 0.7
const BUSH_MIN_SCALE = 0.5
const BUSH_SCALE_RANGE = 0.9
const BUSH_FLATTEN = 0.62
const BUSH_COLORS = ['#4a8a4f', '#3f7d45', '#58975c']
const HILL_SEGMENTS = 180
const HILL_RANGES = [
  { radius: 285, base: 9, swell: 16, color: '#0f1a2c', seed: 3 },
  { radius: 240, base: 4, swell: 9, color: '#0a1320', seed: 5 },
]
const HILL_WAVES = [2, 5, 11, 23]
const HILL_FOOT = -2
const FIREFLY_COUNT = 260
const FIREFLY_SEED = 31
const FIREFLY_BELT_START = 1
const FIREFLY_BELT_DEPTH = 46
const FIREFLY_MIN_HEIGHT = 0.4
const FIREFLY_HEIGHT_RANGE = 2.4

interface FlagPlazaMeadowProps {
  plazaRadius: number
  calm: boolean
  details: boolean
  lite: boolean
}

function createHills(radius: number, base: number, swell: number, seed: number) {
  const positions = new Float32Array((HILL_SEGMENTS + 1) * 2 * 3)
  const indices: number[] = []
  const phases = HILL_WAVES.map((_, wave) => seededShare(`h${wave}`, seed) * Math.PI * 2)
  for (let segment = 0; segment <= HILL_SEGMENTS; segment++) {
    const angle = segment / HILL_SEGMENTS * Math.PI * 2
    const crest = HILL_WAVES.reduce((sum, wave, index) =>
      sum + (Math.sin(angle * wave + phases[index]) * 0.5 + 0.5) / (index + 1), 0)
    const x = Math.cos(angle) * radius
    const z = Math.sin(angle) * radius
    positions.set([x, HILL_FOOT, z, x, base + crest * swell, z], segment * 6)
    if (segment < HILL_SEGMENTS) {
      const low = segment * 2
      indices.push(low, low + 1, low + 2, low + 1, low + 3, low + 2)
    }
  }
  return new BufferGeometry().setAttribute('position', new BufferAttribute(positions, 3)).setIndex(indices)
}

function createFireflies(plazaRadius: number) {
  const positions = new Float32Array(FIREFLY_COUNT * 3)
  const seeds = new Float32Array(FIREFLY_COUNT)
  for (let fly = 0; fly < FIREFLY_COUNT; fly++) {
    const angle = seededShare(`a${fly}`, FIREFLY_SEED) * Math.PI * 2
    const distance = plazaRadius + FIREFLY_BELT_START + seededShare(`d${fly}`, FIREFLY_SEED) * FIREFLY_BELT_DEPTH
    positions.set([Math.cos(angle) * distance,
      FIREFLY_MIN_HEIGHT + seededShare(`y${fly}`, FIREFLY_SEED) * FIREFLY_HEIGHT_RANGE,
      Math.sin(angle) * distance], fly * 3)
    seeds[fly] = seededShare(`s${fly}`, FIREFLY_SEED)
  }
  return new BufferGeometry()
    .setAttribute('position', new BufferAttribute(positions, 3))
    .setAttribute('aSeed', new BufferAttribute(seeds, 1))
}

function createStars() {
  const positions = new Float32Array(STAR_COUNT * 3)
  for (let star = 0; star < STAR_COUNT; star++) {
    const angle = seededShare(`a${star}`, STAR_SEED) * Math.PI * 2
    const elevation = STAR_MIN_ELEVATION + seededShare(`e${star}`, STAR_SEED) * (Math.PI / 2 - STAR_MIN_ELEVATION)
    positions[star * 3] = STAR_RADIUS * Math.cos(elevation) * Math.cos(angle)
    positions[star * 3 + 1] = STAR_RADIUS * Math.sin(elevation)
    positions[star * 3 + 2] = STAR_RADIUS * Math.cos(elevation) * Math.sin(angle)
  }
  return new BufferGeometry().setAttribute('position', new BufferAttribute(positions, 3))
}

function createMoonTexture() {
  const canvas = document.createElement('canvas')
  canvas.width = MOON_TEXTURE_SIZE
  canvas.height = MOON_TEXTURE_SIZE
  const context = canvas.getContext('2d')
  const middle = MOON_TEXTURE_SIZE / 2
  if (context) {
    const halo = context.createRadialGradient(middle, middle, 0, middle, middle, middle)
    halo.addColorStop(0, 'rgba(255,250,235,1)')
    halo.addColorStop(0.18, 'rgba(248,244,228,1)')
    halo.addColorStop(0.22, 'rgba(200,210,255,0.35)')
    halo.addColorStop(1, 'rgba(120,140,220,0)')
    context.fillStyle = halo
    context.fillRect(0, 0, MOON_TEXTURE_SIZE, MOON_TEXTURE_SIZE)
  }
  const texture = new CanvasTexture(canvas)
  texture.colorSpace = SRGBColorSpace
  return texture
}

function createTreeGeometry() {
  const lower = new ConeGeometry(1.7, 3.4, TREE_SIDES).translate(0, 2.9, 0)
  const upper = new ConeGeometry(1.15, 2.8, TREE_SIDES).translate(0, 4.7, 0)
  const merged = mergeGeometries([lower, upper])
  lower.dispose()
  upper.dispose()
  return merged
}

function scatter(mesh: InstancedMesh | null, count: number, seed: number, start: number, depth: number,
  place: (dummy: Object3D, share: number) => void) {
  if (!mesh) return
  const dummy = new Object3D()
  for (let item = 0; item < count; item++) {
    const angle = seededShare(`t${item}`, seed) * Math.PI * 2
    const distance = start + seededShare(`d${item}`, seed) ** TREE_SPREAD_POWER * depth
    dummy.position.set(Math.cos(angle) * distance, 0, Math.sin(angle) * distance)
    dummy.rotation.set(0, angle, 0)
    place(dummy, seededShare(`s${item}`, seed))
    dummy.updateMatrix()
    mesh.setMatrixAt(item, dummy.matrix)
  }
  mesh.instanceMatrix.needsUpdate = true
  mesh.computeBoundingSphere()
}

export function FlagPlazaMeadow({ plazaRadius, calm, details, lite }: FlagPlazaMeadowProps) {
  const scenic = details && !lite
  const trees = useRef<InstancedMesh>(null)
  const trunks = useRef<InstancedMesh>(null)
  const bushes = useRef<InstancedMesh>(null)
  const sky = useMemo(() => createSkyMaterial(), [])
  const meadow = useMemo(() => createMeadowMaterial(plazaRadius), [plazaRadius])
  const stars = useMemo(() => createStars(), [])
  const moon = useMemo(() => createMoonTexture(), [])
  const treeGeometry = useMemo(() => createTreeGeometry(), [])
  const trunkGeometry = useMemo(() => new CylinderGeometry(0.18, 0.26, 1.6, 6).translate(0, 0.8, 0), [])
  const bushGeometry = useMemo(() => new IcosahedronGeometry(BUSH_RADIUS, 0), [])
  const bushTints = useMemo(() => Float32Array.from(Array.from({ length: BUSH_COUNT },
    (_, bush) => new Color(BUSH_COLORS[bush % BUSH_COLORS.length]).toArray()).flat()), [])
  const hills = useMemo(() => HILL_RANGES.map(range => ({
    key: range.radius,
    color: range.color,
    geometry: createHills(range.radius, range.base, range.swell, range.seed),
  })), [])
  const fireflies = useMemo(() => createFireflies(plazaRadius), [plazaRadius])
  const fireflyMaterial = useMemo(() => createFireflyMaterial(calm), [calm])
  const moonPosition = useMemo(() => MOON_DIRECTION.map(axis => axis * MOON_DISTANCE) as [number, number, number], [])

  useEffect(() => () => {
    sky.dispose()
    stars.dispose()
    moon.dispose()
    treeGeometry.dispose()
    trunkGeometry.dispose()
    bushGeometry.dispose()
  }, [sky, stars, moon, treeGeometry, trunkGeometry, bushGeometry])

  useEffect(() => () => meadow.dispose(), [meadow])
  useEffect(() => () => hills.forEach(hill => hill.geometry.dispose()), [hills])
  useEffect(() => () => fireflies.dispose(), [fireflies])
  useEffect(() => () => fireflyMaterial.dispose(), [fireflyMaterial])

  useFrame(state => advanceFireflyTime(state.clock.elapsedTime))

  useLayoutEffect(() => {
    const placeTree = (dummy: Object3D, share: number) => dummy.scale.setScalar(TREE_MIN_SCALE + share * TREE_SCALE_RANGE)
    scatter(trees.current, TREE_COUNT, TREE_SEED, TREE_BELT_START, TREE_BELT_DEPTH, placeTree)
    scatter(trunks.current, TREE_COUNT, TREE_SEED, TREE_BELT_START, TREE_BELT_DEPTH, placeTree)
    scatter(bushes.current, BUSH_COUNT, BUSH_SEED, plazaRadius + BUSH_BELT_START, BUSH_BELT_DEPTH, (dummy, share) => {
      const size = BUSH_MIN_SCALE + share * BUSH_SCALE_RANGE
      dummy.scale.set(size, size * BUSH_FLATTEN, size)
      dummy.position.y = size * BUSH_RADIUS * BUSH_FLATTEN * BUSH_FLATTEN
    })
  }, [plazaRadius, details])

  return (
    <>
      <mesh material={sky} renderOrder={-1}>
        <sphereGeometry args={[SKY_RADIUS, SKY_SEGMENTS, SKY_SEGMENTS]} />
      </mesh>
      <points geometry={stars}>
        <pointsMaterial color={STAR_COLOR} size={STAR_SIZE} sizeAttenuation fog={false} />
      </points>
      <sprite position={moonPosition} scale={MOON_SIZE}>
        <spriteMaterial map={moon} fog={false} depthWrite={false} toneMapped={false} />
      </sprite>
      {scenic && hills.map(hill => (
        <mesh key={hill.key} geometry={hill.geometry}>
          <meshBasicMaterial color={hill.color} fog={false} side={DoubleSide} />
        </mesh>
      ))}
      {scenic && <points geometry={fireflies} material={fireflyMaterial} frustumCulled={false} />}
      <mesh rotation-x={GROUND_TILT} position-y={MEADOW_DEPTH} material={meadow}>
        <circleGeometry args={[MEADOW_RADIUS, MEADOW_SEGMENTS]} />
      </mesh>
      {details && <>
        <instancedMesh ref={trees} args={[treeGeometry, undefined, TREE_COUNT]}>
          <meshLambertMaterial color={TREE_COLOR} flatShading />
        </instancedMesh>
        <instancedMesh ref={trunks} args={[trunkGeometry, undefined, TREE_COUNT]}>
          <meshLambertMaterial color={TRUNK_COLOR} />
        </instancedMesh>
        <instancedMesh ref={bushes} args={[bushGeometry, undefined, BUSH_COUNT]}>
          <instancedBufferAttribute attach="instanceColor" args={[bushTints, 3]} />
          <meshLambertMaterial flatShading />
        </instancedMesh>
      </>}
    </>
  )
}
