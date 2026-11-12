import { useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import { AdditiveBlending, Color, type Group } from 'three'
import { FLAG_PLAZA_REGIONS } from './flagPlazaConstants'
import type { FlagPlazaSector } from './flagPlazaLayout'
import { plazaPoint } from './flagPlazaLayout'

const GROUND_TILT = -Math.PI / 2
const ROUND_SEGMENTS = 72
const STEPS = [
  { radius: 5.6, height: 0.28 },
  { radius: 4.6, height: 0.28 },
  { radius: 3.6, height: 0.28 },
]
const STEP_TOP = STEPS.reduce((height, step) => height + step.height, 0)
const STACKED_STEPS = STEPS.map((step, index) => ({
  ...step,
  y: STEPS.slice(0, index).reduce((height, below) => height + below.height, 0) + step.height / 2,
}))
const LANTERN_BASE = STEPS[0].height + STEPS[1].height
const POOL_INNER = 5.9
const POOL_OUTER = 7.4
const POOL_RIM = 0.18
const COLUMN_HEIGHT = 5.4
const COLUMN_BOTTOM = 0.62
const COLUMN_TOP = 0.4
const BAND_HEIGHTS = [0.35, COLUMN_HEIGHT - 0.25]
const BAND_TUBE = 0.08
const GLOBE_CENTER = STEP_TOP + COLUMN_HEIGHT + 1.9
const GLOBE_RADIUS = 1.05
const ARMILLARY_RADIUS = 1.65
const ARMILLARY_TUBE = 0.05
const ARMILLARY_TILTS = [0, Math.PI / 2.6, -Math.PI / 2.6]
const CRADLE_RADIUS = 0.75
const CRADLE_HEIGHT = 0.5
const SPIN_PER_SECOND = 0.12
const LANTERN_ORBIT = 4.15
const LANTERN_HEIGHT = 1.25
const LANTERN_POST = 0.09
const LANTERN_RADIUS = 0.24
const MIN_LANTERN_GLOW = 0.25
const MAX_LANTERN_GLOW = 3
const STONE_COLOR = '#3a4256'
const STONE_EDGE = '#4d566c'
const COLUMN_COLOR = '#e2e8f0'
const COLUMN_GLOW = '#64748b'
const GOLD_COLOR = '#d9a441'
const GLOBE_COLOR = '#1e3a8a'
const GLOBE_GLOW = '#60a5fa'
const WATER_COLOR = '#0b2440'
const RIM_COLOR = '#fcd34d'
const REGION_COLORS = new Map(FLAG_PLAZA_REGIONS.map(region => [region.key, region.color]))

interface FlagPlazaMonumentProps {
  sectors: FlagPlazaSector[]
  shares: number[]
  calm: boolean
}

export function FlagPlazaMonument({ sectors, shares, calm }: FlagPlazaMonumentProps) {
  const armillary = useRef<Group>(null)
  const colors = useMemo(() => sectors.map(sector => new Color(REGION_COLORS.get(sector.region))), [sectors])

  useFrame((_, delta) => {
    if (!armillary.current || calm) return
    armillary.current.rotation.y += delta * SPIN_PER_SECOND
  })

  return (
    <group>
      <mesh rotation-x={GROUND_TILT} position-y={0.02}>
        <ringGeometry args={[POOL_INNER, POOL_OUTER, ROUND_SEGMENTS]} />
        <meshStandardMaterial color={WATER_COLOR} metalness={0.9} roughness={0.12} />
      </mesh>
      <mesh rotation-x={GROUND_TILT} position-y={0.03}>
        <ringGeometry args={[POOL_OUTER - POOL_RIM, POOL_OUTER, ROUND_SEGMENTS]} />
        <meshBasicMaterial color={RIM_COLOR} transparent opacity={0.45} blending={AdditiveBlending} toneMapped={false} />
      </mesh>
      {STACKED_STEPS.map(step => (
        <mesh key={step.radius} position-y={step.y}>
          <cylinderGeometry args={[step.radius, step.radius + 0.12, step.height, ROUND_SEGMENTS]} />
          <meshStandardMaterial color={step.radius === STEPS[0].radius ? STONE_EDGE : STONE_COLOR} roughness={0.75} metalness={0.1} />
        </mesh>
      ))}
      <mesh position-y={STEP_TOP + COLUMN_HEIGHT / 2}>
        <cylinderGeometry args={[COLUMN_TOP, COLUMN_BOTTOM, COLUMN_HEIGHT, 24]} />
        <meshStandardMaterial color={COLUMN_COLOR} emissive={COLUMN_GLOW} emissiveIntensity={0.35} roughness={0.45} metalness={0.2} />
      </mesh>
      {BAND_HEIGHTS.map(height => (
        <mesh key={height} position-y={STEP_TOP + height} rotation-x={Math.PI / 2}>
          <torusGeometry args={[COLUMN_BOTTOM - (COLUMN_BOTTOM - COLUMN_TOP) * height / COLUMN_HEIGHT + 0.02, BAND_TUBE, 8, 32]} />
          <meshStandardMaterial color={GOLD_COLOR} metalness={0.9} roughness={0.25} />
        </mesh>
      ))}
      <mesh position-y={STEP_TOP + COLUMN_HEIGHT + CRADLE_HEIGHT / 2}>
        <cylinderGeometry args={[CRADLE_RADIUS, COLUMN_TOP, CRADLE_HEIGHT, 24]} />
        <meshStandardMaterial color={GOLD_COLOR} metalness={0.9} roughness={0.25} />
      </mesh>
      <group ref={armillary} position-y={GLOBE_CENTER}>
        <mesh>
          <sphereGeometry args={[GLOBE_RADIUS, 32, 24]} />
          <meshStandardMaterial color={GLOBE_COLOR} emissive={GLOBE_GLOW} emissiveIntensity={0.55} roughness={0.4} metalness={0.3} />
        </mesh>
        {ARMILLARY_TILTS.map(tilt => (
          <mesh key={tilt} rotation={[Math.PI / 2 + tilt, 0, tilt / 2]}>
            <torusGeometry args={[ARMILLARY_RADIUS, ARMILLARY_TUBE, 8, 64]} />
            <meshStandardMaterial color={GOLD_COLOR} metalness={0.95} roughness={0.2} emissive={GOLD_COLOR} emissiveIntensity={0.25} />
          </mesh>
        ))}
        <mesh>
          <torusGeometry args={[ARMILLARY_RADIUS * 1.08, ARMILLARY_TUBE * 1.4, 8, 64]} />
          <meshStandardMaterial color={GOLD_COLOR} metalness={0.95} roughness={0.2} emissive={GOLD_COLOR} emissiveIntensity={0.25} />
        </mesh>
      </group>
      {sectors.map((sector, index) => {
        const [x, z] = plazaPoint(LANTERN_ORBIT, sector.start + sector.length / 2)
        const glow = MIN_LANTERN_GLOW + (shares[index] ?? 0) * (MAX_LANTERN_GLOW - MIN_LANTERN_GLOW)
        return (
          <group key={sector.region} position={[x, LANTERN_BASE, z]}>
            <mesh position-y={LANTERN_HEIGHT / 2}>
              <cylinderGeometry args={[LANTERN_POST, LANTERN_POST * 1.3, LANTERN_HEIGHT, 8]} />
              <meshStandardMaterial color={STONE_EDGE} metalness={0.6} roughness={0.4} />
            </mesh>
            <mesh position-y={LANTERN_HEIGHT + LANTERN_RADIUS}>
              <sphereGeometry args={[LANTERN_RADIUS, 16, 12]} />
              <meshStandardMaterial color={colors[index]} emissive={colors[index]} emissiveIntensity={glow} toneMapped={false} />
            </mesh>
          </group>
        )
      })}
    </group>
  )
}
