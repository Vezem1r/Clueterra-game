#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { mkdir, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { dirname, join, resolve } from 'node:path'

const NE_COMMIT = 'f1890d9f152c896d250a77557a5751a93d494776'
const NE_BASE = `https://raw.githubusercontent.com/nvkelso/natural-earth-vector/${NE_COMMIT}/geojson`
const COUNTRIES_URL = `${NE_BASE}/ne_10m_admin_0_countries_deu.geojson`
const LEVANT_URL = `${NE_BASE}/ne_10m_admin_0_countries_pse.geojson`
const LEVANT_CODES = new Set(['IL', 'PS', 'SY'])
const SPARQL_API = 'https://query.wikidata.org/sparql'
const USER_AGENT = 'ClueterraDev/1.0 (silhouette-catalog) https://clueterra.com'
const CACHE = resolve('scripts/.cache/silhouette-game')
const CACHE_MAX_AGE_MS = 30 * 86400000
const FLAGS = resolve('backend/clueterra-api/src/main/resources/assets/flags')
const BUNDLE = resolve(argument('bundle') ?? 'backend/clueterra-questions/src/main/resources/assets/country-catalog/catalog.json')
const BUNDLE_VERSION = 1
const WORLD_DIR = resolve(argument('world-dir') ?? 'frontend/public/maps')
const WORLD_NAME = /^silhouette-world(\.[0-9a-f]{12})?\.json$/
const MAP_ASSETS_MODULE = resolve('frontend/src/pages/games/silhouettes/mapAssets.ts')
const POOL_NAME = /^country-pool(\.[0-9a-f]{12})?\.json$/
const WORLD_HASH_LENGTH = 12
const FRONTEND = resolve('frontend/package.json')

const UN_MEMBERS = `AF AL DZ AD AO AG AR AM AU AT AZ BS BH BD BB BY BE BZ BJ BT BO BA BW BR BN BG BF BI CV KH CM CA CF TD
  CL CN CO KM CG CD CR CI HR CU CY CZ DK DJ DM DO EC EG SV GQ ER EE SZ ET FJ FI FR GA GM GE DE GH GR GD GT GN GW GY HT HN
  HU IS IN ID IR IQ IE IL IT JM JP JO KZ KE KI KP KR KW KG LA LV LB LS LR LY LI LT LU MG MW MY MV ML MT MH MR MU MX FM MD
  MC MN ME MA MZ MM NA NR NP NL NZ NI NE NG MK NO OM PK PW PA PG PY PE PH PL PT QA RO RU RW KN LC VC WS SM ST SA SN RS SC
  SL SG SK SI SB SO ZA SS ES LK SD SR SE CH SY TJ TZ TH TL TG TO TT TN TR TM TV UG UA AE GB US UY UZ VU VE VN YE ZM ZW`
  .split(/\s+/).filter(Boolean)
const OBSERVERS_AND_PARTIALLY_RECOGNISED = ['VA', 'PS', 'XK', 'TW']
const POOL = [...UN_MEMBERS, ...OBSERVERS_AND_PARTIALLY_RECOGNISED].sort()
const UN_MEMBER_COUNT = 193

