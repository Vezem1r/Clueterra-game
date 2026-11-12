import { useEffect, useState } from 'react'
import { riversApi } from '@/api/rivers'
import { RIVER_NAME_MIN_QUERY, RIVER_NAME_SEARCH_DELAY_MS, RIVER_NAME_SUGGESTION_LIMIT } from './riverGameConstants'

interface NameSearch {
  query: string
  names: string[]
}

const EMPTY_SEARCH: NameSearch = { query: '', names: [] }

export function useRiverNameSuggestions(query: string, enabled: boolean) {
  const [search, setSearch] = useState<NameSearch>(EMPTY_SEARCH)
  const trimmed = query.trim()
  const active = enabled && trimmed.length >= RIVER_NAME_MIN_QUERY

  useEffect(() => {
    if (!active) return
    let cancelled = false
    const timer = window.setTimeout(() => {
      riversApi.searchNames(trimmed)
        .then(names => {
          if (!cancelled) setSearch({ query: trimmed, names: names.slice(0, RIVER_NAME_SUGGESTION_LIMIT) })
        })
        .catch(() => {
          if (!cancelled) setSearch(EMPTY_SEARCH)
        })
    }, RIVER_NAME_SEARCH_DELAY_MS)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [trimmed, active])

  return {
    suggestions: active && search.query === trimmed ? search.names : [],
    clearSuggestions: () => setSearch(EMPTY_SEARCH),
  }
}
