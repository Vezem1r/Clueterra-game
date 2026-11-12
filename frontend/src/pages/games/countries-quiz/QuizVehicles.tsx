import { useMemo, type RefObject } from 'react'
import { DoubleSide, Group, Shape, Vector2 } from 'three'
import { QUIZ_COLORS } from './quizGameConstants'

interface VehicleProps {
  groupRef: RefObject<Group | null>
}

const WHEEL_SIDES = [-1, 1]
const ROUND_SEGMENTS = 20
const BODY_EXTRUSION = { depth: 0.26, bevelEnabled: true, bevelThickness: 0.035, bevelSize: 0.03, bevelSegments: 4, curveSegments: 12 }
const WING_EXTRUSION = { depth: 0.018, bevelEnabled: true, bevelThickness: 0.006, bevelSize: 0.006, bevelSegments: 2 }
const BODY_PROFILE: [number, number][] = [
  [-0.27, 0.04], [0.27, 0.04], [0.29, 0.1], [0.27, 0.15], [0.12, 0.16], [0.06, 0.25], [-0.13, 0.25], [-0.2, 0.16], [-0.28, 0.15], [-0.29, 0.09],
]
const FUSELAGE_PROFILE: [number, number][] = [
  [0, -0.36], [0.035, -0.33], [0.055, -0.22], [0.06, 0.12], [0.05, 0.28], [0.022, 0.38], [0, 0.4],
]
const WING_OUTLINE: [number, number][] = [[0, 0.06], [0.4, -0.08], [0.42, -0.12], [0.06, -0.06], [0, -0.06]]
const TAIL_OUTLINE: [number, number][] = [[0, 0.02], [0.15, -0.05], [0.16, -0.08], [0.02, -0.06], [0, -0.06]]
const FIN_OUTLINE: [number, number][] = [[0, 0], [0.12, 0], [0.04, 0.14], [-0.01, 0.14]]

function outline(points: [number, number][]) {
  const [first, ...rest] = points
  const shape = new Shape()
  shape.moveTo(...first)
  rest.forEach(point => shape.lineTo(...point))
  shape.closePath()
  return shape
}

export function QuizCar({ groupRef }: VehicleProps) {
  const body = useMemo(() => outline(BODY_PROFILE), [])
  return <group ref={groupRef}>
    <group rotation={[0, -Math.PI / 2, 0]} position={[0, 0.02, 0]}>
      <mesh position={[0, 0, -BODY_EXTRUSION.depth / 2]}>
        <extrudeGeometry args={[body, BODY_EXTRUSION]} />
        <meshStandardMaterial color={QUIZ_COLORS.car} roughness={0.35} metalness={0.35} />
      </mesh>
      <mesh position={[-0.035, 0.205, 0]} scale={[0.17, 0.07, 0.3]}>
        <boxGeometry />
        <meshStandardMaterial color={QUIZ_COLORS.carGlass} roughness={0.08} metalness={0.6} />
      </mesh>
      <mesh position={[0, 0.07, 0]} scale={[0.6, 0.03, 0.34]}>
        <boxGeometry />
        <meshStandardMaterial color={QUIZ_COLORS.carTrim} roughness={0.7} />
      </mesh>
    </group>
    {WHEEL_SIDES.flatMap(side => WHEEL_SIDES.map(end => <group key={`${side}${end}`} position={[side * 0.165, 0.06, end * 0.17]}>
      <mesh rotation={[0, 0, Math.PI / 2]}>
        <cylinderGeometry args={[0.06, 0.06, 0.05, ROUND_SEGMENTS]} />
        <meshStandardMaterial color={QUIZ_COLORS.tyre} roughness={0.9} />
      </mesh>
      <mesh rotation={[0, 0, Math.PI / 2]} position={[side * 0.027, 0, 0]}>
        <cylinderGeometry args={[0.028, 0.028, 0.006, ROUND_SEGMENTS]} />
        <meshStandardMaterial color={QUIZ_COLORS.carRoof} metalness={0.8} roughness={0.25} />
      </mesh>
    </group>))}
    {WHEEL_SIDES.map(side => <mesh key={`head${side}`} position={[side * 0.09, 0.12, 0.3]}>
      <sphereGeometry args={[0.025, ROUND_SEGMENTS / 2, ROUND_SEGMENTS / 2]} />
      <meshBasicMaterial color={QUIZ_COLORS.headlight} toneMapped={false} />
    </mesh>)}
    {WHEEL_SIDES.map(side => <mesh key={`tail${side}`} position={[side * 0.1, 0.13, -0.3]}>
      <boxGeometry args={[0.05, 0.022, 0.01]} />
      <meshBasicMaterial color={QUIZ_COLORS.taillight} toneMapped={false} />
    </mesh>)}
  </group>
}

