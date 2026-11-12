import { useLayoutEffect, useMemo, useRef } from 'react'
import { type ThreeEvent, useFrame } from '@react-three/fiber'
import {
  BoxGeometry,
  Color,
  CylinderGeometry,
  type Group,
  type InstancedMesh,
  type Material,
  Object3D,
  Quaternion,
  SphereGeometry,
  Vector3,
} from 'three'
import {
  FLAG_HEIGHT,
  FLAG_PLAZA_REGIONS,
  FLAG_TOP_Y,
  FLAG_WIDTH,
  HIT_BOX_SIZE,
  PLAQUE_SIZE,
  POLE_BASE_SIZE,
  POLE_FINIAL_RADIUS,
  POLE_HEIGHT,
  POLE_RADIUS,
} from './flagPlazaConstants'
import { facingOutward, type FlagPlazaSlot } from './flagPlazaLayout'
import { fadedOpacity, plazaFocus } from './flagPlazaMaterials'

const POLE_SEGMENTS = 8
const FINIAL_SEGMENTS = 10
const CLICK_TOLERANCE_PX = 6
const CLOTH_DEPTH = 0.5
const HIDDEN_DEPTH = -1000
const UP = new Vector3(0, 1, 0)
const DEPTH_FADE_LIMIT = 0.5
const BASE_HEIGHT = 0.18
const PLAQUE_HEIGHT = 0.55
const PLAQUE_FORWARD = 0.33
const DARK_PLAQUE = new Color('#1b2130')
const SELECTED_PLAQUE = new Color('#ffffff')
const POLE_COLOR = '#9aa4b5'
const BASE_COLOR = '#1a2030'
const FINIAL_COLOR = '#e5e7eb'
const REGION_COLORS = new Map(FLAG_PLAZA_REGIONS.map(region => [region.key, new Color(region.color)]))

interface FlagPlazaPolesProps {
  slots: FlagPlazaSlot[]
  raised: boolean[]
  selected: number | null
  hovered: number | null
  isFlying: () => boolean
  onHover: (index: number | null) => void
  onSelect: (index: number) => void
}

