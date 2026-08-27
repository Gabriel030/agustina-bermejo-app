import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { AnyAuthenticatedRoute } from '@/routes/AnyAuthenticatedRoute';
import { Login } from '@/pages/Login';
import { Registrarse } from '@/pages/Registrarse';
import { Catalogo } from '@/pages/Catalogo';
import { NoAutorizado } from '@/pages/NoAutorizado';
import { CargaInicial } from '@/pages/CargaInicial';
import { Placeholder } from '@/pages/Placeholder';
import { AlumnoLayout } from '@/layouts/AlumnoLayout';
import { DocenteLayout } from '@/layouts/DocenteLayout';
import { PreceptorLayout } from '@/layouts/PreceptorLayout';
import { AdministrativoLayout } from '@/layouts/AdministrativoLayout';
import { DirectorLayout } from '@/layouts/DirectorLayout';
import { AutoservicioLayout } from '@/layouts/AutoservicioLayout';

// path -> título de pantalla mostrado en el placeholder, hasta que la fase
// correspondiente del roadmap implemente la pantalla real.
const RUTAS_POR_ROL = {
  alumno: { Layout: AlumnoLayout, paginas: ['inicio', 'mis-cursos', 'notas', 'asistencia', 'mesas-de-examen', 'opciones'] },
  docente: { Layout: DocenteLayout, paginas: ['inicio', 'tomar-asistencia'] },
  preceptor: { Layout: PreceptorLayout, paginas: ['inicio', 'alumnos', 'oferta-academica', 'mesas-de-examen', 'periodos'] },
  administrativo: { Layout: AdministrativoLayout, paginas: ['inicio', 'alumnos', 'facturacion', 'constancias'] },
  director: { Layout: DirectorLayout, paginas: ['inicio', 'aprobaciones'] },
};

export function AppRouter() {
  const { cargando, usuario } = useAuth();

  if (cargando) return <CargaInicial />;

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/registrarse" element={<Registrarse />} />
      <Route path="/no-autorizado" element={<NoAutorizado />} />

      {/* Autoservicio (Épica 7 Fase 2): fuera de RUTAS_POR_ROL a propósito — cualquier
          persona con sesión entra, tenga rol asignado o no (ver Spec técnica - Frontend,
          registro y catálogo de autoservicio). */}
      <Route
        element={
          <AnyAuthenticatedRoute>
            <AutoservicioLayout />
          </AnyAuthenticatedRoute>
        }
      >
        <Route path="/catalogo" element={<Catalogo />} />
        <Route path="/mis-solicitudes" element={<Placeholder titulo="Mis solicitudes" />} />
      </Route>

      {Object.entries(RUTAS_POR_ROL).map(([rol, { Layout, paginas }]) => (
        <Route
          key={rol}
          path={`/${rol}`}
          element={
            <ProtectedRoute rol={rol.toUpperCase()}>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to={paginas[0]} replace />} />
          {paginas.map((pagina) => (
            <Route key={pagina} path={pagina} element={<Placeholder titulo={pagina} />} />
          ))}
        </Route>
      ))}

      <Route path="/" element={<Navigate to={usuario ? `/${usuario.roles[0]?.toLowerCase()}/inicio` : '/login'} replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
