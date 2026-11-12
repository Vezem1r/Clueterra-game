import { forwardRef, type InputHTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  containerClassName?: string
  error?: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, containerClassName, error, ...props }, ref) => {
    return (
      <div className={cn('w-full', containerClassName)}>
        <input
          ref={ref}
          className={cn(
            'w-full px-4 py-3 bg-white/5 border border-white/10 rounded-lg',
            'text-white placeholder:text-white/40',
            'focus:outline-none focus:border-white/30 focus:ring-1 focus:ring-white/20',
            'transition-all duration-200',
            error && 'border-red-500/50 focus:border-red-500',
            className
          )}
          {...props}
        />
        {error && <p className="mt-1 text-sm text-red-400">{error}</p>}
      </div>
    )
  }
)

Input.displayName = 'Input'
