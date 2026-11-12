import { useCallback, useState } from 'react'
import { motion } from 'framer-motion'
import { Info, Maximize2 } from 'lucide-react'
import type { Hint } from '@/types'
import { CategoryIcon } from '@/components/game/CategoryIcon'
import { HintImageModal } from '@/components/game/HintImageModal'
import { PanoramaHintImage } from '@/components/game/PanoramaHintImage'
import { useTranslation } from '@/i18n'

interface HintCardProps {
  hint: Hint
  displayNumber: number
  isNewest?: boolean
  onInfoClick?: (hint: Hint) => void
}

export function HintCard({ hint, displayNumber, isNewest = false, onInfoClick }: HintCardProps) {
  const { t } = useTranslation()
  const [isImageOpen, setIsImageOpen] = useState(false)
  const openImage = useCallback(() => setIsImageOpen(true), [])
  const closeImage = useCallback(() => setIsImageOpen(false), [])
  const openImageInfo = useCallback(() => {
    setIsImageOpen(false)
    onInfoClick?.(hint)
  }, [hint, onInfoClick])

  return (
    <motion.div
      layout="position"
      initial={isNewest ? { y: 10, scale: 0.985 } : false}
      animate={{ y: 0, scale: 1 }}
      transition={{ type: 'tween', duration: 0.2, ease: 'easeOut' }}
      className="group"
    >
      <div
        className={`relative p-5 sm:p-7 xl:p-8 rounded-2xl bg-white/[.025] border shadow-lg shadow-black/20 transition-colors overflow-hidden ${
          isNewest
            ? 'border-accent/20 shadow-[0_0_30px_rgba(124,92,252,.14)]'
            : 'border-white/[.06] hover:border-white/[.1]'
        }`}
      >
        {isNewest && (
          <div
            className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-accent/70 to-transparent"
            aria-hidden
          />
        )}
        {!hint.imageUrl && (
          <div className="absolute bottom-3 right-4 pointer-events-none" aria-hidden>
            <CategoryIcon category={hint.category} size={48} className="text-white/[.03]" />
          </div>
        )}

        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-2.5">
              <span className="text-[10px] sm:text-xs font-semibold text-accent uppercase tracking-wider">
                Clue {displayNumber}
              </span>
              <span className="text-white/15">&middot;</span>
              <span className="text-[10px] sm:text-xs text-white/25">{hint.category}</span>
            </div>
            <p className="text-base sm:text-lg xl:text-xl text-white/85 leading-relaxed">
              {hint.hintText}
            </p>
            {hint.imageUrl && (hint.type === 'PANORAMA'
              ? <PanoramaHintImage src={hint.imageUrl} onOpen={openImage} openLabel={t.play.openImage} />
              : <HintImage src={hint.imageUrl} onOpen={openImage} openLabel={t.play.openImage} />
            )}
          </div>
          {onInfoClick && (
            <button
              type="button"
              onClick={() => onInfoClick(hint)}
              aria-label={t.play.hintDetails}
              title={t.play.hintDetails}
              className="p-2 rounded-lg text-white/35 hover:text-white/65 hover:bg-white/[.06]
                         transition-all shrink-0 cursor-pointer"
            >
              <Info size={16} />
            </button>
          )}
        </div>
      </div>
      {hint.imageUrl && (
        <HintImageModal
          src={hint.imageUrl}
          isOpen={isImageOpen}
          isPanorama={hint.type === 'PANORAMA'}
          onInfoClick={openImageInfo}
          onClose={closeImage}
        />
      )}
    </motion.div>
  )
}

function HintImage({ src, onOpen, openLabel }: { src: string; onOpen: () => void; openLabel: string }) {
  const previewSrc = hintImagePreviewUrl(src)

  return (
    <button
      type="button"
      onClick={onOpen}
      aria-label={openLabel}
      title={openLabel}
      className="group/image relative mt-3 block aspect-video w-full cursor-zoom-in overflow-hidden rounded-xl border border-white/[.08] bg-[radial-gradient(circle_at_center,rgba(124,92,252,.08),rgba(0,0,0,.5))]"
    >
      <img
        src={previewSrc}
        alt=""
        className="absolute inset-0 h-full w-full object-contain"
        loading="lazy"
        decoding="async"
      />
      <span className="absolute right-2.5 top-2.5 flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-black/55 text-white/65 opacity-0 shadow-lg backdrop-blur-md transition-opacity group-hover/image:opacity-100 group-focus-visible/image:opacity-100">
        <Maximize2 size={17} />
      </span>
    </button>
  )
}

function hintImagePreviewUrl(src: string) {
  if (!src.startsWith('/api/assets/hints/')) return src
  return `${src}${src.includes('?') ? '&' : '?'}width=960`
}
