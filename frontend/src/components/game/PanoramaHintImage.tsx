import { useCallback, useState, lazy, Suspense } from 'react'

const PanoramaViewer = lazy(() =>
  import('@/components/game/PanoramaViewer').then(m => ({ default: m.PanoramaViewer }))
)

type FallbackState = 'panorama' | 'image' | 'text'

interface PanoramaHintImageProps {
  src: string
  onOpen: () => void
  openLabel: string
}

export function PanoramaHintImage({ src, onOpen, openLabel }: PanoramaHintImageProps) {
  const [state, setState] = useState<FallbackState>('panorama')
  const handleViewerError = useCallback(() => setState('image'), [])

  if (state === 'text') {
    return null
  }

  if (state === 'image') {
    return (
      <button
        type="button"
        onClick={onOpen}
        aria-label={openLabel}
        title={openLabel}
        className="relative mt-3 block h-80 w-full cursor-zoom-in overflow-hidden rounded-xl border border-white/[.08] bg-[radial-gradient(circle_at_center,rgba(124,92,252,.08),rgba(0,0,0,.5))]"
      >
        <img
          src={src}
          alt=""
          className="absolute inset-0 h-full w-full object-contain"
          loading="lazy"
          decoding="async"
          onError={() => setState('text')}
        />
      </button>
    )
  }

  return (
    <Suspense
      fallback={
        <div className="mt-3 flex h-80 items-center justify-center rounded-xl border border-white/[.08] bg-white/[.02]">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-accent/30 border-t-accent" />
        </div>
      }
    >
      <PanoramaViewer
        src={src}
        onError={handleViewerError}
        onOpen={onOpen}
        openLabel={openLabel}
      />
    </Suspense>
  )
}
