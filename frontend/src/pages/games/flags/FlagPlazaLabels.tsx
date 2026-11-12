import { useEffect, useMemo, useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import { CanvasTexture, type Group, type Sprite, SRGBColorSpace } from 'three'
import { FLAG_PLAZA_REGIONS, type FlagPlazaRegionKey, LABEL_DISTANCE, LABEL_HEIGHT } from './flagPlazaConstants'
import type { FlagPlazaSector } from './flagPlazaLayout'
import { plazaPoint } from './flagPlazaLayout'
import { fadedOpacity, plazaFocus } from './flagPlazaMaterials'

const CANVAS_WIDTH = 640
const CANVAS_HEIGHT = 220
const LABEL_WORLD_WIDTH = 5
const LABEL_WORLD_HEIGHT = LABEL_WORLD_WIDTH * CANVAS_HEIGHT / CANVAS_WIDTH
const PILL_INSET = 14
const PILL_RADIUS = 60
const PILL_BORDER = 5
const NAME_FONT_SIZE = 74
const COUNT_FONT_SIZE = 44
const NAME_BASELINE = 112
const COUNT_BASELINE = 172
const NAME_TRACKING = '4px'
const FALLBACK_FONT = 'sans-serif'
const DISPLAY_FONT_VARIABLE = '--font-display'
const PILL_FILL = 'rgba(6, 9, 16, 0.78)'
const NAME_COLOR = '#f8fafc'
const COUNT_SEPARATOR = ' / '
const REGION_COLORS = new Map(FLAG_PLAZA_REGIONS.map(region => [region.key, region.color]))

interface FlagPlazaLabelsProps {
  sectors: FlagPlazaSector[]
  raised: boolean[]
  names: Record<FlagPlazaRegionKey, string>
}

function displayFont() {
  return getComputedStyle(document.documentElement).getPropertyValue(DISPLAY_FONT_VARIABLE).trim() || FALLBACK_FONT
}

function drawLabel(name: string, count: string, color: string, font: string) {
  const canvas = document.createElement('canvas')
  canvas.width = CANVAS_WIDTH
  canvas.height = CANVAS_HEIGHT
  const context = canvas.getContext('2d')
  if (context) {
    context.beginPath()
    context.roundRect(PILL_INSET, PILL_INSET, CANVAS_WIDTH - PILL_INSET * 2, CANVAS_HEIGHT - PILL_INSET * 2, PILL_RADIUS)
    context.fillStyle = PILL_FILL
    context.fill()
    context.lineWidth = PILL_BORDER
    context.strokeStyle = color
    context.stroke()
    context.textAlign = 'center'
    context.letterSpacing = NAME_TRACKING
    context.fillStyle = NAME_COLOR
    context.font = `700 ${NAME_FONT_SIZE}px ${font}`
    context.fillText(name.toUpperCase(), CANVAS_WIDTH / 2, NAME_BASELINE)
    context.letterSpacing = '0px'
    context.fillStyle = color
    context.font = `600 ${COUNT_FONT_SIZE}px ${font}`
    context.fillText(count, CANVAS_WIDTH / 2, COUNT_BASELINE)
  }
  const texture = new CanvasTexture(canvas)
  texture.colorSpace = SRGBColorSpace
  return texture
}

export function FlagPlazaLabels({ sectors, raised, names }: FlagPlazaLabelsProps) {
  const labels = useMemo(() => {
    const font = displayFont()
    return sectors.map(sector => {
      const solved = sector.slotIndexes.filter(index => raised[index]).length
      const [x, z] = plazaPoint(sector.outerRadius + LABEL_DISTANCE, sector.start + sector.length / 2)
      return {
        region: sector.region,
        position: [x, LABEL_HEIGHT, z] as [number, number, number],
        texture: drawLabel(names[sector.region], `${solved}${COUNT_SEPARATOR}${sector.slotIndexes.length}`,
          REGION_COLORS.get(sector.region) ?? NAME_COLOR, font),
      }
    })
  }, [sectors, raised, names])

  const group = useRef<Group>(null)

  useEffect(() => () => labels.forEach(label => label.texture.dispose()), [labels])

  useFrame(() => {
    const opacity = fadedOpacity(plazaFocus().fade)
    group.current?.children.forEach(child => {
      (child as Sprite).material.opacity = opacity
    })
  })

  return <group ref={group}>
    {labels.map(label => (
      <sprite key={label.region} position={label.position} scale={[LABEL_WORLD_WIDTH, LABEL_WORLD_HEIGHT, 1]}>
        <spriteMaterial map={label.texture} transparent depthWrite={false} toneMapped={false} />
      </sprite>
    ))}
  </group>
}
