import { SILHOUETTE_VIEWBOX } from './silhouetteGameConstants'

const NUMBER = /-?\d+(?:\.\d+)?/g
const COORDINATE_PAIR = 2
const PADDING_SHARE = 0.06
const MIN_SIDE = 40

export function shapeViewBox(shapePath: string) {
  const values = (shapePath.match(NUMBER) ?? []).map(Number)
  if (values.length < COORDINATE_PAIR) return SILHOUETTE_VIEWBOX
  const xs = values.filter((_, index) => index % COORDINATE_PAIR === 0)
  const ys = values.filter((_, index) => index % COORDINATE_PAIR === 1)
  const minX = Math.min(...xs)
  const minY = Math.min(...ys)
  const width = Math.max(MIN_SIDE, Math.max(...xs) - minX)
  const height = Math.max(MIN_SIDE, Math.max(...ys) - minY)
  const padding = Math.max(width, height) * PADDING_SHARE
  return `${minX - padding} ${minY - padding} ${width + padding * 2} ${height + padding * 2}`
}
