import { useCallback, useMemo, useRef, useState, type CSSProperties, type RefObject } from 'react'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import {
  RIVER_CONTROLS_WIDTH_PX,
  RIVER_DESKTOP_QUERY,
  RIVER_MOBILE_ATTRIBUTION_SLOT_PX,
  RIVER_MOBILE_CONTROLS_GAP_PX,
  RIVER_PANEL_GAP_PX,
} from '../rivers/globe/riverGlobeConstants'
import type { RiverPadding } from '../rivers/globe/riverMercator'
import { riverChromeRight, useRiverStageLayout, type RiverStageLayout } from '../rivers/globe/useRiverStageLayout'

export interface GlobeGameStageState {
  desktop: boolean
  stageRef: RefObject<HTMLDivElement | null>
  headerRef: RefObject<HTMLDivElement | null>
  panelRef: RefObject<HTMLDivElement | null>
  layout: RiverStageLayout
  padding: RiverPadding
  chromeOffset: number
  chromeRight: number
  controlsStyle: CSSProperties | undefined
  collapsed: boolean
  collapse: () => void
  expand: () => void
}

export function useGlobeGameStage(): GlobeGameStageState {
  const desktop = useMediaQuery(RIVER_DESKTOP_QUERY, true)
  const stageRef = useRef<HTMLDivElement>(null)
  const headerRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const layout = useRiverStageLayout(stageRef, headerRef, panelRef)
  const [collapsedOnPhone, setCollapsedOnPhone] = useState(false)
  const sheetHeight = desktop ? 0 : Math.max(0, layout.height - layout.panelTop)

  const padding = useMemo<RiverPadding>(() => {
    if (desktop) {
      return {
        top: layout.headerBottom + RIVER_PANEL_GAP_PX,
        right: Math.max(RIVER_PANEL_GAP_PX, layout.width - layout.panelLeft + RIVER_PANEL_GAP_PX),
        bottom: RIVER_PANEL_GAP_PX * 2,
        left: RIVER_CONTROLS_WIDTH_PX + RIVER_PANEL_GAP_PX,
      }
    }
    return {
      top: layout.headerBottom + RIVER_MOBILE_CONTROLS_GAP_PX,
      right: RIVER_PANEL_GAP_PX,
      bottom: sheetHeight + RIVER_PANEL_GAP_PX,
      left: RIVER_PANEL_GAP_PX,
    }
  }, [layout, desktop, sheetHeight])

  const collapse = useCallback(() => {
    if (desktop) return
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
    setCollapsedOnPhone(true)
  }, [desktop])
  const expand = useCallback(() => setCollapsedOnPhone(false), [])
  const chromeOffset = sheetHeight + RIVER_MOBILE_CONTROLS_GAP_PX

  return {
    desktop,
    stageRef,
    headerRef,
    panelRef,
    layout,
    padding,
    chromeOffset,
    chromeRight: desktop ? riverChromeRight(layout) : 0,
    controlsStyle: desktop ? undefined : { bottom: chromeOffset, right: RIVER_MOBILE_ATTRIBUTION_SLOT_PX },
    collapsed: collapsedOnPhone && !desktop,
    collapse,
    expand,
  }
}
