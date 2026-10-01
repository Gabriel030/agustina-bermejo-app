import { useLocation, useParams, useSearchParams } from 'react-router-dom';
import { useMiInscripcion, useMisCursos, useMisMatriculaciones } from '@/hooks/useAlumno';
import { useComisionesDictadas, useConfiguracionComision } from '@/hooks/useDocente';
import { useConfiguracionComisionStaff } from '@/hooks/useDirector';
import { useLegajoAlumno } from '@/features/gestion-personas/hooks/useLegajoAlumno';
import { useLegajoPersonal } from '@/features/gestion-personas/hooks/useLegajoPersonal';
import { Skeleton } from '@/components/Skeleton';

const ETIQUETA_TAB_COMISION = {
  avisos: 'Avisos',
};

const ETIQUETA_TAB = {
  'plan-de-estudio': 'Plan de estudio',
  notas: 'Notas',
  'mesas-de-examen': 'Mesas de examen',
  asistencia: 'Asistencia',
};

const ETIQUETA_TAB_PADRON = {
  activos: 'Activos',
  prospectos: 'Prospectos',
  bajas: 'Bajas',
};

export function useBreadcrumb() {
  const location = useLocation();
  const params = useParams();
  const [searchParams] = useSearchParams();

  const enDetalleMateria = /^\/alumno\/mis-carreras\/materias\/[^/]+$/.test(location.pathname);
  const { data: inscripcion } = useMiInscripcion(params.id, enDetalleMateria);

  const enDetallePagosCurso = /^\/alumno\/pagos\/curso\/[^/]+$/.test(location.pathname);
  const enDetallePagosCarrera = /^\/alumno\/pagos\/carrera\/[^/]+$/.test(location.pathname);
  const enDetallePagos = enDetallePagosCurso || enDetallePagosCarrera;
  const { data: cursos } = useMisCursos();
  const { data: carreras } = useMisMatriculaciones();

  const enLegajoAlumno = /^\/(preceptor|administrativo|director)\/alumnos\/[^/]+$/.test(location.pathname);
  const { data: legajo } = useLegajoAlumno(params.id, enLegajoAlumno);

  const enLegajoPersonal = /^\/(administrativo|director)\/personal\/[^/]+$/.test(location.pathname);
  const { data: legajoPersonal } = useLegajoPersonal(params.id, enLegajoPersonal);

  const enDetalleComision = /^\/docente\/mis-comisiones\/[^/]+$/.test(location.pathname);
  const esCursoSuelto = searchParams.get('type') === 'COURSE';
  const { data: comision } = useConfiguracionComision(enDetalleComision && !esCursoSuelto ? params.sectionId : undefined);
  const { data: dictadas } = useComisionesDictadas(enDetalleComision && esCursoSuelto);

  const enRevisarConfiguracion = /^\/director\/revisar-configuracion\/[^/]+$/.test(location.pathname);
  const { data: configRevisar } = useConfiguracionComisionStaff(enRevisarConfiguracion ? params.sectionId : undefined);

  const enPagos = location.pathname === '/alumno/pagos';
  if (enPagos) {
    return [{ label: 'Mis pagos' }];
  }

  if (enDetallePagos) {
    let productName;
    const id = +params.enrollmentId;
    if (enDetallePagosCurso) {
      const found = cursos?.find((c) => c.id === id);
      productName = found ? (found.courseName ?? `Curso #${found.courseId}`) : <Skeleton className="h-4 w-32" />;
    } else {
      const found = carreras?.find((m) => m.id === id);
      productName = found ? (found.programName ?? `Carrera #${found.programId}`) : <Skeleton className="h-4 w-32" />;
    }
    return [
      { label: 'Mis pagos', to: '/alumno/pagos' },
      { label: productName },
    ];
  }

  if (enLegajoPersonal) {
    const rolBase = location.pathname.split('/')[1];
    const tabOrigen = searchParams.get('tab');
    const items = [{ label: 'Gestión de personal', to: `/${rolBase}/personal` }];
    if (tabOrigen === 'bajas') {
      items.push({ label: 'Bajas', to: `/${rolBase}/personal?tab=bajas` });
    }
    items.push({ label: legajoPersonal ? `${legajoPersonal.apellido}, ${legajoPersonal.nombre}` : <Skeleton className="h-4 w-24" /> });
    return items;
  }

  if (enLegajoAlumno) {
    const rolBase = location.pathname.split('/')[1];
    const tabOrigen = searchParams.get('tab');
    const items = [
      { label: 'Padrón de alumnos', to: `/${rolBase}/alumnos` },
    ];
    if (tabOrigen && ETIQUETA_TAB_PADRON[tabOrigen]) {
      items.push({ label: ETIQUETA_TAB_PADRON[tabOrigen], to: `/${rolBase}/alumnos?tab=${tabOrigen}` });
    }
    items.push({ label: legajo ? `${legajo.apellido}, ${legajo.nombre}` : <Skeleton className="h-4 w-24" /> });
    return items;
  }

  if (enDetalleComision) {
    const tabComision = esCursoSuelto ? 'avisos' : (searchParams.get('tab') ?? 'evaluacion');
    const curso = esCursoSuelto
      ? (dictadas ?? []).find((s) => s.type === 'COURSE' && String(s.id) === params.sectionId)
      : null;
    const seccion = esCursoSuelto ? curso : comision;
    const items = [
      { label: 'Mis comisiones', to: '/docente/mis-comisiones' },
      { label: seccion ? `${seccion.subjectName} · ${seccion.sectionName}` : <Skeleton className="h-4 w-24" /> },
    ];
    if (ETIQUETA_TAB_COMISION[tabComision]) {
      if (!esCursoSuelto) {
        items[1] = { ...items[1], to: `/docente/mis-comisiones/${params.sectionId}` };
      }
      items.push({ label: ETIQUETA_TAB_COMISION[tabComision] });
    }
    return items;
  }

  if (enRevisarConfiguracion) {
    return [
      { label: 'Aprobaciones', to: '/director/aprobaciones' },
      { label: configRevisar ? `${configRevisar.subjectName} · ${configRevisar.sectionName}` : <Skeleton className="h-4 w-24" /> },
    ];
  }

  if (enDetalleMateria) {
    return [
      { label: 'Mis carreras', to: '/alumno/mis-carreras' },
      { label: 'Materias', to: '/alumno/mis-carreras?tab=materias' },
      { label: inscripcion ? inscripcion.subjectName : <Skeleton className="h-4 w-24" /> },
    ];
  }

  if (location.pathname === '/alumno/mis-carreras') {
    const tab = searchParams.get('tab') ?? 'materias';
    if (tab === 'materias') {
      return [{ label: 'Mis carreras' }];
    }
    return [{ label: 'Mis carreras', to: '/alumno/mis-carreras' }, { label: ETIQUETA_TAB[tab] ?? tab }];
  }

  return [];
}
