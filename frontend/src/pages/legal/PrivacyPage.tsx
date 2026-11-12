import { Link } from 'react-router-dom'
import { Home } from 'lucide-react'
import { PrivacyContent } from './legalContent'
import { useSeoMeta } from '@/hooks/useSeoMeta'

export function PrivacyPage() {
  useSeoMeta({
    title: 'Privacy Policy | Clueterra',
    description: 'Learn what personal data Clueterra processes, why it is used, how long it is retained, and what privacy rights users have.',
    canonicalPath: '/privacy',
    keywords: 'Clueterra privacy policy, Clueterra personal data',
  })

  return (
    <div className="min-h-screen bg-background text-white/80">
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-10 sm:py-16">
        <Link
          to="/"
          className="inline-flex items-center gap-2 text-white/30 hover:text-white/60 text-xs transition-colors mb-8"
        >
          <Home size={14} />
          Home
        </Link>

        <h1 className="text-2xl sm:text-3xl font-bold text-white mb-6">Privacy Policy</h1>

        <div className="space-y-6 sm:space-y-8 text-xs sm:text-sm leading-relaxed">
          <PrivacyContent />
        </div>
      </div>
    </div>
  )
}
