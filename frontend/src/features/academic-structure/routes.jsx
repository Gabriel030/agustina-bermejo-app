import { AdministrativoSubjects } from '@/features/gestion-academica/pages/AdministrativoSubjects';
import { AdministrativoSubjectWizard } from '@/features/gestion-academica/pages/AdministrativoSubjectWizard';
import { CarreraWizard } from '@/features/gestion-academica/pages/CarreraWizard';
import { DirectorAniosLectivos } from '@/features/gestion-academica/pages/DirectorAniosLectivos';
import { CarreraDetalle } from '@/features/gestion-academica/pages/CarreraDetalle';
import { CarreraEditarDatos } from '@/features/gestion-academica/pages/CarreraEditarDatos';
import { CursoWizard } from '@/features/gestion-academica/pages/CursoWizard';
import { CursoDetalle } from '@/features/gestion-academica/pages/CursoDetalle';
import { CursoEditarDatos } from '@/features/gestion-academica/pages/CursoEditarDatos';
import { DirectorSubjectReview } from '@/features/gestion-academica/pages/DirectorSubjectReview';
import { PreceptorOfertaAcademica } from '@/features/oferta-academica/pages/PreceptorOfertaAcademica';
import { PreceptorDetalleCarrera } from '@/features/oferta-academica/pages/PreceptorDetalleCarrera';
import { PreceptorDetalleCurso } from '@/features/oferta-academica/pages/PreceptorDetalleCurso';
import { PreceptorCalendario } from '@/features/oferta-academica/pages/PreceptorCalendario';
import { PreceptorNuevoCiclo } from '@/features/oferta-academica/pages/PreceptorNuevoCiclo';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['administrativo'], path: 'gestion-academica', element: <AdministrativoSubjects /> },
    { roles: ['administrativo'], path: 'gestion-academica/nueva', element: <AdministrativoSubjectWizard /> },
    { roles: ['administrativo'], path: 'gestion-academica/:id', element: <AdministrativoSubjectWizard /> },
    { roles: ['administrativo'], path: 'gestion-academica/carreras/nueva', element: <CarreraWizard /> },
    { roles: ['administrativo'], path: 'gestion-academica/carreras/:id', element: <CarreraDetalle /> },
    { roles: ['administrativo'], path: 'gestion-academica/carreras/:id/editar', element: <CarreraEditarDatos /> },
    { roles: ['administrativo'], path: 'gestion-academica/cursos/nueva', element: <CursoWizard /> },
    { roles: ['administrativo'], path: 'gestion-academica/cursos/:id', element: <CursoDetalle /> },
    { roles: ['administrativo'], path: 'gestion-academica/cursos/:id/editar', element: <CursoEditarDatos /> },
    { roles: ['director'], path: 'gestion-academica', element: <DirectorSubjectReview /> },
    { roles: ['director'], path: 'financiero/anios-lectivos', element: <DirectorAniosLectivos /> },
    { roles: ['preceptor'], path: 'oferta-academica', element: <PreceptorOfertaAcademica /> },
    { roles: ['preceptor'], path: 'oferta-academica/carreras/:programaId', element: <PreceptorDetalleCarrera /> },
    { roles: ['preceptor'], path: 'oferta-academica/cursos/:cursoId', element: <PreceptorDetalleCurso /> },
    { roles: ['preceptor'], path: 'oferta-academica/calendario', element: <PreceptorCalendario /> },
    { roles: ['preceptor'], path: 'oferta-academica/nuevo-ciclo', element: <PreceptorNuevoCiclo /> },
  ],
};
