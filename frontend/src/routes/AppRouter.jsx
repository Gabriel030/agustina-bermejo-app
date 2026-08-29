import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { AnyAuthenticatedRoute } from '@/routes/AnyAuthenticatedRoute';
import { Login } from '@/pages/Login';
import { Registrarse } from '@/pages/Registrarse';
import { Catalogo } from '@/pages/Catalogo';
import { DetalleCarrera } from '@/pages/DetalleCarrera';
import { DetalleCurso } from '@/pages/DetalleCurso';
import { InscripcionCarrera } from '@/pages/InscripcionCarrera';
import { InscripcionCurso } from '@/pages/InscripcionCurso';
import { DetalleSolicitud } from '@/pages/DetalleSolicitud';
import { NoAutorizado } from '@/pages/NoAutorizado';
import { CargaInicial } from '@/pages/CargaInicial';
import { Placeholder } from '@/pages/Placeholder';
import { AlumnoInicio } from '@/pages/AlumnoInicio';
import { AlumnoLayout } from '@/layouts/AlumnoLayout';
import { DocenteLayout } from '@/layouts/DocenteLayout';
import { PreceptorLayout } from '@/layouts/PreceptorLayout';
import { AdministrativoLayout } from '@/layouts/AdministrativoLayout';
import { DirectorLayout } from '@/layouts/DirectorLayout';
import { AutoservicioLayout } from '@/layouts/AutoservicioLayout';

// path -> título de pantalla mostrado en el placeholder, hasta que la fase
// correspondiente del roadmap implemente la pantalla real. `paginasReales`
// (opcional) pisa el Placeholder para las páginas ya implementadas — Épica 2
// Fase 1 empieza por "inicio", el resto de Alumno sigue en placeholder.
const RUTAS_POR_ROL = {
  alumno: {
    Layout: AlumnoLayout,
    paginas: ['inicio', 'mis-cursos', 'notas', 'asistencia', 'mesas-de-examen', 'opciones'],
    paginasReales: { inicio: AlumnoInicio },
  },
  docente: { Layout: DocenteLayout, paginas: ['inicio', 'tomar-asistencia'] },
  preceptor: { Layout: PreceptorLayout, paginas: ['inicio', 'alumnos', 'oferta-academica', 'mesas-de-examen', 'periodos'] },
  administrativo: { Layout: AdministrativoLayout, paginas: ['inicio', 'alumnos', 'facturacion', 'constancias'] },
  director: { Layout: DirectorLayout, paginas: ['inicio', 'aprobaciones'] },
};

// /catalogo y /mis-solicitudes* son rutas compartidas (decisión 1 de la Spec
// técnica de Épica 7 Fase 2): quien todavía no tiene ningún rol ve el sidebar
// reducido de AutoservicioLayout (Catálogo + Mis solicitudes, nada más existe
// para esa persona todavía); quien YA tiene un rol (ej. Alumno cursando que
// quiere sumarse a un curso suelto) tiene que seguir viendo SU sidebar
// completo (Inicio, Mis cursos, etc.) — perderlo de vista acá sería sacarle
// la navegación principal solo por visitar el catálogo.
function AutoservicioOCapaDelRol() {
  const { usuario } = useAuth();
  const rol = usuario?.roles[0]?.toLowerCase();
  const Layout = (rol && RUTAS_POR_ROL[rol]?.Layout) || AutoservicioLayout;
  return <Layout />;
}

export function AppRouter() {
  const { cargando, usuario } = useAuth();

  if (cargando) return <CargaInicial />;

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/registrarse" element={<Registrarse />} />
      <Route path="/no-autorizado" element={<NoAutorizado />} />

      <Route
        element={
          <AnyAuthenticatedRoute>
            <AutoservicioOCapaDelRol />
          </AnyAuthenticatedRoute>
        }
      >
        <Route path="/catalogo" element={<Catalogo />} />
        <Route path="/catalogo/carreras/:id" element={<DetalleCarrera />} />
        <Route path="/catalogo/carreras/:id/inscribirme" element={<InscripcionCarrera />} />
        <Route path="/catalogo/cursos/:id" element={<DetalleCurso />} />
        <Route path="/catalogo/cursos/:id/inscribirme" element={<InscripcionCurso />} />
        {/* "Mis solicitudes" pasó a ser una tab de /catalogo (Épica 7 Fase 3) —
            la ruta vieja redirige en vez de 404 para no romper bookmarks. */}
        <Route path="/mis-solicitudes" element={<Navigate to="/catalogo?tab=mis-solicitudes" replace />} />
        <Route path="/mis-solicitudes/:id" element={<DetalleSolicitud />} />
      </Route>

      {Object.entries(RUTAS_POR_ROL).map(([rol, { Layout, paginas, paginasReales }]) => (
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
          {paginas.map((pagina) => {
            const PaginaReal = paginasReales?.[pagina];
            return <Route key={pagina} path={pagina} element={PaginaReal ? <PaginaReal /> : <Placeholder titulo={pagina} />} />;
          })}
        </Route>
      ))}

      <Route path="/" element={<Navigate to={usuario ? `/${usuario.roles[0]?.toLowerCase()}/inicio` : '/login'} replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
