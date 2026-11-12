import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type Language = 'en' | 'ru' | 'cs'

interface SettingsState {
  language: Language
  setLanguage: (lang: Language) => void
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      language: 'en',
      setLanguage: (language) => set({ language }),
    }),
    {
      name: 'clueterra-settings',
      skipHydration: true,
    }
  )
)
