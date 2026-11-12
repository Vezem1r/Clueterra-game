import { useEffect, useLayoutEffect, useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import {
  AdditiveBlending,
  CircleGeometry,
  Color,
  CylinderGeometry,
  InstancedBufferAttribute,
  type InstancedMesh,
  Object3D,
  PlaneGeometry,
  Quaternion,
  Vector3,
} from 'three'
import {
  BEAM_RADIUS,
  FLAG_HEIGHT,
  FLAG_LOWERED_Y,
  FLAG_PLAZA_REGIONS,
  FLAG_SEGMENTS_X,
  FLAG_SEGMENTS_Y,
  FLAG_TOP_Y,
  FLAG_WIDTH,
  GLOW_RADIUS,
  POLE_HEIGHT,
  POLE_RADIUS,
  RAISE_SECONDS,
} from './flagPlazaConstants'
import { facingOutward, type FlagPlazaSlot } from './flagPlazaLayout'
import { advanceFlagTime, createBeamMaterial, createFlagMaterial, focusPlaza } from './flagPlazaMaterials'
import type { FlagAtlas } from './useFlagAtlas'

const MIN_RAISE_SCALE = 0.35
const RAISE_DELAY_SECONDS = 0.15
const FADE_IN_SECONDS = 0.45
const FADE_OUT_SECONDS = 0.9
const SPOTLIGHT_LINGER_SECONDS = 0.8
const SPOTLIGHT_ORDER = 10
const NO_FOCUS = -1
const GLOW_OPACITY = 0.3
const GLOW_HEIGHT = 0.02
const PHASE_SPREAD = Math.PI * 2
const GOLDEN_FRACTION = 0.618
const BEAM_SEGMENTS = 18
const CIRCLE_SEGMENTS = 32
const GROUND_TILT = -Math.PI / 2
const UP = new Vector3(0, 1, 0)
const REGION_COLORS = new Map(FLAG_PLAZA_REGIONS.map(region => [region.key, new Color(region.color)]))
const NEUTRAL_LIGHT = new Color('#ffffff')

interface FlagPlazaFlagsProps {
  slots: FlagPlazaSlot[]
  raised: boolean[]
  atlas: FlagAtlas
  calm: boolean
  isFlying: () => boolean
}

function eased(progress: number) {
  return progress < 0.5 ? 4 * progress ** 3 : 1 - (-2 * progress + 2) ** 3 / 2
}

export function FlagPlazaFlags({ slots, raised, atlas, calm, isFlying }: FlagPlazaFlagsProps) {
  const flags = useRef<InstancedMesh>(null)
  const spotlight = useRef<InstancedMesh>(null)
  const focus = useRef({ slot: NO_FOCUS, linger: 0, fade: 0 })
  const glows = useRef<InstancedMesh>(null)
  const beams = useRef<InstancedMesh>(null)
  const progress = useRef<Float32Array | null>(null)
  const settled = useRef(0)
  const dummy = useMemo(() => new Object3D(), [])
  const facing = useMemo(() => slots.map(slot => new Quaternion().setFromAxisAngle(UP, facingOutward(slot.angle))), [slots])
  const material = useMemo(() => createFlagMaterial(atlas.cellSize, atlas.texture, calm, false), [atlas, calm])
  const spotMaterial = useMemo(() => createFlagMaterial(atlas.cellSize, atlas.texture, calm, true), [atlas, calm])
  const beamMaterial = useMemo(() => createBeamMaterial(), [])
  const flagGeometry = useMemo(() => {
    const geometry = new PlaneGeometry(FLAG_WIDTH, FLAG_HEIGHT, FLAG_SEGMENTS_X, FLAG_SEGMENTS_Y)
    geometry.translate(FLAG_WIDTH / 2 + POLE_RADIUS, -FLAG_HEIGHT / 2, 0)
    geometry.setAttribute('aAtlasOffset', new InstancedBufferAttribute(atlas.offsets, 2))
    geometry.setAttribute('aPhase', new InstancedBufferAttribute(
      Float32Array.from(slots, slot => (slot.index * GOLDEN_FRACTION % 1) * PHASE_SPREAD), 1))
    geometry.setAttribute('aSlot', new InstancedBufferAttribute(Float32Array.from(slots, slot => slot.index), 1))
    return geometry
  }, [atlas, slots])
  const tints = useMemo(() => Float32Array.from(slots.flatMap(slot =>
    (REGION_COLORS.get(slot.country.region) ?? NEUTRAL_LIGHT).toArray())), [slots])
  const glowGeometry = useMemo(() => new CircleGeometry(GLOW_RADIUS, CIRCLE_SEGMENTS).rotateX(GROUND_TILT), [])
  const beamGeometry = useMemo(() => {
    const geometry = new CylinderGeometry(BEAM_RADIUS, POLE_RADIUS * 4, POLE_HEIGHT, BEAM_SEGMENTS, 1, true)
    geometry.translate(0, POLE_HEIGHT / 2, 0)
    return geometry
  }, [])

  useEffect(() => () => {
    material.dispose()
    spotMaterial.dispose()
    flagGeometry.dispose()
  }, [material, spotMaterial, flagGeometry])

  useEffect(() => () => focusPlaza(NO_FOCUS, 0), [])

  useEffect(() => () => {
    beamMaterial.dispose()
    glowGeometry.dispose()
    beamGeometry.dispose()
  }, [beamMaterial, glowGeometry, beamGeometry])

  const write = (index: number, value: number) => {
    const slot = slots[index]
    const lift = eased(value)
    const scale = value <= 0 ? 0 : MIN_RAISE_SCALE + (1 - MIN_RAISE_SCALE) * lift
    dummy.position.set(slot.x, FLAG_LOWERED_Y + (FLAG_TOP_Y - FLAG_LOWERED_Y) * lift, slot.z)
    dummy.quaternion.copy(facing[index])
    dummy.scale.setScalar(scale)
    dummy.updateMatrix()
    flags.current?.setMatrixAt(index, dummy.matrix)
    spotlight.current?.setMatrixAt(index, dummy.matrix)
    dummy.position.set(slot.x, GLOW_HEIGHT, slot.z)
    dummy.quaternion.identity()
    dummy.scale.setScalar(lift)
    dummy.updateMatrix()
    glows.current?.setMatrixAt(index, dummy.matrix)
    dummy.position.set(slot.x, 0, slot.z)
    dummy.scale.set(lift, lift, lift)
    dummy.updateMatrix()
    beams.current?.setMatrixAt(index, dummy.matrix)
  }

  const markDirty = () => {
    [flags, spotlight, glows, beams].forEach(mesh => {
      if (mesh.current) mesh.current.instanceMatrix.needsUpdate = true
    })
  }

  useLayoutEffect(() => {
    if (!progress.current || progress.current.length !== slots.length) {
      progress.current = Float32Array.from(raised, value => (value ? 1 : 0))
    }
    slots.forEach(slot => write(slot.index, progress.current?.[slot.index] ?? 0))
    markDirty()
  })

  useFrame((state, delta) => {
    advanceFlagTime(state.clock.elapsedTime)
    const values = progress.current
    if (!values) return
    const step = calm ? 1 : delta / RAISE_SECONDS
    settled.current = isFlying() ? 0 : settled.current + delta
    const holding = !calm && settled.current < RAISE_DELAY_SECONDS
    let changed = false
    raised.forEach((isRaised, index) => {
      const target = isRaised ? 1 : 0
      const current = values[index]
      if (current === target || (holding && target > current)) return
      values[index] = target > current ? Math.min(target, current + step) : Math.max(target, current - step)
      write(index, values[index])
      changed = true
    })
    if (changed) markDirty()
    const rising = raised.flatMap((isRaised, index) => (isRaised && values[index] < 1 ? [index] : []))
    const spot = focus.current
    if (rising.length === 1) {
      spot.slot = rising[0]
      spot.linger = SPOTLIGHT_LINGER_SECONDS
    } else {
      spot.linger = Math.max(0, spot.linger - delta)
    }
    const wanted = rising.length === 1 || spot.linger > 0 ? 1 : 0
    const speed = wanted > spot.fade ? FADE_IN_SECONDS : FADE_OUT_SECONDS
    spot.fade = calm ? wanted : wanted > spot.fade
      ? Math.min(wanted, spot.fade + delta / speed)
      : Math.max(wanted, spot.fade - delta / speed)
    if (spot.fade === 0) spot.slot = NO_FOCUS
    focusPlaza(spot.slot, spot.fade)
  })

  return (
    <>
      <instancedMesh ref={flags} args={[flagGeometry, material, slots.length]} frustumCulled={false} />
      <instancedMesh ref={spotlight} args={[flagGeometry, spotMaterial, slots.length]} frustumCulled={false}
        renderOrder={SPOTLIGHT_ORDER} />
      <instancedMesh ref={glows} args={[glowGeometry, undefined, slots.length]} frustumCulled={false}>
        <instancedBufferAttribute attach="instanceColor" args={[tints, 3]} />
        <meshBasicMaterial transparent opacity={GLOW_OPACITY} blending={AdditiveBlending} depthWrite={false} />
      </instancedMesh>
      <instancedMesh ref={beams} args={[beamGeometry, beamMaterial, slots.length]} frustumCulled={false}>
        <instancedBufferAttribute attach="instanceColor" args={[tints, 3]} />
      </instancedMesh>
    </>
  )
}
