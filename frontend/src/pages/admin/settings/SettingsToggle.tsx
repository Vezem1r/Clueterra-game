import { cn } from '@/utils/cn'

interface SettingsToggleProps {
  checked: boolean
  onChange: () => void
  label: string
  description: string
  sublabel?: string
}

export function SettingsToggle({ checked, onChange, label, description, sublabel }: SettingsToggleProps) {
  return (
    <div className="flex items-center justify-between gap-4 px-1">
      <div>
        <p className="text-xs text-white/40">{label}</p>
        <p className="text-xs text-white/20 mt-0.5">{description}</p>
        {sublabel && <p className="text-xs text-white/15 mt-0.5">{sublabel}</p>}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={onChange}
        className={cn(
          'relative shrink-0 w-11 h-6 rounded-full transition-colors duration-200 cursor-pointer',
          checked ? 'bg-success/60' : 'bg-white/[.12]'
        )}
      >
        <span className={cn(
          'absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow-sm transition-transform duration-200',
          checked && 'translate-x-5'
        )} />
      </button>
    </div>
  )
}