const SUBREGION_OVERRIDES = { AU: 'Australia and New Zealand' }
const LOCALES = ['en', 'ru', 'cs']
const NAME_OVERRIDES = {
  BA: { en: 'Bosnia and Herzegovina' },
  CD: { en: 'DR Congo', ru: 'ДР Конго', cs: 'Demokratická republika Kongo' },
  CG: { en: 'Republic of the Congo', ru: 'Республика Конго', cs: 'Kongo' },
  CI: { en: 'Ivory Coast' },
  FM: { en: 'Micronesia', ru: 'Микронезия' },
  KP: { ru: 'Северная Корея' },
  KR: { ru: 'Южная Корея' },
  MH: { ru: 'Маршалловы Острова' },
  SB: { ru: 'Соломоновы Острова' },
  SC: { ru: 'Сейшельские Острова' },
  US: { ru: 'США' },
  VC: { en: 'Saint Vincent and the Grenadines' },
  MM: { en: 'Myanmar' },
  PS: { en: 'Palestine', ru: 'Палестина', cs: 'Palestina' },
  VA: { en: 'Vatican City' },
}
const CURRENCY_NAME_OVERRIDES = {
  ARS: { en: 'Argentine peso' },
  PEN: { en: 'Peruvian sol', ru: 'перуанский соль', cs: 'peruánský sol' },
  KRW: { en: 'South Korean won' },
  MXN: { en: 'Mexican peso' },
  JPY: { en: 'Japanese yen' },
  ETB: { en: 'Ethiopian birr' },
  PLN: { en: 'Polish złoty', cs: 'polský zlotý' },
}
const EXTRA_ALIASES = {
  US: ['USA', 'US', 'United States of America', 'America', 'США', 'Америка'],
  GB: ['UK', 'Britain', 'Great Britain', 'Великобритания', 'Британия', 'Velká Británie'],
  AE: ['UAE', 'ОАЭ', 'SAE'],
  CD: ['DRC', 'Congo-Kinshasa', 'ДРК', 'Конго-Киншаса', 'DR Kongo'],
  CG: ['Congo-Brazzaville', 'Конго-Браззавиль'],
  CI: ["Côte d'Ivoire", 'Кот-д’Ивуар', "Кот-д'Ивуар", 'Pobřeží slonoviny'],
  CZ: ['Czech Republic', 'Чешская Республика', 'Česká republika'],
  MK: ['Macedonia', 'Македония', 'Makedonie'],
  MM: ['Burma', 'Бирма', 'Barma'],
  SZ: ['Swaziland', 'Свазиленд', 'Svazijsko'],
  TL: ['East Timor', 'Восточный Тимор', 'Východní Timor'],
  CV: ['Cape Verde', 'Кабо-Верде', 'Kapverdy'],
  TR: ['Turkey', 'Türkiye', 'Турция', 'Turecko'],
  NL: ['Holland', 'Голландия', 'Holandsko'],
  BY: ['Белоруссия', 'Bělorusko'],
  KG: ['Киргизия', 'Kirgizsko'],
  MD: ['Молдавия', 'Moldavsko'],
  KR: ['Korea', 'Республика Корея', 'Jižní Korea'],
  KP: ['КНДР', 'Severní Korea'],
  RU: ['Russian Federation', 'РФ', 'Ruská federace'],
  VA: ['Holy See', 'Святой Престол', 'Svatý stolec'],
}

const SILHOUETTE_SIZE = 1000
const SILHOUETTE_MARGIN = 1.1
const SHAPE_TOLERANCE_PX = 0.8
const SHAPE_MIN_RING_AREA_PX = 4
const NEAR_PART_MIN_KM = 250
const NEAR_PART_RADIUS_SHARE = 0.6
const FAR_PART_KM = 3500
const FAR_PART_MIN_SHARE = 0.1
const FAR_PART_EXCLUDED = new Set(['NO'])
const MAIN_SAMPLE_LIMIT = 1500
const MIN_DOT_PX = 22
const DOT_SIDES = 8
const SHAPE_DOT_MAIN_EXTENT_PX = 350
const EARTH_RADIUS_KM = 6371.0088
const WORLD_TOLERANCE_DEG = 0.05
const WORLD_DECIMALS = 2
const WORLD_MIN_RING_AREA_KM2 = 30
const WORLD_MICROSTATE_DECIMALS = 4
const FLAG_WIDTH = 200
const FLAG_HEIGHT = 150
const FLAG_BIN_BITS = 4
const FLAG_MERGE_DISTANCE = 48
const FLAG_MIN_SHARE = 0.025
const FLAG_MAX_COLORS = 5
const INTERIOR_GRID = 24
const INTERIOR_ROUNDS = 3
const INTERIOR_ZOOM = 4
const RETRY_ATTEMPTS = 6
const RETRY_BASE_MS = 5000
const MS_PER_SECOND = 1000
const NEIGHBOUR_KEY_DECIMALS = 3
const CURRENCY_CODE = /^[A-Z]{3}$/
const NEIGHBOUR_MIN_SHARED_POINTS = 2

