import { DocenteComisionDetalle } from '@/features/announcements/pages/DocenteComisionDetalle';
import { AvisoWizard } from '@/features/announcements/pages/AvisoWizard';
import { AvisoEditar } from '@/features/announcements/pages/AvisoEditar';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['docente'], path: 'mis-comisiones/:sectionId', element: <DocenteComisionDetalle /> },
    { roles: ['docente'], path: 'mis-comisiones/:sectionId/avisos/nuevo', element: <AvisoWizard /> },
    { roles: ['docente'], path: 'mis-comisiones/:sectionId/avisos/:avisoId/editar', element: <AvisoEditar /> },
  ],
};
