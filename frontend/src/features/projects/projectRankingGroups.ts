import type { ProjectHubEntry, ProjectHubPage, ProjectRankingScope } from './projectTypes'

export interface ProjectRankingGroup {
  id: string
  title: string
  description: string | null
  projects: ProjectHubEntry[]
}

export function projectRankingGroups(hub: ProjectHubPage): ProjectRankingGroup[] {
  if (hub.kind !== 'CATEGORY' || hub.category !== 'AIRPORT') {
    return [{ id: 'ranking', title: 'Ranking', description: null, projects: hub.projects }]
  }

  const definitions: Array<Omit<ProjectRankingGroup, 'projects'> & { scope: ProjectRankingScope }> = [
    {
      id: 'airports', title: 'Airports and major programmes',
      description: 'Whole airports, phases and redevelopment programmes. Check each project’s cost scope and source.',
      scope: 'WHOLE',
    },
    {
      id: 'expansions', title: 'Terminals and expansion projects',
      description: 'Named terminal, runway and expansion packages. These figures do not represent the cost of an entire airport.',
      scope: 'EXPANSION',
    },
    {
      id: 'airport-rail', title: 'Airport rail links',
      description: 'Rail and metro connections serving airports, shown separately from airport construction costs.',
      scope: 'ACCESS_LINK',
    },
  ]
  return definitions.map(({ scope, ...group }) => ({
    ...group,
    projects: hub.projects.filter(project => project.rankingScope === scope).map((project, index) => ({ ...project, rank: index + 1 })),
  })).filter(group => group.projects.length > 0)
}
