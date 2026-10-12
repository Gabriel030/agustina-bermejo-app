import { DirectorAprobacionesEgresos } from '@/features/graduation/pages/DirectorAprobacionesEgresos';
import { DirectorEgresoDetalle } from '@/features/graduation/pages/DirectorEgresoDetalle';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['director'], path: 'aprobaciones/egresos', element: <DirectorAprobacionesEgresos /> },
    { roles: ['director'], path: 'aprobaciones/egresos/:id', element: <DirectorEgresoDetalle /> },
  ],
};
