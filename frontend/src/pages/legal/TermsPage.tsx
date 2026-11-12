import { Link } from 'react-router-dom'
import { Home } from 'lucide-react'
import { TermsContent } from './legalContent'
import { useSeoMeta } from '@/hooks/useSeoMeta'

export function TermsPage() {
  useSeoMeta({
    title: 'Terms of Service | Clueterra',
    description: 'Read the terms that govern access to and use of the Clueterra geography game and related services.',
    canonicalPath: '/terms',
    keywords: 'Clueterra terms of service',
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

        <h1 className="text-2xl sm:text-3xl font-bold text-white mb-6">Terms of Service</h1>

        <div className="space-y-6 sm:space-y-8 text-xs sm:text-sm leading-relaxed">
          <TermsContent />
        </div>
      </div>
    </div>
  )
}
