import { DocenteComisionDetalle } from '@/features/avisos/pages/DocenteComisionDetalle';
import { AvisoWizard } from '@/features/avisos/pages/AvisoWizard';
import { AvisoEditar } from '@/features/avisos/pages/AvisoEditar';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['docente'], path: 'mis-comisiones/:sectionId', element: <DocenteComisionDetalle /> },
    { roles: ['docente'], path: 'mis-comisiones/:sectionId/avisos/nuevo', element: <AvisoWizard /> },
    { roles: ['docente'], path: 'mis-comisiones/:sectionId/avisos/:avisoId/editar', element: <AvisoEditar /> },
  ],
};
