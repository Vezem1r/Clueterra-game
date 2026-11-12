import type { ProjectCategory, ProjectHubPage } from './projectTypes'

export const PROJECT_HUB_ROOT_PATH = '/projects/most-expensive'
export const COUNTRY_RANKINGS_ANCHOR = 'by-country'
export const COUNTRY_RANKINGS_PATH = `${PROJECT_HUB_ROOT_PATH}#${COUNTRY_RANKINGS_ANCHOR}`

const HUB_DESCRIPTION_LIMIT = 158
const COUNTRIES_WITH_ARTICLE = new Set(['US', 'GB', 'AE', 'PH', 'NL', 'CZ', 'BS', 'DO', 'CF', 'GM', 'MV', 'KM'])

export const PROJECT_HUB_CATEGORIES: Record<ProjectCategory, { slug: string; label: string; noun: string }> = {
  METRO_RAIL: { slug: 'railways-and-metros', label: 'Railways & metros', noun: 'railway and metro projects' },
  ROAD_HIGHWAY: { slug: 'roads-and-highways', label: 'Roads & highways', noun: 'road and highway projects' },
  BRIDGE: { slug: 'bridges', label: 'Bridges', noun: 'bridges' },
  ENERGY: { slug: 'energy-projects', label: 'Energy', noun: 'energy projects' },
  SKYSCRAPER: { slug: 'skyscrapers', label: 'Skyscrapers', noun: 'skyscrapers' },
  STADIUM: { slug: 'stadiums', label: 'Stadiums', noun: 'stadiums' },
  AIRPORT: { slug: 'airports', label: 'Airports', noun: 'airports' },
  SPACE: { slug: 'space-missions', label: 'Space missions', noun: 'space missions' },
  TUNNEL: { slug: 'tunnels', label: 'Tunnels', noun: 'tunnels' },
  DAM: { slug: 'dams', label: 'Dams', noun: 'dams' },
  PORT: { slug: 'ports', label: 'Ports', noun: 'ports' },
}

export function categoryHubPath(category: ProjectCategory) {
  return `${PROJECT_HUB_ROOT_PATH}/${PROJECT_HUB_CATEGORIES[category].slug}`
}

export function countryPhrase(countryName: string, countryCode: string | null) {
  return countryCode && COUNTRIES_WITH_ARTICLE.has(countryCode) ? `the ${countryName}` : countryName
}

export function hubSubject(hub: ProjectHubPage) {
  if (hub.kind === 'CATEGORY' && hub.category) return PROJECT_HUB_CATEGORIES[hub.category].noun
  return 'megaprojects'
}

export function hubHeading(hub: ProjectHubPage) {
  if (hub.kind === 'CATEGORY' && hub.category === 'AIRPORT') return 'Most expensive airports'
  if (hub.kind === 'COUNTRY' && hub.countryName) {
    return `The most expensive megaprojects from ${countryPhrase(hub.countryName, hub.countryCode)} in our catalogue`
  }
  return `The most expensive ${hubSubject(hub)} in our catalogue`
}

const LOWERCASE_TITLE_WORDS = new Set(['and', 'of', 'in', 'the'])

function titleCase(phrase: string) {
  return phrase
    .split(' ')
    .map((word) => (LOWERCASE_TITLE_WORDS.has(word) ? word : word.charAt(0).toUpperCase() + word.slice(1)))
    .join(' ')
}

export function hubTitle(hub: ProjectHubPage) {
  if (hub.kind === 'COUNTRY' && hub.countryName) {
    return `The Most Expensive Megaprojects from ${countryPhrase(hub.countryName, hub.countryCode)} in Our Catalogue | Clueterra`
  }
  return `The Most Expensive ${titleCase(hubSubject(hub))} in Our Catalogue | Clueterra`
}

export function hubDescription(hub: ProjectHubPage) {
  if (hub.kind === 'CATEGORY' && hub.category === 'AIRPORT') {
    return 'Compare high-cost airports, terminal expansions and airport rail links in separate lists, with original amounts, cost scopes and sources.'
  }
  const top = hub.projects[0]
  const place = hub.kind === 'COUNTRY' && hub.countryName
    ? ` from ${countryPhrase(hub.countryName, hub.countryCode)}`
    : ''
  const base = `Explore ${hub.projects.length} high-cost ${hubSubject(hub)}${place} in our catalogue, with documented US-dollar amounts and sources.`
  const withTop = top ? `${base} #1: ${top.name}, ${top.costUsd}.` : base
  return withTop.length <= HUB_DESCRIPTION_LIMIT ? withTop : base
}
