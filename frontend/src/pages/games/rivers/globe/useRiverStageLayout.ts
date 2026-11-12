import { useEffect, useState, type RefObject } from 'react'
import { RIVER_ATTRIBUTION_ZONE_PX } from './riverGlobeConstants'

export interface RiverStageLayout {
  width: number
  height: number
  headerBottom: number
  panelTop: number
  panelLeft: number
  panelBottom: number
}

const INITIAL_LAYOUT: RiverStageLayout = { width: 0, height: 0, headerBottom: 0, panelTop: 0, panelLeft: 0, panelBottom: 0 }

function sameLayout(a: RiverStageLayout, b: RiverStageLayout) {
  return a.width === b.width && a.height === b.height && a.headerBottom === b.headerBottom
    && a.panelTop === b.panelTop && a.panelLeft === b.panelLeft && a.panelBottom === b.panelBottom
}

export function riverChromeRight(layout: RiverStageLayout) {
  return layout.panelBottom > layout.height - RIVER_ATTRIBUTION_ZONE_PX ? Math.max(0, layout.width - layout.panelLeft) : 0
}

export function useRiverStageLayout(
  stageRef: RefObject<HTMLElement | null>,
  headerRef: RefObject<HTMLElement | null>,
  panelRef: RefObject<HTMLElement | null>,
) {
  const [layout, setLayout] = useState(INITIAL_LAYOUT)

  useEffect(() => {
    const measure = () => {
      const stage = stageRef.current?.getBoundingClientRect()
      const header = headerRef.current?.getBoundingClientRect()
      const panel = panelRef.current?.getBoundingClientRect()
      if (!stage || !header) return
      const hasPanel = panel != null && panel.height > 0
      const next = {
        width: Math.round(stage.width),
        height: Math.round(stage.height),
        headerBottom: Math.round(header.bottom - stage.top),
        panelTop: Math.round(hasPanel ? panel.top - stage.top : stage.height),
        panelLeft: Math.round(hasPanel ? panel.left - stage.left : stage.width),
        panelBottom: Math.round(hasPanel ? panel.bottom - stage.top : 0),
      }
      setLayout(previous => sameLayout(previous, next) ? previous : next)
    }
    const observer = new ResizeObserver(measure)
    ;[stageRef.current, headerRef.current, panelRef.current].forEach(element => element && observer.observe(element))
    window.addEventListener('resize', measure)
    measure()
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', measure)
    }
  }, [stageRef, headerRef, panelRef])

  return layout
}
