import { StrictMode } from 'react'
import { PassThrough } from 'node:stream'
import { renderToPipeableStream } from 'react-dom/server'
import { StaticRouter } from 'react-router'

import { AppContents, AppRuntime } from './App'
import { createAppQueryClient } from './lib/queryClient'
import type { ProjectSsrData } from './features/projects/projectTypes'
export { getProjectDocumentMeta } from './features/projects/projectSeo'

const RENDER_TIMEOUT_MS = 15_000

import { registerTranslations } from './i18n'
import { cs } from './i18n/cs'
import { ru } from './i18n/ru'

registerTranslations('ru', ru)
registerTranslations('cs', cs)

export { getTranslation } from './i18n'
export { LOCALIZED_PATHS as localizedPaths, PREFIXED_LANGUAGES as prefixedLanguages } from './lib/locale/localePaths'

export function render(url: string, initialProjectData: ProjectSsrData | null = null): Promise<string> {
  const queryClient = createAppQueryClient()

  return new Promise<string>((resolve, reject) => {
    let settled = false
    let renderError: unknown = null

    const timeout = setTimeout(() => {
      if (settled) return
      settled = true
      abort()
      reject(new Error(`Rendering ${url} exceeded ${RENDER_TIMEOUT_MS}ms`))
    }, RENDER_TIMEOUT_MS)

    const { pipe, abort } = renderToPipeableStream(
      <StrictMode>
        <AppRuntime
          initialProjectData={initialProjectData}
          queryClient={queryClient}
          router={
            <StaticRouter location={url}>
              <AppContents />
            </StaticRouter>
          }
        />
      </StrictMode>,
      {
        onAllReady() {
          if (settled) return

          const output = new PassThrough()
          const chunks: Buffer[] = []

          output.on('data', (chunk: Buffer) => chunks.push(chunk))
          output.on('error', (error) => {
            if (settled) return
            settled = true
            clearTimeout(timeout)
            reject(error)
          })
          output.on('end', () => {
            if (settled) return
            settled = true
            clearTimeout(timeout)

            if (renderError) {
              reject(renderError)
              return
            }

            resolve(Buffer.concat(chunks).toString('utf8'))
          })

          pipe(output)
        },
        onShellError(error) {
          if (settled) return
          settled = true
          clearTimeout(timeout)
          reject(error)
        },
        onError(error) {
          renderError = error
        },
      },
    )
  }).finally(() => {
    queryClient.clear()
  })
}
