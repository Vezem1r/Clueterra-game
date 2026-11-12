import {
  Globe, Palette, BookOpen, Trophy, Leaf, Utensils, Cloud,
  Users, TrendingUp, Building2, GraduationCap, Heart,
  Wrench, TreePine, Scale, HelpCircle, Lightbulb, Music,
  type LucideIcon,
} from 'lucide-react'

const CATEGORY_ICONS: Record<string, LucideIcon> = {
  GEOGRAPHY: Globe,
  CULTURE: Palette,
  HISTORY: BookOpen,
  SPORTS: Trophy,
  NATURE: Leaf,
  FOOD: Utensils,
  CLIMATE: Cloud,
  DEMOGRAPHICS: Users,
  ECONOMY: TrendingUp,
  LANDMARKS: Building2,
  EDUCATION: GraduationCap,
  HEALTH: Heart,
  INFRASTRUCTURE: Wrench,
  ENVIRONMENT: TreePine,
  POLITICS: Scale,
  TRIVIA: HelpCircle,
  GENERAL: Lightbulb,
  MUSIC: Music,
}

interface CategoryIconProps {
  category: string
  size?: number
  className?: string
}

export function CategoryIcon({ category, size = 48, className }: CategoryIconProps) {
  const Icon = CATEGORY_ICONS[category.toUpperCase()] ?? HelpCircle
  return <Icon size={size} className={className} />
}
