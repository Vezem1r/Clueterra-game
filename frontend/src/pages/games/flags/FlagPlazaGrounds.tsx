import { useLayoutEffect, useMemo, useRef } from 'react'
import { AdditiveBlending, BoxGeometry, CircleGeometry, CylinderGeometry, type InstancedMesh, Object3D, SphereGeometry } from 'three'
import { FIRST_RING_RADIUS, FLAG_PLAZA_REGIONS, RING_GAP, SECTOR_GAP_RADIANS, SECTOR_PADDING } from './flagPlazaConstants'
import type { FlagPlazaLayout } from './flagPlazaLayout'
import { plazaPoint } from './flagPlazaLayout'

const GROUND_TILT = -Math.PI / 2
const RING_SEGMENTS = 128
const SECTOR_SEGMENTS = 48
const PLAZA_COLOR = '#2a3142'
const COURT_COLOR = '#353d50'
const COURT_EDGE_COLOR = '#fcd34d'
const COURT_EDGE_WIDTH = 0.12
const COURT_EDGE_OPACITY = 0.35
const PLAZA_HEIGHT = 0.12
const CURB_WIDTH = 0.7
const CURB_HEIGHT = 0.34
const CURB_COLOR = '#4a5368'
const PATH_COLOR = '#ffffff'
const PATH_OPACITY = 0.06
const PATH_WIDTH = 0.08
const SECTOR_OPACITY = 0.1
const SPOKE_WIDTH = 1.1
const SPOKE_COLOR = '#3a4256'
const LAMP_SPACING = 6.5
const LAMP_INSET = 1.3
const LAMP_HEIGHT = 2.6
const LAMP_POST_RADIUS = 0.07
const LAMP_GLOBE_RADIUS = 0.2
const LAMP_POST_COLOR = '#3b4458'
const LAMP_GLOW_COLOR = '#ffd59a'
const LAMP_POOL_RADIUS = 2.4
const LAMP_POOL_OPACITY = 0.13
const HEDGE_OFFSET = 1.9
const HEDGE_LENGTH = 2.3
const HEDGE_SPACING = 2.6
const HEDGE_HEIGHT = 0.85
const HEDGE_DEPTH = 0.9
const HEDGE_GATE = 3.2
const HEDGE_COLOR = '#3f7d45'
const FULL_TURN = Math.PI * 2
const REGION_COLORS = new Map(FLAG_PLAZA_REGIONS.map(region => [region.key, region.color]))

interface FlagPlazaGroundsProps {
  layout: FlagPlazaLayout
  shares: number[]
  details: boolean
  lite: boolean
}

