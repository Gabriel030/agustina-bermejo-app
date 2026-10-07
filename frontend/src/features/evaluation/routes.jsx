import { PreceptorMesaExamen } from '@/pages/PreceptorMesaExamen';
import { DirectorAprobacionesConfigEvaluacion } from '@/pages/DirectorAprobacionesConfigEvaluacion';
import { DirectorEsquemasEvaluacion } from '@/pages/DirectorEsquemasEvaluacion';
import { DirectorRevisarConfiguracion } from '@/pages/DirectorRevisarConfiguracion';

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
