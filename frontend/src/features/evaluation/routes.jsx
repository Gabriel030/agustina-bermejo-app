import { lazyPage } from '@/routes/lazyPage';
const PreceptorMesaExamen = lazyPage(() => import('@/features/evaluation/pages/PreceptorMesaExamen'), 'PreceptorMesaExamen');
const DirectorAprobacionesConfigEvaluacion = lazyPage(() => import('@/features/evaluation/pages/DirectorAprobacionesConfigEvaluacion'), 'DirectorAprobacionesConfigEvaluacion');
const DirectorEsquemasEvaluacion = lazyPage(() => import('@/features/evaluation/pages/DirectorEsquemasEvaluacion'), 'DirectorEsquemasEvaluacion');
const DirectorRevisarConfiguracion = lazyPage(() => import('@/features/evaluation/pages/DirectorRevisarConfiguracion'), 'DirectorRevisarConfiguracion');
const DirectorSectionGrades = lazyPage(() => import('@/features/evaluation/pages/DirectorSectionGrades'), 'DirectorSectionGrades');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['preceptor'], path: 'mesas-de-examen', element: <PreceptorMesaExamen /> },
    { roles: ['docente'], path: 'mesas-de-examen', element: <PreceptorMesaExamen role="docente" /> },
    { roles: ['director'], path: 'esquemas-evaluacion', element: <DirectorEsquemasEvaluacion /> },
    { roles: ['director'], path: 'revisar-configuracion/:sectionId', element: <DirectorRevisarConfiguracion /> },
    { roles: ['director'], path: 'aprobaciones/configuracion-evaluacion', element: <DirectorAprobacionesConfigEvaluacion /> },
    { roles: ['director'], path: 'oferta-academica/ciclos/:cycleId/comisiones/:sectionId/notas', element: <DirectorSectionGrades /> },
  ],
};