function argument(name) {
  return process.argv.find(arg => arg.startsWith(`--${name}=`))?.slice(name.length + 3)
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

async function cached(key, load) {
  const path = resolve(CACHE, `${sha256(key)}.json`)
  const info = await stat(path).catch(() => null)
  if (info && Date.now() - info.mtimeMs < CACHE_MAX_AGE_MS) return JSON.parse(await readFile(path, 'utf8'))
  const value = await load()
  await mkdir(CACHE, { recursive: true })
  await writeFile(path, JSON.stringify(value))
  return value
}

async function fetchJson(url, init) {
  for (let attempt = 0; attempt < RETRY_ATTEMPTS; attempt++) {
    const response = await fetch(url, { ...init, headers: { 'User-Agent': USER_AGENT, ...init?.headers } })
    if (response.ok) return response.json()
    if (![429, 500, 502, 503, 504].includes(response.status) || attempt === RETRY_ATTEMPTS - 1) throw new Error(`${url}: HTTP ${response.status}`)
    const retryAfterSeconds = Number(response.headers.get('retry-after'))
    await new Promise(done => setTimeout(done, Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0
      ? retryAfterSeconds * MS_PER_SECOND
      : RETRY_BASE_MS * (attempt + 1)))
  }
  throw new Error(`Could not fetch ${url}`)
}

const getGeoJson = url => cached(url, () => fetchJson(url))
const sparql = query => cached(query, () => fetchJson(`${SPARQL_API}?query=${encodeURIComponent(query)}`,
  { headers: { Accept: 'application/sparql-results+json' } })).then(body => body.results.bindings)

const radians = degrees => (degrees * Math.PI) / 180
const degrees = value => (value * 180) / Math.PI

function vector([lng, lat]) {
  const phi = radians(lat)
  const lambda = radians(lng)
  return [Math.cos(phi) * Math.cos(lambda), Math.cos(phi) * Math.sin(lambda), Math.sin(phi)]
}

function angle(a, b) {
  return Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])))
}

function normalize([x, y, z]) {
  const length = Math.hypot(x, y, z) || 1
  return [x / length, y / length, z / length]
}

function lngLat([x, y, z]) {
  return [degrees(Math.atan2(y, x)), degrees(Math.asin(Math.max(-1, Math.min(1, z))))]
}

function ringAreaKm2(ring) {
  let sum = 0
  for (let i = 0; i < ring.length - 1; i++) {
    const [lng1, lat1] = ring[i]
    const [lng2, lat2] = ring[i + 1]
    let delta = lng2 - lng1
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    sum += radians(delta) * (2 + Math.sin(radians(lat1)) + Math.sin(radians(lat2)))
  }
  return Math.abs((sum * EARTH_RADIUS_KM * EARTH_RADIUS_KM) / 2)
}

function polygonAreaKm2(polygon) {
  return Math.max(0, ringAreaKm2(polygon[0]) - polygon.slice(1).reduce((sum, hole) => sum + ringAreaKm2(hole), 0))
}

function ringCenter(ring) {
  return normalize(ring.reduce((sum, point) => {
    const v = vector(point)
    return [sum[0] + v[0], sum[1] + v[1], sum[2] + v[2]]
  }, [0, 0, 0]))
}

function polygonsOf(geometry) {
  if (!geometry) return []
  return geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates
}

function mainPolygon(polygons) {
  return polygons.reduce((best, polygon) => polygonAreaKm2(polygon) > polygonAreaKm2(best) ? polygon : best)
}

function landNeighbours(codes, membersOf) {
  const owners = new Map()
  codes.forEach(iso => {
    const keys = new Set(mainPolygon(membersOf(iso).flatMap(member => polygonsOf(member.geometry))).flat()
      .map(([lng, lat]) => `${lng.toFixed(NEIGHBOUR_KEY_DECIMALS)},${lat.toFixed(NEIGHBOUR_KEY_DECIMALS)}`))
    keys.forEach(key => owners.set(key, [...(owners.get(key) ?? []), iso]))
  })
  const shared = new Map()
  owners.forEach(list => list.forEach(iso => list.filter(other => other !== iso).forEach(other => {
    const pair = `${iso}:${other}`
    shared.set(pair, (shared.get(pair) ?? 0) + 1)
  })))
  const result = new Map(codes.map(iso => [iso, []]))
  shared.forEach((count, pair) => {
    const [iso, other] = pair.split(':')
    if (count >= NEIGHBOUR_MIN_SHARED_POINTS) result.get(iso).push(other)
  })
  result.forEach(list => list.sort())
  return result
}

function sample(list, limit) {
  const step = Math.max(1, Math.ceil(list.length / limit))
  return list.filter((_, index) => index % step === 0)
}

function silhouetteParts(iso, polygons) {
  const parts = polygons.map(polygon => {
    const area = polygonAreaKm2(polygon)
    return { polygon, area, center: ringCenter(polygon[0]), radiusKm: Math.sqrt(area / Math.PI) }
  })
  const main = parts.reduce((best, part) => (part.area > best.area ? part : best))
  const mainVertices = sample(main.polygon[0], MAIN_SAMPLE_LIMIT).map(vector)
  const nearKm = Math.max(NEAR_PART_MIN_KM, main.radiusKm * NEAR_PART_RADIUS_SHARE)
  const mainDistance = part => Math.max(0, Math.min(...mainVertices.map(vertex => angle(vertex, part.center))) * EARTH_RADIUS_KM - part.radiusKm)
  const kept = new Set([main])
  parts.filter(part => part !== main && !FAR_PART_EXCLUDED.has(iso)
    && part.area >= main.area * FAR_PART_MIN_SHARE && mainDistance(part) <= FAR_PART_KM).forEach(part => kept.add(part))
  const queue = [...kept]
  const pending = new Set(parts.filter(part => !kept.has(part)))
  while (queue.length) {
    const anchor = queue.pop()
    for (const part of [...pending]) {
      const distanceKm = anchor === main
        ? mainDistance(part)
        : angle(anchor.center, part.center) * EARTH_RADIUS_KM - anchor.radiusKm - part.radiusKm
      if (distanceKm <= nearKm) {
        pending.delete(part)
        kept.add(part)
        queue.push(part)
      }
    }
  }
  return [...kept]
}

