#!/usr/bin/env node

// Builds a reviewable, source-backed bundle. The Questions admin operation imports
// this bundle; it never guesses river lengths or names from country river-network SVGs.
import { createHash } from 'node:crypto'
import { mkdir, readFile, readdir, stat, unlink, writeFile } from 'node:fs/promises'
import { resolve, sep } from 'node:path'
import { createTerrainRenderer } from './river-map/terrain-renderer.mjs'
import { createRiverWebpRenderer } from './river-map/webp-renderer.mjs'

const NE_VERSION = 'v5.1.2'
const NE_BASE = `https://raw.githubusercontent.com/nvkelso/natural-earth-vector/${NE_VERSION}/geojson`
const RIVER_URL = `${NE_BASE}/ne_10m_rivers_lake_centerlines_scale_rank.geojson`
const SUPPLEMENTAL_RIVER_URLS = [
  `${NE_BASE}/ne_10m_rivers_europe.geojson`,
  `${NE_BASE}/ne_10m_rivers_north_america.geojson`,
  `${NE_BASE}/ne_10m_rivers_australia.geojson`,
]
const BORDER_URL = `${NE_BASE}/ne_10m_admin_0_countries.geojson`
const GEOMETRY_SOURCE_URL = 'https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-rivers-lake-centerlines/'
const WD_API = 'https://www.wikidata.org/w/api.php'
const SPARQL_API = 'https://query.wikidata.org/sparql'
const CITY_CLASSES = ['Q515', 'Q3957', 'Q1549591', 'Q5119']
const CITY_MIN_POPULATION = 20000
const CITY_MAX_DISTANCE_KM = 15
const SPARQL_BATCH = 60
const KM_PER_DEGREE = 111.32
const NAME_QUALIFIER = /\s*(\(.*\)|,.*)$/
const LEAK_PREFIX_MIN_LENGTH = 5
const GENERIC_NAME_WORDS = new Set(['river', 'rio', 'riviere', 'rivier', 'fluss', 'creek', 'fork', 'branch',
  'the', 'of', 'de', 'del', 'da', 'do', 'la', 'le', 'el', 'du', 'des', 'and', 'lake', 'gulf', 'bay', 'sea',
  'upper', 'lower', 'great', 'little', 'big', 'grand', 'north', 'south', 'east', 'west', 'northern',
  'southern', 'eastern', 'western', 'white', 'black', 'blue', 'red', 'green', 'yellow', 'saint', 'san',
  'santa', 'st', 'new', 'old'])
const DEFAULT_OUTPUT = resolve('backend/clueterra-questions/src/main/resources/assets/river-game')
const OUTPUT = resolve(process.argv.find(arg => arg.startsWith('--output='))?.slice('--output='.length)
  ?? DEFAULT_OUTPUT)
const CACHE = resolve('scripts/.cache/river-game')
const requestedCountries = new Set((process.argv.find(arg => arg.startsWith('--countries='))?.split('=')[1] ?? '')
  .split(',').map(value => value.trim().toUpperCase()).filter(Boolean))
const dryRun = process.argv.includes('--dry-run')
const COURSE_TOLERANCE_DEG = 0.004
const COURSE_DECIMALS = 3
const COURSE_PLACEHOLDER = '__river_course__'

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

async function getJson(url) {
  const cachePath = resolve(CACHE, `${sha256(url)}.json`)
  const maxAgeMs = url.includes('raw.githubusercontent.com') ? 30 * 86400000 : 7 * 86400000
  try {
    const info = await stat(cachePath)
    if (Date.now() - info.mtimeMs < maxAgeMs) return JSON.parse(await readFile(cachePath, 'utf8'))
  } catch {
    // A missing or corrupt cache is rebuilt from the pinned source.
  }
  for (let attempt = 0; attempt < 4; attempt++) {
    const response = await fetch(url, { headers: { 'User-Agent': 'ClueterraRiverCatalog/1.0 (https://clueterra.com)' } })
    if (response.ok) {
      const value = await response.json()
      await mkdir(CACHE, { recursive: true })
      await writeFile(cachePath, JSON.stringify(value))
      return value
    }
    if (![429, 500, 502, 503, 504].includes(response.status) || attempt === 3) {
      throw new Error(`${url}: HTTP ${response.status}`)
    }
    await new Promise(done => setTimeout(done, 2000 * (attempt + 1)))
  }
  throw new Error(`Could not fetch ${url}`)
}

