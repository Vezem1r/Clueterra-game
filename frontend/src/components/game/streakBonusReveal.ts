import type { Transition } from 'framer-motion'

const REVEAL_DELAY_SECONDS = 0.42
const FADE_SECONDS = 0.24

export const STREAK_BONUS_REVEAL: Transition = {
  opacity: { delay: REVEAL_DELAY_SECONDS, duration: FADE_SECONDS, ease: 'easeOut' },
  scale: { delay: REVEAL_DELAY_SECONDS, type: 'spring', stiffness: 260, damping: 20 },
}
