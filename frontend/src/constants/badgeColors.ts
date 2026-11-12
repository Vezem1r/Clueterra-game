export interface BadgeColorPreset {
  key: string
  family: string
  familyLabel: string
  variant: string
  variantLabel: string
  label: string
  hex: string
}

export interface BadgeAccentValues {
  solid: string
  glow: string
  softGlow: string
  border: string
  surface: string
}

type AccentKind = 'accent' | 'effect'

const FAMILY_LABELS = {
  violet: 'Violet',
  indigo: 'Indigo',
  sky: 'Sky',
  cyan: 'Cyan',
  emerald: 'Emerald',
  amber: 'Amber',
  rose: 'Rose',
} as const

const VARIANT_LABELS = {
  deep: 'Deep',
  core: 'Core',
  glow: 'Glow',
} as const

const PRESET_HEXES = {
  violet: { deep: '#6D28D9', core: '#8B5CF6', glow: '#C084FC' },
  indigo: { deep: '#4338CA', core: '#6366F1', glow: '#A5B4FC' },
  sky: { deep: '#0369A1', core: '#0EA5E9', glow: '#7DD3FC' },
  cyan: { deep: '#0F766E', core: '#14B8A6', glow: '#67E8F9' },
  emerald: { deep: '#047857', core: '#22C55E', glow: '#6EE7B7' },
  amber: { deep: '#B45309', core: '#F59E0B', glow: '#FCD34D' },
  rose: { deep: '#BE123C', core: '#F43F5E', glow: '#FDA4AF' },
} as const

export const DEFAULT_BADGE_ACCENT_KEY = 'sky-core'
export const DEFAULT_BADGE_EFFECT_KEY = 'amber-glow'

function hexToRgb(hex: string) {
  const sanitized = hex.replace('#', '')
  const value = parseInt(sanitized, 16)
  return {
    r: (value >> 16) & 255,
    g: (value >> 8) & 255,
    b: value & 255,
  }
}

function buildAccentValues(hex: string, kind: AccentKind): BadgeAccentValues {
  const { r, g, b } = hexToRgb(hex)

  if (kind === 'effect') {
    return {
      solid: `rgb(${r}, ${g}, ${b})`,
      glow: `rgba(${r}, ${g}, ${b}, 0.98)`,
      softGlow: `rgba(${r}, ${g}, ${b}, 0.3)`,
      border: `rgba(${r}, ${g}, ${b}, 0.42)`,
      surface: `rgba(${r}, ${g}, ${b}, 0.2)`,
    }
  }

  return {
    solid: `rgb(${r}, ${g}, ${b})`,
    glow: `rgba(${r}, ${g}, ${b}, 0.9)`,
    softGlow: `rgba(${r}, ${g}, ${b}, 0.22)`,
    border: `rgba(${r}, ${g}, ${b}, 0.34)`,
    surface: `rgba(${r}, ${g}, ${b}, 0.14)`,
  }
}

export const BADGE_COLOR_GROUPS = Object.entries(PRESET_HEXES).map(([family, variants]) => {
  const familyKey = family as keyof typeof PRESET_HEXES
  const presets = Object.entries(variants).map(([variant, hex]) => {
    const variantKey = variant as keyof typeof VARIANT_LABELS
    return {
      key: `${familyKey}-${variantKey}`,
      family: familyKey,
      familyLabel: FAMILY_LABELS[familyKey],
      variant: variantKey,
      variantLabel: VARIANT_LABELS[variantKey],
      label: `${FAMILY_LABELS[familyKey]} ${VARIANT_LABELS[variantKey]}`,
      hex,
    } satisfies BadgeColorPreset
  })

  return {
    key: familyKey,
    label: FAMILY_LABELS[familyKey],
    presets,
  }
})

export const BADGE_COLOR_PRESETS = BADGE_COLOR_GROUPS.flatMap(group => group.presets)

const BADGE_COLOR_PRESET_MAP = new Map(BADGE_COLOR_PRESETS.map(preset => [preset.key, preset]))

export function getBadgeColorPreset(key?: string | null): BadgeColorPreset | null {
  if (!key) {
    return null
  }
  return BADGE_COLOR_PRESET_MAP.get(key) ?? null
}

export function resolveBadgeColorAccent(key: string | null | undefined, kind: AccentKind = 'accent'): BadgeAccentValues | null {
  const preset = getBadgeColorPreset(key)
  if (!preset) {
    return null
  }
  return buildAccentValues(preset.hex, kind)
}