function allLines(geometry) {
  if (!geometry) return []
  if (geometry.type === 'LineString') return [geometry.coordinates]
  if (geometry.type === 'MultiLineString') return geometry.coordinates
  return []
}

function allPolygons(geometry) {
  if (!geometry) return []
  if (geometry.type === 'Polygon') return [geometry.coordinates]
  if (geometry.type === 'MultiPolygon') return geometry.coordinates
  return []
}

function bounds(points) {
  return points.reduce((box, [x, y]) => ({
    minX: Math.min(box.minX, x), minY: Math.min(box.minY, y),
    maxX: Math.max(box.maxX, x), maxY: Math.max(box.maxY, y),
  }), { minX: 180, minY: 90, maxX: -180, maxY: -90 })
}

function intersects(a, b) {
  return a.minX <= b.maxX && a.maxX >= b.minX && a.minY <= b.maxY && a.maxY >= b.minY
}

function inRing(point, ring) {
  const [x, y] = point
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

function inPolygon(point, polygon) {
  return inRing(point, polygon[0]) && !polygon.slice(1).some(hole => inRing(point, hole))
}

function kilometers(a, b) {
  const rad = Math.PI / 180
  const latitude = (b[1] - a[1]) * rad
  const longitude = (b[0] - a[0]) * rad
  const haversine = Math.sin(latitude / 2) ** 2
    + Math.cos(a[1] * rad) * Math.cos(b[1] * rad) * Math.sin(longitude / 2) ** 2
  return 12742 * Math.asin(Math.min(1, Math.sqrt(haversine)))
}

function mappedLength(lines) {
  return lines.reduce((sum, line) => sum + line.slice(1)
    .reduce((length, point, index) => length + kilometers(line[index], point), 0), 0)
}

function perpendicularDistance([x, y], [x1, y1], [x2, y2]) {
  const dx = x2 - x1
  const dy = y2 - y1
  const length = Math.hypot(dx, dy)
  return length === 0 ? Math.hypot(x - x1, y - y1) : Math.abs(dy * x - dx * y + x2 * y1 - y2 * x1) / length
}

function simplify(line, tolerance) {
  if (line.length <= 2) return line
  let farthest = 0
  let index = 0
  for (let i = 1; i < line.length - 1; i++) {
    const distance = perpendicularDistance(line[i], line[0], line[line.length - 1])
    if (distance > farthest) {
      farthest = distance
      index = i
    }
  }
  if (farthest <= tolerance) return [line[0], line[line.length - 1]]
  return [...simplify(line.slice(0, index + 1), tolerance).slice(0, -1), ...simplify(line.slice(index), tolerance)]
}

function riverCourse(lines) {
  const factor = 10 ** COURSE_DECIMALS
  const coordinates = lines
    .map(line => simplify(line, COURSE_TOLERANCE_DEG)
      .map(([x, y]) => [Math.round(x * factor) / factor, Math.round(y * factor) / factor]))
    .filter(line => line.length >= 2)
  const box = bounds(coordinates.flat())
  return { bbox: [box.minX, box.minY, box.maxX, box.maxY], coordinates }
}

function formatCatalog(manifest) {
  const courses = []
  const text = JSON.stringify(manifest,
    (key, value) => key === 'course' ? `${COURSE_PLACEHOLDER}${courses.push(value) - 1}` : value, 2)
  return `${text.replace(new RegExp(`"${COURSE_PLACEHOLDER}(\\d+)"`, 'g'),
    (_, index) => JSON.stringify(courses[Number(index)]))}\n`
}

function linesWithinClaimedCountries(river, entity, countryCodes, countryByCode) {
  const extent = bounds(river.lines.flat())
  const reportedLengthKm = officialLengthKm(entity)
  const coordinate = statements(entity, 'P625').map(statement => statement.mainsnak.datavalue?.value)
    .find(value => Number.isFinite(value?.longitude) && Number.isFinite(value?.latitude))
  const extentKm = kilometers([extent.minX, extent.minY], [extent.maxX, extent.maxY])
  if (coordinate && reportedLengthKm && extentKm > Math.max(600, reportedLengthKm * 1.7)) {
    const anchor = [coordinate.longitude, coordinate.latitude]
    const radiusKm = Math.max(350, reportedLengthKm * 2)
    const anchored = river.lines.filter(line => line.some((point, index) =>
      (index % Math.max(1, Math.floor(line.length / 20)) === 0 || index === line.length - 1)
      && kilometers(point, anchor) <= radiusKm))
    if (anchored.length) return anchored
  }
  if (!countryCodes.length || (extent.maxX - extent.minX <= 45 && extent.maxY - extent.minY <= 45)) {
    return river.lines
  }
  const polygons = countryCodes.flatMap(code => countryByCode.get(code)?.polygons ?? [])
  return river.lines.filter(line => {
    const step = Math.max(1, Math.floor(line.length / 20))
    return line.some((point, index) => (index % step === 0 || index === line.length - 1)
      && polygons.some(polygon => point[0] >= polygon.box.minX && point[0] <= polygon.box.maxX
        && point[1] >= polygon.box.minY && point[1] <= polygon.box.maxY
        && inPolygon(point, polygon.rings)))
  })
}

function buildCountries(features) {
  const countries = new Map()
  for (const feature of features) {
    const iso2 = (feature.properties.ISO_A2 === '-99'
      ? feature.properties.ISO_A2_EH : feature.properties.ISO_A2)?.toUpperCase()
    if (!/^[A-Z]{2}$/.test(iso2)) continue
    const polygons = allPolygons(feature.geometry)
      .filter(polygon => polygon[0]?.length >= 4)
      .map(polygon => ({ rings: polygon, box: bounds(polygon[0]) }))
    if (!polygons.length) continue
    const country = countries.get(iso2) ?? {
      iso2, name: feature.properties.NAME_EN || feature.properties.NAME_LONG || feature.properties.NAME,
      wikidataId: feature.properties.WIKIDATAID,
      polygons: [],
    }
    country.polygons.push(...polygons)
    countries.set(iso2, country)
  }
  return [...countries.values()]
}

function buildRivers(features) {
  const rivers = new Map()
  for (const feature of features) {
    const qid = feature.properties.wikidataid
    const name = feature.properties.name_en || feature.properties.name
    if (!/^Q[1-9][0-9]*$/.test(qid) || !name
      || !/river/i.test(feature.properties.featurecla ?? '')) continue
    const river = rivers.get(qid) ?? { qid, name, lines: [], scaleRank: 99 }
    river.lines.push(...allLines(feature.geometry).filter(line => line.length >= 2))
    river.scaleRank = Math.min(river.scaleRank, Number(feature.properties.scalerank) || 99)
    rivers.set(qid, river)
  }
  return [...rivers.values()].map(river => ({ ...river, mappedLengthKm: mappedLength(river.lines) }))
    .filter(river => river.mappedLengthKm >= 50)
}

function mappedCountryDistances(river, countries) {
  const riverBox = bounds(river.lines.flat())
  const distances = new Map()
  for (const country of countries) {
    const polygons = country.polygons.filter(polygon => intersects(riverBox, polygon.box))
    if (!polygons.length) continue
    let insideKm = 0
    for (const line of river.lines) {
      for (let index = 1; index < line.length; index++) {
        const first = line[index - 1]
        const second = line[index]
        const midpoint = [(first[0] + second[0]) / 2, (first[1] + second[1]) / 2]
        if (polygons.some(polygon => midpoint[0] >= polygon.box.minX && midpoint[0] <= polygon.box.maxX
          && midpoint[1] >= polygon.box.minY && midpoint[1] <= polygon.box.maxY
          && inPolygon(midpoint, polygon.rings))) insideKm += kilometers(first, second)
      }
    }
    if (insideKm >= Math.max(15, river.mappedLengthKm * 0.015)) distances.set(country.iso2, insideKm)
  }
  return distances
}

async function entities(ids, props = 'claims|labels') {
  const result = new Map()
  const unique = [...new Set(ids)]
  for (let start = 0; start < unique.length; start += 40) {
    const batch = unique.slice(start, start + 40)
    const params = new URLSearchParams({ action: 'wbgetentities', ids: batch.join('|'),
      props, languages: 'en|ru|cs', format: 'json' })
    const response = await getJson(`${WD_API}?${params}`)
    for (const [id, entity] of Object.entries(response.entities ?? {})) result.set(id, entity)
    if (start && start % 400 === 0) console.log(`Wikidata: ${start}/${unique.length} entities`)
    await new Promise(done => setTimeout(done, 120))
  }
  return result
}

function statements(entity, property) {
  return (entity?.claims?.[property] ?? [])
    .filter(statement => statement.rank !== 'deprecated' && statement.mainsnak.snaktype === 'value')
    .sort((a, b) => (a.rank === 'preferred' ? -1 : 0) - (b.rank === 'preferred' ? -1 : 0))
}

function officialLengthKm(entity) {
  for (const statement of statements(entity, 'P2043')) {
    const value = statement.mainsnak.datavalue?.value
    const amount = Number(value?.amount)
    const unit = value?.unit?.split('/').pop()
    const km = unit === 'Q828224' ? amount
      : unit === 'Q11573' ? amount / 1000
        : unit === 'Q253276' ? amount * 1.609344 : null
    if (km && km >= 50 && km <= 10000) return Math.round(km)
  }
  return null
}

function playabilityScore(river, lengthKm, entity) {
  const wikipediaLanguages = Object.keys(entity?.sitelinks ?? {})
    .filter(key => key.endsWith('wiki') && !key.includes('_')).length
  return Math.log1p(wikipediaLanguages) * 28 + Math.log1p(lengthKm) * 10
    + Math.max(0, 10 - river.scaleRank) * 3
}

function firstItem(entity, property) {
  return statements(entity, property).map(statement => statement.mainsnak.datavalue?.value?.id)
    .find(value => /^Q[1-9][0-9]*$/.test(value))
}

function answerAliases(entity) {
  const answerName = entity.labels.en.value
  const wikidataAliases = ['en', 'ru', 'cs'].flatMap(language =>
    (entity.aliases?.[language] ?? []).map(alias => alias.value))
  const shortName = answerName.replace(/^River\s+/i, '').replace(/\s+River$/i, '')
  return [...new Set([shortName, ...wikidataAliases])]
    .filter(alias => alias !== answerName && alias.length >= 2 && alias.length <= 100 && !alias.includes(';'))
    .slice(0, 12)
}

function nameWords(value) {
  return value.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('en-US')
    .split(/[^\p{L}]+/u).filter(word => word.length >= 2 && !GENERIC_NAME_WORDS.has(word))
}

function labelLeaksAnswer(label, answerName, aliases) {
  if (!label) return false
  const labelWords = nameWords(label)
  return [answerName, ...aliases].some(name => nameWords(name.replace(NAME_QUALIFIER, '')).some(word =>
    labelWords.some(labelWord => labelWord === word
      || (word.length >= LEAK_PREFIX_MIN_LENGTH && labelWord.startsWith(word)))))
}

function waterBody(label) {
  return `${/^Lake /.test(label) ? '' : 'the '}${label}`
}

function geographyClue({ mouthId, landmark, outlet, sourceLandmark, leaksAnswer }) {
  if (mouthId && !leaksAnswer(landmark)) return `Its waters flow into ${waterBody(landmark)}.`
  if (mouthId && outlet && !leaksAnswer(outlet)) return `Its waters reach ${waterBody(outlet)} further downstream.`
  if (sourceLandmark && !leaksAnswer(sourceLandmark)) return `Its source is associated with ${sourceLandmark}.`
  if (!mouthId) return null
  const kind = /estuary|liman/i.test(landmark) ? 'an estuary' : /gulf/i.test(landmark) ? 'a gulf'
    : /bay/i.test(landmark) ? 'a bay' : /lake|reservoir/i.test(landmark) ? 'a lake' : 'a larger river'
  return `Its waters flow into ${kind}.`
}

async function riverCities(qids) {
  const cities = new Map()
  const unique = [...new Set(qids)]
  for (let start = 0; start < unique.length; start += SPARQL_BATCH) {
    const query = `SELECT ?river ?city ?cityLabel (MAX(?population) AS ?pop) (SAMPLE(?coord) AS ?point) WHERE {
      VALUES ?river { ${unique.slice(start, start + SPARQL_BATCH).map(qid => `wd:${qid}`).join(' ')} }
      VALUES ?class { ${CITY_CLASSES.map(qid => `wd:${qid}`).join(' ')} }
      ?city wdt:P206 ?river ; wdt:P1082 ?population ; wdt:P625 ?coord ; wdt:P31/wdt:P279* ?class .
      ?city rdfs:label ?cityLabel . FILTER(LANG(?cityLabel) = "en")
    } GROUP BY ?river ?city ?cityLabel`
    const response = await getJson(`${SPARQL_API}?${new URLSearchParams({ format: 'json', query })}`)
    for (const row of response.results.bindings) {
      const riverId = row.river.value.split('/').pop()
      const [lng, lat] = row.point.value.replace(/^Point\(|\)$/g, '').split(' ').map(Number)
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) continue
      cities.set(riverId, [...(cities.get(riverId) ?? []), {
        id: row.city.value.split('/').pop(), name: row.cityLabel.value, population: Number(row.pop.value), lng, lat,
      }])
    }
  }
  return cities
}

