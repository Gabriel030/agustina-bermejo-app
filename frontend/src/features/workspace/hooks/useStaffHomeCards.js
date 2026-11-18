import { useMyModules } from '@/hooks/useMyModules';
import { useConfigsPendientes, useClosureList, useMesas } from '@/features/evaluation';
import { useEgresosPendientes, useEgresosPorVerificar, usePendingRegularRequests } from '@/features/graduation';
import { useExcepcionesPendientes, usePendingApplications } from '@/features/enrollment';
import { useAuthorizationRequests, useDebtors } from '@/features/billing';
import { usePendingOfferCycles } from '@/features/academic-structure';
import { useJustificacionesPendientes, useSolicitudesPendientes } from '@/features/attendance';
import { formatearFecha } from '@/lib/comisiones';
import { oldestAgeDays, toCard, upcomingBoards } from '@/features/workspace/lib/homeCards';

const BILLING_PENDING_SEARCH = { statuses: ['PENDING'], sort: 'EXPIRES_ON', descending: false, page: 0, size: 50 };
const CLOSURES_WAITING = { status: 'TEACHER_CONFIRMED', termId: '', q: '', page: 1 };

function listCard(definition, query, dateField) {
  return toCard(definition, [query], () => {
    const items = query.data ?? [];
    return { count: items.length, ageDays: oldestAgeDays(items.map((item) => item[dateField])) };
  });
}

function closuresCard(definition, query) {
  return toCard(definition, [query], () => {
    const items = query.data?.items ?? [];
    const total = query.data?.totalElements ?? items.length;
    return { count: total, ageDays: oldestAgeDays(items.map((item) => item.statusDate), total) };
  });
}

function pagedCard(definition, query, dateField) {
  return toCard(definition, [query], () => {
    const items = query.data?.items ?? [];
    const total = query.data?.totalElements ?? items.length;
    return { count: total, ageDays: oldestAgeDays(items.map((item) => item[dateField]), total) };
  });
}

export function useDirectorHomeCards() {
  const modules = useMyModules();
  const graduations = useEgresosPendientes();
  const offers = usePendingOfferCycles();
  const configs = useConfigsPendientes();
  const exceptions = useExcepcionesPendientes();
  const closures = useClosureList(CLOSURES_WAITING);
  const boards = useMesas('SCHEDULED');
  const authorizations = useAuthorizationRequests(BILLING_PENDING_SEARCH, { enabled: modules.billingEnabled });

  const approvalRows = [
    { label: 'Egresos', count: graduations.data?.length ?? 0, to: '/director/aprobaciones/egresos' },
    { label: 'Oferta académica', count: offers.data?.length ?? 0, to: '/director/aprobaciones/oferta-academica' },
    { label: 'Configuración de evaluación', count: configs.data?.length ?? 0, to: '/director/aprobaciones/configuracion-evaluacion' },
  ];

  const cards = [
    toCard(
      { id: 'approvals', title: 'Otras aprobaciones', icon: 'inbox', to: '/director/aprobaciones', unit: 'pendientes', tip: 'Trámites que esperan tu decisión, por tipo. No incluye excepciones ni facturación, que tienen su propia tarjeta.', wide: true },
      [graduations, offers, configs],
      () => ({
        count: approvalRows.reduce((sum, row) => sum + row.count, 0),
        ageDays: oldestAgeDays([
          ...graduations.data.map((item) => item.requestedAt),
          ...offers.data.map((item) => item.submittedAt),
          ...configs.data.map((item) => item.submittedAt),
        ]),
        rows: approvalRows,
      }),
    ),
    closuresCard({ id: 'closures', title: 'Cierres sin acta', icon: 'lock', to: '/director/cierres-de-comision?status=TEACHER_CONFIRMED', unit: 'pendientes', tip: 'Comisiones confirmadas por el docente que esperan el acta de cierre.' }, closures),
    listCard({ id: 'exceptions', title: 'Excepciones elevadas', icon: 'alert', to: '/director/aprobaciones/excepciones', unit: 'pendientes', tip: 'Excepciones que Secretaría elevó a Dirección para decidir.' }, exceptions, 'requestedAt'),
    toCard(
      { id: 'boards', title: 'Mesas de examen', icon: 'calendar', to: '/director/mesas-de-examen', unit: 'esta semana', tip: 'Mesas de examen programadas para los próximos 7 días.' },
      [boards],
      () => {
        const upcoming = upcomingBoards(boards.data ?? []);
        return { count: upcoming.length, ageDays: null, foot: upcoming[0] ? `Próxima: ${formatearFecha(upcoming[0].examDate)}` : null, tone: 'warning' };
      },
    ),
  ];

  if (modules.billingEnabled) {
    cards.push(pagedCard({ id: 'authorizations', title: 'Facturación por autorizar', icon: 'receipt', to: '/director/aprobaciones/facturacion', unit: 'pendientes', tip: 'Operaciones de facturación que Administración pidió autorizar.' }, authorizations, 'requestedAt'));
  }
  return cards;
}

