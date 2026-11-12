import { useState, useEffect, useCallback, useRef } from 'react'
import { motion, useAnimationControls } from 'framer-motion'
import { cn } from '@/utils/cn'

const ANIMATION_FILL_DURATION = 0.8
const ANIMATION_RESET_DURATION = 0.15
const MIN_FILL_FOR_DOT = 0.04

function xpThreshold(level: number, divisor: number): number {
  return (level - 1) ** 2 * divisor
}

function pct(value: number): string {
  return `${Math.max(0, Math.min(100, value * 100))}%`
}

const SESSION_ANIMATED_PREFIX = 'xp-animated-'

interface XpProgressBarProps {
  totalXp: number
  level: number
  xpForCurrentLevel: number
  xpForNextLevel: number
  levelDivisor: number
  previousTotalXp?: number
  previousLevel?: number
  sessionId?: string
  onLevelUp?: (level: number) => Promise<void>
  onAnimationComplete?: () => void
  showCurrentLevelMarker?: boolean
  showNextLevelMarker?: boolean
  className?: string
}

export function XpProgressBar({
  totalXp,
  level,
  xpForCurrentLevel,
  xpForNextLevel,
  levelDivisor,
  previousTotalXp,
  previousLevel: previousLevelProp,
  sessionId,
  onLevelUp,
  onAnimationComplete,
  showCurrentLevelMarker = true,
  showNextLevelMarker = true,
  className,
}: XpProgressBarProps) {
  const safeLevelDivisor = Math.max(1, levelDivisor || 1)
  const safeCurrentThreshold = Number.isFinite(xpForCurrentLevel)
    ? xpForCurrentLevel
    : xpThreshold(level, safeLevelDivisor)
  const safeNextThreshold = Number.isFinite(xpForNextLevel) && xpForNextLevel > safeCurrentThreshold
    ? xpForNextLevel
    : xpThreshold(level + 1, safeLevelDivisor)
  const xpRange = Math.max(1, safeNextThreshold - safeCurrentThreshold)
  const currentXpValue = Math.max(0, Math.min(xpRange, totalXp - safeCurrentThreshold))
  const currentProgress = currentXpValue / xpRange
  const finalProgress = Math.max(0, Math.min(1, currentProgress))

  const computedPrevLevel = previousTotalXp !== undefined
    ? Math.floor(Math.sqrt(previousTotalXp / safeLevelDivisor)) + 1
    : level
  const prevLevel = previousLevelProp ?? computedPrevLevel
  const alreadyAnimated = sessionId
    ? sessionStorage.getItem(`${SESSION_ANIMATED_PREFIX}${sessionId}`) === '1'
    : false
  const shouldAnimate = !alreadyAnimated && previousTotalXp !== undefined && previousTotalXp < totalXp
  const controls = useAnimationControls()
  const [displayLevel, setDisplayLevel] = useState(shouldAnimate ? prevLevel : level)
  const [isAnimating, setIsAnimating] = useState(false)
  const animatedRef = useRef(false)

  const runAnimation = useCallback(async () => {
    if (!shouldAnimate || animatedRef.current) return
    animatedRef.current = true
    setIsAnimating(true)

    const animPrevLevel = previousLevelProp ?? Math.floor(Math.sqrt(previousTotalXp! / safeLevelDivisor)) + 1
    setDisplayLevel(animPrevLevel)

    const levelBoundaries: number[] = []
    for (let l = animPrevLevel + 1; l <= level; l++) {
      levelBoundaries.push(l)
    }

    if (levelBoundaries.length === 0) {
      const prevThreshold = xpThreshold(animPrevLevel, safeLevelDivisor)
      const nextThreshold = xpThreshold(animPrevLevel + 1, safeLevelDivisor)
      const range = nextThreshold - prevThreshold
      const startPct = range > 0 ? (previousTotalXp! - prevThreshold) / range : 0
      await controls.set({ width: pct(startPct) })
      await controls.start({ width: pct(finalProgress), transition: { duration: ANIMATION_FILL_DURATION, ease: 'easeOut' } })
    } else {
      const firstThreshold = xpThreshold(animPrevLevel, safeLevelDivisor)
      const firstNextThreshold = xpThreshold(animPrevLevel + 1, safeLevelDivisor)
      const firstRange = firstNextThreshold - firstThreshold
      const startPct = firstRange > 0 ? (previousTotalXp! - firstThreshold) / firstRange : 0
      await controls.set({ width: pct(startPct) })

      for (const newLevel of levelBoundaries) {
        await controls.start({ width: '100%', transition: { duration: ANIMATION_FILL_DURATION * 0.6, ease: 'easeOut' } })

        if (onLevelUp) {
          await onLevelUp(newLevel)
        }

        setDisplayLevel(newLevel)
        await controls.set({ width: '0%' })
        await new Promise(r => setTimeout(r, ANIMATION_RESET_DURATION * 1000))
      }

      await controls.start({ width: pct(finalProgress), transition: { duration: ANIMATION_FILL_DURATION, ease: 'easeOut' } })
    }

    if (sessionId) {
      sessionStorage.setItem(`${SESSION_ANIMATED_PREFIX}${sessionId}`, '1')
    }
    setIsAnimating(false)
    onAnimationComplete?.()
  }, [shouldAnimate, previousTotalXp, previousLevelProp, safeLevelDivisor, level, controls, finalProgress, sessionId, onLevelUp, onAnimationComplete])

  useEffect(() => {
    if (!shouldAnimate) return
    const raf = requestAnimationFrame(() => { runAnimation() })
    return () => cancelAnimationFrame(raf)
  }, [runAnimation, shouldAnimate])

  const displayNextLevel = displayLevel + 1
  const displayXpCurrent = shouldAnimate && isAnimating
    ? xpThreshold(displayLevel, safeLevelDivisor)
    : safeCurrentThreshold
  const displayXpNext = shouldAnimate && isAnimating
    ? xpThreshold(displayNextLevel, safeLevelDivisor)
    : safeNextThreshold
  const displayRange = Math.max(1, displayXpNext - displayXpCurrent)
  const displayXpValue = Math.max(0, Math.min(displayRange, totalXp - displayXpCurrent))
  const showEdgeDot = (shouldAnimate ? isAnimating : finalProgress > MIN_FILL_FOR_DOT)

  return (
    <div className={cn('w-full', className)}>
      <div className="flex items-center gap-3">
        {showCurrentLevelMarker && (
          <span className="bg-white/[.06] border border-white/[.08] rounded-full px-2.5 py-0.5 text-[11px] font-semibold text-white/50 shrink-0">
            Lv {displayLevel}
          </span>
        )}

        <div className="relative h-3 flex-1 rounded-full bg-white/[.06] border border-white/[.08] overflow-hidden">
          <motion.div
            className="absolute inset-y-0 left-0 rounded-full overflow-hidden"
            style={{
              background: 'linear-gradient(90deg, #7c5cfc, #a78bfa, #34d399)',
              boxShadow: '0 0 20px rgba(124,92,252,0.4), 0 0 40px rgba(124,92,252,0.15)',
            }}
            initial={{ width: shouldAnimate ? '0%' : pct(finalProgress) }}
            animate={shouldAnimate ? controls : { width: pct(finalProgress) }}
            transition={shouldAnimate ? undefined : { duration: 0.6, ease: 'easeOut' }}
          >
            {/* shimmer sweep */}
            <div
              className="absolute inset-0 animate-xp-shimmer"
              style={{
                background: 'linear-gradient(105deg, transparent 40%, rgba(255,255,255,0.2) 50%, transparent 60%)',
                animationDuration: isAnimating ? '1.2s' : '2.5s',
              }}
            />

            {/* glowing edge dot */}
            {showEdgeDot && (
              <motion.div
                className="absolute top-1/2 -translate-y-1/2 right-px w-1.5 h-1.5 rounded-full bg-white will-change-transform"
                style={{
                  boxShadow: '0 0 6px #fff, 0 0 12px rgba(124,92,252,0.6)',
                }}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.3 }}
              />
            )}
          </motion.div>
        </div>

        {showNextLevelMarker && (
          <span className="bg-white/[.06] border border-white/[.08] rounded-full px-2.5 py-0.5 text-[11px] font-semibold text-white/50 shrink-0">
            Lv {displayNextLevel}
          </span>
        )}
      </div>

      <p className="text-[11px] text-white/30 text-center mt-2">
        <span className="text-white/50">{displayXpValue.toLocaleString()}</span> / {displayRange.toLocaleString()} XP
      </p>
    </div>
  )
}