export function QuizPlane({ groupRef }: VehicleProps) {
  const fuselage = useMemo(() => FUSELAGE_PROFILE.map(([radius, height]) => new Vector2(radius, height)), [])
  const wing = useMemo(() => outline(WING_OUTLINE), [])
  const tail = useMemo(() => outline(TAIL_OUTLINE), [])
  const fin = useMemo(() => outline(FIN_OUTLINE), [])

  return <group ref={groupRef}>
    <group position={[0, 0.1, 0]}>
      <mesh rotation={[Math.PI / 2, 0, 0]}>
        <latheGeometry args={[fuselage, ROUND_SEGMENTS]} />
        <meshStandardMaterial color={QUIZ_COLORS.plane} roughness={0.3} metalness={0.25} />
      </mesh>
      <mesh rotation={[Math.PI / 2, 0, 0]} position={[0, 0.012, 0]} scale={[1.02, 1, 1.02]}>
        <cylinderGeometry args={[0.061, 0.061, 0.5, ROUND_SEGMENTS, 1, true, -Math.PI / 6, Math.PI / 3]} />
        <meshStandardMaterial color={QUIZ_COLORS.planeStripe} side={DoubleSide} roughness={0.4} />
      </mesh>
      <mesh position={[0, 0.03, 0.29]} scale={[0.04, 0.025, 0.06]}>
        <sphereGeometry args={[1, ROUND_SEGMENTS, ROUND_SEGMENTS / 2]} />
        <meshStandardMaterial color={QUIZ_COLORS.cockpit} roughness={0.1} metalness={0.6} />
      </mesh>
      {WHEEL_SIDES.map(side => <group key={`wing${side}`} scale={[side, 1, 1]}>
        <mesh rotation={[Math.PI / 2, 0, 0]} position={[0.04, -0.01, 0.02]}>
          <extrudeGeometry args={[wing, WING_EXTRUSION]} />
          <meshStandardMaterial color={QUIZ_COLORS.planeWing} side={DoubleSide} roughness={0.4} metalness={0.3} />
        </mesh>
        <mesh rotation={[Math.PI / 2, 0, 0]} position={[0.17, -0.04, 0.01]}>
          <cylinderGeometry args={[0.024, 0.02, 0.1, ROUND_SEGMENTS]} />
          <meshStandardMaterial color={QUIZ_COLORS.carTrim} roughness={0.5} metalness={0.5} />
        </mesh>
        <mesh rotation={[Math.PI / 2, 0, 0]} position={[0.03, 0.01, -0.28]}>
          <extrudeGeometry args={[tail, WING_EXTRUSION]} />
          <meshStandardMaterial color={QUIZ_COLORS.planeWing} side={DoubleSide} roughness={0.4} />
        </mesh>
      </group>)}
      <mesh rotation={[0, -Math.PI / 2, 0]} position={[-0.009, 0.04, -0.36]}>
        <extrudeGeometry args={[fin, WING_EXTRUSION]} />
        <meshStandardMaterial color={QUIZ_COLORS.planeStripe} side={DoubleSide} roughness={0.4} />
      </mesh>
    </group>
  </group>
}
