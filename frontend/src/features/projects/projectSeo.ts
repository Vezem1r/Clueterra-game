import { hubDescription, hubHeading, hubTitle, PROJECT_HUB_ROOT_PATH } from './projectHubs'
import { projectRankingGroups } from './projectRankingGroups'
import type { ProjectDetail, ProjectDirectory, ProjectHubPage, ProjectSsrData } from './projectTypes'

export const PROJECT_DIRECTORY_DESCRIPTION = 'Explore costs, timelines, dimensions and original sources for real infrastructure, energy and space projects.'

export interface ProjectDocumentMeta {
  title: string
  description: string
  keywords: string
  robots: string
  canonicalPath: string | null
  type: 'website' | 'article'
  image?: string | null
  imageAlt?: string | null
  previousPath?: string | null
  nextPath?: string | null
  structuredData: Record<string, unknown> | null
}

export function getProjectDocumentMeta(data: ProjectSsrData, siteOrigin = 'https://clueterra.com'): ProjectDocumentMeta {
  if (data.kind === 'directory') {
    return directoryMeta(data.directory, data.path, siteOrigin)
  }
  if (data.kind === 'detail') {
    return detailMeta(data.detail.project, data.preview, siteOrigin)
  }
  if (data.kind === 'hub') {
    return hubMeta(data.hub, siteOrigin)
  }
  if (data.kind === 'error') {
    return {
      title: 'Projects temporarily unavailable | Clueterra',
      description: 'Clueterra project research is temporarily unavailable.',
      keywords: '',
      robots: 'noindex,nofollow',
      canonicalPath: null,
      type: 'website',
      structuredData: null,
    }
  }
  return {
    title: 'Project not found | Clueterra',
    description: 'The requested Clueterra project page could not be found.',
    keywords: '',
    robots: 'noindex,nofollow',
    canonicalPath: null,
    type: 'website',
    structuredData: null,
  }
}

function directoryMeta(directory: ProjectDirectory, path: string, siteOrigin: string): ProjectDocumentMeta {
  const title = directory.pageNumber === 1
    ? 'Real megaproject costs and sources | Clueterra'
    : `Page ${directory.pageNumber} — Real megaproject costs and sources | Clueterra`
  const description = directory.pageNumber === 1
    ? PROJECT_DIRECTORY_DESCRIPTION
    : `${PROJECT_DIRECTORY_DESCRIPTION} Page ${directory.pageNumber}.`

  return {
    title,
    description,
    keywords: 'megaproject costs, infrastructure project costs, space mission costs, construction cost database, BudgetGuessr',
    robots: 'index,follow',
    canonicalPath: path,
    type: 'website',
    previousPath: directory.pageNumber > 1
      ? (directory.pageNumber === 2 ? '/projects' : `/projects/page/${directory.pageNumber - 1}`)
      : null,
    nextPath: directory.pageNumber < directory.totalPages
      ? `/projects/page/${directory.pageNumber + 1}`
      : null,
    structuredData: directoryStructuredData(directory, path, siteOrigin),
  }
}

function detailMeta(project: ProjectDetail, preview: boolean, siteOrigin: string): ProjectDocumentMeta {
  const title = project.seoTitle || `${project.name} project cost | Clueterra`
  return {
    title: preview ? `Preview — ${title}` : title,
    description: project.metaDescription || `Cost, scope, timeline and source information for ${project.name}.`,
    keywords: project.keywords || '',
    robots: preview ? 'noindex,nofollow' : 'index,follow',
    canonicalPath: preview || !project.slug ? null : `/projects/${project.slug}`,
    type: 'article',
    image: project.image.url,
    imageAlt: project.image.alt,
    structuredData: preview || !project.slug ? null : projectStructuredData(project, siteOrigin),
  }
}