export function FlagPlazaGrounds({ layout, shares, details, lite }: FlagPlazaGroundsProps) {
  const hedged = details && !lite
  const posts = useRef<InstancedMesh>(null)
  const globes = useRef<InstancedMesh>(null)
  const pools = useRef<InstancedMesh>(null)
  const hedges = useRef<InstancedMesh>(null)
  const rings = useMemo(() => {
    const outer = Math.max(...layout.sectors.map(sector => sector.outerRadius))
    const count = Math.round((outer - FIRST_RING_RADIUS) / RING_GAP) + 2
    return Array.from({ length: count }, (_, ring) => FIRST_RING_RADIUS - RING_GAP / 2 + ring * RING_GAP)
  }, [layout])
  const lampCount = Math.round(Math.PI * 2 * (layout.radius - LAMP_INSET) / LAMP_SPACING)
  const postGeometry = useMemo(() => new CylinderGeometry(LAMP_POST_RADIUS, LAMP_POST_RADIUS * 1.5, LAMP_HEIGHT, 6)
    .translate(0, LAMP_HEIGHT / 2, 0), [])
  const hedgeRadius = layout.radius + CURB_WIDTH + HEDGE_OFFSET
  const hedgeAngles = useMemo(() => {
    const count = Math.round(FULL_TURN * hedgeRadius / HEDGE_SPACING)
    const gates = layout.sectors.map(sector => sector.start - SECTOR_GAP_RADIANS / 2)
    const halfGate = HEDGE_GATE / hedgeRadius
    return Array.from({ length: count }, (_, hedge) => hedge / count * FULL_TURN).filter(angle =>
      gates.every(gate => Math.abs(Math.atan2(Math.sin(angle - gate), Math.cos(angle - gate))) > halfGate))
  }, [layout, hedgeRadius])
  const poolGeometry = useMemo(() => new CircleGeometry(LAMP_POOL_RADIUS, 24).rotateX(GROUND_TILT), [])
  const hedgeGeometry = useMemo(() => new BoxGeometry(HEDGE_DEPTH, HEDGE_HEIGHT, HEDGE_LENGTH)
    .translate(0, HEDGE_HEIGHT / 2, 0), [])
  const globeGeometry = useMemo(() => new SphereGeometry(LAMP_GLOBE_RADIUS, 12, 8).translate(0, LAMP_HEIGHT + LAMP_GLOBE_RADIUS, 0), [])

  useLayoutEffect(() => {
    const dummy = new Object3D()
    for (let lamp = 0; lamp < lampCount; lamp++) {
      const [x, z] = plazaPoint(layout.radius - LAMP_INSET, lamp / lampCount * Math.PI * 2)
      dummy.position.set(x, 0, z)
      dummy.updateMatrix()
      posts.current?.setMatrixAt(lamp, dummy.matrix)
      globes.current?.setMatrixAt(lamp, dummy.matrix)
      dummy.position.y = 0.02
      dummy.updateMatrix()
      pools.current?.setMatrixAt(lamp, dummy.matrix)
    }
    hedgeAngles.forEach((angle, hedge) => {
      const [x, z] = plazaPoint(hedgeRadius, angle)
      dummy.position.set(x, -PLAZA_HEIGHT, z)
      dummy.rotation.set(0, angle, 0)
      dummy.updateMatrix()
      hedges.current?.setMatrixAt(hedge, dummy.matrix)
    })
    ;[posts, globes, pools, hedges].forEach(mesh => {
      if (!mesh.current) return
      mesh.current.instanceMatrix.needsUpdate = true
      mesh.current.computeBoundingSphere()
    })
  }, [layout, lampCount, hedgeAngles, hedgeRadius, hedged])

  return (
    <>
      <mesh position-y={-PLAZA_HEIGHT / 2}>
        <cylinderGeometry args={[layout.radius, layout.radius, PLAZA_HEIGHT, RING_SEGMENTS]} />
        <meshStandardMaterial color={PLAZA_COLOR} roughness={0.92} />
      </mesh>
      <mesh position-y={CURB_HEIGHT / 2 - PLAZA_HEIGHT}>
        <cylinderGeometry args={[layout.radius + CURB_WIDTH, layout.radius + CURB_WIDTH, CURB_HEIGHT, RING_SEGMENTS, 1, true]} />
        <meshStandardMaterial color={CURB_COLOR} roughness={0.8} />
      </mesh>
      <mesh rotation-x={GROUND_TILT} position-y={CURB_HEIGHT - PLAZA_HEIGHT}>
        <ringGeometry args={[layout.radius, layout.radius + CURB_WIDTH, RING_SEGMENTS]} />
        <meshStandardMaterial color={CURB_COLOR} roughness={0.8} />
      </mesh>
      <mesh rotation-x={GROUND_TILT} position-y={0.005}>
        <circleGeometry args={[FIRST_RING_RADIUS - SECTOR_PADDING - RING_GAP / 2, RING_SEGMENTS]} />
        <meshStandardMaterial color={COURT_COLOR} roughness={0.85} />
      </mesh>
      <mesh rotation-x={GROUND_TILT} position-y={0.02}>
        <ringGeometry args={[
          FIRST_RING_RADIUS - SECTOR_PADDING - RING_GAP / 2 - COURT_EDGE_WIDTH,
          FIRST_RING_RADIUS - SECTOR_PADDING - RING_GAP / 2,
          RING_SEGMENTS,
        ]} />
        <meshBasicMaterial color={COURT_EDGE_COLOR} transparent opacity={COURT_EDGE_OPACITY} depthWrite={false} toneMapped={false} />
      </mesh>
      {rings.map(radius => (
        <mesh key={radius} rotation-x={GROUND_TILT} position-y={0.01}>
          <ringGeometry args={[radius - PATH_WIDTH, radius + PATH_WIDTH, RING_SEGMENTS]} />
          <meshBasicMaterial color={PATH_COLOR} transparent opacity={PATH_OPACITY} depthWrite={false} />
        </mesh>
      ))}
      {layout.sectors.map((sector, index) => (
        <mesh key={sector.region} rotation-x={GROUND_TILT} position-y={0.015}>
          <ringGeometry args={[
            sector.innerRadius - SECTOR_PADDING,
            sector.outerRadius + SECTOR_PADDING,
            SECTOR_SEGMENTS,
            1,
            sector.start,
            sector.length,
          ]} />
          <meshBasicMaterial
            color={REGION_COLORS.get(sector.region)}
            transparent
            opacity={SECTOR_OPACITY + (shares[index] ?? 0) * SECTOR_OPACITY}
            depthWrite={false}
          />
        </mesh>
      ))}
      {layout.sectors.map(sector => {
        const angle = sector.start - SECTOR_GAP_RADIANS / 2
        const [x, z] = plazaPoint(layout.radius / 2, angle)
        return (
          <mesh key={sector.region} position={[x, 0.012, z]} rotation={[GROUND_TILT, 0, angle]}>
            <planeGeometry args={[layout.radius, SPOKE_WIDTH]} />
            <meshStandardMaterial color={SPOKE_COLOR} roughness={0.7} />
          </mesh>
        )
      })}
      <instancedMesh ref={posts} args={[postGeometry, undefined, lampCount]}>
        <meshStandardMaterial color={LAMP_POST_COLOR} metalness={0.6} roughness={0.4} />
      </instancedMesh>
      <instancedMesh ref={globes} args={[globeGeometry, undefined, lampCount]}>
        <meshBasicMaterial color={LAMP_GLOW_COLOR} toneMapped={false} />
      </instancedMesh>
      <instancedMesh ref={pools} args={[poolGeometry, undefined, lampCount]}>
        <meshBasicMaterial color={LAMP_GLOW_COLOR} transparent opacity={LAMP_POOL_OPACITY}
          blending={AdditiveBlending} depthWrite={false} />
      </instancedMesh>
      {hedged && <instancedMesh ref={hedges} args={[hedgeGeometry, undefined, hedgeAngles.length]}>
        <meshLambertMaterial color={HEDGE_COLOR} />
      </instancedMesh>}
    </>
  )
}
