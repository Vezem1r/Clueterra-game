import { Link } from 'react-router-dom'
import { useTranslation } from '@/i18n'

interface HomeGuestLegalNoticeProps {
  actionLabel: string
  onOpenPrivacy: () => void
  onOpenTerms: () => void
  template: string
}

export function HomeGuestLegalNotice({
  actionLabel,
  onOpenPrivacy,
  onOpenTerms,
  template,
}: HomeGuestLegalNoticeProps) {
  const { t } = useTranslation()
  const tokens = template
    .replaceAll('{action}', actionLabel)
    .split(/(\{terms\}|\{privacy\}|\{dataSources\})/g)

  return (
    <>
      {tokens.map((token, index) => {
        if (token === '{terms}') {
          return (
            <button
              key={`terms-${index}`}
              type="button"
              onClick={onOpenTerms}
              className="inline p-0 font-medium text-white/72 underline underline-offset-2 transition-colors hover:text-white"
            >
              {t.footer.termsLink}
            </button>
          )
        }

        if (token === '{privacy}') {
          return (
            <button
              key={`privacy-${index}`}
              type="button"
              onClick={onOpenPrivacy}
              className="inline p-0 font-medium text-white/72 underline underline-offset-2 transition-colors hover:text-white"
            >
              {t.footer.privacyLink}
            </button>
          )
        }

        if (token === '{dataSources}') {
          return (
            <Link
              key={`data-sources-${index}`}
              to="/data-sources"
              className="font-medium text-white/72 underline underline-offset-2 transition-colors hover:text-white"
            >
              {t.footer.registryLink}
            </Link>
          )
        }

        return token
      })}
    </>
  )
}