export function useAdministrativoHomeCards() {
  const modules = useMyModules();
  const graduations = useEgresosPorVerificar();
  const certificates = usePendingRegularRequests();
  const applications = usePendingApplications();
  const justifications = useJustificacionesPendientes();
  const attendanceChanges = useSolicitudesPendientes();
  const closures = useClosureList(CLOSURES_WAITING);
  const debtors = useDebtors({ enabled: modules.billingEnabled });

  const cards = [
    listCard({ id: 'graduations', title: 'Egresos por verificar', icon: 'cap', to: '/administrativo/egresos-por-verificar', unit: 'pendientes', tip: 'Solicitudes de egreso a las que les falta verificar la documentación antes de elevarlas a Dirección.' }, graduations, 'requestedAt'),
    listCard({ id: 'certificates', title: 'Constancias solicitadas', icon: 'file', to: '/administrativo/constancias', unit: 'pendientes', tip: 'Constancias que pidieron los alumnos y todavía no se emitieron.' }, certificates, 'requestedAt'),
    listCard({ id: 'applications', title: 'Solicitudes de ingreso', icon: 'userPlus', to: '/administrativo/solicitudes-de-ingreso', unit: 'pendientes', tip: 'Postulantes con solicitud de ingreso sin resolver.' }, applications, 'requestedAt'),
    pagedCard({ id: 'justifications', title: 'Justificaciones', icon: 'clip', to: '/administrativo/solicitudes-de-asistencia?tab=justificaciones', unit: 'pendientes', tip: 'Justificaciones de inasistencia que esperan revisión.' }, justifications, 'classDate'),
    listCard({ id: 'attendanceChanges', title: 'Cambios de asistencia', icon: 'userCheck', to: '/administrativo/solicitudes-de-asistencia?tab=correcciones', unit: 'pendientes', tip: 'Cambios de asistencia pedidos por docentes que esperan aprobación.' }, attendanceChanges, 'createdAt'),
    closuresCard({ id: 'closures', title: 'Cierres a aprobar', icon: 'lock', to: '/administrativo/cierres-de-comision?status=TEACHER_CONFIRMED', unit: 'pendientes', tip: 'Cierres de comisión que esperan la aprobación administrativa.' }, closures),
  ];

  if (modules.billingEnabled) {
    cards.push(
      toCard(
        { id: 'debtors', title: 'Morosos', icon: 'wallet', to: '/administrativo/financiero', unit: 'alumnos', tip: 'Alumnos con cuotas vencidas. La antigüedad es la deuda más vieja.' },
        [debtors],
        () => {
          const items = debtors.data?.items ?? [];
          const total = debtors.data?.totalElements ?? items.length;
          const ages = items.map((item) => item.debtAgeDays).filter((age) => age !== null && age !== undefined);
          return { count: total, ageDays: ages.length ? Math.max(...ages) : null, tone: 'danger' };
        },
      ),
    );
  }
  return cards;
}
