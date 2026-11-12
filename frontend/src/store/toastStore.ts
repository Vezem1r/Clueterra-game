import { create } from 'zustand'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
  id: string
  type: ToastType
  message: string
  duration?: number
}

interface ToastInput {
  type: ToastType
  message: string
  duration?: number
}

interface ToastState {
  toasts: Toast[]
  addToast: (input: ToastInput) => void
  removeToast: (id: string) => void
}

let counter = 0

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],

  addToast: ({ type, message, duration = 5000 }) => {
    const id = `toast-${++counter}-${Date.now()}`
    set((s) => ({
      toasts: [...s.toasts.slice(-4), { id, type, message, duration }],
    }))
    if (duration > 0) {
      setTimeout(() => {
        set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
      }, duration)
    }
  },

  removeToast: (id) => {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
  },
}))

export const toast = {
  success: (msg: string) => useToastStore.getState().addToast({ type: 'success', message: msg }),
  error: (msg: string) => useToastStore.getState().addToast({ type: 'error', message: msg }),
  warning: (msg: string) => useToastStore.getState().addToast({ type: 'warning', message: msg }),
  info: (msg: string) => useToastStore.getState().addToast({ type: 'info', message: msg }),
}
