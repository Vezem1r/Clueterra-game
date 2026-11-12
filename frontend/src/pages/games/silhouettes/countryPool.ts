import { useEffect, useState } from 'react'
import { COUNTRY_POOL_URL } from './mapAssets'
import { toCountryPool, type CountryPool, type SilhouetteWorldProperties } from './silhouetteWorld'

let poolRequest: Promise<CountryPool> | null = null

export function loadCountryPool() {
  poolRequest ??= fetch(COUNTRY_POOL_URL)
    .then(response => {
      if (!response.ok) throw new Error(`Country pool failed: ${response.status}`)
      return response.json() as Promise<SilhouetteWorldProperties[]>
    })
    .then(toCountryPool)
    .catch(error => {
      poolRequest = null
      throw error
    })
  return poolRequest
}

export function useCountryPool() {
  const [pool, setPool] = useState<CountryPool | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    loadCountryPool()
      .then(loaded => {
        if (!cancelled) setPool(loaded)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { pool, failed }
}