function areaWeightedCenter(parts) {
  return normalize(parts.reduce((sum, part) => [
    sum[0] + part.center[0] * part.area, sum[1] + part.center[1] * part.area, sum[2] + part.center[2] * part.area,
  ], [0, 0, 0]))
}

function projection(center) {
  const [lng0, lat0] = lngLat(center).map(radians)
  const sinLat0 = Math.sin(lat0)
  const cosLat0 = Math.cos(lat0)
  return ([lng, lat]) => {
    const lambda = radians(lng) - lng0
    const phi = radians(lat)
    const cosC = sinLat0 * Math.sin(phi) + cosLat0 * Math.cos(phi) * Math.cos(lambda)
    const k = Math.sqrt(2 / Math.max(1e-9, 1 + cosC))
    return [k * Math.cos(phi) * Math.sin(lambda), k * (cosLat0 * Math.sin(phi) - sinLat0 * Math.cos(phi) * Math.cos(lambda))]
  }
}

function simplify(points, tolerance) {
  if (points.length <= 4) return points
  const keep = new Uint8Array(points.length)
  keep[0] = 1
  keep[points.length - 1] = 1
  const stack = [[0, points.length - 1]]
  const squared = tolerance * tolerance
  while (stack.length) {
    const [first, last] = stack.pop()
    const [x1, y1] = points[first]
    const [x2, y2] = points[last]
    const dx = x2 - x1
    const dy = y2 - y1
    const lengthSquared = dx * dx + dy * dy
    let farthest = 0
    let index = -1
    for (let i = first + 1; i < last; i++) {
      const [x, y] = points[i]
      const t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / lengthSquared))
      const ex = x - (x1 + t * dx)
      const ey = y - (y1 + t * dy)
      const distance = ex * ex + ey * ey
      if (distance > farthest) {
        farthest = distance
        index = i
      }
    }
    if (index >= 0 && farthest > squared) {
      keep[index] = 1
      stack.push([first, index], [index, last])
    }
  }
  return points.filter((_, i) => keep[i])
}

function planarArea(ring) {
  let sum = 0
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) sum += (ring[j][0] + ring[i][0]) * (ring[j][1] - ring[i][1])
  return Math.abs(sum / 2)
}

function toPixels(points, half) {
  const scale = SILHOUETTE_SIZE / 2 / half
  return points.map(([x, y]) => [SILHOUETTE_SIZE / 2 + x * scale, SILHOUETTE_SIZE / 2 - y * scale])
}

function path(rings) {
  return rings.map(ring => {
    const coordinates = ring.map(([x, y]) => `${Math.round(x)} ${Math.round(y)}`)
    const unique = coordinates.filter((value, i) => i === 0 || value !== coordinates[i - 1])
    return unique.length >= 3 ? `M${unique.join(' ')}Z` : ''
  }).join('')
}

function dot(ring, size = MIN_DOT_PX) {
  const xs = ring.map(([x]) => x)
  const ys = ring.map(([, y]) => y)
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2
  const cy = (Math.min(...ys) + Math.max(...ys)) / 2
  return Array.from({ length: DOT_SIDES }, (_, i) => {
    const turn = (2 * Math.PI * i) / DOT_SIDES
    return [cx + (size / 2) * Math.cos(turn), cy + (size / 2) * Math.sin(turn)]
  })
}

function extent(ring) {
  const xs = ring.map(([x]) => x)
  const ys = ring.map(([, y]) => y)
  return Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))
}

function shapeRings(parts, project, origin, half) {
  const pixels = ring => simplify(toPixels(ring.map(project).map(([x, y]) => [x - origin[0], y - origin[1]]), half), SHAPE_TOLERANCE_PX)
  const polygons = parts.map(part => part.polygon.map(pixels))
  const dots = Math.max(...polygons.map(([outer]) => extent(outer))) < SHAPE_DOT_MAIN_EXTENT_PX
  return polygons.flatMap(([outer, ...holes]) => {
    if (dots && extent(outer) < MIN_DOT_PX) return [dot(outer)]
    return [outer, ...holes].filter(ring => planarArea(ring) >= SHAPE_MIN_RING_AREA_PX)
  })
}

