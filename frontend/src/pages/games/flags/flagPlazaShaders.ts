import { AdditiveBlending, BackSide, Color, ShaderMaterial, UniformsLib, UniformsUtils } from 'three'
import { HORIZON_COLOR, ZENITH_COLOR } from './flagPlazaConstants'

const MEADOW_DARK = '#173a1e'
const MEADOW_LIGHT = '#2f5f2a'
const MEADOW_GLOW = '#c9d98a'
const FIREFLY_COLOR = '#f7ef8a'
const FIREFLY_SIZE = 0.32
const CALM_DRIFT = 0
const FULL_DRIFT = 1
const fireflyTime = { value: 0 }

export function advanceFireflyTime(seconds: number) {
  fireflyTime.value = seconds
}

const FIREFLY_VERTEX = /* glsl */ `
  attribute float aSeed;
  uniform float uTime;
  uniform float uSize;
  uniform float uDrift;
  varying float vGlow;

  void main() {
    float phase = aSeed * 6.2831;
    vec3 point = position + vec3(
      sin(uTime * 0.37 + phase) * 1.4,
      sin(uTime * 0.83 + phase * 1.7) * 0.45,
      cos(uTime * 0.29 + phase * 2.3) * 1.4) * uDrift;
    vec4 mvPosition = modelViewMatrix * vec4(point, 1.0);
    float pulse = 0.5 + 0.5 * sin(uTime * (1.3 + aSeed) + phase * 3.0);
    vGlow = mix(0.6, pulse * pulse, uDrift);
    gl_PointSize = uSize * 420.0 / -mvPosition.z;
    gl_Position = projectionMatrix * mvPosition;
  }
`

const FIREFLY_FRAGMENT = /* glsl */ `
  uniform vec3 uColor;
  varying float vGlow;

  void main() {
    float fade = 1.0 - smoothstep(0.05, 0.5, length(gl_PointCoord - 0.5));
    gl_FragColor = vec4(uColor * vGlow * fade, 1.0);
    #include <colorspace_fragment>
  }
`

const SKY_VERTEX = /* glsl */ `
  varying vec3 vDirection;

  void main() {
    vDirection = normalize(position);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

const SKY_FRAGMENT = /* glsl */ `
  uniform vec3 uHorizon;
  uniform vec3 uZenith;
  varying vec3 vDirection;

  void main() {
    float height = clamp(vDirection.y, 0.0, 1.0);
    gl_FragColor = vec4(mix(uHorizon, uZenith, pow(height, 0.45)), 1.0);
    #include <colorspace_fragment>
  }
`

const MEADOW_VERTEX = /* glsl */ `
  varying vec3 vWorld;
  #include <fog_pars_vertex>

  void main() {
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    vec4 mvPosition = viewMatrix * world;
    gl_Position = projectionMatrix * mvPosition;
    #include <fog_vertex>
  }
`

const MEADOW_FRAGMENT = /* glsl */ `
  uniform vec3 uDark;
  uniform vec3 uLight;
  uniform vec3 uGlow;
  uniform float uPlazaRadius;
  varying vec3 vWorld;
  #include <fog_pars_fragment>

  float hash(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453);
  }

  float noise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    vec2 blend = local * local * (3.0 - 2.0 * local);
    return mix(mix(hash(cell), hash(cell + vec2(1.0, 0.0)), blend.x),
      mix(hash(cell + vec2(0.0, 1.0)), hash(cell + vec2(1.0, 1.0)), blend.x), blend.y);
  }

  float fbm(vec2 point) {
    float total = 0.0;
    float weight = 0.5;
    for (int octave = 0; octave < 4; octave++) {
      total += noise(point) * weight;
      point *= 2.03;
      weight *= 0.5;
    }
    return total;
  }

  void main() {
    vec2 point = vWorld.xz;
    float broad = smoothstep(0.25, 0.75, fbm(point * 0.03));
    float detail = 1.0 - smoothstep(18.0, 70.0, distance(cameraPosition, vWorld));
    float blades = noise(point * 2.1) * 0.6 + noise(point * 6.3) * 0.4;
    vec3 color = mix(uDark, uLight, broad);
    color *= 0.85 + (blades - 0.5) * 0.45 * detail;
    float edge = max(length(point) - uPlazaRadius, 0.0);
    color += uGlow * exp(-edge * 0.2) * 0.07;
    gl_FragColor = vec4(color, 1.0);
    #include <colorspace_fragment>
    #include <fog_fragment>
  }
`

export function createSkyMaterial() {
  return new ShaderMaterial({
    vertexShader: SKY_VERTEX,
    fragmentShader: SKY_FRAGMENT,
    side: BackSide,
    depthWrite: false,
    uniforms: {
      uHorizon: { value: new Color(HORIZON_COLOR) },
      uZenith: { value: new Color(ZENITH_COLOR) },
    },
  })
}

export function createMeadowMaterial(plazaRadius: number) {
  return new ShaderMaterial({
    vertexShader: MEADOW_VERTEX,
    fragmentShader: MEADOW_FRAGMENT,
    fog: true,
    uniforms: UniformsUtils.merge([UniformsLib.fog, {
      uDark: { value: new Color(MEADOW_DARK) },
      uLight: { value: new Color(MEADOW_LIGHT) },
      uGlow: { value: new Color(MEADOW_GLOW) },
      uPlazaRadius: { value: plazaRadius },
    }]),
  })
}

export function createFireflyMaterial(calm: boolean) {
  return new ShaderMaterial({
    vertexShader: FIREFLY_VERTEX,
    fragmentShader: FIREFLY_FRAGMENT,
    transparent: true,
    depthWrite: false,
    blending: AdditiveBlending,
    uniforms: {
      uTime: fireflyTime,
      uSize: { value: FIREFLY_SIZE },
      uDrift: { value: calm ? CALM_DRIFT : FULL_DRIFT },
      uColor: { value: new Color(FIREFLY_COLOR) },
    },
  })
}
