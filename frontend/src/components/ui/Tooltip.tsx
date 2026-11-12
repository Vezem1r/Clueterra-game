import {
  useState,
  useRef,
  useCallback,
  useEffect,
  useLayoutEffect,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react'
import { createPortal } from 'react-dom'
import { motion, AnimatePresence } from 'framer-motion'

type TooltipPlacement = 'top' | 'bottom' | 'left' | 'right'

interface TooltipProps {
  content: ReactNode
  children: ReactNode
  placement?: TooltipPlacement
  className?: string
  contentClassName?: string
  offset?: number
  maxWidth?: number
  disableTouch?: boolean
}

const TOOLTIP_HEIGHT_ESTIMATE = 120
const VIEWPORT_MARGIN = 12
const TOOLTIP_OPEN_EVENT = 'clueterra:tooltip-open'
const MIN_TOOLTIP_WIDTH = 160
const ARROW_INSET = 16
const POINTER_FOCUS_WINDOW_MS = 600

function clamp(value: number, min: number, max: number) {
  if (max < min) return min
  return Math.max(min, Math.min(value, max))
}

function getViewportBounds() {
  const visualViewport = window.visualViewport
  const left = visualViewport?.offsetLeft ?? 0
  const top = visualViewport?.offsetTop ?? 0
  const width = visualViewport?.width ?? document.documentElement.clientWidth
  const height = visualViewport?.height ?? window.innerHeight

  return {
    left,
    top,
    right: left + width,
    bottom: top + height,
    width,
    height,
  }
}

export function Tooltip({
  content,
  children,
  placement = 'top',
  className,
  contentClassName,
  offset = 20,
  maxWidth = 248,
  disableTouch = false,
}: TooltipProps) {
  const [visible, setVisible] = useState(false)
  const [pos, setPos] = useState({
    x: 0,
    y: 0,
    actualPlacement: placement,
    arrowLeft: 0,
    arrowTop: 0,
    width: maxWidth,
    maxHeight: TOOLTIP_HEIGHT_ESTIMATE,
  })
  const triggerRef = useRef<HTMLDivElement>(null)
  const tooltipRef = useRef<HTMLDivElement>(null)
  const idRef = useRef(Symbol('tooltip'))
  const isTouchRef = useRef(false)
  const pointerDownAtRef = useRef(Number.NEGATIVE_INFINITY)

  const computePosition = useCallback((measuredHeight = TOOLTIP_HEIGHT_ESTIMATE) => {
    if (!triggerRef.current) return null
    const rect = triggerRef.current.getBoundingClientRect()
    const viewport = getViewportBounds()
    const triggerCenterX = rect.left + rect.width / 2
    const availableWidth = Math.max(0, viewport.width - VIEWPORT_MARGIN * 2)
    const requestedWidth = viewport.width <= 480 ? Math.max(maxWidth, availableWidth) : maxWidth
    const tooltipWidth = Math.min(requestedWidth, Math.max(MIN_TOOLTIP_WIDTH, availableWidth))
    if (placement === 'left' || placement === 'right') {
      const sideWidth = Math.min(maxWidth, availableWidth)
      const rightSpace = viewport.right - VIEWPORT_MARGIN - rect.right - offset
      const leftSpace = rect.left - viewport.left - VIEWPORT_MARGIN - offset
      const side: TooltipPlacement = placement === 'right'
        ? rightSpace < sideWidth && leftSpace > rightSpace ? 'left' : 'right'
        : leftSpace < sideWidth && rightSpace > leftSpace ? 'right' : 'left'
      const sideHeight = Math.min(measuredHeight, viewport.height - VIEWPORT_MARGIN * 2)
      const triggerCenterY = rect.top + rect.height / 2
      const sideY = clamp(triggerCenterY - sideHeight / 2, viewport.top + VIEWPORT_MARGIN,
        viewport.bottom - VIEWPORT_MARGIN - sideHeight)
      const sideX = side === 'right' ? rect.right + offset : rect.left - offset - sideWidth
      return {
        x: clamp(sideX, viewport.left + VIEWPORT_MARGIN, viewport.right - VIEWPORT_MARGIN - sideWidth),
        y: sideY,
        actualPlacement: side,
        arrowLeft: 0,
        arrowTop: clamp(triggerCenterY - sideY, ARROW_INSET, sideHeight - ARROW_INSET),
        width: sideWidth,
        maxHeight: viewport.height - VIEWPORT_MARGIN * 2,
      }
    }
    const topSpace = Math.max(0, rect.top - viewport.top - VIEWPORT_MARGIN - offset)
    const bottomSpace = Math.max(0, viewport.bottom - VIEWPORT_MARGIN - rect.bottom - offset)

    let actualPlacement: TooltipPlacement = placement
    if (placement === 'top' && topSpace < measuredHeight && bottomSpace > topSpace) {
      actualPlacement = 'bottom'
    } else if (placement === 'bottom' && bottomSpace < measuredHeight && topSpace > bottomSpace) {
      actualPlacement = 'top'
    }

    const availableHeight = actualPlacement === 'top' ? topSpace : bottomSpace
    const tooltipHeight = Math.min(measuredHeight, availableHeight)
    const rawY = actualPlacement === 'top'
      ? rect.top - offset - tooltipHeight
      : rect.bottom + offset
    const y = clamp(
      rawY,
      viewport.top + VIEWPORT_MARGIN,
      viewport.bottom - VIEWPORT_MARGIN - tooltipHeight
    )
    const minLeft = viewport.left + VIEWPORT_MARGIN
    const maxLeft = viewport.right - VIEWPORT_MARGIN - tooltipWidth
    const left = clamp(triggerCenterX - tooltipWidth / 2, minLeft, maxLeft)
    const arrowLeft = clamp(triggerCenterX - left, ARROW_INSET, tooltipWidth - ARROW_INSET)

    return { x: left, y, actualPlacement, arrowLeft, arrowTop: 0, width: tooltipWidth, maxHeight: availableHeight }
  }, [offset, placement, maxWidth])

  const updatePosition = useCallback(() => {
    const measuredHeight = tooltipRef.current?.scrollHeight ?? TOOLTIP_HEIGHT_ESTIMATE
    const p = computePosition(measuredHeight)
    if (p) setPos(p)
  }, [computePosition])

  const show = useCallback(() => {
    const p = computePosition()
    if (!p) return
    window.dispatchEvent(new CustomEvent<symbol>(TOOLTIP_OPEN_EVENT, { detail: idRef.current }))
    setPos(p)
    setVisible(true)
  }, [computePosition])

  const hide = useCallback(() => setVisible(false), [])

  const handleFocus = useCallback(() => {
    if (performance.now() - pointerDownAtRef.current < POINTER_FOCUS_WINDOW_MS) return
    show()
  }, [show])

  const handlePointerDownCapture = useCallback(() => {
    pointerDownAtRef.current = performance.now()
  }, [])

  const handleMouseEnter = useCallback(() => {
    if (isTouchRef.current) return
    show()
  }, [show])

  const handleMouseLeave = useCallback(() => {
    if (isTouchRef.current) return
    hide()
  }, [hide])

  const handlePointerDown = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    isTouchRef.current = event.pointerType !== 'mouse'
    if (disableTouch || !isTouchRef.current) return

    event.preventDefault()
    if (visible) hide()
    else show()
  }, [disableTouch, visible, show, hide])

  useEffect(() => {
    if (!visible || !isTouchRef.current) return
    const dismiss = (e: globalThis.PointerEvent) => {
      const target = e.target as Node
      if (
        triggerRef.current
        && !triggerRef.current.contains(target)
        && !tooltipRef.current?.contains(target)
      ) {
        hide()
      }
    }
    document.addEventListener('pointerdown', dismiss)
    return () => document.removeEventListener('pointerdown', dismiss)
  }, [visible, hide])

  useEffect(() => {
    const closeOtherTooltip = (event: Event) => {
      if ((event as CustomEvent<symbol>).detail !== idRef.current) {
        hide()
      }
    }

    window.addEventListener(TOOLTIP_OPEN_EVENT, closeOtherTooltip)
    return () => window.removeEventListener(TOOLTIP_OPEN_EVENT, closeOtherTooltip)
  }, [hide])

  useLayoutEffect(() => {
    if (visible) updatePosition()
  }, [visible, content, updatePosition])

  useEffect(() => {
    if (!visible) return
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    window.visualViewport?.addEventListener('resize', updatePosition)
    window.visualViewport?.addEventListener('scroll', updatePosition)

    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
      window.visualViewport?.removeEventListener('resize', updatePosition)
      window.visualViewport?.removeEventListener('scroll', updatePosition)
    }
  }, [visible, updatePosition])

  const arrowStyle = { left: pos.arrowLeft }
  const sideArrowStyle = { top: pos.arrowTop }

  return (
    <>
      <div
        ref={triggerRef}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        onPointerDown={handlePointerDown}
        onPointerDownCapture={handlePointerDownCapture}
        onFocus={handleFocus}
        onBlur={hide}
        className={className}
      >
        {children}
      </div>
      {typeof document !== 'undefined' && createPortal(
        <AnimatePresence>
          {visible && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              transition={{ duration: 0.15 }}
              className="fixed z-[100] pointer-events-none"
              style={{
                left: pos.x,
                top: pos.y,
              }}
            >
              <div
                ref={tooltipRef}
                role="tooltip"
                className={`pointer-events-auto relative overflow-x-hidden overflow-y-auto overscroll-contain break-words rounded-[1.1rem] border border-white/[.1] bg-[linear-gradient(180deg,rgba(20,28,44,.76),rgba(7,12,22,.9))] px-3.5 py-3 text-center text-xs whitespace-pre-line text-white/84 shadow-[0_24px_56px_rgba(0,0,0,.34)] [overflow-wrap:anywhere] backdrop-blur-2xl ${contentClassName ?? ''}`}
                style={{
                  width: pos.width,
                  maxWidth: `calc(100vw - ${VIEWPORT_MARGIN * 2}px)`,
                  maxHeight: pos.maxHeight,
                }}
              >
                <div className="pointer-events-none absolute inset-x-8 -top-8 h-16 rounded-full bg-[radial-gradient(circle,rgba(125,211,252,.16),transparent_70%)] blur-2xl" />
                <div className="pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent via-white/18 to-transparent" />
                {content}
                {pos.actualPlacement === 'top' && (
                  <div
                    className="pointer-events-none absolute top-full h-3 w-3 -translate-x-1/2 -translate-y-1/2 rotate-45 border-r border-b border-white/[.08] bg-[rgba(8,13,24,.88)] backdrop-blur-2xl"
                    style={arrowStyle}
                  />
                )}
                {pos.actualPlacement === 'right' && (
                  <div
                    className="pointer-events-none absolute right-full h-3 w-3 translate-x-1/2 -translate-y-1/2 rotate-45 border-b border-l border-white/[.08] bg-[rgba(8,13,24,.88)] backdrop-blur-2xl"
                    style={sideArrowStyle}
                  />
                )}
                {pos.actualPlacement === 'left' && (
                  <div
                    className="pointer-events-none absolute left-full h-3 w-3 -translate-x-1/2 -translate-y-1/2 rotate-45 border-r border-t border-white/[.08] bg-[rgba(8,13,24,.88)] backdrop-blur-2xl"
                    style={sideArrowStyle}
                  />
                )}
                {pos.actualPlacement === 'bottom' && (
                  <div
                    className="pointer-events-none absolute bottom-full h-3 w-3 -translate-x-1/2 translate-y-1/2 rotate-45 border-l border-t border-white/[.08] bg-[rgba(8,13,24,.88)] backdrop-blur-2xl"
                    style={arrowStyle}
                  />
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>,
        document.body
      )}
    </>
  )
}
