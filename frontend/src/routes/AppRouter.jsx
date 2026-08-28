import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { AnyAuthenticatedRoute } from '@/routes/AnyAuthenticatedRoute';
import { Login } from '@/pages/Login';
import { Registrarse } from '@/pages/Registrarse';
import { Catalogo } from '@/pages/Catalogo';
import { MisSolicitudes } from '@/pages/MisSolicitudes';
import { DetalleSolicitud } from '@/pages/DetalleSolicitud';
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
        <Route path="/mis-solicitudes" element={<MisSolicitudes />} />
        <Route path="/mis-solicitudes/:id" element={<DetalleSolicitud />} />
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
