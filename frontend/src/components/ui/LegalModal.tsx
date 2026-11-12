import { Modal } from './Modal'
import { PrivacyContent, TermsContent } from '@/pages/legal/legalContent'

interface LegalModalProps {
  type: 'terms' | 'privacy' | null
  onClose: () => void
}

export function LegalModal({ type, onClose }: LegalModalProps) {
  return (
    <Modal
      isOpen={type !== null}
      onClose={onClose}
      title={type === 'terms' ? 'Terms of Service' : 'Privacy Policy'}
      className="max-w-2xl max-h-[80vh] flex flex-col"
    >
      <div className="text-xs sm:text-sm text-white/70 leading-relaxed space-y-6 overflow-y-auto">
        {type === 'terms' ? <TermsContent /> : <PrivacyContent />}
      </div>
    </Modal>
  )
}
