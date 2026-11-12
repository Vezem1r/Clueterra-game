import { createContext, useContext } from 'react'
import { create } from 'zustand'
import { en, type TranslationKeys } from './en'
import { useSettingsStore, type Language } from '@/store/settingsStore'

type LazyLanguage = Exclude<Language, 'en'>

const translations: Partial<Record<Language, TranslationKeys>> = { en }
const loaders: Record<LazyLanguage, () => Promise<TranslationKeys>> = {
  ru: () => import('./ru').then((module) => module.ru),
  cs: () => import('./cs').then((module) => module.cs),
}
const pending = new Map<Language, Promise<void>>()

export const PathLanguageContext = createContext<Language | null>(null)
const useLoadedVersion = create(() => ({ version: 0 }))

export const languageNames: Record<Language, string> = {
  en: 'English',
  ru: 'Русский',
  cs: 'Čeština',
}

export function registerTranslations(language: Language, translation: TranslationKeys) {
  translations[language] = translation
  useLoadedVersion.setState((state) => ({ version: state.version + 1 }))
}

export function loadLanguage(language: Language): Promise<void> {
  if (translations[language] || language === 'en') return Promise.resolve()
  const existing = pending.get(language)
  if (existing) return existing
  const request = loaders[language]()
    .then((translation) => registerTranslations(language, translation))
    .finally(() => pending.delete(language))
  pending.set(language, request)
  return request
}

export function useTranslation() {
  const storedLanguage = useSettingsStore((s) => s.language)
  const requested = useContext(PathLanguageContext) ?? storedLanguage
  useLoadedVersion((state) => state.version)
  const loaded = translations[requested]
  return loaded ? { t: loaded, language: requested } : { t: en, language: 'en' as Language }
}

export function getTranslation(language: Language) {
  return translations[language] ?? en
}

export type { TranslationKeys, Language }