function segmentKm([x, y], [x1, y1], [x2, y2]) {
  const scale = Math.cos(y * Math.PI / 180)
  const dx = (x2 - x1) * scale
  const dy = y2 - y1
  const t = dx || dy ? Math.max(0, Math.min(1, ((x - x1) * scale * dx + (y - y1) * dy) / (dx * dx + dy * dy))) : 0
  return Math.hypot((x - x1) * scale - t * dx, y - y1 - t * dy) * KM_PER_DEGREE
}

function distanceToCourseKm(point, course) {
  return Math.min(...course.coordinates.flatMap(line => line.slice(1).map((end, index) => segmentKm(point, line[index], end))))
}

function placeClue(entity, course, cities, tributaries, leaksAnswer) {
  const factor = 10 ** COURSE_DECIMALS
  const city = (cities.get(entity.id) ?? [])
    .filter(item => item.population >= CITY_MIN_POPULATION && !leaksAnswer(item.name))
    .map(item => ({ ...item, distanceKm: distanceToCourseKm([item.lng, item.lat], course) }))
    .filter(item => item.distanceKm <= CITY_MAX_DISTANCE_KM)
    .sort((a, b) => b.population - a.population)[0]
  if (city) {
    return {
      kind: 'CITY', text: `${city.name} lies on its banks.`, sourceUrl: `https://www.wikidata.org/wiki/${city.id}`,
      place: { name: city.name, lng: Math.round(city.lng * factor) / factor, lat: Math.round(city.lat * factor) / factor },
    }
  }
  const tributary = statements(entity, 'P974')
    .map(statement => tributaries.get(statement.mainsnak.datavalue?.value?.id))
    .filter(item => item?.labels?.en?.value && !leaksAnswer(item.labels.en.value))
    .sort((a, b) => (officialLengthKm(b) ?? 0) - (officialLengthKm(a) ?? 0))[0]
  return tributary
    ? { kind: 'TRIBUTARY', text: `Its tributaries include the ${tributary.labels.en.value}.`, sourceUrl: `https://www.wikidata.org/wiki/${tributary.id}` }
    : null
}

