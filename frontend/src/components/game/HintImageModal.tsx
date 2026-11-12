import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  type WheelEvent as ReactWheelEvent,
} from 'react'
import { createPortal } from 'react-dom'
import { Info, Minus, Plus, RotateCcw, X } from 'lucide-react'
import { useTranslation } from '@/i18n'

const PanoramaViewer = lazy(() =>
  import('@/components/game/PanoramaViewer').then(module => ({ default: module.PanoramaViewer }))
)

const MIN_SCALE = 1
const MAX_SCALE = 5
const SCALE_STEP = 0.5

interface HintImageModalProps {
  src: string
  isOpen: boolean
  isPanorama: boolean
  onInfoClick?: () => void
  onClose: () => void
}

export function HintImageModal({ src, isOpen, isPanorama, onInfoClick, onClose }: HintImageModalProps) {
  const { t } = useTranslation()
  const titleId = useId()
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const [failedPanoramaSrc, setFailedPanoramaSrc] = useState<string | null>(null)
  const panoramaFailed = failedPanoramaSrc === src
  const handlePanoramaError = useCallback(() => setFailedPanoramaSrc(src), [src])

  useEffect(() => {
    if (!isOpen) return

    const previousOverflow = document.body.style.overflow
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }

    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', handleKeyDown)
    closeButtonRef.current?.focus()

    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
      previousFocus?.focus()
    }
  }, [isOpen, onClose])

  return createPortal(
    isOpen ? (
      <div
        className="fixed inset-0 z-[100] flex flex-col bg-[#05070d]/95 backdrop-blur-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="flex h-14 shrink-0 items-center justify-between border-b border-white/[.08] px-3 sm:h-16 sm:px-5">
          <h2 id={titleId} className="text-sm font-medium text-white/70 sm:text-base">
            {t.play.imagePreview}
          </h2>
          <div className="flex items-center gap-2">
            {onInfoClick && (
              <button
                type="button"
                onClick={onInfoClick}
                aria-label={t.play.imageCredit}
                title={t.play.imageCredit}
                className="flex h-10 cursor-pointer items-center justify-center gap-2 rounded-xl border border-white/[.08] bg-white/[.04] px-3 text-white/60 transition-colors hover:bg-white/[.1] hover:text-white"
              >
                <Info size={18} />
                <span className="hidden text-xs font-medium sm:inline">{t.play.imageCredit}</span>
              </button>
            )}
            <button
              ref={closeButtonRef}
              type="button"
              onClick={onClose}
              aria-label={t.play.closeImage}
              title={t.play.closeImage}
              className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-xl border border-white/[.08] bg-white/[.04] text-white/60 transition-colors hover:bg-white/[.1] hover:text-white"
            >
              <X size={20} />
            </button>
          </div>
        </div>

        <div className="relative min-h-0 flex-1 overflow-hidden p-2 sm:p-4">
          {isPanorama && !panoramaFailed ? (
            <Suspense fallback={<ViewerLoader />}>
              <PanoramaViewer src={src} variant="modal" onError={handlePanoramaError} />
            </Suspense>
          ) : (
            <ZoomableImage src={src} />
          )}
        </div>
      </div>
    ) : null,
    document.body
  )
}

function ViewerLoader() {
  return (
    <div className="flex h-full items-center justify-center rounded-2xl border border-white/[.08] bg-black/30">
      <div className="h-7 w-7 animate-spin rounded-full border-2 border-accent/30 border-t-accent" />
    </div>
  )
}

function ZoomableImage({ src }: { src: string }) {
  const { t } = useTranslation()
  const [scale, setScale] = useState(MIN_SCALE)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [isDragging, setIsDragging] = useState(false)
  const dragRef = useRef<{
    pointerId: number
    startX: number
    startY: number
    originX: number
    originY: number
  } | null>(null)

  const reset = useCallback(() => {
    setScale(MIN_SCALE)
    setOffset({ x: 0, y: 0 })
  }, [])

  const updateScale = useCallback((nextScale: number) => {
    const clampedScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, nextScale))
    setScale(clampedScale)
    if (clampedScale === MIN_SCALE) {
      setOffset({ x: 0, y: 0 })
    }
  }, [])

  const handleWheel = (event: ReactWheelEvent<HTMLDivElement>) => {
    event.preventDefault()
    updateScale(scale + (event.deltaY < 0 ? SCALE_STEP : -SCALE_STEP))
  }

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (scale === MIN_SCALE || event.button !== 0) return

    event.currentTarget.setPointerCapture(event.pointerId)
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: offset.x,
      originY: offset.y,
    }
    setIsDragging(true)
  }

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current
    if (!drag || drag.pointerId !== event.pointerId) return

    setOffset({
      x: drag.originX + event.clientX - drag.startX,
      y: drag.originY + event.clientY - drag.startY,
    })
  }

  const stopDragging = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (dragRef.current?.pointerId !== event.pointerId) return
    dragRef.current = null
    setIsDragging(false)
  }

  return (
    <div className="relative h-full overflow-hidden rounded-2xl border border-white/[.08] bg-black/45">
      <div
        className={`flex h-full w-full touch-none items-center justify-center ${
          scale > MIN_SCALE ? (isDragging ? 'cursor-grabbing' : 'cursor-grab') : 'cursor-zoom-in'
        }`}
        onWheel={handleWheel}
        onDoubleClick={() => (scale === MIN_SCALE ? updateScale(2) : reset())}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={stopDragging}
        onPointerCancel={stopDragging}
      >
        <img
          src={src}
          alt=""
          draggable={false}
          className="max-h-full max-w-full select-none object-contain"
          style={{
            transform: `translate3d(${offset.x}px, ${offset.y}px, 0) scale(${scale})`,
            transition: isDragging ? 'none' : 'transform 160ms ease-out',
          }}
        />
      </div>

      <div className="absolute bottom-[max(0.75rem,env(safe-area-inset-bottom))] left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-2xl border border-white/[.1] bg-black/65 p-1.5 shadow-2xl backdrop-blur-xl">
        <ZoomButton
          label={t.play.zoomOut}
          disabled={scale === MIN_SCALE}
          onClick={() => updateScale(scale - SCALE_STEP)}
        >
          <Minus size={18} />
        </ZoomButton>
        <span className="min-w-14 text-center text-xs tabular-nums text-white/65">
          {Math.round(scale * 100)}%
        </span>
        <ZoomButton
          label={t.play.zoomIn}
          disabled={scale === MAX_SCALE}
          onClick={() => updateScale(scale + SCALE_STEP)}
        >
          <Plus size={18} />
        </ZoomButton>
        <div className="mx-1 h-5 w-px bg-white/10" />
        <ZoomButton label={t.play.resetZoom} disabled={scale === MIN_SCALE} onClick={reset}>
          <RotateCcw size={16} />
        </ZoomButton>
      </div>
    </div>
  )
}

interface ZoomButtonProps {
  label: string
  disabled: boolean
  onClick: () => void
  children: ReactNode
}

function ZoomButton({ label, disabled, onClick, children }: ZoomButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-xl text-white/70 transition-colors hover:bg-white/10 hover:text-white disabled:cursor-default disabled:opacity-25"
    >
      {children}
    </button>
  )
}
