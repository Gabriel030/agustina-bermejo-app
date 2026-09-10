import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { AnyAuthenticatedRoute } from '@/routes/AnyAuthenticatedRoute';
import { Login } from '@/pages/Login';
import { Registrarse } from '@/pages/Registrarse';
import { ForgotPassword } from '@/pages/ForgotPassword';
import { Inscripcion } from '@/pages/Inscripcion';
import { VerificacionEmail } from '@/pages/VerificacionEmail';
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
import { AlumnoMisCarreras } from '@/pages/AlumnoMisCarreras';
import { AlumnoMateriaDetalle } from '@/pages/AlumnoMateriaDetalle';
import { DocenteMisComisiones } from '@/pages/DocenteMisComisiones';
import { DocenteConfiguracionEvaluacion } from '@/pages/DocenteConfiguracionEvaluacion';
import { DocenteTomarAsistencia } from '@/pages/DocenteTomarAsistencia';
import { AdministrativoSolicitudesAsistencia } from '@/pages/AdministrativoSolicitudesAsistencia';
import { PreceptorMesaExamen } from '@/pages/PreceptorMesaExamen';
import { PadronAlumnos } from '@/pages/PadronAlumnos';
import { NuevoAlumno } from '@/pages/NuevoAlumno';
import { DirectorAprobaciones } from '@/pages/DirectorAprobaciones';
import { DirectorAprobacionesConfigEvaluacion } from '@/pages/DirectorAprobacionesConfigEvaluacion';
import { DirectorAprobacionesEgresos } from '@/pages/DirectorAprobacionesEgresos';
import { DirectorEgresoDetalle } from '@/pages/DirectorEgresoDetalle';
import { DirectorAprobacionesExcepciones } from '@/pages/DirectorAprobacionesExcepciones';
import { DirectorExcepcionDetalle } from '@/pages/DirectorExcepcionDetalle';
import { DirectorEsquemasEvaluacion } from '@/pages/DirectorEsquemasEvaluacion';
import { DirectorRevisarConfiguracion } from '@/pages/DirectorRevisarConfiguracion';
import { PublicLayout } from '@/layouts/PublicLayout';
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
    // Notas, Asistencia y Mesas de examen dejaron de ser páginas de nav de primer
    // nivel (Épica 2 Fase 2) — ahora son pestañas dentro de Mis carreras, ver
    // AlumnoMisCarreras.jsx y el comentario más abajo sobre la ruta de detalle.
    paginas: ['inicio', 'mis-cursos', 'mis-carreras', 'opciones'],
    paginasReales: { inicio: AlumnoInicio, 'mis-carreras': AlumnoMisCarreras },
  },
  docente: {
    Layout: DocenteLayout,
    paginas: ['inicio', 'mis-comisiones', 'tomar-asistencia'],
    paginasReales: { 'mis-comisiones': DocenteMisComisiones, 'tomar-asistencia': DocenteTomarAsistencia },
  },
  preceptor: {
    Layout: PreceptorLayout,
    paginas: ['inicio', 'alumnos', 'oferta-academica', 'mesas-de-examen', 'periodos'],
    paginasReales: { 'mesas-de-examen': PreceptorMesaExamen, alumnos: PadronAlumnos },
  },
  administrativo: {
    Layout: AdministrativoLayout,
    paginas: ['inicio', 'alumnos', 'facturacion', 'constancias', 'solicitudes-de-asistencia'],
    paginasReales: { 'solicitudes-de-asistencia': AdministrativoSolicitudesAsistencia, alumnos: PadronAlumnos },
  },
  director: {
    Layout: DirectorLayout,
    paginas: ['inicio', 'aprobaciones', 'esquemas-evaluacion'],
    paginasReales: { aprobaciones: DirectorAprobaciones, 'esquemas-evaluacion': DirectorEsquemasEvaluacion },
  },
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
      <Route element={<PublicLayout />}>
        <Route path="/login" element={<Login />} />
        <Route path="/registrarse" element={<Registrarse />} />
        <Route path="/olvide-mi-contrasena" element={<ForgotPassword />} />
        <Route path="/no-autorizado" element={<NoAutorizado />} />
        <Route path="/inscripcion" element={<Inscripcion />} />
        <Route path="/verificar-email" element={<VerificacionEmail />} />
      </Route>

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
          {/* Detalle de una materia puntual (Épica 2 Fase 2) — no es una "página" plana del
              nav como el resto de `paginas`, cuelga de la pestaña Materias dentro de Mis
              carreras, así que se agrega acá a mano en vez de sumarse a la lista de arriba. */}
          {rol === 'alumno' && <Route path="mis-carreras/materias/:id" element={<AlumnoMateriaDetalle />} />}
          {rol === 'docente' && <Route path="mis-comisiones/:sectionId" element={<DocenteConfiguracionEvaluacion />} />}
          {(rol === 'preceptor' || rol === 'administrativo') && <Route path="alumnos/nuevo" element={<NuevoAlumno />} />}
          {rol === 'director' && <Route path="revisar-configuracion/:sectionId" element={<DirectorRevisarConfiguracion />} />}
          {rol === 'director' && <Route path="aprobaciones/configuracion-evaluacion" element={<DirectorAprobacionesConfigEvaluacion />} />}
          {rol === 'director' && <Route path="aprobaciones/egresos" element={<DirectorAprobacionesEgresos />} />}
          {rol === 'director' && <Route path="aprobaciones/egresos/:id" element={<DirectorEgresoDetalle />} />}
          {rol === 'director' && <Route path="aprobaciones/excepciones" element={<DirectorAprobacionesExcepciones />} />}
          {rol === 'director' && <Route path="aprobaciones/excepciones/:id" element={<DirectorExcepcionDetalle />} />}
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