function nameClue(answerName) {
  const core = answerName.replace(/\s*\(.*\)$/, '').replace(/^River\s+/i, '').replace(/\s+River$/i, '').trim()
  const letters = (core.match(/\p{L}/gu) ?? []).length
  const words = core.split(/[\s-]+/).filter(Boolean).length
  const first = core.match(/\p{L}/u)[0].toLocaleUpperCase('en-US')
  return words > 1
    ? `Its name starts with “${first}” and has ${letters} letters in ${words} words.`
    : `Its name starts with “${first}” and has ${letters} letters.`
}

function riverClues({ entity, answerName, lengthKm, countryNames, geographyHint, course, cities, tributaries, leaksAnswer }) {
  const sourceUrl = `https://www.wikidata.org/wiki/${entity.id}`
  const lengthText = `Its reported length is about ${lengthKm.toLocaleString('en-US')} km.`
  const countriesText = `Countries along its course include ${countryNames.join(', ')}.`
  const place = placeClue(entity, course, cities, tributaries, leaksAnswer)
  return {
    hints: [
      { position: 1, kind: 'GEOGRAPHY', text: geographyHint, sourceUrl },
      place ? { position: 2, ...place } : { position: 2, kind: 'COUNTRIES', text: countriesText, sourceUrl },
      { position: 3, kind: 'NAME', text: nameClue(answerName), sourceUrl },
    ],
    facts: [
      { kind: 'LENGTH', text: lengthText },
      { kind: 'COUNTRIES', text: countriesText },
      { kind: 'GEOGRAPHY', text: geographyHint },
      ...(place ? [{ kind: place.kind, text: place.text, ...(place.place ? { place: place.place } : {}) }] : []),
    ],
  }
}

