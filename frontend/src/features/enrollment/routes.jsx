const Catalogo = lazyPage(() => import('@/features/enrollment/pages/Catalogo'), 'Catalogo');
const DetalleCarrera = lazyPage(() => import('@/features/enrollment/pages/DetalleCarrera'), 'DetalleCarrera');
const DetalleCurso = lazyPage(() => import('@/features/enrollment/pages/DetalleCurso'), 'DetalleCurso');
const InscripcionCarrera = lazyPage(() => import('@/features/enrollment/pages/InscripcionCarrera'), 'InscripcionCarrera');
const InscripcionCurso = lazyPage(() => import('@/features/enrollment/pages/InscripcionCurso'), 'InscripcionCurso');
const DetalleSolicitud = lazyPage(() => import('@/features/enrollment/pages/DetalleSolicitud'), 'DetalleSolicitud');
const AlumnoMisCursos = lazyPage(() => import('@/features/enrollment/pages/AlumnoMisCursos'), 'AlumnoMisCursos');
const AlumnoMisCarreras = lazyPage(() => import('@/features/workspace/pages/AlumnoMisCarreras'), 'AlumnoMisCarreras');
const AlumnoMateriaDetalle = lazyPage(() => import('@/features/workspace/pages/AlumnoMateriaDetalle'), 'AlumnoMateriaDetalle');
const DirectorAprobacionesExcepciones = lazyPage(() => import('@/features/enrollment/pages/DirectorAprobacionesExcepciones'), 'DirectorAprobacionesExcepciones');
const DirectorExcepcionDetalle = lazyPage(() => import('@/features/enrollment/pages/DirectorExcepcionDetalle'), 'DirectorExcepcionDetalle');
const InscribirAlumno = lazyPage(() => import('@/features/enrollment/pages/InscribirAlumno'), 'InscribirAlumno');
const SolicitudesDeIngreso = lazyPage(() => import('@/features/enrollment/pages/SolicitudesDeIngreso'), 'SolicitudesDeIngreso');
const SolicitudDeIngresoDetalle = lazyPage(() => import('@/features/enrollment/pages/SolicitudDeIngresoDetalle'), 'SolicitudDeIngresoDetalle');
const PadronAlumnos = lazyPage(() => import('@/features/identity/pages/PadronAlumnos'), 'PadronAlumnos');
import { Navigate } from 'react-router-dom';
import { lazyPage } from '@/routes/lazyPage';

export const routes = {
  public: [],
  shared: [
    { path: '/catalogo', element: <Catalogo /> },
    { path: '/catalogo/carreras/:id', element: <DetalleCarrera /> },
    { path: '/catalogo/carreras/:id/inscribirme', element: <InscripcionCarrera /> },
    { path: '/catalogo/cursos/:id', element: <DetalleCurso /> },
    { path: '/catalogo/cursos/:id/inscribirme', element: <InscripcionCurso /> },
    { path: '/mis-solicitudes', element: <Navigate to="/catalogo?tab=mis-solicitudes" replace /> },
    { path: '/mis-solicitudes/:id', element: <DetalleSolicitud /> },
  ],
  role: [
    { roles: ['preceptor', 'administrativo', 'director'], path: 'alumnos', element: <PadronAlumnos /> },
    { roles: ['preceptor', 'administrativo', 'director'], path: 'alumnos/:id/inscribir', element: <InscribirAlumno /> },
    { roles: ['preceptor', 'administrativo'], path: 'solicitudes-de-ingreso', element: <SolicitudesDeIngreso /> },
    { roles: ['preceptor', 'administrativo'], path: 'solicitudes-de-ingreso/:id', element: <SolicitudDeIngresoDetalle /> },
    { roles: ['alumno'], path: 'mis-cursos', element: <AlumnoMisCursos /> },
    { roles: ['alumno'], path: 'mis-carreras', element: <AlumnoMisCarreras /> },
    { roles: ['alumno'], path: 'mis-carreras/materias/:id', element: <AlumnoMateriaDetalle /> },
    { roles: ['director'], path: 'aprobaciones/excepciones', element: <DirectorAprobacionesExcepciones /> },
    { roles: ['director'], path: 'aprobaciones/excepciones/:id', element: <DirectorExcepcionDetalle /> },
  ],
};
