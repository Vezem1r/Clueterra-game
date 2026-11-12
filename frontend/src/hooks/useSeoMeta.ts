import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { SITE_ORIGIN } from '@/constants/site'
import {
  DEFAULT_LANGUAGE,
  isLocalizedPath,
  languageOfPath,
  localizedPath,
  SEO_LANGUAGES,
  X_DEFAULT,
} from '@/lib/locale/localePaths'

interface SeoMetaOptions {
  title: string
  description: string
  keywords?: string
  canonicalPath?: string | null
  ogTitle?: string
  ogDescription?: string
  robots?: string
  image?: string
  imageAlt?: string
  ogType?: 'website' | 'article'
  structuredData?: Record<string, unknown> | Record<string, unknown>[]
}

const ALTERNATE_MARKER = 'data-seo-alternate'
const PRERENDERED_ALTERNATES = `link[rel="alternate"][hreflang]:not([${ALTERNATE_MARKER}])`

interface SnapshotEntry {
  element: HTMLMetaElement | HTMLLinkElement
  previousContent: string | null
  attribute: 'content' | 'href'
}

interface DetachedElementEntry {
  element: Element
  nextSibling: ChildNode | null
  parent: Node
}

function ensureMetaByName(name: string): HTMLMetaElement {
  let element = document.head.querySelector(`meta[name="${name}"]`) as HTMLMetaElement | null
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute('name', name)
    document.head.appendChild(element)
  }
  return element
}

function ensureMetaByProperty(property: string): HTMLMetaElement {
  let element = document.head.querySelector(`meta[property="${property}"]`) as HTMLMetaElement | null
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute('property', property)
    document.head.appendChild(element)
  }
  return element
}

function ensureCanonicalLink(): HTMLLinkElement {
  let element = document.head.querySelector('link[rel="canonical"]') as HTMLLinkElement | null
  if (!element) {
    element = document.createElement('link')
    element.setAttribute('rel', 'canonical')
    document.head.appendChild(element)
  }
  return element
}

function appendAlternates(path: string): HTMLLinkElement[] {
  const variants: Array<[string, string]> = [
    ...SEO_LANGUAGES.map(language => [language, localizedPath(path, language)] as [string, string]),
    [X_DEFAULT, localizedPath(path, DEFAULT_LANGUAGE)],
  ]
  return variants.map(([hreflang, href]) => {
    const element = document.createElement('link')
    element.setAttribute('rel', 'alternate')
    element.setAttribute('hreflang', hreflang)
    element.setAttribute('href', new URL(href, SITE_ORIGIN).toString())
    element.setAttribute(ALTERNATE_MARKER, '')
    document.head.appendChild(element)
    return element
  })
}

function setSnapshot(
  snapshots: SnapshotEntry[],
  element: HTMLMetaElement | HTMLLinkElement,
  attribute: 'content' | 'href',
  value: string
) {
  snapshots.push({
    element,
    attribute,
    previousContent: element.getAttribute(attribute),
  })
  element.setAttribute(attribute, value)
}

function detachElement(detachedElements: DetachedElementEntry[], element: Element | null) {
  if (!element?.parentNode) return

  detachedElements.push({
    element,
    nextSibling: element.nextSibling,
    parent: element.parentNode,
  })
  element.remove()
}

