const RESPONSIVE_IMAGE_PATH = /^\/api\/assets\/budget\/(?:variants|projects)\//

function responsiveImageUrl(url: string) {
  try {
    const parsed = new URL(url, 'https://clueterra.com')
    return RESPONSIVE_IMAGE_PATH.test(parsed.pathname) ? parsed : null
  } catch {
    return null
  }
}

export function budgetImageVariantUrl(url: string, maximumDimension: 480 | 960 | 1600) {
  const parsed = responsiveImageUrl(url)
  if (!parsed) return url
  parsed.searchParams.set('width', String(maximumDimension))
  return url.startsWith('/') ? `${parsed.pathname}${parsed.search}${parsed.hash}` : parsed.href
}

export function budgetImageSrcSet(url: string, maximumDimension: 480 | 960 | 1600 = 1600) {
  if (!responsiveImageUrl(url)) return undefined
  return [480, 960, 1600]
    .filter(width => width <= maximumDimension)
    .map(width => `${budgetImageVariantUrl(url, width as 480 | 960 | 1600)} ${width}w`)
    .join(', ')
}
