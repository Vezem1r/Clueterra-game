import { useRef, useEffect, useState, useCallback } from 'react'
import { Viewer } from '@photo-sphere-viewer/core'
import { Maximize2 } from 'lucide-react'
import '@photo-sphere-viewer/core/index.css'

interface PanoramaViewerProps {
  src: string
  onError?: () => void
  onOpen?: () => void
  openLabel?: string
  variant?: 'card' | 'modal'
}

const LOAD_TIMEOUT_MS = 10000

export function PanoramaViewer({
  src,
  onError,
  onOpen,
  openLabel = 'Open image',
  variant = 'card',
}: PanoramaViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const viewerRef = useRef<Viewer | null>(null)
  const [loading, setLoading] = useState(true)

  const handleError = useCallback(() => {
    onError?.()
  }, [onError])

  useEffect(() => {
    if (!containerRef.current) return

    let cancelled = false

    const failLoading = () => {
      clearTimeout(timeout)
      if (cancelled) return
      setLoading(false)
      handleError()
    }

    function init() {
      try {
        const viewer = new Viewer({
          container: containerRef.current!,
          panorama: src,
          defaultYaw: 0,
          defaultPitch: 0,
          defaultZoomLvl: 50,
          navbar: false,
          touchmoveTwoFingers: false,
          mousewheelCtrlKey: false,
        })

        viewer.addEventListener('ready', () => {
          if (cancelled) return
          clearTimeout(timeout)
          setLoading(false)
        })

        viewer.addEventListener('panorama-error', () => {
          failLoading()
        })

        viewerRef.current = viewer
      } catch {
        failLoading()
      }
    }

    const timeout = setTimeout(() => {
      failLoading()
    }, LOAD_TIMEOUT_MS)

    init()

    return () => {
      cancelled = true
      clearTimeout(timeout)
      if (viewerRef.current) {
        viewerRef.current.destroy()
        viewerRef.current = null
      }
    }
  }, [src, handleError])

  return (
    <div className={variant === 'modal'
      ? 'relative h-full overflow-hidden rounded-2xl border border-white/[.08]'
      : 'relative mt-3 overflow-hidden rounded-xl border border-white/[.08]'}>
      {loading && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-black/60">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-accent/30 border-t-accent" />
        </div>
      )}
      <div
        ref={containerRef}
        className="psv-clueterra h-full w-full"
        style={{ height: variant === 'modal' ? '100%' : '20rem' }}
      />
      {onOpen && variant === 'card' && (
        <button
          type="button"
          onClick={onOpen}
          aria-label={openLabel}
          title={openLabel}
          className="absolute right-2.5 top-2.5 z-20 flex h-9 w-9 cursor-zoom-in items-center justify-center rounded-xl border border-white/10 bg-black/55 text-white/70 shadow-lg backdrop-blur-md transition-colors hover:bg-black/75 hover:text-white"
        >
          <Maximize2 size={17} />
        </button>
      )}
      <span className="absolute bottom-1.5 right-2 z-20 text-[10px] text-white/35">
        Mapillary imagery
      </span>
    </div>
  )
}