export function FlagPlazaPoles({ slots, raised, selected, hovered, isFlying, onHover, onSelect }: FlagPlazaPolesProps) {
  const poles = useRef<InstancedMesh>(null)
  const bases = useRef<InstancedMesh>(null)
  const finials = useRef<InstancedMesh>(null)
  const plaques = useRef<InstancedMesh>(null)
  const targets = useRef<InstancedMesh>(null)
  const cloths = useRef<InstancedMesh>(null)
  const spotPole = useRef<Group>(null)
  const poleMaterial = useRef<Material>(null)
  const baseMaterial = useRef<Material>(null)
  const finialMaterial = useRef<Material>(null)
  const plaqueMaterial = useRef<Material>(null)
  const poleGeometry = useMemo(() => new CylinderGeometry(POLE_RADIUS, POLE_RADIUS * 1.4, POLE_HEIGHT, POLE_SEGMENTS)
    .translate(0, POLE_HEIGHT / 2, 0), [])
  const baseGeometry = useMemo(() => new BoxGeometry(POLE_BASE_SIZE, BASE_HEIGHT, POLE_BASE_SIZE)
    .translate(0, BASE_HEIGHT / 2, 0), [])
  const finialGeometry = useMemo(() => new SphereGeometry(POLE_FINIAL_RADIUS, FINIAL_SEGMENTS, FINIAL_SEGMENTS)
    .translate(0, POLE_HEIGHT, 0), [])
  const plaqueGeometry = useMemo(() => new BoxGeometry(...PLAQUE_SIZE), [])
  const targetGeometry = useMemo(() => new BoxGeometry(HIT_BOX_SIZE, POLE_HEIGHT, HIT_BOX_SIZE)
    .translate(0, POLE_HEIGHT / 2, 0), [])
  const clothGeometry = useMemo(() => new BoxGeometry(FLAG_WIDTH, FLAG_HEIGHT, CLOTH_DEPTH)
    .translate(FLAG_WIDTH / 2 + POLE_RADIUS, -FLAG_HEIGHT / 2, 0), [])

  useLayoutEffect(() => {
    const mesh = cloths.current
    if (!mesh) return
    const dummy = new Object3D()
    slots.forEach(slot => {
      dummy.position.set(slot.x, raised[slot.index] ? FLAG_TOP_Y : HIDDEN_DEPTH, slot.z)
      dummy.quaternion.copy(new Quaternion().setFromAxisAngle(UP, facingOutward(slot.angle)))
      dummy.updateMatrix()
      mesh.setMatrixAt(slot.index, dummy.matrix)
    })
    mesh.instanceMatrix.needsUpdate = true
    mesh.computeBoundingSphere()
  }, [slots, raised])

  useLayoutEffect(() => {
    const dummy = new Object3D()
    slots.forEach(slot => {
      dummy.position.set(slot.x, 0, slot.z)
      dummy.rotation.set(0, Math.atan2(slot.x, slot.z), 0)
      dummy.updateMatrix()
      ;[poles, bases, finials, targets].forEach(mesh => mesh.current?.setMatrixAt(slot.index, dummy.matrix))
      dummy.translateZ(PLAQUE_FORWARD)
      dummy.position.y = PLAQUE_HEIGHT
      dummy.updateMatrix()
      plaques.current?.setMatrixAt(slot.index, dummy.matrix)
    })
    ;[poles, bases, finials, plaques, targets].forEach(mesh => {
      if (!mesh.current) return
      mesh.current.instanceMatrix.needsUpdate = true
      mesh.current.computeBoundingSphere()
    })
  }, [slots])

  useLayoutEffect(() => {
    const mesh = plaques.current
    if (!mesh) return
    slots.forEach(slot => {
      const color = slot.index === selected || slot.index === hovered
        ? SELECTED_PLAQUE
        : raised[slot.index] ? REGION_COLORS.get(slot.country.region) ?? DARK_PLAQUE : DARK_PLAQUE
      mesh.setColorAt(slot.index, color)
    })
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true
  }, [slots, raised, selected, hovered])

  useFrame(() => {
    const { slot, fade } = plazaFocus()
    const opacity = fadedOpacity(fade)
    ;[poleMaterial, baseMaterial, finialMaterial, plaqueMaterial].forEach(material => {
      if (!material.current) return
      material.current.opacity = opacity
      material.current.depthWrite = fade < DEPTH_FADE_LIMIT
    })
    const group = spotPole.current
    const focused = slots[slot]
    if (!group) return
    group.visible = focused !== undefined && fade > 0
    if (focused) group.position.set(focused.x, 0, focused.z)
  })

  const hover = (event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    onHover(event.nativeEvent.buttons === 0 && !isFlying() ? event.instanceId ?? null : null)
  }
  const select = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    const index = event.instanceId
    if (index !== undefined && raised[index] && event.delta <= CLICK_TOLERANCE_PX) onSelect(index)
  }

  return (
    <>
      <instancedMesh ref={poles} args={[poleGeometry, undefined, slots.length]}>
        <meshStandardMaterial ref={poleMaterial} color={POLE_COLOR} metalness={0.7} roughness={0.32} transparent />
      </instancedMesh>
      <instancedMesh ref={bases} args={[baseGeometry, undefined, slots.length]}>
        <meshStandardMaterial ref={baseMaterial} color={BASE_COLOR} roughness={0.85} transparent />
      </instancedMesh>
      <instancedMesh ref={finials} args={[finialGeometry, undefined, slots.length]}>
        <meshStandardMaterial ref={finialMaterial} color={FINIAL_COLOR} metalness={0.9} roughness={0.2}
          emissive={FINIAL_COLOR} emissiveIntensity={0.15} transparent />
      </instancedMesh>
      <instancedMesh ref={plaques} args={[plaqueGeometry, undefined, slots.length]}>
        <meshBasicMaterial ref={plaqueMaterial} toneMapped={false} transparent />
      </instancedMesh>
      <group ref={spotPole} visible={false}>
        <mesh geometry={poleGeometry}>
          <meshStandardMaterial color={POLE_COLOR} metalness={0.7} roughness={0.32} />
        </mesh>
        <mesh geometry={baseGeometry}>
          <meshStandardMaterial color={BASE_COLOR} roughness={0.85} />
        </mesh>
        <mesh geometry={finialGeometry}>
          <meshStandardMaterial color={FINIAL_COLOR} metalness={0.9} roughness={0.2} emissive={FINIAL_COLOR} emissiveIntensity={0.15} />
        </mesh>
      </group>
      <instancedMesh
        ref={targets}
        args={[targetGeometry, undefined, slots.length]}
        onPointerMove={hover}
        onPointerOut={() => onHover(null)}
        onClick={select}
      >
        <meshBasicMaterial colorWrite={false} depthWrite={false} />
      </instancedMesh>
      <instancedMesh
        ref={cloths}
        args={[clothGeometry, undefined, slots.length]}
        onPointerMove={hover}
        onPointerOut={() => onHover(null)}
        onClick={select}
      >
        <meshBasicMaterial colorWrite={false} depthWrite={false} />
      </instancedMesh>
    </>
  )
}
