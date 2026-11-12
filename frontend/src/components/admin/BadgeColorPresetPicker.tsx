import { Sparkles } from 'lucide-react'
import { BADGE_COLOR_GROUPS, getBadgeColorPreset } from '@/constants/badgeColors'
import { cn } from '@/utils/cn'

interface BadgeColorPresetPickerProps {
  label: string
  helper: string
  value: string
  onChange: (value: string) => void
}

export function BadgeColorPresetPicker({ label, helper, value, onChange }: BadgeColorPresetPickerProps) {
  const fallbackPreset = BADGE_COLOR_GROUPS[0]?.presets[1] ?? BADGE_COLOR_GROUPS[0]?.presets[0]
  const selected = getBadgeColorPreset(value) ?? fallbackPreset
  const selectedGroup = BADGE_COLOR_GROUPS.find(group => group.key === selected?.family) ?? BADGE_COLOR_GROUPS[0]

  const handleFamilyChange = (familyKey: string) => {
    if (!selected) return
    onChange(`${familyKey}-${selected.variant}`)
  }

  const handleVariantChange = (variantKey: string) => {
    if (!selectedGroup) return
    onChange(`${selectedGroup.key}-${variantKey}`)
  }

  return (
    <div className="space-y-3 rounded-[1.25rem] border border-white/[.06] bg-white/[.02] p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-xs font-semibold text-white/78">{label}</p>
          <p className="mt-1 text-[11px] leading-relaxed text-white/34">{helper}</p>
        </div>
        {selected && (
          <div className="inline-flex items-center gap-2 rounded-full border border-white/[.08] bg-white/[.03] px-3 py-1.5 text-[11px] text-white/58">
            <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: selected.hex }} />
            <Sparkles size={12} className="text-white/35" />
            <span>{selected.label}</span>
          </div>
        )}
      </div>

      <div className="space-y-3">
        <div>
          <p className="mb-2 text-[10px] font-semibold uppercase tracking-[.18em] text-white/28">
            Color family
          </p>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 xl:grid-cols-7">
            {BADGE_COLOR_GROUPS.map(group => {
              const active = group.key === selectedGroup?.key
              const groupPreview = group.presets[1] ?? group.presets[0]

              return (
                <button
                  key={group.key}
                  type="button"
                  onClick={() => handleFamilyChange(group.key)}
                  className={cn(
                    'group flex items-center gap-2 rounded-xl border px-3 py-2.5 text-left transition-all duration-200 cursor-pointer',
                    active
                      ? 'border-white/18 bg-white/[.06] shadow-[0_10px_24px_rgba(0,0,0,.18)]'
                      : 'border-white/[.08] bg-white/[.025] hover:border-white/[.14] hover:bg-white/[.045]'
                  )}
                >
                  <span
                    className="relative h-5 w-5 shrink-0 rounded-full border border-white/18"
                    style={{
                      background: `radial-gradient(circle at 30% 30%, rgba(255,255,255,.7), ${groupPreview.hex})`,
                      boxShadow: active ? `0 0 16px ${groupPreview.hex}55` : `0 0 10px ${groupPreview.hex}22`,
                    }}
                  />
                  <span className="min-w-0 truncate text-[11px] font-medium text-white/72">
                    {group.label}
                  </span>
                </button>
              )
            })}
          </div>
        </div>

        <div>
          <p className="mb-2 text-[10px] font-semibold uppercase tracking-[.18em] text-white/28">
            Tone
          </p>
          <div className="grid grid-cols-3 gap-2">
            {selectedGroup?.presets.map(preset => {
              const active = preset.key === selected?.key

              return (
                <button
                  key={preset.key}
                  type="button"
                  onClick={() => handleVariantChange(preset.variant)}
                  className={cn(
                    'group relative overflow-hidden rounded-xl border px-3 py-3 text-left transition-all duration-200 cursor-pointer',
                    active
                      ? 'border-white/20 bg-white/[.06] shadow-[0_10px_24px_rgba(0,0,0,.18)]'
                      : 'border-white/[.08] bg-white/[.025] hover:border-white/[.14] hover:bg-white/[.045]'
                  )}
                >
                  <span
                    className="absolute inset-0 opacity-90"
                    style={{
                      background: `linear-gradient(145deg, ${preset.hex}, rgba(255,255,255,.04))`,
                    }}
                  />
                  <span className="absolute inset-[1px] rounded-[0.7rem] bg-[linear-gradient(180deg,rgba(9,11,17,.18),rgba(9,11,17,.5))]" />
                  <span className="relative flex items-center justify-between gap-2">
                    <span>
                      <span className="block text-[10px] font-semibold uppercase tracking-[.16em] text-white/86">
                        {preset.variantLabel}
                      </span>
                      <span className="mt-1 block text-[11px] text-white/55">
                        {preset.familyLabel}
                      </span>
                    </span>
                    <span
                      className="h-7 w-7 shrink-0 rounded-full border border-white/16"
                      style={{
                        background: `radial-gradient(circle at 30% 30%, rgba(255,255,255,.76), ${preset.hex})`,
                        boxShadow: active ? `0 0 20px ${preset.hex}66` : `0 0 12px ${preset.hex}33`,
                      }}
                    />
                  </span>
                </button>
              )
            })}
          </div>
        </div>

        {selected && (
          <div className="rounded-[1rem] border border-white/[.06] bg-[linear-gradient(180deg,rgba(255,255,255,.05),rgba(255,255,255,.02))] p-3">
            <div className="flex items-center gap-3">
              <div
                className="relative h-12 w-12 shrink-0 rounded-2xl border border-white/14"
                style={{
                  background: `linear-gradient(145deg, ${selected.hex}, rgba(255,255,255,.08))`,
                  boxShadow: `0 0 24px ${selected.hex}44`,
                }}
              >
                <span className="absolute inset-2 rounded-xl border border-white/12 bg-[linear-gradient(180deg,rgba(255,255,255,.18),rgba(255,255,255,.04))]" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-semibold text-white/82">{selected.label}</p>
                <p className="mt-0.5 text-[11px] text-white/36">
                  {selected.hex} · {selected.familyLabel} · {selected.variantLabel}
                </p>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
