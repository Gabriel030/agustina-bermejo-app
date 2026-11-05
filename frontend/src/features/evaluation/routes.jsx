import { lazyPage } from '@/routes/lazyPage';
const PreceptorMesaExamen = lazyPage(() => import('@/features/evaluation/pages/PreceptorMesaExamen'), 'PreceptorMesaExamen');
const DirectorAprobacionesConfigEvaluacion = lazyPage(() => import('@/features/evaluation/pages/DirectorAprobacionesConfigEvaluacion'), 'DirectorAprobacionesConfigEvaluacion');
const DirectorEsquemasEvaluacion = lazyPage(() => import('@/features/evaluation/pages/DirectorEsquemasEvaluacion'), 'DirectorEsquemasEvaluacion');
const DirectorRevisarConfiguracion = lazyPage(() => import('@/features/evaluation/pages/DirectorRevisarConfiguracion'), 'DirectorRevisarConfiguracion');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['preceptor'], path: 'mesas-de-examen', element: <PreceptorMesaExamen /> },
    { roles: ['director'], path: 'esquemas-evaluacion', element: <DirectorEsquemasEvaluacion /> },
    { roles: ['director'], path: 'revisar-configuracion/:sectionId', element: <DirectorRevisarConfiguracion /> },
    { roles: ['director'], path: 'aprobaciones/configuracion-evaluacion', element: <DirectorAprobacionesConfigEvaluacion /> },
  ],
};
