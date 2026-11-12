import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'outline' | 'danger' | 'icon'
  size?: 'sm' | 'md' | 'lg'
  isLoading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', isLoading, children, disabled, ...props }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        className={cn(
          'inline-flex items-center justify-center font-medium transition-all duration-200 cursor-pointer',
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background',
          'disabled:opacity-40 disabled:cursor-not-allowed disabled:pointer-events-none',
          // Variants
          variant === 'primary' && 'bg-white text-black hover:bg-white/90 active:bg-white/80 shadow-[0_0_20px_rgba(255,255,255,0.08)] hover:shadow-[0_0_30px_rgba(255,255,255,0.12)]',
          variant === 'secondary' && 'bg-white/[.07] text-white/90 hover:bg-white/[.12] active:bg-white/[.16] border border-white/[.08]',
          variant === 'ghost' && 'bg-transparent text-white/60 hover:text-white hover:bg-white/[.05] active:bg-white/[.08]',
          variant === 'outline' && 'bg-transparent border border-white/[.12] text-white/70 hover:border-white/[.22] hover:text-white hover:bg-white/[.04] active:bg-white/[.06]',
          variant === 'danger' && 'bg-error/15 text-error border border-error/20 hover:bg-error/25 hover:border-error/35 active:bg-error/30 shadow-[0_0_20px_rgba(248,113,113,0.06)]',
          variant === 'icon' && 'bg-white/[.05] text-white/50 hover:text-white/80 hover:bg-white/[.1] active:bg-white/[.12] border border-white/[.06]',
          // Sizes
          variant === 'icon'
            ? {
                'h-8 w-8 rounded-lg': size === 'sm',
                'h-10 w-10 rounded-xl': size === 'md',
                'h-12 w-12 rounded-xl': size === 'lg',
              }
            : {
                'h-8 px-3 text-xs rounded-lg gap-1.5': size === 'sm',
                'h-10 px-5 text-sm rounded-xl gap-2': size === 'md',
                'h-12 px-7 text-base rounded-xl gap-2.5': size === 'lg',
              },
          className
        )}
        {...props}
      >
        {isLoading && (
          <svg className="animate-spin h-4 w-4 opacity-70" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" className="opacity-20" />
            <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
          </svg>
        )}
        {children}
      </button>
    )
  }
)

Button.displayName = 'Button'