function slug(qid) {
  return qid.toLowerCase()
}

async function pruneStaleAssets(entries) {
  if (requestedCountries.size || OUTPUT !== DEFAULT_OUTPUT) return
  const keep = new Set(entries.flatMap(entry => [entry.plainImageKey, entry.bordersImageKey]))
  const folders = await readdir(OUTPUT, { withFileTypes: true })
  let pruned = 0
  for (const asset of folders.filter(item => item.isFile() && /^[0-9a-f]{64}\.(svg|webp)$/.test(item.name))) {
    const objectKey = `river-game/${asset.name}`
    if (!keep.has(objectKey)) { await unlink(resolve(OUTPUT, asset.name)); pruned++ }
  }
  for (const folder of folders.filter(item => item.isDirectory() && /^[a-z]{2}$/.test(item.name))) {
    const folderPath = resolve(OUTPUT, folder.name)
    for (const asset of await readdir(folderPath, { withFileTypes: true })) {
      if (!asset.isFile() || !/^q[0-9]+-(plain|borders)-[0-9a-f]{64}\.svg$/.test(asset.name)) continue
      const path = resolve(folderPath, asset.name)
      if (!path.startsWith(OUTPUT + sep)) throw new Error('Unsafe asset path')
      const objectKey = `river-game/${folder.name}/${asset.name}`
      if (!keep.has(objectKey)) { await unlink(path); pruned++ }
    }
  }
  console.log(`Pruned ${pruned} stale generated river maps.`)
}

