import { useState, useMemo, useCallback, useRef } from 'react'

export interface ColumnDef {
  key: string
  label: string
  sortKey?: string
  defaultVisible: boolean
  defaultWidth: number
}

export interface UseAdminTableOptions {
  storageKey: string
  columns: ColumnDef[]
  defaultSort: string
  defaultSortDir?: 'asc' | 'desc'
  pinnedColumns?: string[]
  onSortChange?: () => void
}

const MIN_COL_WIDTH = 40
const GRID_H_PADDING = 24

function loadFromStorage<T>(key: string, fallback: T): T {
  try {
    const saved = localStorage.getItem(key)
    if (saved) return JSON.parse(saved) as T
  } catch { /* ignore */ }
  return fallback
}

export function useAdminTable(options: UseAdminTableOptions) {
  const {
    storageKey,
    columns,
    defaultSort,
    defaultSortDir = 'asc',
    pinnedColumns = ['actions'],
    onSortChange,
  } = options

  const colsKey = `${storageKey}-columns`
  const widthsKey = `${storageKey}-colwidths`
  const pinnedSet = useMemo(() => new Set(pinnedColumns), [pinnedColumns])

  const [visibleCols, setVisibleCols] = useState<Set<string>>(
    () => loadFromStorage<string[]>(colsKey, []).length > 0
      ? new Set(loadFromStorage<string[]>(colsKey, []))
      : new Set(columns.filter(c => c.defaultVisible).map(c => c.key))
  )
  const [colWidths, setColWidths] = useState<Record<string, number>>(
    () => loadFromStorage<Record<string, number>>(widthsKey, {})
  )
  const [showColPicker, setShowColPicker] = useState(false)
  const [sortBy, setSortBy] = useState(defaultSort)
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>(defaultSortDir)

  const [prevKey, setPrevKey] = useState(storageKey)
  if (prevKey !== storageKey) {
    setPrevKey(storageKey)
    const savedCols = loadFromStorage<string[]>(colsKey, [])
    setVisibleCols(
      savedCols.length > 0
        ? new Set(savedCols)
        : new Set(columns.filter(c => c.defaultVisible).map(c => c.key))
    )
    setColWidths(loadFromStorage<Record<string, number>>(widthsKey, {}))
    setShowColPicker(false)
  }

  const saveVisible = useCallback((cols: Set<string>) => {
    localStorage.setItem(colsKey, JSON.stringify([...cols]))
  }, [colsKey])

  const saveWidths = useCallback((widths: Record<string, number>) => {
    localStorage.setItem(widthsKey, JSON.stringify(widths))
  }, [widthsKey])

  const toggleCol = useCallback((key: string) => {
    if (pinnedSet.has(key)) return
    setVisibleCols(prev => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      saveVisible(next)
      return next
    })
  }, [pinnedSet, saveVisible])

  const showAllCols = useCallback(() => {
    const all = new Set(columns.map(c => c.key))
    setVisibleCols(all)
    saveVisible(all)
  }, [columns, saveVisible])

  const resetCols = useCallback(() => {
    const defaults = new Set(columns.filter(c => c.defaultVisible).map(c => c.key))
    setVisibleCols(defaults)
    saveVisible(defaults)
  }, [columns, saveVisible])

  const toggleableColumns = useMemo(
    () => columns.filter(c => !pinnedSet.has(c.key)),
    [columns, pinnedSet]
  )

  const getWidth = useCallback((col: ColumnDef) => colWidths[col.key] ?? col.defaultWidth, [colWidths])

  const rafRef = useRef(0)
  const onResizeStart = useCallback((e: React.MouseEvent, colKey: string, startWidth: number) => {
    e.preventDefault()
    const startX = e.clientX
    const onMouseMove = (ev: MouseEvent) => {
      cancelAnimationFrame(rafRef.current)
      rafRef.current = requestAnimationFrame(() => {
        setColWidths(prev => ({ ...prev, [colKey]: Math.max(MIN_COL_WIDTH, startWidth + ev.clientX - startX) }))
      })
    }
    const onMouseUp = () => {
      cancelAnimationFrame(rafRef.current)
      setColWidths(prev => { saveWidths(prev); return prev })
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }, [saveWidths])

  const containerRef = useRef<HTMLDivElement>(null)

  const [measuredActionsW, setMeasuredActionsW] = useState(0)
  const actionsRefCb = useCallback((node: HTMLDivElement | null) => {
    if (node && !measuredActionsW) setMeasuredActionsW(node.offsetWidth)
  }, [measuredActionsW])

  const actionsCol = useMemo(() => columns.find(c => c.key === 'actions'), [columns])
  const actionsW = measuredActionsW || actionsCol?.defaultWidth || 60

  const activeCols = useMemo(
    () => columns.filter(c => visibleCols.has(c.key)),
    [columns, visibleCols]
  )
  const dataCols = useMemo(() => activeCols.filter(c => c.key !== 'actions'), [activeCols])
  const hasActions = useMemo(() => activeCols.some(c => c.key === 'actions'), [activeCols])

  const dataColsTemplate = useMemo(
    () => dataCols.map(c => `${colWidths[c.key] ?? c.defaultWidth}px`).join(' '),
    [dataCols, colWidths]
  )

  const handleFitToContainer = useCallback(() => {
    if (!containerRef.current) return
    const totalWidth = containerRef.current.clientWidth
    const available = Math.max(0, totalWidth - actionsW - GRID_H_PADDING)
    const totalDefault = dataCols.reduce((sum, c) => sum + c.defaultWidth, 0)
    const newWidths: Record<string, number> = {}
    dataCols.forEach(col => {
      newWidths[col.key] = Math.max(MIN_COL_WIDTH, Math.floor((col.defaultWidth / totalDefault) * available))
    })
    setColWidths(prev => {
      const updated = { ...prev, ...newWidths }
      saveWidths(updated)
      return updated
    })
  }, [dataCols, actionsW, saveWidths])

  const handleResetWidths = useCallback(() => {
    setColWidths({})
    saveWidths({})
  }, [saveWidths])

  const handleSort = useCallback((col: ColumnDef) => {
    if (!col.sortKey) return
    setSortBy(prev => {
      if (prev === col.sortKey) {
        setSortDir(d => d === 'asc' ? 'desc' : 'asc')
        return prev
      }
      setSortDir('asc')
      return col.sortKey!
    })
    onSortChange?.()
  }, [onSortChange])

  return {
    visibleCols,
    showColPicker,
    setShowColPicker,
    toggleCol,
    showAllCols,
    resetCols,
    toggleableColumns,

    colWidths,
    getWidth,
    onResizeStart,
    handleFitToContainer,
    handleResetWidths,

    sortBy,
    sortDir,
    setSortBy,
    setSortDir,
    handleSort,

    activeCols,
    dataCols,
    hasActions,
    dataColsTemplate,
    containerRef,
    actionsRefCb,
    actionsW,
  }
}
