import { useSyncExternalStore } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { AboutModal } from './AboutModal'
import { GuideModal } from './GuideModal'
import { BudgetGuideModal } from '@/pages/budget/BudgetGuideModal'
import { RiverGuideModal } from '@/pages/games/rivers/RiverGuideModal'
import { RIVER_GUIDE_QUERY } from '@/pages/games/rivers/riverGameConstants'
import { SilhouetteGuideModal } from '@/pages/games/silhouettes/SilhouetteGuideModal'
import { SILHOUETTE_GUIDE_QUERY } from '@/pages/games/silhouettes/silhouetteGameConstants'
import { FlagGuideModal } from '@/pages/games/flags/FlagGuideModal'
import { FLAG_GUIDE_QUERY } from '@/pages/games/flags/flagGameConstants'
import { QuizGuideModal } from '@/pages/games/countries-quiz/QuizGuideModal'
import { QUIZ_GUIDE_QUERY } from '@/pages/games/countries-quiz/quizGameConstants'

const subscribeToHydration = () => () => undefined

function useHydrated() {
  return useSyncExternalStore(subscribeToHydration, () => true, () => false)
}

export function GuideModalHost() {
  const location = useLocation()
  const navigate = useNavigate()
  const hydrated = useHydrated()

  const params = new URLSearchParams(location.search)
  const guideMode = params.get('guide')
  const queryOpen = hydrated && (guideMode === '1' || guideMode === 'budget' || guideMode === RIVER_GUIDE_QUERY
    || guideMode === SILHOUETTE_GUIDE_QUERY || guideMode === FLAG_GUIDE_QUERY || guideMode === QUIZ_GUIDE_QUERY)
  const aboutOpen = hydrated && params.get('about') === '1'
  const budgetGuideOpen = hydrated && guideMode === 'budget'
  const riverGuideOpen = hydrated && guideMode === RIVER_GUIDE_QUERY
  const silhouetteGuideOpen = hydrated && guideMode === SILHOUETTE_GUIDE_QUERY
  const flagGuideOpen = hydrated && guideMode === FLAG_GUIDE_QUERY
  const quizGuideOpen = hydrated && guideMode === QUIZ_GUIDE_QUERY
  const isAdminRoute = location.pathname.startsWith('/admin')
  const isOpen = !isAdminRoute && (queryOpen || aboutOpen)

  const handleClose = () => {
    if (!queryOpen && !aboutOpen) {
      return
    }

    const nextParams = new URLSearchParams(location.search)
    nextParams.delete(aboutOpen ? 'about' : 'guide')
    const nextSearch = nextParams.toString()

    navigate(
      {
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : '',
      },
      { replace: true }
    )
  }

  if (aboutOpen) {
    return <AboutModal isOpen={isOpen} onClose={handleClose} />
  }

  if (flagGuideOpen) {
    return <FlagGuideModal isOpen={isOpen} onClose={handleClose} />
  }

  if (quizGuideOpen) {
    return <QuizGuideModal isOpen={isOpen} onClose={handleClose} />
  }

  if (silhouetteGuideOpen) {
    return <SilhouetteGuideModal isOpen={isOpen} onClose={handleClose} />
  }

  if (riverGuideOpen) {
    return <RiverGuideModal isOpen={isOpen} onClose={handleClose} />
  }

  return budgetGuideOpen
    ? <BudgetGuideModal isOpen={isOpen} onClose={handleClose} />
    : <GuideModal isOpen={isOpen} onClose={handleClose} />
}