function boundsOf(parts, center) {
  const [centerLng] = lngLat(center)
  const points = parts.flatMap(part => part.polygon[0])
  const relative = points.map(([lng]) => ((lng - centerLng + 540) % 360) - 180)
  const latitudes = points.map(([, lat]) => lat)
  return [centerLng + Math.min(...relative), Math.min(...latitudes), centerLng + Math.max(...relative), Math.max(...latitudes)]
    .map(value => Math.round(value * 1000) / 1000)
}

function worldGeometry(polygons) {
  const factor = 10 ** WORLD_DECIMALS
  const simplified = polygons.map(polygon => ({ polygon, area: polygonAreaKm2(polygon) }))
  const largest = Math.max(...simplified.map(entry => entry.area))
  const coordinates = simplified
    .filter(entry => entry.area >= WORLD_MIN_RING_AREA_KM2 || entry.area === largest)
    .map(entry => entry.polygon
      .map(ring => simplify(ring, WORLD_TOLERANCE_DEG).map(([lng, lat]) => [Math.round(lng * factor) / factor, Math.round(lat * factor) / factor]))
      .filter(ring => ring.length >= 4))
    .filter(polygon => polygon.length > 0)
  return { type: 'MultiPolygon', coordinates: coordinates.length > 0 ? coordinates : microstateOutline(simplified, largest) }
}

function microstateOutline(simplified, largest) {
  const factor = 10 ** WORLD_MICROSTATE_DECIMALS
  const outline = simplified.find(entry => entry.area === largest).polygon[0]
    .map(([lng, lat]) => [Math.round(lng * factor) / factor, Math.round(lat * factor) / factor])
    .filter(([lng, lat], i, points) => i === 0 || lng !== points[i - 1][0] || lat !== points[i - 1][1])
  return outline.length >= 4 ? [[outline]] : []
}

function insideRing([x, y], ring) {
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

function edgeDistance([x, y], [x1, y1], [x2, y2], scale) {
  const dx = (x2 - x1) * scale
  const dy = y2 - y1
  const length = dx * dx + dy * dy
  const t = length === 0 ? 0 : Math.max(0, Math.min(1, (((x - x1) * scale) * dx + (y - y1) * dy) / length))
  return Math.hypot((x - x1) * scale - t * dx, y - y1 - t * dy)
}

function interiorPoint(geometry) {
  const polygons = geometry.coordinates
  if (!polygons.length) return null
  const polygon = polygons.reduce((best, candidate) => (ringAreaKm2(candidate[0]) > ringAreaKm2(best[0]) ? candidate : best))
  const xs = polygon[0].map(([x]) => x)
  const ys = polygon[0].map(([, y]) => y)
  const scale = Math.cos(radians((Math.min(...ys) + Math.max(...ys)) / 2))
  const score = point => {
    if (!insideRing(point, polygon[0]) || polygon.slice(1).some(hole => insideRing(point, hole))) return -1
    return Math.min(...polygon.flatMap(ring => ring.slice(1).map((vertex, index) => edgeDistance(point, ring[index], vertex, scale))))
  }
  let box = [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)]
  let best = null
  let bestScore = -1
  for (let round = 0; round < INTERIOR_ROUNDS; round++) {
    const [west, south, east, north] = box
    for (let i = 0; i <= INTERIOR_GRID; i++) {
      for (let j = 0; j <= INTERIOR_GRID; j++) {
        const point = [west + ((east - west) * i) / INTERIOR_GRID, south + ((north - south) * j) / INTERIOR_GRID]
        const value = score(point)
        if (value > bestScore) {
          bestScore = value
          best = point
        }
      }
    }
    if (!best) return null
    const halfX = (east - west) / INTERIOR_ZOOM / 2
    const halfY = (north - south) / INTERIOR_ZOOM / 2
    box = [best[0] - halfX, best[1] - halfY, best[0] + halfX, best[1] + halfY]
  }
  return best
}

function isoOf(feature) {
  const code = feature.properties.ISO_A2_EH
  return /^[A-Z]{2}$/.test(code) ? code : null
}

function cleanName(value) {
  return value.replace(/\s*\(.*\)$/, '').replace(/\s*&\s*/g, ' and ').replace(/^St\. /, 'Saint ').trim()
}

