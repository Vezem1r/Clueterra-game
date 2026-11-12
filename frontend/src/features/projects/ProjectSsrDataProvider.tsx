import type { ReactNode } from 'react'
import { ProjectSsrDataContext } from './projectSsrData'
import type { ProjectSsrData } from './projectTypes'

export function ProjectSsrDataProvider({
  children,
  value,
}: {
  children: ReactNode
  value: ProjectSsrData | null
}) {
  return (
    <ProjectSsrDataContext.Provider value={value}>
      {children}
    </ProjectSsrDataContext.Provider>
  )
}

