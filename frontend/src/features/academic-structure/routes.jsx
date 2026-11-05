import { lazyPage } from '@/routes/lazyPage';
const AdministrativoSubjects = lazyPage(() => import('@/features/academic-structure/pages/AdministrativoSubjects'), 'AdministrativoSubjects');
const AdministrativoSubjectWizard = lazyPage(() => import('@/features/academic-structure/pages/AdministrativoSubjectWizard'), 'AdministrativoSubjectWizard');
const CarreraWizard = lazyPage(() => import('@/features/academic-structure/pages/CarreraWizard'), 'CarreraWizard');
const DirectorAniosLectivos = lazyPage(() => import('@/features/academic-structure/pages/DirectorAniosLectivos'), 'DirectorAniosLectivos');
const CarreraDetalle = lazyPage(() => import('@/features/academic-structure/pages/CarreraDetalle'), 'CarreraDetalle');
const CarreraEditarDatos = lazyPage(() => import('@/features/academic-structure/pages/CarreraEditarDatos'), 'CarreraEditarDatos');
const CursoWizard = lazyPage(() => import('@/features/academic-structure/pages/CursoWizard'), 'CursoWizard');
const CursoDetalle = lazyPage(() => import('@/features/academic-structure/pages/CursoDetalle'), 'CursoDetalle');
const CursoEditarDatos = lazyPage(() => import('@/features/academic-structure/pages/CursoEditarDatos'), 'CursoEditarDatos');
const DirectorSubjectReview = lazyPage(() => import('@/features/academic-structure/pages/DirectorSubjectReview'), 'DirectorSubjectReview');
const PreceptorOfertaAcademica = lazyPage(() => import('@/features/academic-structure/pages/PreceptorOfertaAcademica'), 'PreceptorOfertaAcademica');
const PreceptorDetalleCarrera = lazyPage(() => import('@/features/academic-structure/pages/PreceptorDetalleCarrera'), 'PreceptorDetalleCarrera');
const PreceptorDetalleCurso = lazyPage(() => import('@/features/academic-structure/pages/PreceptorDetalleCurso'), 'PreceptorDetalleCurso');
const PreceptorCalendario = lazyPage(() => import('@/features/academic-structure/pages/PreceptorCalendario'), 'PreceptorCalendario');
const PreceptorPeriodos = lazyPage(() => import('@/features/academic-structure/pages/PreceptorPeriodos'), 'PreceptorPeriodos');
const PreceptorPeriodoForm = lazyPage(() => import('@/features/academic-structure/pages/PreceptorPeriodoForm'), 'PreceptorPeriodoForm');
const PreceptorNuevoCiclo = lazyPage(() => import('@/features/academic-structure/pages/PreceptorNuevoCiclo'), 'PreceptorNuevoCiclo');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['administrativo'], path: 'gestion-academica', element: <AdministrativoSubjects /> },
    { roles: ['administrativo'], path: 'gestion-academica/nueva', element: <AdministrativoSubjectWizard /> },
    { roles: ['administrativo'], path: 'gestion-academica/:id', element: <AdministrativoSubjectWizard /> },
    { roles: ['administrativo'], path: 'gestion-academica/carreras/nueva', element: <CarreraWizard /> },
    { roles: ['administrativo'], path: 'gestion-academica/carreras/:id', element: <CarreraDetalle /> },
    { roles: ['administrativo'], path: 'gestion-academica/carreras/:id/editar', element: <CarreraEditarDatos /> },
    { roles: ['administrativo'], path: 'gestion-academica/cursos/nueva', element: <CursoWizard /> },
    { roles: ['administrativo'], path: 'gestion-academica/cursos/:id', element: <CursoDetalle /> },
    { roles: ['administrativo'], path: 'gestion-academica/cursos/:id/editar', element: <CursoEditarDatos /> },
    { roles: ['director'], path: 'gestion-academica', element: <DirectorSubjectReview /> },
    { roles: ['director'], path: 'financiero/anios-lectivos', element: <DirectorAniosLectivos /> },
    { roles: ['preceptor'], path: 'oferta-academica', element: <PreceptorOfertaAcademica /> },
    { roles: ['preceptor'], path: 'oferta-academica/carreras/:programaId', element: <PreceptorDetalleCarrera /> },
    { roles: ['preceptor'], path: 'oferta-academica/cursos/:cursoId', element: <PreceptorDetalleCurso /> },
    { roles: ['preceptor'], path: 'oferta-academica/calendario', element: <PreceptorCalendario /> },
    { roles: ['preceptor'], path: 'oferta-academica/nuevo-ciclo', element: <PreceptorNuevoCiclo /> },
    { roles: ['preceptor', 'administrativo', 'director'], path: 'periodos', element: <PreceptorPeriodos /> },
    { roles: ['preceptor', 'administrativo', 'director'], path: 'periodos/nuevo', element: <PreceptorPeriodoForm /> },
    { roles: ['preceptor', 'administrativo', 'director'], path: 'periodos/:termId/editar', element: <PreceptorPeriodoForm /> },
  ],
};
