import { createContext, useContext } from 'react'
import type { ProjectSsrData } from './projectTypes'

export const ProjectSsrDataContext = createContext<ProjectSsrData | null>(null)

export function useProjectSsrData() {
  return useContext(ProjectSsrDataContext)
}