function namesOf(iso) {
  return Object.fromEntries(LOCALES.map(locale => {
    const name = NAME_OVERRIDES[iso]?.[locale] ?? new Intl.DisplayNames([locale], { type: 'region' }).of(iso)
    return [locale, locale === 'en' ? cleanName(name) : name.replace(/\s*\(.*\)$/, '')]
  }))
}

function aliasesOf(iso, names, properties) {
  const candidates = [
    ...Object.values(names),
    ...LOCALES.map(locale => new Intl.DisplayNames([locale], { type: 'region' }).of(iso)),
    properties.NAME, properties.NAME_LONG, properties.FORMAL_EN, properties.ADMIN, properties.NAME_EN, properties.NAME_RU,
    ...(EXTRA_ALIASES[iso] ?? []),
  ]
  const seen = new Set()
  return candidates.filter(Boolean).map(value => value.trim()).filter(value => {
    const key = value.toLocaleLowerCase()
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function label(binding, prefix) {
  const en = binding[`${prefix}En`]?.value ?? binding[`${prefix}Cs`]?.value
  if (!en) return null
  return { en, ru: binding[`${prefix}Ru`]?.value ?? en, cs: binding[`${prefix}Cs`]?.value ?? en }
}

async function loadWikidata() {
  const capitals = await sparql(`SELECT ?iso ?capital ?capitalEn ?capitalRu ?capitalCs WHERE {
    ?country wdt:P297 ?iso; p:P36 ?statement. ?statement ps:P36 ?capital; wikibase:rank ?rank.
    FILTER(?rank != wikibase:DeprecatedRank) FILTER NOT EXISTS { ?statement pq:P582 ?end }
    OPTIONAL { ?capital rdfs:label ?capitalEn FILTER(LANG(?capitalEn) = "en") }
    OPTIONAL { ?capital rdfs:label ?capitalRu FILTER(LANG(?capitalRu) = "ru") }
    OPTIONAL { ?capital rdfs:label ?capitalCs FILTER(LANG(?capitalCs) = "cs") } }`)
  const facts = await sparql(`SELECT ?iso ?population ?area ?landlocked ?peak ?peakEn ?peakRu ?peakCs ?elevation WHERE {
    ?country wdt:P297 ?iso.
    OPTIONAL { ?country wdt:P1082 ?population } OPTIONAL { ?country wdt:P2046 ?area }
    BIND(EXISTS { ?country wdt:P31 wd:Q123480 } AS ?landlocked)
    OPTIONAL { ?country wdt:P610 ?peak. OPTIONAL { ?peak wdt:P2044 ?elevation }
      OPTIONAL { ?peak rdfs:label ?peakEn FILTER(LANG(?peakEn) = "en") }
      OPTIONAL { ?peak rdfs:label ?peakRu FILTER(LANG(?peakRu) = "ru") }
      OPTIONAL { ?peak rdfs:label ?peakCs FILTER(LANG(?peakCs) = "cs") } } }`)
  const currencies = await sparql(`SELECT ?iso ?currency ?code ?currencyEn ?currencyRu ?currencyCs WHERE {
    ?country wdt:P297 ?iso; p:P38 ?statement. ?statement ps:P38 ?currency; wikibase:rank ?rank.
    FILTER(?rank != wikibase:DeprecatedRank) FILTER NOT EXISTS { ?statement pq:P582 ?end }
    ?currency wdt:P498 ?code. FILTER NOT EXISTS { ?currency wdt:P576 ?abolished }
    OPTIONAL { ?currency rdfs:label ?currencyEn FILTER(LANG(?currencyEn) = "en") }
    OPTIONAL { ?currency rdfs:label ?currencyRu FILTER(LANG(?currencyRu) = "ru") }
    OPTIONAL { ?currency rdfs:label ?currencyCs FILTER(LANG(?currencyCs) = "cs") } }`)
  const byIso = new Map()
  const entry = iso => byIso.get(iso) ?? byIso.set(iso, {
    capitals: new Map(), populations: [], areas: [], landlocked: false, peaks: [], currencies: new Map(),
  }).get(iso)
  capitals.forEach(binding => {
    const capital = label(binding, 'capital')
    if (capital) entry(binding.iso.value).capitals.set(binding.capital.value, capital)
  })
  facts.forEach(binding => {
    const item = entry(binding.iso.value)
    if (binding.population) item.populations.push(Number(binding.population.value))
    if (binding.area) item.areas.push(Number(binding.area.value))
    if (binding.landlocked?.value === 'true') item.landlocked = true
    const peak = label(binding, 'peak')
    if (peak && binding.elevation) item.peaks.push({ name: peak, elevation: Number(binding.elevation.value) })
  })
  currencies.forEach(binding => {
    const currency = label(binding, 'currency')
    if (currency && CURRENCY_CODE.test(binding.code.value)) {
      entry(binding.iso.value).currencies.set(binding.code.value, {
        code: binding.code.value, name: { ...currency, ...CURRENCY_NAME_OVERRIDES[binding.code.value] },
      })
    }
  })
  return byIso
}

function hex([r, g, b]) {
  return `#${[r, g, b].map(value => Math.round(value).toString(16).padStart(2, '0')).join('')}`
}

function palette(pixels) {
  const bins = new Map()
  const shift = 8 - FLAG_BIN_BITS
  let total = 0
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i + 3] < 128) continue
    total++
    const key = ((pixels[i] >> shift) << (2 * FLAG_BIN_BITS)) | ((pixels[i + 1] >> shift) << FLAG_BIN_BITS) | (pixels[i + 2] >> shift)
    const bin = bins.get(key) ?? bins.set(key, { count: 0, sum: [0, 0, 0] }).get(key)
    bin.count++
    bin.sum[0] += pixels[i]
    bin.sum[1] += pixels[i + 1]
    bin.sum[2] += pixels[i + 2]
  }
  const clusters = []
  ;[...bins.values()].sort((a, b) => b.count - a.count).forEach(bin => {
    const color = bin.sum.map(value => value / bin.count)
    const near = clusters.find(cluster => Math.hypot(...cluster.color.map((value, i) => value - color[i])) < FLAG_MERGE_DISTANCE)
    if (!near) {
      clusters.push({ color, count: bin.count })
      return
    }
    near.color = near.color.map((value, i) => (value * near.count + color[i] * bin.count) / (near.count + bin.count))
    near.count += bin.count
  })
  const kept = clusters.filter(cluster => cluster.count / total >= FLAG_MIN_SHARE)
    .sort((a, b) => b.count - a.count).slice(0, FLAG_MAX_COLORS)
  const keptTotal = kept.reduce((sum, cluster) => sum + cluster.count, 0)
  return kept.map(cluster => ({ color: hex(cluster.color), share: Math.round((cluster.count / keptTotal) * 1000) / 1000 }))
}

