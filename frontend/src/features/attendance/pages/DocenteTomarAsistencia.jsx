import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, Ban, Check, ChevronDown, ChevronLeft, Download, Info, Minus, RefreshCw, Search, Undo2, WifiOff } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { formatDate } from '@/lib/billing';
import { attendancePercentToPoints } from '@/lib/attendancePercent';
import { StatusPill } from '@/components/StatusPill';
import { TabCount } from '@/components/TabCount';
import { MisPedidosDeAsistencia } from '@/features/attendance/components/MisPedidosDeAsistencia';
import {
  attendanceHref,
  findPendingRequestForAttendance,
  ordersHref,
  pendingRequestCount,
  pendingRequestsLabel,
  requestHref,
  sectionParamOf,
  sectionParamOfRequest,
} from '@/features/attendance/lib/attendanceRequests';
import { ExportAttendanceDialog } from '@/features/attendance/components/ExportAttendanceDialog';
import { EXPORT_SCOPES } from '@/features/attendance/lib/exportScopes';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Label } from '@/components/ui/label';
import { AttendanceStudentRow } from '@/features/attendance/components/AttendanceStudentRow';
import { AttendanceSummaryCard } from '@/features/attendance/components/AttendanceSummaryCard';
import { AttendanceSaveBar } from '@/features/attendance/components/AttendanceSaveBar';
import { AttendanceConfirmDialog } from '@/features/attendance/components/AttendanceConfirmDialog';
import { ClassNotesCard } from '@/features/attendance/components/ClassNotesCard';
import { ModalSolicitarModificacion } from '@/features/attendance/components/ModalSolicitarModificacion';
import { useTakeAttendanceForm } from '@/features/attendance/hooks/useTakeAttendanceForm';
import { useDraftExitGuard } from '@/features/attendance/hooks/useDraftExitGuard';
import { STATUS_BY_VALUE, justificationState } from '@/features/attendance/lib/attendanceStatuses';
import { JustificarAusenciaDialog } from '@/features/attendance/components/JustificarAusenciaDialog';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import {
  useMisComisionesParaAsistencia,
  useHistorialAsistencia,
  useReporteAusencias,
  useMisPedidosDeAsistencia,
} from '@/features/attendance/hooks/useDocenteAsistencia';

const CLASS_DATE_LABEL = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'long' });

function SavedStatePill({ canceled, saved }) {
  if (canceled) {
    return (
      <StatusPill tone="danger">
        <Ban className="size-3.5" aria-hidden="true" />
        Clase cancelada
      </StatusPill>
    );
  }
  if (saved) {
    return (
      <StatusPill tone="success">
        <Check className="size-3.5" aria-hidden="true" />
        Asistencia guardada
      </StatusPill>
    );
  }
  return (
    <StatusPill tone="neutral">
      <Minus className="size-3.5" aria-hidden="true" />
      Sin tomar todavía
    </StatusPill>
  );
}

const BANNER_TONES = {
  danger: 'border-danger bg-danger-bg text-danger-ink',
  neutral: 'border-border bg-neutral-bg text-neutral-ink',
};

function AttendanceBanner({ tone, icon: Icon, role = 'status', children, action }) {
  return (
    <div role={role} className={`flex flex-wrap items-center gap-x-3 gap-y-2 rounded-card border px-4 py-3 text-size-13 font-semibold ${BANNER_TONES[tone]}`}>
      <Icon className="size-4 shrink-0" aria-hidden="true" />
      <span className="min-w-0 flex-1 basis-56">{children}</span>
      {action}
    </div>
  );
}

