import { Catalogo } from '@/pages/Catalogo';
import { DetalleCarrera } from '@/pages/DetalleCarrera';
import { DetalleCurso } from '@/pages/DetalleCurso';
import { InscripcionCarrera } from '@/pages/InscripcionCarrera';
import { InscripcionCurso } from '@/pages/InscripcionCurso';
import { DetalleSolicitud } from '@/pages/DetalleSolicitud';
import { AlumnoMisCarreras } from '@/pages/AlumnoMisCarreras';
import { AlumnoMateriaDetalle } from '@/pages/AlumnoMateriaDetalle';
import { DirectorAprobacionesExcepciones } from '@/pages/DirectorAprobacionesExcepciones';
import { DirectorExcepcionDetalle } from '@/pages/DirectorExcepcionDetalle';
import { PadronAlumnos } from '@/features/identity/pages/PadronAlumnos';
import { Navigate } from 'react-router-dom';

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
    { roles: ['alumno'], path: 'mis-carreras', element: <AlumnoMisCarreras /> },
    { roles: ['alumno'], path: 'mis-carreras/materias/:id', element: <AlumnoMateriaDetalle /> },
    { roles: ['director'], path: 'aprobaciones/excepciones', element: <DirectorAprobacionesExcepciones /> },
    { roles: ['director'], path: 'aprobaciones/excepciones/:id', element: <DirectorExcepcionDetalle /> },
  ],
};
