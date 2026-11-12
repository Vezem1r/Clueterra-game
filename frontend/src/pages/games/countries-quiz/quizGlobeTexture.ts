import { CanvasTexture, SRGBColorSpace } from 'three'
import type { SilhouetteWorld } from '../silhouettes/silhouetteWorld'
import { QUIZ_COLORS, QUIZ_LEVEL_PAINT } from './quizGameConstants'

export interface QuizGlobePaint {
  levels: ReadonlyMap<string, number>
  current: string | null
  next: string | null
  focus: string | null
}

export interface QuizGlobeTextures {
  color: CanvasTexture
  glow: CanvasTexture
}

const WIDTH = 4096
const HEIGHT = 2048
const GLOW_WIDTH = 2048
const GLOW_HEIGHT = 1024
const FULL_LONGITUDE = 360
const FULL_LATITUDE = 180
const HALF_LONGITUDE = 180
const HALF_LATITUDE = 90
const GRID_STEP = 30
const GRID_LATITUDE_LIMIT = 60
const GRID_LINE = 1.5
const BORDER_LINE = 1.6
const HIGHLIGHT_LINE = 2.6
const LEVEL_LINE = 2.4
const GLOW_BLUR_PX = 6
const CURRENT_GLOW = 0.25
const ANISOTROPY = 8
const BLACK = '#000000'
const HEX_ALPHA_MAX = 255
const HEX_RADIX = 16
const HEX_PAIR = 2
const LAYER_BASE = 0
const LAYER_LEVEL = 1
const LAYER_HIGHLIGHT = 2

interface Tone {
  fill: string
  edge: string
  line: number
  layer: number
}

function mapX(longitude: number, width: number) {
  return (longitude + HALF_LONGITUDE) / FULL_LONGITUDE * width
}

function mapY(latitude: number, height: number) {
  return (HALF_LATITUDE - latitude) / FULL_LATITUDE * height
}

function drawRing(context: CanvasRenderingContext2D, ring: number[][], width: number, height: number) {
  let previousLongitude: number | null = null
  ring.forEach(([longitude, latitude], index) => {
    if (longitude === undefined || latitude === undefined) return
    const x = mapX(longitude, width)
    const y = mapY(latitude, height)
    if (index === 0 || (previousLongitude !== null && Math.abs(longitude - previousLongitude) > HALF_LONGITUDE)) {
      context.moveTo(x, y)
    } else {
      context.lineTo(x, y)
    }
    previousLongitude = longitude
  })
  context.closePath()
}

function drawGrid(context: CanvasRenderingContext2D) {
  context.strokeStyle = QUIZ_COLORS.grid
  context.lineWidth = GRID_LINE
  for (let longitude = -HALF_LONGITUDE + GRID_STEP; longitude <= HALF_LONGITUDE; longitude += GRID_STEP) {
    context.beginPath()
    context.moveTo(mapX(longitude, WIDTH), 0)
    context.lineTo(mapX(longitude, WIDTH), HEIGHT)
    context.stroke()
  }
  for (let latitude = -GRID_LATITUDE_LIMIT; latitude <= GRID_LATITUDE_LIMIT; latitude += GRID_STEP) {
    context.beginPath()
    context.moveTo(0, mapY(latitude, HEIGHT))
    context.lineTo(WIDTH, mapY(latitude, HEIGHT))
    context.stroke()
  }
}

function tone(iso: string | null, pool: boolean, paint: QuizGlobePaint): Tone {
  const level = iso ? paint.levels.get(iso) ?? 0 : 0
  const levelPaint = QUIZ_LEVEL_PAINT[level]
  if (iso && iso === paint.current) {
    return { fill: levelPaint?.fill ?? QUIZ_COLORS.current, edge: QUIZ_COLORS.currentEdge, line: HIGHLIGHT_LINE, layer: LAYER_HIGHLIGHT }
  }
  if (iso && (iso === paint.next || iso === paint.focus)) {
    return { fill: levelPaint?.fill ?? QUIZ_COLORS.next, edge: QUIZ_COLORS.nextEdge, line: HIGHLIGHT_LINE, layer: LAYER_HIGHLIGHT }
  }
  if (levelPaint) return { fill: levelPaint.fill, edge: levelPaint.edge, line: LEVEL_LINE, layer: LAYER_LEVEL }
  return { fill: pool ? QUIZ_COLORS.land : QUIZ_COLORS.territory, edge: QUIZ_COLORS.landEdge, line: BORDER_LINE, layer: LAYER_BASE }
}

function alphaHex(share: number) {
  return Math.round(share * HEX_ALPHA_MAX).toString(HEX_RADIX).padStart(HEX_PAIR, '0')
}

function canvasTexture(width: number, height: number) {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const texture = new CanvasTexture(canvas)
  texture.colorSpace = SRGBColorSpace
  texture.anisotropy = ANISOTROPY
  return texture
}

export function createQuizGlobeTextures(): QuizGlobeTextures {
  return { color: canvasTexture(WIDTH, HEIGHT), glow: canvasTexture(GLOW_WIDTH, GLOW_HEIGHT) }
}

export function paintQuizGlobe(textures: QuizGlobeTextures, world: SilhouetteWorld, paint: QuizGlobePaint) {
  const context = (textures.color.image as HTMLCanvasElement).getContext('2d')
  const glow = (textures.glow.image as HTMLCanvasElement).getContext('2d')
  if (!context || !glow) return
  const ocean = context.createLinearGradient(0, 0, 0, HEIGHT)
  ocean.addColorStop(0, QUIZ_COLORS.oceanDeep)
  ocean.addColorStop(1 / 2, QUIZ_COLORS.ocean)
  ocean.addColorStop(1, QUIZ_COLORS.oceanDeep)
  context.fillStyle = ocean
  context.fillRect(0, 0, WIDTH, HEIGHT)
  drawGrid(context)
  context.lineJoin = 'round'
  glow.fillStyle = BLACK
  glow.fillRect(0, 0, GLOW_WIDTH, GLOW_HEIGHT)
  glow.filter = `blur(${GLOW_BLUR_PX}px)`

  const painted = world.collection.features.map(feature => ({ feature, ...tone(feature.properties.iso, feature.properties.pool, paint) }))
  painted.sort((left, right) => left.layer - right.layer)
  painted.forEach(({ feature, fill, edge, line }) => {
    context.beginPath()
    feature.geometry.coordinates.forEach(polygon => polygon.forEach(ring => drawRing(context, ring, WIDTH, HEIGHT)))
    context.fillStyle = fill
    context.fill('evenodd')
    context.strokeStyle = edge
    context.lineWidth = line
    context.stroke()

    const iso = feature.properties.iso
    const level = iso ? paint.levels.get(iso) ?? 0 : 0
    const strength = Math.max(QUIZ_LEVEL_PAINT[level]?.glow ?? 0, iso && iso === paint.current ? CURRENT_GLOW : 0)
    if (strength <= 0) return
    glow.beginPath()
    feature.geometry.coordinates.forEach(polygon => polygon.forEach(ring => drawRing(glow, ring, GLOW_WIDTH, GLOW_HEIGHT)))
    glow.fillStyle = `${QUIZ_LEVEL_PAINT[level]?.fill ?? QUIZ_COLORS.current}${alphaHex(strength)}`
    glow.fill('evenodd')
  })
  glow.filter = 'none'
  textures.color.needsUpdate = true
  textures.glow.needsUpdate = true
}
