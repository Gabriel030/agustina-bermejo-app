import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { AnyAuthenticatedRoute } from '@/routes/AnyAuthenticatedRoute';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { PUBLIC_ROUTES, SHARED_ROUTES, roleRoutesFor } from '@/routes/featureRoutes';
import { CargaInicial } from '@/pages/CargaInicial';
import { Placeholder } from '@/pages/Placeholder';
import { PublicLayout } from '@/layouts/PublicLayout';
import { AutoservicioLayout } from '@/layouts/AutoservicioLayout';

function SharedRoutesLayout() {
  const { usuario } = useAuth();
  const role = usuario?.roles[0]?.toLowerCase();
  const Layout = (role && ROLE_CONFIG[role]?.Layout) || AutoservicioLayout;
  return <Layout />;
}

function renderRoleRoutes(role, pages) {
  const routes = roleRoutesFor(role);
  const covered = new Set(routes.map((route) => route.path));
  return [
    ...routes.map(({ path, element }) => <Route key={path} path={path} element={element} />),
    ...pages
      .filter((page) => !covered.has(page))
      .map((page) => <Route key={page} path={page} element={<Placeholder titulo={page} />} />),
  ];
}

export function AppRouter() {
  const { cargando, usuario } = useAuth();

  if (cargando) return <CargaInicial />;

  return (
    <Routes>
      <Route element={<PublicLayout />}>
        {PUBLIC_ROUTES.map(({ path, element }) => (
          <Route key={path} path={path} element={element} />
        ))}
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
          <Route index element={<Navigate to={pages[0]} replace />} />
          {renderRoleRoutes(role, pages)}
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
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
