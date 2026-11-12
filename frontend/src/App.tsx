import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  useLocation,
} from "react-router-dom";
import { QueryClientProvider, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { lazy, Suspense, useEffect, useRef, useState, type ReactNode } from "react";

import { ErrorBoundary } from "@/components/common/ErrorBoundary";
import { useToastStore } from "@/store/toastStore";
import { CookieBanner } from "@/components/ui/CookieBanner";
import { useAuthStore } from "@/store/authStore";
import { AUTH_LOGOUT_EVENT, AUTH_REVALIDATE_EVENT } from "@/api/client";
import { useGameStore } from "@/store/gameStore";
import { useSettingsStore } from "@/store/settingsStore";
import { languageOfPath, PREFIXED_LANGUAGES } from "@/lib/locale/localePaths";
import { loadLanguage, PathLanguageContext } from "@/i18n";
import { createAppQueryClient } from "@/lib/queryClient";
import { ProjectSsrDataProvider } from "@/features/projects/ProjectSsrDataProvider";
import { COUNTRY_RANKINGS_PATH } from "@/features/projects/projectHubs";
import type { ProjectSsrData } from "@/features/projects/projectTypes";
import {
  loadAboutPage,
  loadBudgetGamePage,
  loadBudgetHowToPage,
  loadDataSourcesPage,
  loadGamesPage,
  loadHomePage,
  loadHowToPage,
  loadNotFoundPage,
  loadPrivacyPage,
  loadProjectDetailPage,
  loadProjectDirectoryPage,
  loadProjectHubPage,
  loadRiversGamePage,
  loadRiversHowToPage,
  loadCountrySilhouettesPage,
  loadCountrySilhouettesHowToPage,
  loadCountryFlagsPage,
  loadCountryFlagsHowToPage,
  loadCountriesQuizPage,
  loadCountriesQuizHowToPage,
  loadTermsPage,
} from "@/lib/pageLoaders";
import { lazyPage } from "@/lib/lazyPage";
import { SILHOUETTE_GUIDE_QUERY } from "@/pages/games/silhouettes/silhouetteGameConstants";
import { FLAG_GUIDE_QUERY } from "@/pages/games/flags/flagGameConstants";
import { QUIZ_GUIDE_QUERY } from "@/pages/games/countries-quiz/quizGameConstants";

const HomePage = lazyPage(loadHomePage);
const GamesPage = lazyPage(loadGamesPage);
const RiversGamePage = lazyPage(loadRiversGamePage);
const RiversHowToPage = lazyPage(loadRiversHowToPage);
const CountrySilhouettesPage = lazyPage(loadCountrySilhouettesPage);
const CountrySilhouettesHowToPage = lazyPage(loadCountrySilhouettesHowToPage);
const CountryFlagsPage = lazyPage(loadCountryFlagsPage);
const CountryFlagsHowToPage = lazyPage(loadCountryFlagsHowToPage);
const CountriesQuizPage = lazyPage(loadCountriesQuizPage);
const CountriesQuizHowToPage = lazyPage(loadCountriesQuizHowToPage);
const PlayPage = lazy(() =>
  import("@/pages/game/PlayPage").then((m) => ({ default: m.PlayPage })),
);
const BudgetGamePage = lazyPage(loadBudgetGamePage);
const BudgetHowToPage = lazyPage(loadBudgetHowToPage);
const ResultsPage = lazy(() =>
  import("@/pages/game/ResultsPage").then((m) => ({
    default: m.ResultsPage,
  })),
);
const LoginPage = lazy(() =>
  import("@/pages/auth/LoginPage").then((m) => ({ default: m.LoginPage })),
);
const RegisterPage = lazy(() =>
  import("@/pages/auth/RegisterPage").then((m) => ({
    default: m.RegisterPage,
  })),
);
const OAuthCallbackPage = lazy(() =>
  import("@/pages/auth/OAuthCallbackPage").then((m) => ({
    default: m.OAuthCallbackPage,
  })),
);
const LeaderboardPage = lazy(() =>
  import("@/pages/game/LeaderboardPage").then((m) => ({
    default: m.LeaderboardPage,
  })),
);
const GuideModalHost = lazy(() =>
  import("@/components/game/GuideModalHost").then((m) => ({ default: m.GuideModalHost })),
);
const AchievementPopup = lazy(() =>
  import("@/components/game/AchievementPopup").then((m) => ({ default: m.AchievementPopup })),
);
const ToastContainer = lazy(() =>
  import("@/components/ui/Toast").then((m) => ({ default: m.ToastContainer })),
);
const FriendsPage = lazy(() =>
  import("@/pages/game/FriendsPage").then((m) => ({
    default: m.FriendsPage,
  })),
);
const ProfilePage = lazy(() =>
  import("@/pages/game/ProfilePage").then((m) => ({
    default: m.ProfilePage,
  })),
);
const PublicProfilePage = lazy(() =>
  import("@/pages/game/PublicProfilePage").then((m) => ({
    default: m.PublicProfilePage,
  })),
);
const NotFoundPage = lazyPage(loadNotFoundPage);
const PrivacyPage = lazyPage(loadPrivacyPage);
const TermsPage = lazyPage(loadTermsPage);
const DataSourcesPage = lazyPage(loadDataSourcesPage);
const AboutPage = lazyPage(loadAboutPage);
const HowToPage = lazyPage(loadHowToPage);
const ProjectDirectoryPage = lazyPage(loadProjectDirectoryPage);
const ProjectDetailPage = lazyPage(loadProjectDetailPage);
const ProjectHubPage = lazyPage(loadProjectHubPage);
const LegalAcceptancePage = lazy(() =>
  import("@/pages/legal/LegalAcceptancePage").then((m) => ({
    default: m.LegalAcceptancePage,
  })),
);
const AdminLayout = lazy(() =>
  import("@/components/layout/AdminLayout").then((m) => ({
    default: m.AdminLayout,
  })),
);
const ContentAdminLayout = lazy(() =>
  import("@/components/layout/ContentAdminLayout").then((m) => ({
    default: m.ContentAdminLayout,
  })),
);
const BudgetContentReviewPage = lazy(() =>
  import("@/pages/admin/content/BudgetContentReviewPage").then((m) => ({
    default: m.BudgetContentReviewPage,
  })),
);
const RiverQuestionsPage = lazy(() =>
  import("@/pages/admin/content/RiverQuestionsPage").then((m) => ({ default: m.RiverQuestionsPage })),
);
const SilhouetteQuestionsPage = lazy(() =>
  import("@/pages/admin/content/SilhouetteQuestionsPage").then((m) => ({ default: m.SilhouetteQuestionsPage })),
);
const FlagQuestionsPage = lazy(() =>
  import("@/pages/admin/content/FlagQuestionsPage").then((m) => ({ default: m.FlagQuestionsPage })),
);
const QuizQuestionsPage = lazy(() =>
  import("@/pages/admin/content/QuizQuestionsPage").then((m) => ({ default: m.QuizQuestionsPage })),
);

const DashboardPage = lazy(() =>
  import("@/pages/admin/DashboardPage").then((m) => ({
    default: m.DashboardPage,
  })),
);
const AnalyticsPage = lazy(() =>
  import("@/pages/admin/AnalyticsPage").then((m) => ({
    default: m.AnalyticsPage,
  })),
);
const OperationsPage = lazy(() =>
  import("@/pages/admin/OperationsPage").then((m) => ({
    default: m.OperationsPage,
  })),
);
const LogsPage = lazy(() =>
  import("@/pages/admin/LogsPage").then((m) => ({ default: m.LogsPage })),
);
const HintsPage = lazy(() =>
  import("@/pages/admin/HintsPage").then((m) => ({ default: m.HintsPage })),
);
const CountriesPage = lazy(() =>
  import("@/pages/admin/CountriesPage").then((m) => ({
    default: m.CountriesPage,
  })),
);
const ChallengesPage = lazy(() =>
  import("@/pages/admin/ChallengesPage").then((m) => ({
    default: m.ChallengesPage,
  })),
);
const ReportsPage = lazy(() =>
  import("@/pages/admin/ReportsPage").then((m) => ({
    default: m.ReportsPage,
  })),
);
const StatisticsPage = lazy(() =>
  import("@/pages/admin/StatisticsPage").then((m) => ({
    default: m.StatisticsPage,
  })),
);
const UsersPage = lazy(() =>
  import("@/pages/admin/UsersPage").then((m) => ({ default: m.UsersPage })),
);
const SettingsPage = lazy(() =>
  import("@/pages/admin/SettingsPage").then((m) => ({
    default: m.SettingsPage,
  })),
);
const BadgesPage = lazy(() =>
  import("@/pages/admin/BadgesPage").then((m) => ({
    default: m.BadgesPage,
  })),
);
const IndicatorsPage = lazy(() =>
  import("@/pages/admin/IndicatorsPage").then((m) => ({
    default: m.IndicatorsPage,
  })),
);
const AiPage = lazy(() =>
  import("@/pages/admin/ai/AiPage").then((m) => ({ default: m.AiPage })),
);

const browserQueryClient = createAppQueryClient();

function PageFallback() {
  return (
    <div className="flex items-center justify-center h-screen">
      <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
    </div>
  );
}

function AdminFallback() {
  return (
    <div className="p-6 flex items-center justify-center h-64">
      <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
    </div>
  );
}

function ProtectedRoute({
  children,
  requireAdmin = false,
}: {
  children: React.ReactNode;
  requireAdmin?: boolean;
}) {
  const { isAuthenticated, user, authResolved } = useAuthStore();
  const location = useLocation();

  if (!authResolved) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
      </div>
    );
  }

  if (!isAuthenticated) {
    const redirectTo = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?redirect=${encodeURIComponent(redirectTo)}`} replace />;
  }

  if (requireAdmin && user?.role !== "ADMIN") {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}

function AppRoutes() {
  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        {/* Public Routes */}
        <Route path="/" element={<HomePage />} />
        <Route path="/games" element={<GamesPage />} />
        <Route path="/games/rivers" element={<RiversGamePage />} />
        <Route path="/games/rivers/how-to" element={<RiversHowToPage />} />
        <Route path="/games/country-silhouettes" element={<CountrySilhouettesPage />} />
        <Route path="/games/country-silhouettes/how-to" element={<CountrySilhouettesHowToPage />} />
        <Route path="/games/country-flags" element={<CountryFlagsPage />} />
        <Route path="/games/country-flags/how-to" element={<CountryFlagsHowToPage />} />
        <Route path="/games/countries-quiz" element={<CountriesQuizPage />} />
        <Route path="/games/countries-quiz/how-to" element={<CountriesQuizHowToPage />} />
        {PREFIXED_LANGUAGES.map((language) => (
          <Route key={language} path={`/${language}`}>
            <Route path="games" element={<GamesPage />} />
            <Route path="games/rivers" element={<RiversGamePage />} />
            <Route path="games/rivers/how-to" element={<RiversHowToPage />} />
            <Route path="games/country-silhouettes" element={<CountrySilhouettesPage />} />
            <Route path="games/country-silhouettes/how-to" element={<CountrySilhouettesHowToPage />} />
            <Route path="games/country-flags" element={<CountryFlagsPage />} />
            <Route path="games/country-flags/how-to" element={<CountryFlagsHowToPage />} />
            <Route path="games/countries-quiz" element={<CountriesQuizPage />} />
            <Route path="games/countries-quiz/how-to" element={<CountriesQuizHowToPage />} />
          </Route>
        ))}
        <Route path="/guide" element={<Navigate to="/?guide=1" replace />} />
        <Route path="/about" element={<AboutPage />} />
        <Route path="/how-to" element={<HowToPage />} />
        <Route path="/play" element={<PlayPage />} />
        <Route path="/budget" element={<BudgetGamePage />} />
        <Route path="/budget/how-to" element={<BudgetHowToPage />} />
        <Route path="/results" element={<ResultsPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
        <Route path="/leaderboard" element={<LeaderboardPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/data-sources" element={<DataSourcesPage />} />
        <Route path="/projects" element={<ProjectDirectoryPage />} />
        <Route path="/projects/page/:page" element={<ProjectDirectoryPage />} />
        <Route path="/projects/most-expensive" element={<ProjectHubPage />} />
        <Route path="/projects/countries" element={<Navigate to={COUNTRY_RANKINGS_PATH} replace />} />
        <Route path="/projects/most-expensive/:category" element={<ProjectHubPage />} />
        <Route path="/projects/countries/:country" element={<ProjectHubPage />} />
        <Route path="/projects/preview/:wikidataId" element={<ProjectDetailPage preview />} />
        <Route path="/projects/:slug" element={<ProjectDetailPage />} />
        <Route
          path="/legal/update"
          element={
            <ProtectedRoute>
              <LegalAcceptancePage />
            </ProtectedRoute>
          }
        />
        <Route path="/profile/:username" element={<PublicProfilePage />} />
        <Route path="/guest/profile" element={<Navigate to="/profile" replace />} />

        {/* Protected Routes */}
        <Route path="/profile" element={<ProfilePage />} />
        <Route
          path="/friends"
          element={<FriendsPage />}
        />
        {/* Admin Routes */}
        <Route
          path="/admin/content"
          element={
            <ProtectedRoute requireAdmin>
              <ContentAdminLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="budget" replace />} />
          <Route
            path="budget"
            element={
              <Suspense fallback={<AdminFallback />}>
                <BudgetContentReviewPage />
              </Suspense>
            }
          />
          <Route path="rivers" element={<Suspense fallback={<AdminFallback />}><RiverQuestionsPage /></Suspense>} />
          <Route path="silhouettes" element={<Suspense fallback={<AdminFallback />}><SilhouetteQuestionsPage /></Suspense>} />
          <Route path="flags" element={<Suspense fallback={<AdminFallback />}><FlagQuestionsPage /></Suspense>} />
          <Route path="quiz" element={<Suspense fallback={<AdminFallback />}><QuizQuestionsPage /></Suspense>} />
        </Route>
        <Route
          path="/admin"
          element={
            <ProtectedRoute requireAdmin>
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route
            index
            element={
              <Suspense fallback={<AdminFallback />}>
                <DashboardPage />
              </Suspense>
            }
          />
          <Route
            path="users"
            element={
              <Suspense fallback={<AdminFallback />}>
                <UsersPage />
              </Suspense>
            }
          />
          <Route
            path="analytics"
            element={
              <Suspense fallback={<AdminFallback />}>
                <AnalyticsPage />
              </Suspense>
            }
          />
          <Route
            path="operations"
            element={
              <Suspense fallback={<AdminFallback />}>
                <OperationsPage />
              </Suspense>
            }
          />
          <Route
            path="ai"
            element={
              <Suspense fallback={<AdminFallback />}>
                <AiPage />
              </Suspense>
            }
          />
          <Route
            path="logs"
            element={
              <Suspense fallback={<AdminFallback />}>
                <LogsPage />
              </Suspense>
            }
          />
          <Route
            path="hints"
            element={
              <Suspense fallback={<AdminFallback />}>
                <HintsPage />
              </Suspense>
            }
          />
          <Route
            path="countries"
            element={
              <Suspense fallback={<AdminFallback />}>
                <CountriesPage />
              </Suspense>
            }
          />
          <Route
            path="challenges"
            element={
              <Suspense fallback={<AdminFallback />}>
                <ChallengesPage />
              </Suspense>
            }
          />
          <Route
            path="reports"
            element={
              <Suspense fallback={<AdminFallback />}>
                <ReportsPage />
              </Suspense>
            }
          />
          <Route
            path="statistics"
            element={
              <Suspense fallback={<AdminFallback />}>
                <StatisticsPage />
              </Suspense>
            }
          />
          <Route
            path="badges"
            element={
              <Suspense fallback={<AdminFallback />}>
                <BadgesPage />
              </Suspense>
            }
          />
          <Route
            path="indicators"
            element={
              <Suspense fallback={<AdminFallback />}>
                <IndicatorsPage />
              </Suspense>
            }
          />
          <Route
            path="settings"
            element={
              <Suspense fallback={<AdminFallback />}>
                <SettingsPage />
              </Suspense>
            }
          />
        </Route>

        {/* 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}

function AppShell() {
  const location = useLocation();
  const { authResolved, isAuthenticated, user } = useAuthStore();

  const legalAllowedPaths = new Set([
    "/legal/update",
    "/terms",
    "/privacy",
    "/data-sources",
  ]);
  const legalAllowed = legalAllowedPaths.has(location.pathname);
  if (
    authResolved &&
    isAuthenticated &&
    user?.needsLegalAcceptance &&
    !legalAllowed
  ) {
    return <Navigate to="/legal/update" replace />;
  }

  return <AppRoutes />;
}

function OptionalGuideModal() {
  const { search } = useLocation();
  const params = new URLSearchParams(search);
  const requested = params.get("about") === "1" || ["1", "budget", "river", SILHOUETTE_GUIDE_QUERY, FLAG_GUIDE_QUERY, QUIZ_GUIDE_QUERY].includes(params.get("guide") ?? "");
  const [mounted, setMounted] = useState(requested);
  useEffect(() => {
    const timeout = window.setTimeout(() => setMounted(requested), requested ? 0 : 300);
    return () => window.clearTimeout(timeout);
  }, [requested]);
  return mounted ? <Suspense fallback={null}><GuideModalHost /></Suspense> : null;
}

function AuthenticatedAchievementPopup() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return isAuthenticated
    ? <Suspense fallback={null}><AchievementPopup /></Suspense>
    : null;
}

function OnDemandToasts() {
  const [activated, setActivated] = useState(() => useToastStore.getState().toasts.length > 0);
  useEffect(() => useToastStore.subscribe((state) => {
    if (state.toasts.length > 0) setActivated(true);
  }), []);
  return activated ? <Suspense fallback={null}><ToastContainer /></Suspense> : null;
}

function AuthBridge() {
  const { revalidate, authResolved, isAuthenticated, user } = useAuthStore();
  const queryClient = useQueryClient();
  const principalRef = useRef<string | null>(null);

  useEffect(() => {
    const handleLogout = () => {
      void useAuthStore.getState().logout();
    };
    const handleLegalRequired = () => {
      useAuthStore.getState().setLegalAcceptanceRequired(true);
    };
    const handleRevalidate = () => {
      void revalidate();
    };

    void revalidate();

    window.addEventListener(AUTH_LOGOUT_EVENT, handleLogout);
    window.addEventListener(AUTH_REVALIDATE_EVENT, handleRevalidate);
    window.addEventListener("auth:legal-required", handleLegalRequired);

    return () => {
      window.removeEventListener(AUTH_LOGOUT_EVENT, handleLogout);
      window.removeEventListener(AUTH_REVALIDATE_EVENT, handleRevalidate);
      window.removeEventListener("auth:legal-required", handleLegalRequired);
    };
  }, [revalidate]);

  useEffect(() => {
    if (!authResolved) {
      return;
    }

    const principal = isAuthenticated ? `user:${user?.id ?? "unknown"}` : "guest";
    const previousPrincipal = principalRef.current;
    principalRef.current = principal;

    if (previousPrincipal === null || previousPrincipal === principal) {
      return;
    }

    useGameStore.getState().resetGame();
    [
      ["profile"],
      ["guest-profile"],
      ["today-session"],
      ["today-session-summary"],
      ["today-results"],
      ["guest-progress"],
      ["unacknowledged-badges"],
      ["friends"],
    ].forEach((queryKey) => queryClient.removeQueries({ queryKey }));
  }, [authResolved, isAuthenticated, queryClient, user?.id]);

  return null;
}

function SettingsBridge() {
  const queryClient = useQueryClient();
  const { pathname } = useLocation();
  const pathLanguage = languageOfPath(pathname);
  const storedLanguage = useSettingsStore((state) => state.language);
  const activeLanguage = pathLanguage ?? storedLanguage;

  useEffect(() => {
    void loadLanguage(activeLanguage).catch(() => undefined);
  }, [activeLanguage]);

  useEffect(() => {
    const languageBeforeHydration = useSettingsStore.getState().language;

    void Promise.resolve(useSettingsStore.persist.rehydrate()).then(() => {
      const urlLanguage = languageOfPath(window.location.pathname);
      if (urlLanguage) useSettingsStore.getState().setLanguage(urlLanguage);
      if (useSettingsStore.getState().language !== languageBeforeHydration) {
        void queryClient.invalidateQueries();
      }
    });
  }, [queryClient]);

  useEffect(() => {
    if (pathLanguage && useSettingsStore.getState().language !== pathLanguage) {
      useSettingsStore.getState().setLanguage(pathLanguage);
    }
  }, [pathLanguage]);

  return null;
}

interface AppRuntimeProps {
  initialProjectData?: ProjectSsrData | null;
  queryClient: QueryClient;
  router: ReactNode;
}

export function AppRuntime({ initialProjectData = null, queryClient, router }: AppRuntimeProps) {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ProjectSsrDataProvider value={initialProjectData}>
          {router}
        </ProjectSsrDataProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}

export function AppContents() {
  const { pathname } = useLocation();

  return (
    <PathLanguageContext.Provider value={languageOfPath(pathname)}>
      <SettingsBridge />
      <AuthBridge />
      <AppShell />
      <OptionalGuideModal />
      <AuthenticatedAchievementPopup />
      <OnDemandToasts />
      <CookieBanner />
    </PathLanguageContext.Provider>
  );
}

function App({ initialProjectData = null }: { initialProjectData?: ProjectSsrData | null }) {
  return (
    <AppRuntime
      initialProjectData={initialProjectData}
      queryClient={browserQueryClient}
      router={
        <BrowserRouter>
          <AppContents />
        </BrowserRouter>
      }
    />
  );
}

export default App;
