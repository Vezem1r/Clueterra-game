import { useEffect, useState } from 'react'
import { cn } from '@/utils/cn'
import { FLAG_IMAGE_RETRIES, FLAG_IMAGE_RETRY_MS } from './flagGameConstants'

interface FlagCardProps {
  src: string
  alt: string
  className?: string
  eager?: boolean
}

const RETRY_PARAM = 'retry'

export function FlagCard({ src, alt, className, eager = false }: FlagCardProps) {
  const [failure, setFailure] = useState({ src, count: 0 })
  const retries = failure.src === src ? failure.count : 0
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (!pending) return
    const timer = window.setTimeout(() => {
      setPending(false)
      setFailure(current => ({ src, count: (current.src === src ? current.count : 0) + 1 }))
    }, FLAG_IMAGE_RETRY_MS)
    return () => window.clearTimeout(timer)
  }, [pending, src])

  const url = retries > 0 ? `${src}?${RETRY_PARAM}=${retries}` : src

  return <img src={url} alt={alt} draggable={false} loading={eager ? 'eager' : 'lazy'} decoding="async"
    onError={() => {
      if (retries < FLAG_IMAGE_RETRIES) setPending(true)
    }}
    className={cn('select-none rounded-md object-contain shadow-[0_18px_45px_rgba(0,0,0,.55)] ring-1 ring-white/15', className)} />
}
