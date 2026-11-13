import { lazyPage } from '@/routes/lazyPage';
const PreceptorMesaExamen = lazyPage(() => import('@/features/evaluation/pages/PreceptorMesaExamen'), 'PreceptorMesaExamen');
const DirectorAprobacionesConfigEvaluacion = lazyPage(() => import('@/features/evaluation/pages/DirectorAprobacionesConfigEvaluacion'), 'DirectorAprobacionesConfigEvaluacion');
const DirectorEsquemasEvaluacion = lazyPage(() => import('@/features/evaluation/pages/DirectorEsquemasEvaluacion'), 'DirectorEsquemasEvaluacion');
const DirectorRevisarConfiguracion = lazyPage(() => import('@/features/evaluation/pages/DirectorRevisarConfiguracion'), 'DirectorRevisarConfiguracion');
const DocenteClosureSheet = lazyPage(() => import('@/features/evaluation/pages/DocenteClosureSheet'), 'DocenteClosureSheet');
const ClosureListPage = lazyPage(() => import('@/features/evaluation/pages/ClosureListPage'), 'ClosureListPage');
const StaffClosureSheet = lazyPage(() => import('@/features/evaluation/pages/StaffClosureSheet'), 'StaffClosureSheet');
const DirectorSectionGrades = lazyPage(() => import('@/features/evaluation/pages/DirectorSectionGrades'), 'DirectorSectionGrades');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['preceptor'], path: 'mesas-de-examen', element: <PreceptorMesaExamen /> },
    { roles: ['administrativo'], path: 'mesas-de-examen', element: <PreceptorMesaExamen role="administrativo" /> },
    { roles: ['docente'], path: 'mesas-de-examen', element: <PreceptorMesaExamen role="docente" /> },
    { roles: ['director'], path: 'mesas-de-examen', element: <PreceptorMesaExamen role="director" /> },
    { roles: ['docente'], path: 'mis-comisiones/:sectionId/cierre', element: <DocenteClosureSheet /> },
    { roles: ['preceptor'], path: 'cierre-de-comision/:sectionId', element: <StaffClosureSheet role="preceptor" /> },
    { roles: ['administrativo'], path: 'cierre-de-comision/:sectionId', element: <StaffClosureSheet role="administrativo" /> },
    { roles: ['director'], path: 'cierre-de-comision/:sectionId', element: <StaffClosureSheet role="director" /> },
    { roles: ['administrativo'], path: 'cierres-de-comision', element: <ClosureListPage role="administrativo" /> },
    { roles: ['director'], path: 'cierres-de-comision', element: <ClosureListPage role="director" /> },
    { roles: ['director'], path: 'esquemas-evaluacion', element: <DirectorEsquemasEvaluacion /> },
    { roles: ['director'], path: 'revisar-configuracion/:sectionId', element: <DirectorRevisarConfiguracion /> },
    { roles: ['director'], path: 'aprobaciones/configuracion-evaluacion', element: <DirectorAprobacionesConfigEvaluacion /> },
    { roles: ['director'], path: 'oferta-academica/ciclos/:cycleId/comisiones/:sectionId/notas', element: <DirectorSectionGrades /> },
  ],
};