function hubMeta(hub: ProjectHubPage, siteOrigin: string): ProjectDocumentMeta {
  const description = hubDescription(hub)
  return {
    title: hubTitle(hub),
    description,
    keywords: `${hubHeading(hub).toLowerCase()}, megaproject costs, BudgetGuessr`,
    robots: 'index,follow',
    canonicalPath: hub.path,
    type: 'website',
    image: hub.projects[0]?.imageUrl ?? null,
    imageAlt: hub.projects[0]?.imageAlt ?? null,
    structuredData: hubStructuredData(hub, description, siteOrigin),
  }
}

function hubStructuredData(hub: ProjectHubPage, description: string, siteOrigin: string) {
  const canonicalUrl = `${siteOrigin}${hub.path}`
  const heading = hubHeading(hub)
  const breadcrumbs = [
    { name: 'Clueterra', item: `${siteOrigin}/` },
    { name: 'Projects', item: `${siteOrigin}/projects` },
    ...(hub.kind === 'ALL' ? [] : [{ name: 'Most expensive', item: `${siteOrigin}${PROJECT_HUB_ROOT_PATH}` }]),
    { name: heading, item: canonicalUrl },
  ]
  return {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'CollectionPage',
        '@id': canonicalUrl,
        url: canonicalUrl,
        name: heading,
        description,
        isPartOf: { '@type': 'WebSite', name: 'Clueterra', url: `${siteOrigin}/` },
      },
      ...projectRankingGroups(hub).map(group => ({
        '@type': 'ItemList',
        '@id': `${canonicalUrl}#${group.id}`,
        name: group.title,
        numberOfItems: group.projects.length,
        itemListOrder: 'https://schema.org/ItemListOrderDescending',
        itemListElement: group.projects.map((project) => ({
          '@type': 'ListItem',
          position: project.rank,
          name: project.name,
          url: `${siteOrigin}/projects/${project.slug}`,
        })),
      })),
      {
        '@type': 'BreadcrumbList',
        itemListElement: breadcrumbs.map((crumb, index) => ({
          '@type': 'ListItem',
          position: index + 1,
          name: crumb.name,
          item: crumb.item,
        })),
      },
    ],
  }
}

function directoryStructuredData(directory: ProjectDirectory, canonicalPath: string, siteOrigin: string) {
  const canonicalUrl = `${siteOrigin}${canonicalPath}`
  const firstPosition = (directory.pageNumber - 1) * directory.pageSize + 1
  return {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'CollectionPage',
        '@id': canonicalUrl,
        url: canonicalUrl,
        name: 'Real megaproject costs and sources',
        description: PROJECT_DIRECTORY_DESCRIPTION,
        isPartOf: { '@type': 'WebSite', name: 'Clueterra', url: `${siteOrigin}/` },
      },
      {
        '@type': 'ItemList',
        numberOfItems: directory.totalProjects,
        itemListElement: directory.projects.map((project, index) => ({
          '@type': 'ListItem',
          position: firstPosition + index,
          name: project.name,
          url: `${siteOrigin}/projects/${project.slug}`,
        })),
      },
    ],
  }
}

function projectStructuredData(project: ProjectDetail, siteOrigin: string) {
  const canonicalUrl = `${siteOrigin}/projects/${project.slug}`
  return {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'Article',
        '@id': `${canonicalUrl}#article`,
        headline: project.costQuestion,
        description: project.metaDescription,
        url: canonicalUrl,
        mainEntityOfPage: canonicalUrl,
        image: new URL(project.image.url, siteOrigin).toString(),
        dateModified: project.updatedDate,
        author: { '@type': 'Organization', name: 'Clueterra', url: `${siteOrigin}/about` },
        publisher: { '@type': 'Organization', name: 'Clueterra', url: `${siteOrigin}/` },
        citation: project.sources.map((source) => source.url),
        about: { '@type': 'Thing', name: project.name, sameAs: `https://www.wikidata.org/wiki/${project.wikidataId}` },
      },
      {
        '@type': 'BreadcrumbList',
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: 'Clueterra', item: `${siteOrigin}/` },
          { '@type': 'ListItem', position: 2, name: 'Projects', item: `${siteOrigin}/projects` },
          { '@type': 'ListItem', position: 3, name: project.name, item: canonicalUrl },
        ],
      },
    ],
  }
}
