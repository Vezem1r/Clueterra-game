import { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from '@/i18n'

const AUTO_DISMISS_MS = 3000
const PARTICLE_COUNT = 14

interface LevelUpModalProps {
  isOpen: boolean
  onClose: () => void
  newLevel: number
}

interface Particle {
  angle: number
  distance: number
  size: number
  delay: number
}

export function LevelUpModal({ isOpen, onClose, newLevel }: LevelUpModalProps) {
  const { t } = useTranslation()

  const [particles] = useState<Particle[]>(() =>
    Array.from({ length: PARTICLE_COUNT }, (_, i) => ({
      angle: (360 / PARTICLE_COUNT) * i + Math.random() * 20 - 10,
      distance: 80 + Math.random() * 60,
      size: 4 + Math.random() * 4,
      delay: 0.2 + Math.random() * 0.15,
    }))
  )

  const handleClose = useCallback(() => {
    onClose()
  }, [onClose])

  useEffect(() => {
    if (!isOpen) return
    const timer = setTimeout(handleClose, AUTO_DISMISS_MS)
    return () => clearTimeout(timer)
  }, [isOpen, handleClose])

  return (
    <AnimatePresence>
      {isOpen && (
        <div
          className="fixed inset-0 z-[60] flex items-center justify-center cursor-pointer"
          onClick={handleClose}
        >
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="absolute inset-0 bg-black/70 backdrop-blur-sm"
          />

          <motion.div
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.8 }}
            transition={{ type: 'spring', damping: 20, stiffness: 300 }}
            className="relative z-10 flex flex-col items-center"
          >
            <div className="relative">
              {particles.map((p, i) => (
                <motion.div
                  key={i}
                  className="absolute rounded-full"
                  style={{
                    width: p.size,
                    height: p.size,
                    background: i % 2 === 0
                      ? 'linear-gradient(135deg, #fbbf24, #f59e0b)'
                      : 'linear-gradient(135deg, #fde68a, #fbbf24)',
                    top: '50%',
                    left: '50%',
                    marginTop: -p.size / 2,
                    marginLeft: -p.size / 2,
                  }}
                  initial={{ opacity: 0, x: 0, y: 0, scale: 1 }}
                  animate={{
                    opacity: [0, 1, 0],
                    x: Math.cos((p.angle * Math.PI) / 180) * p.distance,
                    y: Math.sin((p.angle * Math.PI) / 180) * p.distance,
                    scale: [1, 1, 0.3],
                  }}
                  transition={{
                    duration: 0.8,
                    delay: p.delay,
                    ease: 'easeOut',
                  }}
                />
              ))}

              <motion.div
                initial={{ scale: 0, rotate: -10 }}
                animate={{ scale: 1, rotate: 0 }}
                transition={{ type: 'spring', delay: 0.1, stiffness: 200, damping: 15 }}
                className="text-[80px] sm:text-[100px] font-black leading-none bg-gradient-to-br from-amber-400 to-yellow-500 bg-clip-text text-transparent select-none"
                style={{ filter: 'drop-shadow(0 0 30px rgba(251, 191, 36, 0.4))' }}
              >
                {newLevel}
              </motion.div>
            </div>

            <motion.p
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3, duration: 0.3 }}
              className="text-xl sm:text-2xl font-bold text-amber-400 mt-4"
            >
              {t.gameComplete.levelUp}
            </motion.p>

            <motion.p
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.4, duration: 0.3 }}
              className="text-sm text-white/50 mt-2"
            >
              {t.gameComplete.reachedLevel.replace('{level}', String(newLevel))}
            </motion.p>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  )
}
