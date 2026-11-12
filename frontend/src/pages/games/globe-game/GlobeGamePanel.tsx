import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '@/utils/cn'

export interface GlobeGamePanelProps extends HTMLAttributes<HTMLElement> {
  as?: 'section' | 'div'
  children: ReactNode
}

export function GlobeGamePanel({ as: Tag = 'div', className, children, ...props }: GlobeGamePanelProps) {
  return <Tag {...props}
    className={cn('relative flex min-h-0 flex-1 flex-col overflow-hidden rounded-[1.75rem] border border-white/[.1] bg-[rgba(9,11,21,.72)] shadow-[0_32px_90px_rgba(0,0,0,.45)] backdrop-blur-[40px]', className)}>
    <span aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 h-px bg-[linear-gradient(90deg,transparent_8%,rgba(255,255,255,.25)_50%,transparent_92%)]" />
    {children}
  </Tag>
}