export function TabAsistencia({ type, sectionId }) {
  const form = useTakeAttendanceForm(type, sectionId);
  const { leaveDialog } = useDraftExitGuard(form.changedCount, form.hasChanges);
  const [confirmMarkAll, setConfirmMarkAll] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const [requestedChange, setRequestedChange] = useState(null);
  const [absenceToJustify, setAbsenceToJustify] = useState(null);

  const { rosterQuery, historyQuery, classQuery, classDate, students, filteredStudents, statuses, savedCells, savedStatuses, canceled } = form;
  const unmarkedCount = form.unmarkedCount;

  const failedQueries = [
    { query: rosterQuery, resource: 'los alumnos de la comisión' },
    { query: historyQuery, resource: 'la asistencia ya guardada' },
    { query: classQuery, resource: 'los datos de la clase', ignoredStatus: 404 },
  ].filter(({ query, ignoredStatus }) => query.isError && query.data === undefined && query.error?.status !== ignoredStatus);

  async function confirmCancelClass() {
    try {
      await form.cancelClass();
    } finally {
      setConfirmCancel(false);
    }
  }

  if (form.loadingRoster) {
    return (
      <div className="flex flex-col gap-2 pt-4">
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
      </div>
    );
  }

  if (failedQueries.length > 0) {
    return (
      <div className="flex flex-col gap-4 pt-4">
        <Input type="date" value={classDate} onChange={(e) => form.setClassDate(e.target.value)} className="w-fit" />
        <GlassCard className="p-4">
          <LoadErrorState
            bare
            error={failedQueries[0].query.error}
            resource={failedQueries[0].resource}
            onRetry={() => failedQueries.forEach(({ query }) => query.refetch())}
          />
        </GlassCard>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 pt-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="class-date" className="text-size-12-5 font-bold text-ink-soft">Fecha de la clase</Label>
          <Input id="class-date" type="date" value={classDate} onChange={(e) => form.setClassDate(e.target.value)} className="min-h-11 w-fit" />
        </div>
        <div aria-live="polite">
          <SavedStatePill canceled={canceled} saved={form.hasSavedAttendance} />
        </div>
      </div>

      <AttendanceSummaryCard
        enrolled={students.length}
        present={form.presentCount}
        late={form.lateCount}
        absent={form.absentCount}
        marked={form.markedCount}
      />

      {canceled && (
        <AttendanceBanner tone="danger" icon={Ban}>
          Esta clase está cancelada — no cuenta como inasistencia para nadie. Podés seguir editando el temario/observaciones, pero la grilla de asistencia queda deshabilitada.
        </AttendanceBanner>
      )}

      {form.hasSavedAttendance && !canceled && (
        <AttendanceBanner tone="neutral" icon={Info}>
          {form.isToday
            ? 'Podés corregir la asistencia de hoy.'
            : 'La asistencia ya guardada no se edita desde acá: para corregirla, pedí la modificación desde Historial.'}
        </AttendanceBanner>
      )}

      {form.recovered && !form.error && (
        <AttendanceBanner tone="neutral" icon={Undo2}>Recuperamos tu asistencia sin enviar.</AttendanceBanner>
      )}

      {form.error && (
        <AttendanceBanner
          tone="danger"
          role="alert"
          icon={form.error.offline ? WifiOff : AlertTriangle}
          action={
            <Button type="button" variant="outline" className="min-h-11" onClick={form.saveAttendance} disabled={form.saving}>
              <RefreshCw aria-hidden="true" />
              Reintentar
            </Button>
          }
        >
          {form.error.message}
        </AttendanceBanner>
      )}

      <GlassCard role="region" aria-label="Lista de alumnos" className="overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
          <div className="relative min-w-60 max-w-sm flex-1">
            <Search className="pointer-events-none absolute left-3 top-3.5 size-4 text-ink-faint" aria-hidden="true" />
            <Input
              type="search"
              aria-label="Buscar alumno por nombre o DNI"
              placeholder="Buscar por nombre o DNI"
              value={form.search}
              onChange={(e) => form.setSearch(e.target.value)}
              className="min-h-11 pl-10"
            />
          </div>
          <Button type="button" variant="outline" className="min-h-11" onClick={() => setConfirmMarkAll(true)} disabled={unmarkedCount === 0 || canceled}>
            <Check aria-hidden="true" />
            Marcar presentes a los {unmarkedCount} sin marcar
          </Button>
        </div>

        {filteredStudents.length === 0 ? (
          <EmptyState title="No hay alumnos para mostrar" description="Esta comisión no tiene alumnos matriculados activos, o ninguno coincide con la búsqueda." />
        ) : (
          <>
            <div aria-hidden="true" className="hidden grid-cols-[minmax(0,1fr)_14rem_20.5rem] gap-4 border-b border-border bg-table-head px-4 py-2.5 text-size-12 font-extrabold uppercase tracking-wide text-ink-faint sm:grid">
              <span>Alumno</span>
              <span>Inasistencias</span>
              <span>Asistencia</span>
            </div>
            <ul aria-label="Alumnos de la comisión">
              {filteredStudents.map((student) => {
                const enrollmentId = student.enrollmentId;
                const savedStatus = savedStatuses[enrollmentId];
                const locked = canceled || (savedStatus != null && !form.canCorrectSaved(enrollmentId));
                return (
                  <AttendanceStudentRow
                    key={enrollmentId}
                    student={student}
                    status={statuses[enrollmentId]}
                    onChangeStatus={form.changeStatus}
                    locked={locked}
                    justification={justificationState(savedCells[enrollmentId], statuses[enrollmentId])}
                    onAttachProof={() => setAbsenceToJustify({ ...savedCells[enrollmentId], studentName: student.studentName, classDate })}
                    onRequestChange={
                      !canceled && savedStatus != null && !form.canCorrectSaved(enrollmentId)
                        ? () => setRequestedChange({ attendanceId: savedCells[enrollmentId].attendanceId, status: savedStatus })
                        : undefined
                    }
                  />
                );
              })}
            </ul>
          </>
        )}
      </GlassCard>

      <ClassNotesCard
        topic={form.topic}
        onTopicChange={form.setTopic}
        observations={form.observations}
        onObservationsChange={form.setObservations}
        canceled={canceled}
        onCancelClass={() => setConfirmCancel(true)}
      />

      <AttendanceSaveBar
        hasPendingChanges={form.hasChanges}
        changedCount={form.changedCount}
        saved={form.hasSavedAttendance}
        canceled={canceled}
        hasError={Boolean(form.error)}
        unmarkedCount={unmarkedCount}
        saving={form.saving}
        onSave={form.saveAttendance}
        onDiscard={() => setConfirmDiscard(true)}
      />

      <AttendanceConfirmDialog
        open={confirmMarkAll}
        onOpenChange={setConfirmMarkAll}
        title={`¿Marcar presentes a los ${unmarkedCount} alumnos sin asistencia?`}
        description="No modifica a quienes ya tienen un estado marcado."
        cancelLabel="Cancelar"
        confirmLabel="Marcar presentes"
        onConfirm={() => {
          form.markAllPresent();
          setConfirmMarkAll(false);
        }}
      />

      <AttendanceConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title={`¿Cancelar la clase del ${CLASS_DATE_LABEL.format(new Date(`${classDate}T12:00:00`))}?`}
        description="Esa fecha deja de contar como inasistencia para todos los alumnos."
        cancelLabel="Volver"
        confirmLabel="Cancelar clase"
        confirmVariant="destructive"
        onConfirm={confirmCancelClass}
      />

      <AttendanceConfirmDialog
        open={confirmDiscard}
        onOpenChange={setConfirmDiscard}
        title={`¿Descartar ${form.changedCount === 1 ? '1 cambio' : `${form.changedCount} cambios`}?`}
        description="Vuelve a lo último que guardaste y borra el borrador de este dispositivo."
        cancelLabel="Seguir editando"
        confirmLabel="Descartar"
        confirmVariant="destructive"
        onConfirm={() => {
          form.discardChanges();
          setConfirmDiscard(false);
        }}
      />

      {leaveDialog}

      {absenceToJustify && (
        <JustificarAusenciaDialog type={type} sectionId={sectionId} absence={absenceToJustify} onClose={() => setAbsenceToJustify(null)} />
      )}

      {requestedChange && (
        <ModalSolicitarModificacion
          attendanceId={requestedChange.attendanceId}
          estadoActual={requestedChange.status}
          onClose={() => setRequestedChange(null)}
        />
      )}
    </div>
  );
}


const NOMBRE_MES_CORTO = new Intl.DateTimeFormat('es-AR', { month: 'short' });

function agruparPorAnioYMes(fechas) {
  const porAnio = new Map();
  for (const fecha of fechas) {
    const anio = fecha.slice(0, 4);
    const mes = fecha.slice(5, 7);
    if (!porAnio.has(anio)) porAnio.set(anio, new Map());
    const porMes = porAnio.get(anio);
    if (!porMes.has(mes)) porMes.set(mes, []);
    porMes.get(mes).push(fecha);
  }
  return [...porAnio.entries()].map(([anio, porMes]) => ({
    anio,
    meses: [...porMes.entries()].map(([mes, dias]) => ({
      mes,
      etiqueta: NOMBRE_MES_CORTO.format(new Date(`${anio}-${mes}-02T00:00:00`)),
      dias,
    })),
  }));
}

function rejectionText(rejection) {
  const date = formatDate(rejection.rejectedAt);
  return `Justificación rechazada: ${rejection.reason}${date ? ` (${date})` : ''}`;
}

export function TabHistorial({ type, sectionId, returnRequest }) {
  const { data: historial, isLoading } = useHistorialAsistencia(type, sectionId);
  const { data: pedidos } = useMisPedidosDeAsistencia();
  const [celdaSeleccionada, setCeldaSeleccionada] = useState(null);
  const [solicitandoModificacion, setSolicitandoModificacion] = useState(false);
  const [justificando, setJustificando] = useState(false);
  const [alumnoHover, setAlumnoHover] = useState(null);
  const [alumnoFijado, setAlumnoFijado] = useState(null);
  const alumnoResaltado = alumnoHover ?? alumnoFijado;
  const [fechaHover, setFechaHover] = useState(null);

  const pendingRequest = findPendingRequestForAttendance(pedidos, celdaSeleccionada?.attendanceId);
  const gruposPorAnio = useMemo(() => agruparPorAnioYMes(historial?.classDates ?? []), [historial]);
  // Última fecha de cada mes/año: ahí van las líneas divisorias verticales (la de año,
  // más marcada, pisa a la de mes cuando coinciden).
  const finesDeMes = useMemo(() => {
    const set = new Set();
    gruposPorAnio.forEach((a) => a.meses.forEach((m) => set.add(m.dias[m.dias.length - 1])));
    return set;
  }, [gruposPorAnio]);
  const finesDeAnio = useMemo(() => {
    const set = new Set();
    gruposPorAnio.forEach((a) => {
      const ultimoMes = a.meses[a.meses.length - 1];
      set.add(ultimoMes.dias[ultimoMes.dias.length - 1]);
    });
    return set;
  }, [gruposPorAnio]);

  if (isLoading) return <Skeleton className="mt-4 h-40" />;
  if (!historial || historial.classDates.length === 0) {
    return <EmptyState className="pt-8" title="Todavía no hay clases registradas" description="El historial aparece a medida que se toma asistencia." />;
  }

  const celdaSticky = 'sticky left-0 z-10';
  const divisorDeMes = 'border-r-2 border-r-ink-faint/30';
  const divisorDeAnio = 'border-r-[3px] border-r-ink/50';
  const divisor = (fecha) => (finesDeAnio.has(fecha) ? divisorDeAnio : finesDeMes.has(fecha) ? divisorDeMes : '');

  return (
    <div className="flex flex-col gap-4 pt-4">
      {returnRequest && (
        <div role="status" className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-accent-soft px-4 py-3 text-size-13 text-ink">
          <span>
            Pedido #{returnRequest.id} rechazado: clase del {formatDate(returnRequest.classDate)}, {returnRequest.studentName}.
            {returnRequest.rejectionReason ? ` Motivo: ${returnRequest.rejectionReason}` : ''} Podés volver a pedir el cambio.
          </span>
          <Link to={ordersHref(`${type}-${sectionId}`)} className="font-bold text-brand hover:underline">Volver a Mis pedidos</Link>
        </div>
      )}
      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="font-heading text-size-14 font-semibold text-ink">Historial de asistencia</div>
        <div className="overflow-x-auto">
      <table className="border-separate border-spacing-0 text-size-11-5">
        <thead>
          <tr>
            <th rowSpan={3} className={`${celdaSticky} bg-surface whitespace-nowrap border-b border-border px-3 py-1 text-left align-bottom font-semibold text-ink-faint`}>
              Alumno
            </th>
            {gruposPorAnio.map((a, i) => (
              <th
                key={a.anio}
                colSpan={a.meses.reduce((acc, m) => acc + m.dias.length, 0)}
                className={`bg-surface border-b border-border px-1 py-1 text-center font-semibold text-ink-faint ${i < gruposPorAnio.length - 1 ? divisorDeAnio : ''}`}
              >
                {a.anio}
              </th>
            ))}
            <th rowSpan={3} className="bg-surface border-b border-border px-2 py-1 text-center align-bottom font-semibold text-ink-faint">%</th>
          </tr>
          <tr>
            {gruposPorAnio.flatMap((a) =>
              a.meses.map((m, i) => (
                <th
                  key={`${a.anio}-${m.mes}`}
                  colSpan={m.dias.length}
                  className={`bg-surface border-b border-border px-1 py-1 text-center font-semibold capitalize text-ink-faint ${i === a.meses.length - 1 ? divisorDeAnio : divisorDeMes}`}
                >
                  {m.etiqueta}
                </th>
              )),
            )}
          </tr>
          <tr>
            {historial.classDates.map((fecha) => (
              <th
                key={fecha}
                onMouseEnter={() => setFechaHover(fecha)}
                onMouseLeave={() => setFechaHover(null)}
                className={`bg-surface w-7 border-b border-border px-0.5 py-1 text-center font-normal text-ink-faint ${divisor(fecha)} ${fecha === fechaHover ? 'shadow-(--sh-highlight-row)' : ''}`}
              >
                {Number(fecha.slice(8, 10))}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {historial.rows.map((fila) => {
            const resaltada = fila.studentId === alumnoResaltado;
            return (
              <tr
                key={fila.studentId}
                onMouseEnter={() => setAlumnoHover(fila.studentId)}
                onMouseLeave={() => setAlumnoHover(null)}
                onClick={() => setAlumnoFijado((prev) => (prev === fila.studentId ? null : fila.studentId))}
                className="cursor-pointer"
              >
                <td className={`${celdaSticky} whitespace-nowrap border-y-2 px-3 py-1 font-medium ${resaltada ? 'border-brand bg-brand text-on-accent' : 'border-transparent bg-surface text-ink'}`}>
                  {fila.studentName}
                </td>
                {historial.classDates.map((fecha) => {
                  const celda = fila.cellsByDate[fecha];
                  const config = celda ? STATUS_BY_VALUE[celda.status] : null;
                  return (
                    <td
                      key={fecha}
                      onMouseEnter={() => setFechaHover(fecha)}
                      onMouseLeave={() => setFechaHover(null)}
                      onClick={(e) => {
                        if (!celda) return;
                        e.stopPropagation();
                        setCeldaSeleccionada({ studentName: fila.studentName, fecha, ...celda });
                      }}
                      className={`w-7 border-y-[3px] px-0.5 py-1 text-center ${celda ? 'cursor-pointer' : ''} ${config ? config.rowClass : ''} ${divisor(fecha)} ${resaltada ? 'border-brand' : 'border-transparent'} ${returnRequest && fila.studentName === returnRequest.studentName && fecha === returnRequest.classDate ? 'ring-2 ring-inset ring-brand' : ''} ${fecha === fechaHover ? 'shadow-(--sh-highlight-row)' : ''}`}
                      title={celda?.rejection ? rejectionText(celda.rejection) : (config?.label ?? 'Sin cargar')}
                      tabIndex={celda?.rejection ? 0 : undefined}
                      aria-label={celda?.rejection ? `${config.label}. ${rejectionText(celda.rejection)}` : undefined}
                    >
                      {config ? config.label[0] : '·'}
                      {celda?.rejection && <span aria-hidden="true" className="ml-0.5 text-danger">!</span>}
                    </td>
                  );
                })}
                <td className={`border-y-2 px-2 py-1 text-center font-semibold ${resaltada ? 'border-brand bg-brand text-on-accent' : 'border-transparent text-ink'}`}>
                  {fila.attendancePercent == null ? '-' : `${Math.round(attendancePercentToPoints(fila.attendancePercent))}%`}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
        </div>
      </GlassCard>

      <Dialog open={!!celdaSeleccionada && !solicitandoModificacion && !justificando} onOpenChange={(open) => !open && setCeldaSeleccionada(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Detalle de asistencia</DialogTitle>
            <DialogDescription>
              {celdaSeleccionada?.studentName} · {celdaSeleccionada?.fecha} · {STATUS_BY_VALUE[celdaSeleccionada?.status]?.label}
            </DialogDescription>
          </DialogHeader>
          {celdaSeleccionada?.rejection && (
            <p className="text-size-13 font-bold text-danger">{rejectionText(celdaSeleccionada.rejection)}</p>
          )}
          {celdaSeleccionada?.status === 'ABSENT' && !celdaSeleccionada.justifiable && (
            <p className="text-size-13 text-ink-faint">Venció el plazo para justificar esta inasistencia: lo resuelve el preceptor.</p>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setCeldaSeleccionada(null)}>Cerrar</Button>
            {celdaSeleccionada?.status === 'ABSENT' && celdaSeleccionada.justifiable && (
              <Button variant="outline" onClick={() => setJustificando(true)}>Adjuntar comprobante</Button>
            )}
            {pendingRequest ? (
              <>
                <StatusPill tone="warning">Pedido pendiente</StatusPill>
                <Link to={requestHref(pendingRequest.id, `${type}-${sectionId}`)} className="text-size-13 font-bold text-brand hover:underline">
                  Ver pedido #{pendingRequest.id}
                </Link>
              </>
            ) : (
              <Button variant="primary" onClick={() => setSolicitandoModificacion(true)}>Solicitar modificación</Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {justificando && celdaSeleccionada && (
        <JustificarAusenciaDialog
          type={type}
          sectionId={sectionId}
          absence={{ ...celdaSeleccionada, classDate: celdaSeleccionada.fecha }}
          onClose={() => {
            setJustificando(false);
            setCeldaSeleccionada(null);
          }}
        />
      )}

      {solicitandoModificacion && celdaSeleccionada && (
        <ModalSolicitarModificacion
          attendanceId={celdaSeleccionada.attendanceId}
          estadoActual={celdaSeleccionada.status}
          onClose={() => {
            setSolicitandoModificacion(false);
            setCeldaSeleccionada(null);
          }}
        />
      )}
    </div>
  );
}

function GraficoDeTorta({ segmentos, total, centroValor, centroLabel }) {
  const stops = segmentos.map((s, index) => {
    const desde = segmentos.slice(0, index).reduce((sum, previous) => sum + (previous.valor / total) * 100, 0);
    const hasta = desde + (s.valor / total) * 100;
    return `var(${s.colorVar}) ${desde}% ${hasta}%`;
  }).join(', ');

  return (
    <div className="flex flex-wrap items-center gap-6">
      <div className="relative h-36 w-36 shrink-0 rounded-full" style={{ background: `conic-gradient(${stops})` }}>
        <div className="absolute inset-[16px] flex flex-col items-center justify-center rounded-full bg-surface">
          <div className="font-heading text-xl font-bold text-ink">{centroValor}</div>
          <div className="text-size-10 text-ink-faint">{centroLabel}</div>
        </div>
      </div>
      <div className="flex flex-col gap-1.5 text-size-12-5 text-ink-soft">
        {segmentos.map((s) => (
          <div key={s.key} className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full" style={{ background: `var(${s.colorVar})` }} />
            {s.label} · {Math.round((s.valor / total) * 100)}%
          </div>
        ))}
      </div>
    </div>
  );
}

function DistribucionDeAsistencia({ type, sectionId }) {
  const { data: historial } = useHistorialAsistencia(type, sectionId);
  const [anioFiltro, setAnioFiltro] = useState('TODOS');
  const [fechaFiltro, setFechaFiltro] = useState('TODAS');

  const anios = useMemo(
    () => [...new Set((historial?.classDates ?? []).map((f) => f.slice(0, 4)))].sort(),
    [historial],
  );
  // Con carreras de varios años la lista de clases puede ser enorme (300+) — recién se
  // ofrece elegir una clase puntual una vez que el docente acotó a un año concreto.
  const fechasDelAnio = useMemo(
    () => (anioFiltro === 'TODOS' ? [] : (historial?.classDates ?? []).filter((f) => f.startsWith(anioFiltro))),
    [historial, anioFiltro],
  );

  function cambiarAnio(anio) {
    setAnioFiltro(anio);
    setFechaFiltro('TODAS');
  }

  const conteos = useMemo(() => {
    const totales = { PRESENT: 0, LATE: 0, ABSENT: 0, JUSTIFIED: 0, JUSTIFICATION_PENDING: 0 };
    for (const fila of historial?.rows ?? []) {
      for (const [fecha, celda] of Object.entries(fila.cellsByDate)) {
        if (anioFiltro !== 'TODOS' && !fecha.startsWith(anioFiltro)) continue;
        if (fechaFiltro !== 'TODAS' && fecha !== fechaFiltro) continue;
        totales[celda.status] = (totales[celda.status] ?? 0) + 1;
      }
    }
    return totales;
  }, [historial, anioFiltro, fechaFiltro]);

  const total = conteos.PRESENT + conteos.LATE + conteos.ABSENT + conteos.JUSTIFIED + conteos.JUSTIFICATION_PENDING;
  const segmentos = [
    { key: 'PRESENT', label: 'Presente', colorVar: '--success', valor: conteos.PRESENT },
    { key: 'LATE', label: 'Tarde', colorVar: '--warning', valor: conteos.LATE },
    { key: 'ABSENT', label: 'Ausente', colorVar: '--danger', valor: conteos.ABSENT + conteos.JUSTIFICATION_PENDING },
    { key: 'JUSTIFIED', label: 'Justificada', colorVar: '--accent', valor: conteos.JUSTIFIED },
  ].filter((s) => s.valor > 0);

  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="font-heading text-size-14 font-semibold text-ink">Distribución de asistencia</div>
        <div className="flex flex-wrap gap-2">
          <Select value={anioFiltro} onValueChange={cambiarAnio}>
            <SelectTrigger className="w-full sm:w-[140px]"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="TODOS">Todos los años</SelectItem>
              {anios.map((anio) => <SelectItem key={anio} value={anio}>{anio}</SelectItem>)}
            </SelectContent>
          </Select>
          {anioFiltro !== 'TODOS' && (
            <Select value={fechaFiltro} onValueChange={setFechaFiltro}>
              <SelectTrigger className="w-full sm:w-[160px]"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="TODAS">Todas de {anioFiltro}</SelectItem>
                {fechasDelAnio.map((fecha) => <SelectItem key={fecha} value={fecha}>{fecha}</SelectItem>)}
              </SelectContent>
            </Select>
          )}
        </div>
      </div>
      {total === 0 ? (
        <EmptyState title="Todavía no hay datos para mostrar" />
      ) : (
        <GraficoDeTorta
          segmentos={segmentos}
          total={total}
          centroValor={fechaFiltro !== 'TODAS' ? 1 : anioFiltro === 'TODOS' ? historial.classDates.length : fechasDelAnio.length}
          centroLabel={fechaFiltro !== 'TODAS' ? 'clase' : 'clases'}
        />
      )}
    </GlassCard>
  );
}

function BarraAsistenciaPorAlumno({ entrada, totalClases }) {
  const [hover, setHover] = useState(false);
  const porcentaje = attendancePercentToPoints(entrada.attendancePercent) == null ? null : Math.round(attendancePercentToPoints(entrada.attendancePercent));
  const inasistencias = entrada.absenceStanding.countableAbsences;
  const presencias = totalClases != null ? Math.max(0, totalClases - inasistencias) : null;
  const pctPresencias = totalClases ? (presencias / totalClases) * 100 : 0;
  const pctInasistencias = totalClases ? (inasistencias / totalClases) * 100 : 0;
  const tooltip = totalClases
    ? `${presencias} presencia${presencias === 1 ? '' : 's'} · ${inasistencias} inasistencia${inasistencias === 1 ? '' : 's'}`
    : null;

  return (
    <div className="flex items-center gap-3 px-3 py-1.5">
      <div className="w-32 shrink-0 truncate text-size-12-5 font-medium text-ink">{entrada.studentName}</div>
      <div
        className="relative flex h-2 flex-1 cursor-default overflow-visible"
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
      >
        {tooltip && hover && (
          <div className="absolute -top-8 left-1/2 z-10 -translate-x-1/2 whitespace-nowrap rounded-md bg-ink px-2 py-1 text-size-11 font-semibold text-on-accent shadow-popover">
            {tooltip}
            <div className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-ink" />
          </div>
        )}
        <div className="flex h-full w-full overflow-hidden rounded-full bg-neutral-bg">
          {totalClases ? (
            <>
              <div className="h-full bg-success" style={{ width: `${pctPresencias}%` }} />
              <div className="h-full bg-danger" style={{ width: `${pctInasistencias}%` }} />
            </>
          ) : (
            porcentaje != null && (
              <div className={`h-full rounded-full ${entrada.absenceStanding.exceedsAbsenceLimit ? 'bg-danger' : 'bg-success'}`} style={{ width: `${porcentaje}%` }} />
            )
          )}
        </div>
      </div>
      <div className={`w-12 shrink-0 text-right text-size-12 font-semibold ${entrada.absenceStanding.exceedsAbsenceLimit ? 'text-danger' : 'text-ink-faint'}`}>
        {porcentaje == null ? '-' : `${porcentaje}%`}
      </div>
    </div>
  );
}

function TabReporteDeAsistencia({ type, sectionId }) {
  const { data: reporte, isLoading } = useReporteAusencias(type, sectionId);
  const { data: historial } = useHistorialAsistencia(type, sectionId);

  const totalClasesPorAlumno = useMemo(
    () => new Map((historial?.rows ?? []).map((fila) => [fila.studentId, Object.keys(fila.cellsByDate).length])),
    [historial],
  );

  if (isLoading) return <Skeleton className="mt-4 h-40" />;

  return (
    <div className="flex flex-col gap-4 pt-4">
      <DistribucionDeAsistencia type={type} sectionId={sectionId} />

      {(reporte ?? []).length > 0 && (
        <GlassCard className="flex flex-col gap-1 p-3">
          <div className="px-2 pb-1 font-heading text-size-14 font-semibold text-ink">% de asistencia por alumno</div>
          <div className="px-2 pb-1 text-size-11 text-ink-faint"><span className="hidden [@media(hover:hover)]:inline">Pasá el mouse por la barra para ver presencias e inasistencias</span><span className="[@media(hover:hover)]:hidden">Tocá la barra para ver presencias e inasistencias</span></div>
          {[...reporte].sort((a, b) => (attendancePercentToPoints(b.attendancePercent) ?? 0) - (attendancePercentToPoints(a.attendancePercent) ?? 0)).map((entrada) => (
            <BarraAsistenciaPorAlumno key={entrada.enrollmentId} entrada={entrada} totalClases={totalClasesPorAlumno.get(entrada.studentId)} />
          ))}
        </GlassCard>
      )}
    </div>
  );
}

const VIEW_ALIASES = { asistencia: 'pasar-lista', exportar: 'pasar-lista' };
const VIEWS = ['pasar-lista', 'historial', 'reporte'];
const SCOPE_BY_VIEW = { 'pasar-lista': 'class', historial: 'history', reporte: 'absence-report' };
const TAB_LIST_CLASS = 'group-data-horizontal/tabs:h-auto';
const TAB_TRIGGER_CLASS = 'min-h-11';

function exportDisabledReason({ ordersOpen, section, historyLoading, hasClasses }) {
  if (ordersOpen) return 'Elegí Pasar lista, Historial o Reporte para exportar';
  if (!section) return 'Elegí una comisión para exportar';
  if (historyLoading) return 'Cargando clases…';
  if (!hasClasses) return 'Todavía no diste ninguna clase';
  return null;
}

function AttendanceHeaderActions({ ordersOpen, ordersLink, pendingCount, activeScope, disabledReason, onExport, ordersRef, exportRef }) {
  const exportDisabled = disabledReason !== null;
  const scopes = Object.keys(EXPORT_SCOPES).sort((a, b) => (b === activeScope) - (a === activeScope));
  const blockWhenDisabled = (event) => {
    if (exportDisabled) event.preventDefault();
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button asChild variant="outline" className="min-h-11">
        <Link
          ref={ordersRef}
          to={ordersLink}
          aria-current={ordersOpen ? 'page' : undefined}
          aria-label={pendingCount > 0 ? `Mis pedidos, ${pendingRequestsLabel(pendingCount)}` : 'Mis pedidos'}
        >
          Mis pedidos
          <span aria-hidden="true"><TabCount count={pendingCount} /></span>
        </Link>
      </Button>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            ref={exportRef}
            variant="outline"
            className="min-h-11 aria-disabled:cursor-not-allowed aria-disabled:text-(--disabled-fg) aria-disabled:hover:text-(--disabled-fg)"
            aria-disabled={exportDisabled || undefined}
            aria-describedby={exportDisabled ? 'export-disabled-reason' : undefined}
            onPointerDown={blockWhenDisabled}
            onKeyDown={blockWhenDisabled}
          >
            <Download aria-hidden="true" />
            Exportar
            <ChevronDown aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent className="w-72" align="end" collisionPadding={8}>
          {scopes.map((scope) => (
            <DropdownMenuItem key={scope} onSelect={() => onExport(scope)}>
              <span className="flex-1">{EXPORT_SCOPES[scope].menuLabel}</span>
              {scope === activeScope && <span className="text-size-11 font-bold text-brand">Esta vista</span>}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}

function OrdersSubview({ backLink, expandedRequestId, headingRef }) {
  return (
    <section aria-labelledby="my-orders-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="my-orders-heading" ref={headingRef} tabIndex={-1} className="font-heading text-xl font-bold text-ink outline-none">Mis pedidos</h2>
        <Button asChild variant="outline" className="min-h-11">
          <Link to={backLink}>
            <ChevronLeft aria-hidden="true" />
            Volver a Pasar lista
          </Link>
        </Button>
      </div>
      <MisPedidosDeAsistencia expandedRequestId={expandedRequestId} />
    </section>
  );
}

export function DocenteTomarAsistencia() {
  const { data: comisiones, isLoading, isError, error, refetch } = useMisComisionesParaAsistencia();
  const { data: pedidos } = useMisPedidosDeAsistencia();
  const [searchParams, setSearchParams] = useSearchParams();
  const [exportScope, setExportScope] = useState(null);
  const ordersRef = useRef(null);
  const exportRef = useRef(null);
  const ordersHeadingRef = useRef(null);
  const wasOrdersOpen = useRef(false);

  const tabParam = searchParams.get('tab');
  const view = VIEW_ALIASES[tabParam] ?? tabParam;
  const ordersOpen = view === 'pedidos';
  const activeView = VIEWS.includes(view) ? view : 'pasar-lista';
  const seccionId = searchParams.get('seccion');
  const requestIdParam = Number(searchParams.get('pedido')) || null;

  const lista = comisiones ?? [];
  const seccion = lista.find((c) => sectionParamOf(c) === seccionId);
  const { data: historial, isLoading: historyLoading } = useHistorialAsistencia(seccion?.type, seccion?.id);
  const classDates = historial?.classDates ?? [];
  const openRequest = requestIdParam ? (pedidos ?? []).find((p) => p.id === requestIdParam) : null;
  const returnRequest = activeView === 'historial' && !ordersOpen ? openRequest : null;
  const backSection = seccionId ?? sectionParamOfRequest(openRequest, lista);

  function updateParams(changes, options) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      Object.entries(changes).forEach(([key, value]) => (value == null ? next.delete(key) : next.set(key, value)));
      return next;
    }, options);
  }

  useEffect(() => {
    if (VIEW_ALIASES[tabParam]) {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set('tab', VIEW_ALIASES[tabParam]);
        return next;
      }, { replace: true });
    }
  }, [tabParam, setSearchParams]);

  useEffect(() => {
    if (ordersOpen) ordersHeadingRef.current?.focus();
    else if (wasOrdersOpen.current) ordersRef.current?.focus();
    wasOrdersOpen.current = ordersOpen;
  }, [ordersOpen]);

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  if (isError && comisiones === undefined) {
    return (
      <PageContainer>
        <h1 className="font-heading text-2xl font-bold text-ink">Tomar asistencia</h1>
        <GlassCard className="p-4">
          <LoadErrorState bare error={error} resource="tus comisiones" onRetry={refetch} />
        </GlassCard>
      </PageContainer>
    );
  }

  const disabledReason = exportDisabledReason({
    ordersOpen,
    section: seccion,
    historyLoading,
    hasClasses: classDates.length > 0,
  });

  return (
    <PageContainer>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-heading text-2xl font-bold text-ink">Tomar asistencia</h1>
        <AttendanceHeaderActions
          ordersOpen={ordersOpen}
          ordersLink={ordersHref(seccionId)}
          pendingCount={pendingRequestCount(pedidos)}
          activeScope={ordersOpen ? null : SCOPE_BY_VIEW[activeView]}
          disabledReason={disabledReason}
          onExport={setExportScope}
          ordersRef={ordersRef}
          exportRef={exportRef}
        />
      </div>
      {disabledReason && (
        <p id="export-disabled-reason" className="-mt-2 text-size-12-5 text-ink-faint">{disabledReason}</p>
      )}

      {ordersOpen ? (
        <OrdersSubview backLink={attendanceHref({ seccion: backSection })} expandedRequestId={requestIdParam} headingRef={ordersHeadingRef} />
      ) : lista.length === 0 ? (
        <GlassCard className="p-4">
          <EmptyState title="Todavía no tenés comisiones" description="Cuando Dirección te asigne una comisión, aparece acá." />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-4">
          <Select value={seccionId ?? undefined} onValueChange={(value) => updateParams({ seccion: value })}>
            <SelectTrigger className="w-full sm:w-[280px]"><SelectValue placeholder="Elegí una comisión" /></SelectTrigger>
            <SelectContent>
              {lista.map((c) => (
                <SelectItem key={sectionParamOf(c)} value={sectionParamOf(c)}>{c.subjectName} · {c.sectionName}{c.termStartDate ? ` (${new Date(c.termStartDate).getFullYear()})` : ''}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          {!seccion ? (
            <GlassCard className="p-4">
              <EmptyState title="Elegí una comisión" description="Seleccioná arriba la comisión de la que querés tomar asistencia." />
            </GlassCard>
          ) : (
            <Tabs value={activeView} onValueChange={(value) => updateParams({ tab: value, pedido: null, fecha: null })}>
              <TabsList variant="segmented" aria-label="Vistas de asistencia de la comisión" className={TAB_LIST_CLASS}>
                <TabsTrigger value="pasar-lista" className={TAB_TRIGGER_CLASS}>Pasar lista</TabsTrigger>
                <TabsTrigger value="historial" className={TAB_TRIGGER_CLASS}>Historial</TabsTrigger>
                <TabsTrigger value="reporte" aria-label="Reporte de asistencia" className={TAB_TRIGGER_CLASS}>Reporte</TabsTrigger>
              </TabsList>
              <TabsContent value="pasar-lista"><TabAsistencia type={seccion.type} sectionId={seccion.id} /></TabsContent>
              <TabsContent value="historial"><TabHistorial type={seccion.type} sectionId={seccion.id} returnRequest={returnRequest} /></TabsContent>
              <TabsContent value="reporte"><TabReporteDeAsistencia type={seccion.type} sectionId={seccion.id} /></TabsContent>
            </Tabs>
          )}
        </div>
      )}

      <ExportAttendanceDialog
        scope={exportScope}
        type={seccion?.type}
        sectionId={seccion?.id}
        classDates={classDates}
        onClose={() => setExportScope(null)}
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          exportRef.current?.focus();
        }}
      />
    </PageContainer>
  );
}
