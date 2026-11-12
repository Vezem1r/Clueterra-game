import { lazy, useState, type ComponentType } from 'react'

import type { PageLoader } from './pageLoaders'

export function lazyPage<P extends object>(load: PageLoader<P>): ComponentType<P> {
  const LazyPage = lazy(load)

  return function PreloadablePage(props: P) {
    const [Page] = useState<ComponentType<P>>(() => load.loaded()?.default ?? LazyPage)
    return <Page {...props} />
  }
}
