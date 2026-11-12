import { AdditiveBlending, Color, DoubleSide, ShaderMaterial, type Texture } from 'three'
import { FLAG_WIDTH } from './flagPlazaConstants'

const FULL_WIND = 1
const CALM_WIND = 0.18
const BEAM_COLOR = '#ffffff'
const BEAM_OPACITY = 0.12
const FADED_OPACITY = 0.16
const NO_FOCUS = -1
const flagTime = { value: 0 }
const flagFocus = { value: NO_FOCUS }
const flagFade = { value: 0 }

export function advanceFlagTime(seconds: number) {
  flagTime.value = seconds
}

export function focusPlaza(slot: number, fade: number) {
  flagFocus.value = slot
  flagFade.value = fade
}

export function plazaFocus() {
  return { slot: flagFocus.value, fade: flagFade.value }
}

export function fadedOpacity(fade: number) {
  return 1 - fade * (1 - FADED_OPACITY)
}

const FLAG_VERTEX = /* glsl */ `
  attribute vec2 aAtlasOffset;
  attribute float aPhase;
  attribute float aSlot;
  uniform float uTime;
  uniform float uWind;
  uniform vec2 uCellSize;
  uniform float uFocus;
  uniform float uFade;
  varying vec2 vUv;
  varying float vShade;
  varying float vAlpha;

  void main() {
    bool focused = abs(aSlot - uFocus) < 0.5;
    #ifdef SPOTLIGHT
      vAlpha = focused ? 1.0 : 0.0;
    #else
      vAlpha = focused ? 0.0 : 1.0 - uFade * ${(1 - FADED_OPACITY).toFixed(2)};
    #endif
    vec3 point = position;
    float reach = clamp(point.x / ${FLAG_WIDTH.toFixed(3)}, 0.0, 1.0);
    float primary = point.x * 2.3 - uTime * 3.1 + aPhase;
    float ripple = point.x * 4.6 - uTime * 5.4 + aPhase * 1.7 + point.y * 1.2;
    point.z += (sin(primary) * 0.2 + sin(ripple) * 0.05) * reach * uWind;
    point.y -= (1.0 - cos(primary)) * 0.035 * reach * uWind;
    vShade = 0.8 + 0.2 * cos(primary) * uWind;
    vUv = aAtlasOffset + uv * uCellSize;
    vec4 world = vec4(point, 1.0);
    #ifdef USE_INSTANCING
      world = instanceMatrix * world;
    #endif
    gl_Position = vAlpha > 0.0 ? projectionMatrix * modelViewMatrix * world : vec4(2.0, 2.0, 2.0, 1.0);
  }
`

const FLAG_FRAGMENT = /* glsl */ `
  uniform sampler2D uAtlas;
  varying vec2 vUv;
  varying float vShade;
  varying float vAlpha;

  void main() {
    vec4 color = texture2D(uAtlas, vUv);
    if (color.a < 0.5 || vAlpha <= 0.0) discard;
    gl_FragColor = vec4(color.rgb * vShade, vAlpha);
    #include <colorspace_fragment>
  }
`

const BEAM_VERTEX = /* glsl */ `
  varying float vHeight;
  varying vec3 vTint;

  void main() {
    vHeight = uv.y;
    #ifdef USE_INSTANCING_COLOR
      vTint = instanceColor;
    #else
      vTint = vec3(1.0);
    #endif
    vec4 world = vec4(position, 1.0);
    #ifdef USE_INSTANCING
      world = instanceMatrix * world;
    #endif
    gl_Position = projectionMatrix * modelViewMatrix * world;
  }
`

const BEAM_FRAGMENT = /* glsl */ `
  uniform vec3 uColor;
  uniform float uOpacity;
  varying float vHeight;
  varying vec3 vTint;

  void main() {
    gl_FragColor = vec4(uColor * vTint, (1.0 - vHeight) * uOpacity);
  }
`

export function createFlagMaterial(cellSize: [number, number], texture: Texture, calm: boolean, spotlight: boolean) {
  return new ShaderMaterial({
    vertexShader: FLAG_VERTEX,
    fragmentShader: FLAG_FRAGMENT,
    side: DoubleSide,
    transparent: true,
    depthTest: !spotlight,
    depthWrite: !spotlight,
    defines: spotlight ? { SPOTLIGHT: '' } : {},
    uniforms: {
      uTime: flagTime,
      uWind: { value: calm ? CALM_WIND : FULL_WIND },
      uCellSize: { value: cellSize },
      uAtlas: { value: texture },
      uFocus: flagFocus,
      uFade: flagFade,
    },
  })
}

export function createBeamMaterial() {
  return new ShaderMaterial({
    vertexShader: BEAM_VERTEX,
    fragmentShader: BEAM_FRAGMENT,
    transparent: true,
    depthWrite: false,
    blending: AdditiveBlending,
    side: DoubleSide,
    uniforms: {
      uColor: { value: new Color(BEAM_COLOR) },
      uOpacity: { value: BEAM_OPACITY },
    },
  })
}
