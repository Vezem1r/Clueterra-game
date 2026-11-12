import { Spherical, Vector3 } from 'three'

export interface CameraGoal {
  position: Vector3
  target: Vector3
}

export interface CameraFlight {
  from: Spherical
  to: Spherical
  fromHeight: number
  toHeight: number
  duration: number
  elapsed: number
}

const BASE_SECONDS = 1.1
const SECONDS_PER_RADIAN = 0.55
const SECONDS_PER_UNIT = 0.014
const MIN_SECONDS = 1.2
const MAX_SECONDS = 3
const FULL_TURN = Math.PI * 2

function shortestTurn(from: number, to: number) {
  const turn = (to - from) % FULL_TURN
  if (turn > Math.PI) return turn - FULL_TURN
  if (turn < -Math.PI) return turn + FULL_TURN
  return turn
}

function easeInOut(progress: number) {
  return progress < 0.5 ? 4 * progress ** 3 : 1 - (-2 * progress + 2) ** 3 / 2
}

export function planFlight(position: Vector3, target: Vector3, goal: CameraGoal, calm: boolean): CameraFlight {
  const from = new Spherical().setFromVector3(position.clone().sub(target))
  const to = new Spherical().setFromVector3(goal.position.clone().sub(goal.target))
  to.theta = from.theta + shortestTurn(from.theta, to.theta)
  const travel = Math.abs(to.theta - from.theta) * SECONDS_PER_RADIAN
    + Math.abs(to.radius - from.radius) * SECONDS_PER_UNIT
  return {
    from,
    to,
    fromHeight: target.y,
    toHeight: goal.target.y,
    duration: calm ? 0 : Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, BASE_SECONDS + travel)),
    elapsed: 0,
  }
}

export function advanceFlight(flight: CameraFlight, delta: number, position: Vector3, target: Vector3) {
  flight.elapsed = Math.min(flight.duration, flight.elapsed + delta)
  const share = flight.duration === 0 ? 1 : easeInOut(flight.elapsed / flight.duration)
  const step = new Spherical(
    flight.from.radius + (flight.to.radius - flight.from.radius) * share,
    flight.from.phi + (flight.to.phi - flight.from.phi) * share,
    flight.from.theta + (flight.to.theta - flight.from.theta) * share,
  )
  target.set(0, flight.fromHeight + (flight.toHeight - flight.fromHeight) * share, 0)
  position.setFromSpherical(step).add(target)
  return flight.elapsed >= flight.duration
}
