import { ExternalLink } from 'lucide-react'

interface ImageMetadataPanelProps {
  imageUrl?: string | null
  originalImageUrl?: string | null
  imageSourcePageUrl?: string | null
  imageAuthorName?: string | null
  imageAttributionText?: string | null
  imageLicenseLabel?: string | null
  imageLicenseUrl?: string | null
}

function MetadataLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      className="inline-flex items-center gap-1 text-sky-300/80 hover:text-sky-200 transition-colors break-all"
    >
      <span>{children}</span>
      <ExternalLink size={12} className="shrink-0" />
    </a>
  )
}

function hasImageMetadata(props: ImageMetadataPanelProps) {
  return !!(
    props.imageUrl
    || props.originalImageUrl
    || props.imageSourcePageUrl
    || props.imageAuthorName
    || props.imageAttributionText
    || props.imageLicenseLabel
    || props.imageLicenseUrl
  )
}

export function ImageMetadataPanel(props: ImageMetadataPanelProps) {
  if (!hasImageMetadata(props)) {
    return null
  }

  const rows = [
    { label: 'Author', value: props.imageAuthorName || '—' },
    {
      label: 'License',
      value: props.imageLicenseLabel
        ? props.imageLicenseUrl
          ? <MetadataLink href={props.imageLicenseUrl}>{props.imageLicenseLabel}</MetadataLink>
          : props.imageLicenseLabel
        : '—',
    },
    { label: 'Attribution', value: props.imageAttributionText || '—', fullWidth: true },
    {
      label: 'Source Page',
      value: props.imageSourcePageUrl
        ? <MetadataLink href={props.imageSourcePageUrl}>{props.imageSourcePageUrl}</MetadataLink>
        : '—',
      fullWidth: true,
    },
    {
      label: 'Original Image',
      value: props.originalImageUrl
        ? <MetadataLink href={props.originalImageUrl}>{props.originalImageUrl}</MetadataLink>
        : '—',
      fullWidth: true,
    },
    {
      label: 'Stored Image',
      value: props.imageUrl
        ? <MetadataLink href={props.imageUrl}>{props.imageUrl}</MetadataLink>
        : '—',
      fullWidth: true,
    },
  ]

  return (
    <div className="space-y-2 pt-1">
      <p className="text-[10px] text-white/25 uppercase tracking-widest">Image Metadata</p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {rows.map(row => (
          <div
            key={row.label}
            className={row.fullWidth ? 'sm:col-span-2 rounded-lg bg-white/[.03] px-3 py-2.5' : 'rounded-lg bg-white/[.03] px-3 py-2.5'}
          >
            <p className="text-[10px] text-white/25 uppercase tracking-widest">{row.label}</p>
            <div className="text-sm text-white/60 mt-1 break-words">
              {row.value}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
