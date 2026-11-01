import { useLocation, useParams, useSearchParams } from 'react-router-dom';
import { useMiInscripcion, useMisCursos, useMisMatriculaciones } from '@/features/enrollment';
import { useComisionesDictadas } from '@/features/academic-structure';
import { useConfiguracionComision, useConfiguracionComisionStaff } from '@/features/evaluation';
import { useLegajoAlumno } from '@/features/identity/hooks/useLegajoAlumno';
import { useLegajoPersonal } from '@/features/identity/hooks/useLegajoPersonal';
import { Skeleton } from '@/components/Skeleton';

const ETIQUETA_TAB_COMISION = {
  avisos: 'Avisos',
};

const ETIQUETA_TAB = {
  'plan-de-estudio': 'Plan de estudio',
  notas: 'Notas',
  'mesas-de-examen': 'Mesas de examen',
  asistencia: 'Asistencia',
  egreso: 'Egreso',
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
  const { data: cursos } = useMisCursos(enDetallePagosCurso);
  const { data: carreras } = useMisMatriculaciones(enDetallePagosCarrera);

  const enLegajoAlumno = /^\/(preceptor|administrativo|director)\/alumnos\/[^/]+$/.test(location.pathname);
  const { data: legajo } = useLegajoAlumno(params.id, enLegajoAlumno);

  const enPedirReembolso = /^\/(administrativo|director)\/alumnos\/[^/]+\/reembolsos\/nuevo$/.test(location.pathname);
  const { data: legajoReembolso } = useLegajoAlumno(params.id, enPedirReembolso);

  const enLegajoPersonal = /^\/(administrativo|director)\/personal\/[^/]+$/.test(location.pathname);
  const { data: legajoPersonal } = useLegajoPersonal(params.id, enLegajoPersonal);

  const enNuevoAviso = /^\/docente\/mis-comisiones\/[^/]+\/avisos\/nuevo$/.test(location.pathname);
  const enEditarAviso = /^\/docente\/mis-comisiones\/[^/]+\/avisos\/[^/]+\/editar$/.test(location.pathname);
  const enDetalleComision = /^\/docente\/mis-comisiones\/[^/]+$/.test(location.pathname) || enNuevoAviso || enEditarAviso;
  const esCursoSuelto = searchParams.get('type') === 'COURSE';
  const { data: comision } = useConfiguracionComision(enDetalleComision && !esCursoSuelto ? params.sectionId : undefined);
  const { data: dictadas } = useComisionesDictadas(enDetalleComision && esCursoSuelto);

  const enRevisarConfiguracion = /^\/director\/revisar-configuracion\/[^/]+$/.test(location.pathname);
  const { data: configRevisar } = useConfiguracionComisionStaff(enRevisarConfiguracion ? params.sectionId : undefined);

  const enNotaDeCredito = /^\/(administrativo|director)\/facturacion\/cuotas\/[^/]+\/nota-de-credito$/.test(location.pathname);
  const billingInboxDetail = /^\/(director\/aprobaciones\/facturacion|administrativo\/aprobaciones)\/(\d+)$/.exec(location.pathname);
  if (billingInboxDetail) {
    const rolBase = location.pathname.split('/')[1];
    const requestLabel = `Pedido N.º ${billingInboxDetail[2]}`;
    if (rolBase === 'director') {
      return [
        { label: 'Aprobaciones', to: '/director/aprobaciones' },
        { label: 'Operaciones de facturación', to: '/director/aprobaciones/facturacion' },
        { label: requestLabel },
      ];
    }
    return [{ label: 'Aprobaciones', to: '/administrativo/aprobaciones' }, { label: requestLabel }];
  }

  if (location.pathname === '/director/aprobaciones/facturacion') {
    return [{ label: 'Aprobaciones', to: '/director/aprobaciones' }, { label: 'Operaciones de facturación' }];
  }

  if (enNotaDeCredito) {
    const rolBase = location.pathname.split('/')[1];
    const alumno = searchParams.get('alumno');
    return [
      { label: 'Facturación', to: `/${rolBase}/facturacion${alumno ? `?alumno=${alumno}` : ''}` },
      { label: 'Nota de crédito' },
    ];
  }

  if (location.pathname === '/alumno/pagos/notas-de-credito') {
    return [{ label: 'Mis pagos', to: '/alumno/pagos' }, { label: 'Mis notas de crédito' }];
  }

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

  if (enPedirReembolso) {
    const rolBase = location.pathname.split('/')[1];
    return [
      { label: 'Padrón de alumnos', to: `/${rolBase}/alumnos` },
      {
        label: legajoReembolso ? `${legajoReembolso.apellido}, ${legajoReembolso.nombre}` : <Skeleton className="h-4 w-24" />,
        to: `/${rolBase}/alumnos/${params.id}?view=financiero`,
      },
      { label: 'Pedir reembolso' },
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
    const enFormulario = enNuevoAviso || enEditarAviso;
    const tabComision = esCursoSuelto || enFormulario ? 'avisos' : (searchParams.get('tab') ?? 'evaluacion');
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
      const sufijo = esCursoSuelto ? '&type=COURSE' : '';
      items.push(
        enFormulario
          ? { label: 'Avisos', to: `/docente/mis-comisiones/${params.sectionId}?tab=avisos${sufijo}` }
          : { label: ETIQUETA_TAB_COMISION[tabComision] },
      );
      if (enFormulario) {
        items.push({ label: enNuevoAviso ? 'Nuevo aviso' : 'Editar aviso' });
      }
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
