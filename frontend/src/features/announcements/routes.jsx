import { lazyPage } from '@/routes/lazyPage';
const DocenteComisionDetalle = lazyPage(() => import('@/features/announcements/pages/DocenteComisionDetalle'), 'DocenteComisionDetalle');
const AvisoWizard = lazyPage(() => import('@/features/announcements/pages/AvisoWizard'), 'AvisoWizard');
const AvisoEditar = lazyPage(() => import('@/features/announcements/pages/AvisoEditar'), 'AvisoEditar');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['docente'], path: 'mis-comisiones/:sectionId', element: <DocenteComisionDetalle /> },
    { roles: ['docente'], path: 'mis-comisiones/:sectionId/avisos/nuevo', element: <AvisoWizard /> },
    { roles: ['docente'], path: 'mis-comisiones/:sectionId/avisos/:avisoId/editar', element: <AvisoEditar /> },
  ],
};
