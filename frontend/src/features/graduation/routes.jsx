import { DirectorAprobacionesEgresos } from '@/pages/DirectorAprobacionesEgresos';
import { DirectorEgresoDetalle } from '@/pages/DirectorEgresoDetalle';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['director'], path: 'aprobaciones/egresos', element: <DirectorAprobacionesEgresos /> },
    { roles: ['director'], path: 'aprobaciones/egresos/:id', element: <DirectorEgresoDetalle /> },
  ],
};
