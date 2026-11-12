import type {Country} from '@/types'
import type {Language} from '@/store/settingsStore'

const FLAG_ASSET_BASE = '/api/assets/flags'

export type FlagSize = 'w20' | 'w40' | 'w80' | 'w160' | 'w320'

export function getFlagUrl(country: Pick<Country, 'flagUrl' | 'isoAlpha2'>, size: FlagSize = 'w160'): string | null {
    return getFlagUrlByIso2(country.isoAlpha2, size)
}

export function getFlagUrlByIso2(isoAlpha2?: string | null, size: FlagSize = 'w160'): string | null {
    if (!isoAlpha2) return null
    return `${FLAG_ASSET_BASE}/${isoAlpha2.toLowerCase()}.svg?size=${size}`
}

export function getCountryName(
    country: Pick<Country, 'nameEn' | 'nameRu'>,
    language: Language
): string {
    if (language === 'ru' && country.nameRu) return country.nameRu
    return country.nameEn
}
