import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { AnyAuthenticatedRoute } from '@/routes/AnyAuthenticatedRoute';
import { MiPerfil } from '@/features/gestion-personas/pages/MiPerfil';
import { PadronAlumnos } from '@/features/gestion-personas/pages/PadronAlumnos';
import { LegajoAlumno } from '@/features/gestion-personas/pages/LegajoAlumno';
import { NuevoAlumno } from '@/features/gestion-personas/pages/NuevoAlumno';
import { GestionPersonal } from '@/features/gestion-personas/pages/GestionPersonal';
import { LegajoPersonal } from '@/features/gestion-personas/pages/LegajoPersonal';
import { Login } from '@/pages/Login';
import { Registrarse } from '@/pages/Registrarse';
import { ForgotPassword } from '@/pages/ForgotPassword';
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
import { AlumnoPagos } from '@/pages/AlumnoPagos';
import { AlumnoPagoDetalle } from '@/pages/AlumnoPagoDetalle';
import { AlumnoPagarSeleccion } from '@/pages/AlumnoPagarSeleccion';
import { AlumnoPagarTransferencia } from '@/pages/AlumnoPagarTransferencia';
import { AlumnoPagarOnline } from '@/pages/AlumnoPagarOnline';
import { AlumnoPagoEstado } from '@/pages/AlumnoPagoEstado';
import { DocenteMisComisiones } from '@/pages/DocenteMisComisiones';
import { DocenteComisionDetalle } from '@/features/avisos/pages/DocenteComisionDetalle';
import { AvisoWizard } from '@/features/avisos/pages/AvisoWizard';
import { AvisoEditar } from '@/features/avisos/pages/AvisoEditar';
import { DocenteTomarAsistencia } from '@/pages/DocenteTomarAsistencia';
import { AdministrativoFacturacion } from '@/pages/AdministrativoFacturacion';
import { AdministrativoSolicitudesAsistencia } from '@/pages/AdministrativoSolicitudesAsistencia';
import { AdministrativoSubjects } from '@/features/gestion-academica/pages/AdministrativoSubjects';
import { AdministrativoSubjectWizard } from '@/features/gestion-academica/pages/AdministrativoSubjectWizard';
import { CarreraWizard } from '@/features/gestion-academica/pages/CarreraWizard';
import { CarreraDetalle } from '@/features/gestion-academica/pages/CarreraDetalle';
import { CarreraEditarDatos } from '@/features/gestion-academica/pages/CarreraEditarDatos';
import { CursoWizard } from '@/features/gestion-academica/pages/CursoWizard';
import { CursoDetalle } from '@/features/gestion-academica/pages/CursoDetalle';
import { CursoEditarDatos } from '@/features/gestion-academica/pages/CursoEditarDatos';
import { PreceptorMesaExamen } from '@/pages/PreceptorMesaExamen';
import { PreceptorOfertaAcademica } from '@/features/oferta-academica/pages/PreceptorOfertaAcademica';
import { PreceptorDetalleCarrera } from '@/features/oferta-academica/pages/PreceptorDetalleCarrera';
import { PreceptorDetalleCurso } from '@/features/oferta-academica/pages/PreceptorDetalleCurso';
import { PreceptorCalendario } from '@/features/oferta-academica/pages/PreceptorCalendario';
import { PreceptorNuevoCiclo } from '@/features/oferta-academica/pages/PreceptorNuevoCiclo';
import { DirectorSubjectReview } from '@/features/gestion-academica/pages/DirectorSubjectReview';
import { DirectorAprobaciones } from '@/pages/DirectorAprobaciones';
import { DirectorAprobacionesConfigEvaluacion } from '@/pages/DirectorAprobacionesConfigEvaluacion';
import { DirectorAprobacionesEgresos } from '@/pages/DirectorAprobacionesEgresos';
import { DirectorEgresoDetalle } from '@/pages/DirectorEgresoDetalle';
import { DirectorAprobacionesExcepciones } from '@/pages/DirectorAprobacionesExcepciones';
import { DirectorExcepcionDetalle } from '@/pages/DirectorExcepcionDetalle';
import { DirectorEsquemasEvaluacion } from '@/pages/DirectorEsquemasEvaluacion';
import { DirectorRevisarConfiguracion } from '@/pages/DirectorRevisarConfiguracion';
import { DirectorFinanciero } from '@/pages/DirectorFinanciero';
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
    paginas: ['inicio', 'mis-cursos', 'mis-carreras', 'pagos', 'opciones'],
    paginasReales: { inicio: AlumnoInicio, 'mis-carreras': AlumnoMisCarreras, pagos: AlumnoPagos },
  },
  docente: {
    Layout: DocenteLayout,
    paginas: ['inicio', 'mis-comisiones', 'tomar-asistencia'],
    paginasReales: { 'mis-comisiones': DocenteMisComisiones, 'tomar-asistencia': DocenteTomarAsistencia },
  },
  preceptor: {
    Layout: PreceptorLayout,
    paginas: ['inicio', 'alumnos', 'oferta-academica', 'mesas-de-examen', 'periodos'],
    paginasReales: { alumnos: PadronAlumnos, 'mesas-de-examen': PreceptorMesaExamen, 'oferta-academica': PreceptorOfertaAcademica },
  },
  administrativo: {
    Layout: AdministrativoLayout,
    paginas: ['inicio', 'alumnos', 'personal', 'facturacion', 'constancias', 'solicitudes-de-asistencia', 'gestion-academica'],
    paginasReales: { alumnos: PadronAlumnos, personal: GestionPersonal, facturacion: AdministrativoFacturacion, 'solicitudes-de-asistencia': AdministrativoSolicitudesAsistencia, 'gestion-academica': AdministrativoSubjects },
  },
  director: {
    Layout: DirectorLayout,
    paginas: ['inicio', 'alumnos', 'personal', 'aprobaciones', 'gestion-academica', 'esquemas-evaluacion', 'financiero'],
    paginasReales: { alumnos: PadronAlumnos, personal: GestionPersonal, aprobaciones: DirectorAprobaciones, 'gestion-academica': DirectorSubjectReview, 'esquemas-evaluacion': DirectorEsquemasEvaluacion, financiero: DirectorFinanciero },
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
        <Route path="/verificar-email" element={<VerificacionEmail />} />
        <Route path="/no-autorizado" element={<NoAutorizado />} />
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
        <Route path="/perfil" element={<MiPerfil />} />
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
          {rol === 'alumno' && <Route path="pagos/estado" element={<AlumnoPagoEstado />} />}
          {rol === 'alumno' && <Route path="pagos/curso/:enrollmentId" element={<AlumnoPagoDetalle />} />}
          {rol === 'alumno' && <Route path="pagos/carrera/:enrollmentId" element={<AlumnoPagoDetalle />} />}
          {rol === 'alumno' && <Route path="pagos/:invoiceId/seleccion" element={<AlumnoPagarSeleccion />} />}
          {rol === 'alumno' && <Route path="pagos/:invoiceId/transferencia" element={<AlumnoPagarTransferencia />} />}
          {rol === 'alumno' && <Route path="pagos/:invoiceId/online" element={<AlumnoPagarOnline />} />}
          {rol === 'docente' && <Route path="mis-comisiones/:sectionId" element={<DocenteComisionDetalle />} />}
          {rol === 'docente' && <Route path="mis-comisiones/:sectionId/avisos/nuevo" element={<AvisoWizard />} />}
          {rol === 'docente' && <Route path="mis-comisiones/:sectionId/avisos/:avisoId/editar" element={<AvisoEditar />} />}
          {(rol === 'preceptor' || rol === 'administrativo' || rol === 'director') && (
            <Route path="alumnos/:id" element={<LegajoAlumno />} />
          )}
          {(rol === 'preceptor' || rol === 'administrativo') && (
            <Route path="alumnos/nuevo" element={<NuevoAlumno />} />
          )}
          {rol === 'administrativo' && (
            <Route path="gestion-academica/nueva" element={<AdministrativoSubjectWizard />} />
          )}
          {rol === 'administrativo' && (
            <Route path="gestion-academica/:id" element={<AdministrativoSubjectWizard />} />
          )}
          {rol === 'administrativo' && (
            <Route path="gestion-academica/carreras/nueva" element={<CarreraWizard />} />
          )}
          {rol === 'administrativo' && (
            <Route path="gestion-academica/carreras/:id" element={<CarreraDetalle />} />
          )}
          {rol === 'administrativo' && (
            <Route path="gestion-academica/carreras/:id/editar" element={<CarreraEditarDatos />} />
          )}
          {rol === 'administrativo' && (
            <Route path="gestion-academica/cursos/nueva" element={<CursoWizard />} />
          )}
          {rol === 'administrativo' && (
            <Route path="gestion-academica/cursos/:id" element={<CursoDetalle />} />
          )}
          {rol === 'administrativo' && (
            <Route path="gestion-academica/cursos/:id/editar" element={<CursoEditarDatos />} />
          )}
          {rol === 'preceptor' && (
            <Route path="oferta-academica/carreras/:programaId" element={<PreceptorDetalleCarrera />} />
          )}
          {rol === 'preceptor' && (
            <Route path="oferta-academica/cursos/:cursoId" element={<PreceptorDetalleCurso />} />
          )}
          {rol === 'preceptor' && (
            <Route path="oferta-academica/calendario" element={<PreceptorCalendario />} />
          )}
          {rol === 'preceptor' && (
            <Route path="oferta-academica/nuevo-ciclo" element={<PreceptorNuevoCiclo />} />
          )}
          {(rol === 'administrativo' || rol === 'director') && (
            <Route path="personal/:id" element={<LegajoPersonal />} />
          )}
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