async function flagPalettes(codes) {
  const { chromium } = createRequire(FRONTEND)('@playwright/test')
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage()
    const result = new Map()
    for (const iso of codes) {
      const svg = await readFile(resolve(FLAGS, `${iso.toLowerCase()}.svg`), 'utf8')
      const pixels = await page.evaluate(async ({ source, width, height }) => {
        const image = new Image()
        image.src = `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(source)))}`
        await image.decode()
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const context = canvas.getContext('2d')
        context.drawImage(image, 0, 0, width, height)
        return Array.from(context.getImageData(0, 0, width, height).data)
      }, { source: svg, width: FLAG_WIDTH, height: FLAG_HEIGHT })
      result.set(iso, palette(pixels))
    }
    return result
  } finally {
    await browser.close()
  }
}

if (UN_MEMBERS.length !== UN_MEMBER_COUNT || new Set(POOL).size !== POOL.length) {
  throw new Error(`Expected ${UN_MEMBER_COUNT} unique UN members, found ${UN_MEMBERS.length}`)
}

const levant = (await getGeoJson(LEVANT_URL)).features.filter(feature => LEVANT_CODES.has(isoOf(feature)))
const features = [
  ...(await getGeoJson(COUNTRIES_URL)).features.filter(feature => !LEVANT_CODES.has(isoOf(feature))),
  ...levant,
].filter(feature => feature.geometry)
const byIso = new Map()
features.filter(isoOf).forEach(feature => byIso.set(isoOf(feature), [...(byIso.get(isoOf(feature)) ?? []), feature]))
const missing = POOL.filter(iso => !byIso.has(iso))
if (missing.length) throw new Error(`Countries missing from Natural Earth: ${missing.join(', ')}`)

const wikidata = await loadWikidata()
const emptyWikidata = { capitals: new Map(), populations: [], areas: [], landlocked: false, peaks: [], currencies: new Map() }
const flags = await flagPalettes(POOL)
const neighbours = landNeighbours(POOL, iso => byIso.get(iso))