export function useSeoMeta({
  title,
  description,
  keywords,
  canonicalPath = '/',
  ogTitle,
  ogDescription,
  robots = 'index,follow',
  image,
  imageAlt,
  ogType = 'website',
  structuredData,
}: SeoMetaOptions) {
  const pageLanguage = languageOfPath(useLocation().pathname)

  useEffect(() => {
    const previousTitle = document.title
    const previousLanguage = document.documentElement.lang
    const snapshots: SnapshotEntry[] = []
    const detachedElements: DetachedElementEntry[] = []
    const localized = canonicalPath != null && isLocalizedPath(canonicalPath)
    const pagePath = canonicalPath && pageLanguage && localized ? localizedPath(canonicalPath, pageLanguage) : canonicalPath
    const canonicalUrl = pagePath ? new URL(pagePath, SITE_ORIGIN).toString() : null
    document.head.querySelectorAll(PRERENDERED_ALTERNATES).forEach(element => detachElement(detachedElements, element))
    const alternates = localized ? appendAlternates(canonicalPath) : []
    if (pageLanguage) document.documentElement.lang = pageLanguage

    document.title = title

    setSnapshot(snapshots, ensureMetaByName('description'), 'content', description)
    setSnapshot(snapshots, ensureMetaByName('robots'), 'content', robots)
    setSnapshot(snapshots, ensureMetaByName('twitter:card'), 'content', 'summary_large_image')
    setSnapshot(snapshots, ensureMetaByName('twitter:title'), 'content', ogTitle ?? title)
    setSnapshot(snapshots, ensureMetaByName('twitter:description'), 'content', ogDescription ?? description)
    setSnapshot(snapshots, ensureMetaByProperty('og:title'), 'content', ogTitle ?? title)
    setSnapshot(snapshots, ensureMetaByProperty('og:description'), 'content', ogDescription ?? description)
    setSnapshot(snapshots, ensureMetaByProperty('og:type'), 'content', ogType)

    if (image) {
      const absoluteImage = new URL(image, SITE_ORIGIN).toString()
      setSnapshot(snapshots, ensureMetaByProperty('og:image'), 'content', absoluteImage)
      setSnapshot(snapshots, ensureMetaByName('twitter:image'), 'content', absoluteImage)
      if (imageAlt) {
        setSnapshot(snapshots, ensureMetaByProperty('og:image:alt'), 'content', imageAlt)
      }
    } else {
      detachElement(detachedElements, document.head.querySelector('meta[property="og:image"]'))
      detachElement(detachedElements, document.head.querySelector('meta[property="og:image:alt"]'))
      detachElement(detachedElements, document.head.querySelector('meta[name="twitter:image"]'))
    }

    if (canonicalUrl) {
      setSnapshot(snapshots, ensureMetaByProperty('og:url'), 'content', canonicalUrl)
      setSnapshot(snapshots, ensureCanonicalLink(), 'href', canonicalUrl)
    } else {
      detachElement(detachedElements, document.head.querySelector('meta[property="og:url"]'))
      detachElement(detachedElements, document.head.querySelector('link[rel="canonical"]'))
    }

    if (keywords) {
      setSnapshot(snapshots, ensureMetaByName('keywords'), 'content', keywords)
    }

    let structuredDataScript: HTMLScriptElement | null = null
    if (structuredData) {
      structuredDataScript = document.head.querySelector('#dynamic-structured-data') as HTMLScriptElement | null
      if (!structuredDataScript) {
        structuredDataScript = document.createElement('script')
        structuredDataScript.id = 'dynamic-structured-data'
        structuredDataScript.type = 'application/ld+json'
        document.head.appendChild(structuredDataScript)
      }
      structuredDataScript.textContent = JSON.stringify(structuredData)
    }

    return () => {
      document.title = previousTitle
      document.documentElement.lang = previousLanguage
      alternates.forEach(element => element.remove())
      snapshots.reverse().forEach(({ element, attribute, previousContent }) => {
        if (previousContent === null) {
          element.removeAttribute(attribute)
          return
        }
        element.setAttribute(attribute, previousContent)
      })
      detachedElements.reverse().forEach(({ element, nextSibling, parent }) => {
        if (element.isConnected) return
        parent.insertBefore(element, nextSibling?.parentNode === parent ? nextSibling : null)
      })
      if (structuredDataScript) {
        structuredDataScript.remove()
      }
    }
  }, [canonicalPath, description, image, imageAlt, keywords, ogDescription, ogTitle, ogType, pageLanguage, robots, structuredData, title])
}
