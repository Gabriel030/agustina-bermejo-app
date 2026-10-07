import { PreceptorMesaExamen } from '@/features/evaluation/pages/PreceptorMesaExamen';
import { DirectorAprobacionesConfigEvaluacion } from '@/features/evaluation/pages/DirectorAprobacionesConfigEvaluacion';
import { DirectorEsquemasEvaluacion } from '@/features/evaluation/pages/DirectorEsquemasEvaluacion';
import { DirectorRevisarConfiguracion } from '@/features/evaluation/pages/DirectorRevisarConfiguracion';

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