const entries = POOL.map(iso => {
  const members = byIso.get(iso)
  const feature = members.find(member => member.properties.ADMIN === member.properties.SOVEREIGNT) ?? members[0]
  const polygons = members.flatMap(member => polygonsOf(member.geometry))
  const parts = silhouetteParts(iso, polygons)
  const center = areaWeightedCenter(parts)
  const project = projection(center)
  const projected = parts.flatMap(part => part.polygon[0].map(project))
  const xs = projected.map(([x]) => x)
  const ys = projected.map(([, y]) => y)
  const origin = [(Math.min(...xs) + Math.max(...xs)) / 2, (Math.min(...ys) + Math.max(...ys)) / 2]
  const half = (Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)) / 2) * SILHOUETTE_MARGIN
  const geometryAreaKm2 = polygons.reduce((sum, polygon) => sum + polygonAreaKm2(polygon), 0)
  const wikidataEntry = wikidata.get(iso) ?? emptyWikidata
  const names = namesOf(iso)
  const [lng, lat] = interiorPoint(worldGeometry(polygons)) ?? lngLat(center)
  return {
    iso,
    names,
    aliases: aliasesOf(iso, names, feature.properties),
    region: feature.properties.REGION_UN,
    subregion: SUBREGION_OVERRIDES[iso] ?? feature.properties.SUBREGION,
    center: [Math.round(lng * 1000) / 1000, Math.round(lat * 1000) / 1000],
    bounds: boundsOf(parts, center),
    shapePath: path(shapeRings(parts, project, origin, half)),
    flagColors: flags.get(iso),
    neighbours: neighbours.get(iso),
    reference: {
      capitals: [...wikidataEntry.capitals.values()],
      populations: [...new Set(wikidataEntry.populations)],
      areas: [...new Set(wikidataEntry.areas)],
      peaks: wikidataEntry.peaks,
      currencies: [...wikidataEntry.currencies.values()].sort((a, b) => a.code.localeCompare(b.code)),
      landlocked: wikidataEntry.landlocked,
      geometryAreaKm2: Math.round(geometryAreaKm2),
    },
  }
})

const bundle = {
  version: BUNDLE_VERSION,
  borders: 'Natural Earth v5.1.2 admin-0 countries, German point of view; IL, PS and SY from the Palestinian point of view',
  entries: entries.map(({ aliases, ...entry }) => entry),
}
await mkdir(dirname(BUNDLE), { recursive: true })
await writeFile(BUNDLE, JSON.stringify(bundle))

const pool = new Map(entries.map(entry => [entry.iso, entry]))
const territories = features.filter(feature => !pool.has(isoOf(feature)))
  .map(feature => ({ type: 'Feature', properties: { iso: isoOf(feature), pool: false }, geometry: worldGeometry(polygonsOf(feature.geometry)) }))
const countries = entries.map(entry => ({
  type: 'Feature',
  properties: {
    iso: entry.iso, pool: true, subregion: entry.subregion, names: entry.names, aliases: entry.aliases,
    center: entry.center, bounds: entry.bounds, colors: entry.flagColors,
  },
  geometry: worldGeometry(byIso.get(entry.iso).flatMap(member => polygonsOf(member.geometry))),
}))
const unmapped = countries.filter(feature => feature.geometry.coordinates.length === 0).map(feature => feature.properties.iso)
if (unmapped.length) throw new Error(`Countries missing from the world map: ${unmapped.join(', ')}`)
const world = { type: 'FeatureCollection', features: [...territories, ...countries].filter(feature => feature.geometry.coordinates.length > 0) }
const worldText = JSON.stringify(world)
const worldFile = `silhouette-world.${sha256(worldText).slice(0, WORLD_HASH_LENGTH)}.json`
await mkdir(WORLD_DIR, { recursive: true })
await Promise.all((await readdir(WORLD_DIR)).filter(name => WORLD_NAME.test(name) && name !== worldFile)
  .map(name => rm(join(WORLD_DIR, name))))
await writeFile(join(WORLD_DIR, worldFile), worldText)

const poolText = JSON.stringify(countries.map(feature => feature.properties))
const poolFile = `country-pool.${sha256(poolText).slice(0, WORLD_HASH_LENGTH)}.json`
await Promise.all((await readdir(WORLD_DIR)).filter(name => POOL_NAME.test(name) && name !== poolFile)
  .map(name => rm(join(WORLD_DIR, name))))
await writeFile(join(WORLD_DIR, poolFile), poolText)
await writeFile(MAP_ASSETS_MODULE, `export const SILHOUETTE_WORLD_URL = '/maps/${worldFile}'\nexport const COUNTRY_POOL_URL = '/maps/${poolFile}'\n`)

const size = text => `${Math.round(Buffer.byteLength(text) / 1024)} KB`
console.log(`${entries.length} countries; bundle ${size(JSON.stringify(bundle))}; world ${worldFile} ${size(worldText)}; pool ${poolFile} ${size(poolText)}`)