async function main() {
  console.log('Loading pinned Natural Earth river, supplemental river, and border geometry...')
  const [riverGeojson, borderGeojson, ...supplements] = await Promise.all([
    getJson(RIVER_URL), getJson(BORDER_URL), ...SUPPLEMENTAL_RIVER_URLS.map(getJson),
  ])
  const countries = buildCountries(borderGeojson.features)
  const baseQids = new Set(riverGeojson.features
    .filter(feature => /river/i.test(feature.properties.featurecla ?? ''))
    .map(feature => feature.properties.wikidataid))
  const supplementalFeatures = supplements.flatMap(supplement => supplement.features)
    .filter(feature => !baseQids.has(feature.properties.wikidataid))
  const riverFeatures = [...riverGeojson.features, ...supplementalFeatures]
  const rivers = buildRivers(riverFeatures)
  const countryByCode = new Map(countries.map(country => [country.iso2, country]))
  const countryByWikidataId = new Map(countries.filter(country => /^Q[1-9][0-9]*$/.test(country.wikidataId))
    .map(country => [country.wikidataId, country.iso2]))
  const candidates = new Map(countries.map(country => [country.iso2, []]))

  console.log(`${countries.length} countries, ${rivers.length} mapped named rivers; checking fact claims...`)
  const facts = await entities(rivers.map(river => river.qid), 'claims|labels|aliases|sitelinks')
  for (const river of rivers) {
    const claimedCodes = statements(facts.get(river.qid), 'P17')
      .map(statement => countryByWikidataId.get(statement.mainsnak.datavalue?.value?.id))
      .filter(Boolean)
    const consistentLines = linesWithinClaimedCountries(river, facts.get(river.qid), claimedCodes, countryByCode)
    if (consistentLines.length !== river.lines.length) {
      console.log(`Discarded ${river.lines.length - consistentLines.length} geographically inconsistent segments for ${river.qid}`)
      river.lines = consistentLines
      river.mappedLengthKm = mappedLength(consistentLines)
    }
    if (river.mappedLengthKm < 50) continue
    const mappedCodes = [...mappedCountryDistances(river, countries).keys()]
    river.countryCodes = [...new Set(claimedCodes.length ? claimedCodes : mappedCodes)]
    for (const code of river.countryCodes) {
      if (!requestedCountries.size || requestedCountries.has(code)) candidates.get(code)?.push(river)
    }
  }
  const candidateRivers = [...new Map([...candidates.values()].flat()
    .map(river => [river.qid, river])).values()]
  const landmarkIds = candidateRivers.flatMap(river => {
    const entity = facts.get(river.qid)
    return [firstItem(entity, 'P403'), firstItem(entity, 'P885')].filter(Boolean)
  })
  const landmarkLabels = await entities(landmarkIds, 'labels|claims')
  const outletLabels = await entities(candidateRivers
    .map(river => firstItem(landmarkLabels.get(firstItem(facts.get(river.qid), 'P403')), 'P403'))
    .filter(Boolean), 'labels')
  const entries = []
  const missing = []
  const audit = process.argv.includes('--audit')
  let verifiedCount = 0
  let potentialTen = 0
  let potentialFifteen = 0
  const eligibleByCountry = new Map()
  for (const [code, mapped] of candidates) {
    if (requestedCountries.size && !requestedCountries.has(code)) continue
    const eligible = mapped.map(river => {
      const entity = facts.get(river.qid)
      const lengthKm = officialLengthKm(entity)
      const mouthId = firstItem(entity, 'P403')
      const sourceId = firstItem(entity, 'P885')
      const landmarkId = mouthId || sourceId
      const landmark = landmarkLabels.get(landmarkId)?.labels?.en?.value
      const sourceLandmark = landmarkLabels.get(sourceId)?.labels?.en?.value
      const outlet = outletLabels.get(firstItem(landmarkLabels.get(mouthId), 'P403'))?.labels?.en?.value
      return { river, lengthKm, landmark, sourceLandmark, outlet, landmarkId, mouthId,
        playability: playabilityScore(river, lengthKm ?? 0, entity) }
    }).filter(item => {
      const entity = facts.get(item.river.qid)
      if (!item.lengthKm || !item.landmark || !item.river.countryCodes.length || !entity?.labels?.en?.value) return false
      return item.mouthId || !labelLeaksAnswer(item.sourceLandmark, entity.labels.en.value, answerAliases(entity))
    })
      .sort((a, b) => b.playability - a.playability || a.river.name.localeCompare(b.river.name))
    verifiedCount += eligible.length
    potentialTen += Math.min(10, eligible.length)
    potentialFifteen += Math.min(15, eligible.length)
    if (audit && ['US', 'FR', 'DE', 'DZ', 'SA', 'CU', 'DK', 'PA'].includes(code)) {
      console.log(`${code}: ${mapped.length} mapped, ${eligible.length} verified:`,
        eligible.slice(0, 8).map(item => `${item.river.name} (${item.lengthKm} km, ${item.river.scaleRank})`))
    }
    if (eligible.length < 5) missing.push({ code, available: eligible.length, mapped: mapped.length })
    eligibleByCountry.set(code, eligible)
  }

  // Reserve scarce-country rivers first. A well-covered country can trade a
  // cross-border duplicate for another recognisable river, increasing the
  // number of different answers without inventing obscure island questions.
  const selectedByCountry = new Map()
  const usedRivers = new Set()
  const scarcityOrder = [...eligibleByCountry].sort((a, b) => a[1].length - b[1].length
    || a[0].localeCompare(b[0]))
  for (const [code, eligible] of scarcityOrder) {
    const selected = [...eligible].sort((a, b) => {
      const aScore = a.playability - (usedRivers.has(a.river.qid) ? 45 : 0)
      const bScore = b.playability - (usedRivers.has(b.river.qid) ? 45 : 0)
      return bScore - aScore || b.playability - a.playability
    }).slice(0, 5).sort((a, b) => b.lengthKm - a.lengthKm
      || b.playability - a.playability)
    selectedByCountry.set(code, selected)
    selected.forEach(item => usedRivers.add(item.river.qid))
  }

  const selectedQids = [...usedRivers]
  const cities = await riverCities(selectedQids)
  const tributaries = await entities(selectedQids.flatMap(qid => statements(facts.get(qid), 'P974')
    .map(statement => statement.mainsnak.datavalue?.value?.id)).filter(Boolean), 'labels|claims')
  const renderer = dryRun ? null : await createTerrainRenderer({
    riverFeatures, countryFeatures: borderGeojson.features,
    getJson, cacheDir: CACHE,
  })
  const webpRenderer = dryRun ? null : await createRiverWebpRenderer()
  const renderedWebp = new Map()
  try {
  for (const [code, eligible] of selectedByCountry) {

    for (const [index, item] of eligible.entries()) {
      const { river, lengthKm, landmark, sourceLandmark, outlet, mouthId } = item
      const answerName = facts.get(river.qid).labels.en.value
      const aliases = answerAliases(facts.get(river.qid))
      const leaksAnswer = label => labelLeaksAnswer(label, answerName, aliases)
      const geographyHint = geographyClue({ mouthId, landmark, outlet, sourceLandmark, leaksAnswer })
      if (!geographyHint) continue
      const countryNames = river.countryCodes.map(iso2 => countryByCode.get(iso2)?.name)
        .filter(Boolean).sort((a, b) => a.localeCompare(b))
      const key = slug(river.qid)
      if (!dryRun && !renderedWebp.has(key)) {
        const { plain, borders } = await renderer.renderPair(river)
        const maps = {
          plain: await webpRenderer.render(plain),
          borders: await webpRenderer.render(borders),
        }
        const plainFile = `river-game/${sha256(maps.plain)}.webp`
        const bordersFile = `river-game/${sha256(maps.borders)}.webp`
        if (plainFile === bordersFile) throw new Error(`Borders map is identical to the plain map: ${key}`)
        for (const [file, bytes] of [[plainFile, maps.plain], [bordersFile, maps.borders]]) {
          const path = resolve(OUTPUT, file.substring('river-game/'.length))
          await mkdir(resolve(path, '..'), { recursive: true })
          await writeFile(path, bytes)
        }
        renderedWebp.set(key, { plainFile, bordersFile })
      }
      const { plainFile = '', bordersFile = '' } = renderedWebp.get(key) ?? {}
      const course = riverCourse(river.lines)
      const entry = {
        countryCode: code, riverKey: key, nameEn: answerName,
        aliases,
        rankInCountry: index + 1, lengthKm,
        plainImageKey: plainFile, bordersImageKey: bordersFile,
        course,
        geometrySourceUrl: GEOMETRY_SOURCE_URL,
        geometryAttribution: `Natural Earth ${NE_VERSION} vectors and Natural Earth I shaded relief; Wikidata ${river.qid} (CC0 facts)`,
        factsSourceUrl: `https://www.wikidata.org/wiki/${river.qid}`,
        ...riverClues({ entity: facts.get(river.qid), answerName, lengthKm, countryNames, geographyHint, course,
          cities, tributaries, leaksAnswer }),
      }
      entry.contentHash = sha256(JSON.stringify(entry))
      entries.push(entry)
    }
  }

  const manifest = { naturalEarthVersion: NE_VERSION, completeCatalog: requestedCountries.size === 0,
    generatedAt: new Date().toISOString(),
    geometrySourceUrl: GEOMETRY_SOURCE_URL, entries, incompleteCountries: missing }
  if (!dryRun) {
    await mkdir(OUTPUT, { recursive: true })
    await writeFile(resolve(OUTPUT, 'catalog.json'), formatCatalog(manifest))
    await pruneStaleAssets(entries)
  }
  const targetCountries = requestedCountries.size ? countries.filter(country => requestedCountries.has(country.iso2)).length : countries.length
  console.log(`${entries.length} questions / ${usedRivers.size} distinct rivers from ${verifiedCount} verified candidates; ${missing.length}/${targetCountries} target countries have fewer than five verified mapped rivers.`)
  if (audit) console.log(`Potential questions at 10/country: ${potentialTen}; 15/country: ${potentialFifteen}.`)
  console.log('First incomplete countries:', missing.slice(0, 15))
  } finally {
    await webpRenderer?.close()
    await renderer?.close()
  }
}

await main()
