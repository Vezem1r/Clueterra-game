import type { MultiPolygon } from 'geojson'
import type { SilhouetteBounds } from '@/api/silhouettes'
import { flagUrl } from './silhouetteGameConstants'

export type DrapeCorners = [[number, number], [number, number], [number, number], [number, number]]

export interface FlagDrape {
  url: string
  coordinates: DrapeCorners
}

const MIN_TEXTURE_PX = 64
const MAX_TEXTURE_PX = 512
const TEXTURE_PX_PER_DEGREE = 32
const PNG_TYPE = 'image/png'
const MAX_LATITUDE = 85
const DEGREES_TO_RADIANS = Math.PI / 180
const FULL_TURN_DEGREES = 360
const HALF_TURN_DEGREES = 180
const SHADE_TOP = 'rgba(255,255,255,0.18)'
const SHADE_BOTTOM = 'rgba(0,0,0,0.32)'
const SHADE_MIDDLE = 'rgba(0,0,0,0)'
const SHADE_MIDDLE_STOP = 0.45
const EDGE_LIGHT = 'rgba(255,255,255,0.55)'
const EDGE_WIDTH_PX = 2
const flagImages = new Map<string, Promise<HTMLImageElement>>()

function mercatorY(latitude: number) {
  const clamped = Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, latitude)) * DEGREES_TO_RADIANS
  return Math.log(Math.tan(Math.PI / 4 + clamped / 2))
}

function textureSize(spanDegrees: number) {
  const wanted = Math.max(MIN_TEXTURE_PX, spanDegrees * TEXTURE_PX_PER_DEGREE)
  return Math.min(MAX_TEXTURE_PX, 2 ** Math.ceil(Math.log2(wanted)))
}

function canvasUrl(canvas: HTMLCanvasElement) {
  return new Promise<string>((resolve, reject) => canvas.toBlob(blob => {
    if (blob) resolve(URL.createObjectURL(blob))
    else reject(new Error('Canvas could not be encoded'))
  }, PNG_TYPE))
}

function flagImage(iso: string) {
  const cached = flagImages.get(iso)
  if (cached) return cached
  const image = new Image()
  image.decoding = 'async'
  image.src = flagUrl(iso)
  const request = image.decode().then(() => image).catch(error => {
    flagImages.delete(iso)
    throw error
  })
  flagImages.set(iso, request)
  return request
}

export async function drapeFlag(iso: string, geometry: MultiPolygon, [west, south, east, north]: SilhouetteBounds): Promise<FlagDrape> {
  const image = await flagImage(iso)
  const top = mercatorY(north)
  const bottom = mercatorY(south)
  const spanX = (east - west) * DEGREES_TO_RADIANS
  const spanY = Math.max(Number.EPSILON, top - bottom)
  const aspect = spanX / spanY
  const width = textureSize(Math.max(east - west, north - south))
  const height = width
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) throw new Error('Canvas is unavailable')

  const outline = new Path2D()
  geometry.coordinates.forEach(polygon => polygon.forEach(ring => ring.forEach(([lng, lat], index) => {
    const unwrapped = lng < west - HALF_TURN_DEGREES ? lng + FULL_TURN_DEGREES : lng
    const x = ((unwrapped - west) / (east - west)) * width
    const y = ((top - mercatorY(lat)) / spanY) * height
    if (index === 0) outline.moveTo(x, y)
    else outline.lineTo(x, y)
  })))

  context.save()
  context.clip(outline, 'evenodd')
  const scale = Math.max(aspect / image.naturalWidth, 1 / image.naturalHeight)
  const drawWidth = (image.naturalWidth * scale * width) / aspect
  const drawHeight = image.naturalHeight * scale * height
  context.drawImage(image, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight)
  const shade = context.createLinearGradient(0, 0, width, height)
  shade.addColorStop(0, SHADE_TOP)
  shade.addColorStop(SHADE_MIDDLE_STOP, SHADE_MIDDLE)
  shade.addColorStop(1, SHADE_BOTTOM)
  context.fillStyle = shade
  context.fillRect(0, 0, width, height)
  context.strokeStyle = EDGE_LIGHT
  context.lineWidth = EDGE_WIDTH_PX
  context.stroke(outline)
  context.restore()

  return {
    url: await canvasUrl(canvas),
    coordinates: [[west, north], [east, north], [east, south], [west, south]],
  }
}
