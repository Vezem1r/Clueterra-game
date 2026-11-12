import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { gameApi } from '@/api/game'

const MIN_QUERY_LENGTH = 2
const SEARCH_STALE_TIME = 60_000

export function useCountrySearch() {
  const [searchQuery, setSearchQuery] = useState('')

  const { data: searchResults = [], isFetching: isSearching } = useQuery({
    queryKey: ['country-search', searchQuery],
    queryFn: () => gameApi.searchCountries(searchQuery),
    enabled: searchQuery.length >= MIN_QUERY_LENGTH,
    staleTime: SEARCH_STALE_TIME,
    placeholderData: keepPreviousData,
  })

  const clearSearch = () => setSearchQuery('')

  return { searchQuery, setSearchQuery, searchResults, isSearching, clearSearch }
}
