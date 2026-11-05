import { lazyPage } from '@/routes/lazyPage';
const DirectorAprobacionesEgresos = lazyPage(() => import('@/features/graduation/pages/DirectorAprobacionesEgresos'), 'DirectorAprobacionesEgresos');
const DirectorEgresoDetalle = lazyPage(() => import('@/features/graduation/pages/DirectorEgresoDetalle'), 'DirectorEgresoDetalle');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['director'], path: 'aprobaciones/egresos', element: <DirectorAprobacionesEgresos /> },
    { roles: ['director'], path: 'aprobaciones/egresos/:id', element: <DirectorEgresoDetalle /> },
  ],
};
