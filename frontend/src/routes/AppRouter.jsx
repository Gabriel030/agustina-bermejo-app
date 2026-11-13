import { createElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { AnyAuthenticatedRoute } from '@/routes/AnyAuthenticatedRoute';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { firstRealPage, pageLabel } from '@/routes/menuAccess';
import { ModuleGate } from '@/routes/ModuleGate';
import { PUBLIC_ROUTES, SHARED_ROUTES, roleRoutesFor } from '@/routes/featureRoutes';
import { GlassCard } from '@/components/GlassCard';
import { OfflineState } from '@/pwa/OfflineState';
import { CargaInicial } from '@/pages/CargaInicial';
import { Placeholder } from '@/pages/Placeholder';
import { NotFound } from '@/pages/NotFound';
import { lazyPage } from '@/routes/lazyPage';
import { PublicLayout } from '@/layouts/PublicLayout';
import { useStartupPrefetch } from '@/routes/useStartupPrefetch';
import { DesignCatalog } from '@/pages/DesignCatalog';

const showCatalog = import.meta.env.DEV || import.meta.env.VITE_ENABLE_CATALOG === 'true';

const AutoservicioLayout = lazyPage(() => import('@/layouts/AutoservicioLayout'), 'AutoservicioLayout');

function layoutFor(role) {
  return (role && ROLE_CONFIG[role]?.Layout) || AutoservicioLayout;
}

function SharedRoutesLayout() {
  const { usuario } = useAuth();
  return createElement(layoutFor(usuario?.roles[0]?.toLowerCase()));
}

function placeholderElement(role, page, pages) {
  const target = page === 'inicio' ? firstRealPage(role, pages) : null;
  if (target) return <Navigate to={`/${role}/${target}`} replace />;
  return <Placeholder titulo={pageLabel(page)} />;
}

function renderRoleRoutes(role, pages) {
  const routes = roleRoutesFor(role);
  const covered = new Set(routes.map((route) => route.path));
  return [
    ...routes.map(({ path, element }) => <Route key={path} path={path} element={<ModuleGate role={role} path={path}>{element}</ModuleGate>} />),
    ...pages
      .filter((page) => !covered.has(page))
      .map((page) => <Route key={page} path={page} element={placeholderElement(role, page, pages)} />),
  ];
}

function OfflineShellRoutes({ retry }) {
  const { usuario } = useAuth();
  const role = usuario.roles[0]?.toLowerCase();
  return (
    <Routes>
      <Route element={createElement(layoutFor(role))}>
        <Route
          path="*"
          element={
            <div className="flex flex-1 items-center justify-center p-4">
              <GlassCard className="w-full max-w-md p-6">
                <OfflineState onRetry={retry} />
              </GlassCard>
            </div>
          }
        />
      </Route>
    </Routes>
  );
}

export function AppRouter() {
  const { cargando, usuario, sessionPending, retrySession, offlineProfile } = useAuth();

  useStartupPrefetch(sessionPending ? null : usuario);

  if (cargando) return <CargaInicial />;

  if (sessionPending && offlineProfile) return <OfflineShellRoutes retry={retrySession} />;

  if (sessionPending) {
    return (
      <div className="flex min-h-screen items-center justify-center p-4">
        <GlassCard className="w-full max-w-md p-6">
          <OfflineState onRetry={retrySession} />
        </GlassCard>
      </div>
    );
  }

  return (
    <Routes>
      <Route element={<PublicLayout />}>
        {PUBLIC_ROUTES.map(({ path, element }) => (
          <Route key={path} path={path} element={element} />
        ))}
        {showCatalog && <Route path="/dev/catalog" element={<DesignCatalog />} />}
      </Route>

      <Route
        element={
          <AnyAuthenticatedRoute>
            <SharedRoutesLayout />
          </AnyAuthenticatedRoute>
        }
      >
        {SHARED_ROUTES.map(({ path, element }) => (
          <Route key={path} path={path} element={element} />
        ))}
        <Route path="*" element={<NotFound />} />
      </Route>

      {Object.entries(ROLE_CONFIG).map(([role, { Layout, pages }]) => (
        <Route
          key={role}
          path={`/${role}`}
          element={
            <ProtectedRoute rol={role.toUpperCase()}>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to={firstRealPage(role, pages) ?? pages[0]} replace />} />
          {renderRoleRoutes(role, pages)}
          <Route path="*" element={<NotFound />} />
        </Route>
      ))}

      <Route
        path="/"
        element={
          <Navigate
            to={
              !usuario
                ? '/login'
                : usuario.roles.length
                  ? `/${usuario.roles[0].toLowerCase()}/inicio`
                  : '/catalogo'
            }
            replace
          />
        }
      />
    </Routes>
  );
}
