import { useEffect, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import {
  useMisComisionesParaAsistencia,
  useRosterDeAsistencia,
  useGuardarAsistencia,
  useCancelarClase,
  useClaseDelDia,
  useHistorialAsistencia,
  useReporteAusencias,
  useSolicitarModificacion,
  exportarAsistencia,
} from '@/hooks/useDocenteAsistencia';

const ESTADOS = [
  { value: 'PRESENT', label: 'Presente', filaClass: 'bg-success-bg', chipActivo: 'bg-success text-white' },
  { value: 'LATE', label: 'Tarde', filaClass: 'bg-warning-bg', chipActivo: 'bg-warning text-white' },
  { value: 'ABSENT', label: 'Ausente', filaClass: 'bg-danger-bg', chipActivo: 'bg-danger text-white' },
  { value: 'JUSTIFIED', label: 'Justificada', filaClass: 'bg-accent-soft', chipActivo: 'bg-brand text-white' },
];
const CHIP_INACTIVO = 'bg-neutral-bg text-ink-faint hover:bg-neutral-bg/70';
const ESTADO_POR_VALOR = Object.fromEntries(ESTADOS.map((e) => [e.value, e]));

function hoyISO() {
  return new Date().toISOString().slice(0, 10);
}

const NOMBRE_MES = new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric' });

function agruparFechasPorMes(fechas) {
  const grupos = new Map();
  for (const fecha of fechas) {
    const mes = fecha.slice(0, 7);
    if (!grupos.has(mes)) grupos.set(mes, []);
    grupos.get(mes).push(fecha);
  }
  return [...grupos.entries()].map(([mes, fechasDelMes]) => ({
    mes,
    etiqueta: NOMBRE_MES.format(new Date(`${mes}-02T00:00:00`)),
    fechas: fechasDelMes,
  }));
}

const ESTADOS_QUE_CUENTAN_COMO_AUSENCIA = ['ABSENT', 'JUSTIFIED'];

function FilaAlumno({ alumno, estado, onCambiarEstado, deshabilitada }) {
  const config = estado ? ESTADO_POR_VALOR[estado] : null;
  const sumaAusenciaHoy = ESTADOS_QUE_CUENTAN_COMO_AUSENCIA.includes(estado);
  const inasistenciasAMostrar = alumno.absenceCount + (sumaAusenciaHoy ? 1 : 0);
  return (
    <div className={`flex flex-col gap-2 rounded-2xl p-3 sm:flex-row sm:items-center sm:justify-between ${config?.filaClass ?? 'bg-neutral-bg/40'} ${deshabilitada ? 'opacity-50' : ''}`}>
      <div className="min-w-0 flex-1">
        <div className="font-heading text-[14px] font-semibold text-ink">{alumno.studentName}</div>
        {alumno.atRisk && <div className="text-[12px] text-danger">⚠ en riesgo de libre</div>}
      </div>
      <div className={`w-24 shrink-0 text-center text-[12.5px] font-bold ${sumaAusenciaHoy ? 'text-danger' : 'text-ink-faint'}`}>
        {inasistenciasAMostrar} inasist.
      </div>
      <div className="flex shrink-0 flex-wrap gap-1.5">
        {ESTADOS.map((e) => (
          <button
            key={e.value}
            type="button"
            disabled={deshabilitada}
            onClick={() => onCambiarEstado(alumno.enrollmentId, e.value)}
            className={`rounded-full px-3 py-1 text-[11.5px] font-bold transition-colors disabled:cursor-not-allowed ${estado === e.value ? e.chipActivo : CHIP_INACTIVO}`}
          >
            {e.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function TabAsistencia({ type, sectionId }) {
  const { data: roster, isLoading: cargandoRoster } = useRosterDeAsistencia(type, sectionId);
  const { data: historial } = useHistorialAsistencia(type, sectionId);
  const guardar = useGuardarAsistencia(type, sectionId);
  const cancelar = useCancelarClase(type, sectionId);

  const [classDate, setClassDate] = useState(hoyISO);
  const { data: claseDelDia } = useClaseDelDia(type, sectionId, classDate);
  const [busqueda, setBusqueda] = useState('');
  const [estados, setEstados] = useState({});
  const [topic, setTopic] = useState('');
  const [observations, setObservations] = useState('');
  const [huboCambios, setHuboCambios] = useState(false);
  const [confirmarMarcarTodos, setConfirmarMarcarTodos] = useState(false);
  const [confirmarCancelar, setConfirmarCancelar] = useState(false);
  const [error, setError] = useState(null);

  const lista = useMemo(() => roster ?? [], [roster]);

  // Lo ya guardado para esta fecha puntual (si el docente ya la había cargado antes) —
  // se arma cruzando el roster (enrollmentId + studentId) con el historial (studentId +
  // estado por fecha), sin pegarle a un endpoint nuevo.
  const estadosGuardados = useMemo(() => {
    if (!historial) return {};
    const porStudentId = new Map(historial.rows.map((fila) => [fila.studentId, fila.cellsByDate[classDate]]));
    const resultado = {};
    for (const alumno of lista) {
      const celda = porStudentId.get(alumno.studentId);
      if (celda) resultado[alumno.enrollmentId] = celda.status;
    }
    return resultado;
  }, [historial, lista, classDate]);
  const hayAsistenciaGuardada = Object.keys(estadosGuardados).length > 0;

  useEffect(() => {
    setEstados(estadosGuardados);
    setHuboCambios(false);
    setError(null);
  }, [sectionId, classDate, estadosGuardados]);

  useEffect(() => {
    if (claseDelDia) {
      setTopic(claseDelDia.topic ?? '');
      setObservations(claseDelDia.observations ?? '');
    }
  }, [claseDelDia]);

  const claseCancelada = claseDelDia?.status === 'CANCELED';

  const listaFiltrada = useMemo(
    () => lista.filter((a) => a.studentName.toLowerCase().includes(busqueda.toLowerCase()) || (a.dni ?? '').includes(busqueda)),
    [lista, busqueda],
  );

  function cambiarEstado(enrollmentId, status) {
    setEstados((prev) => ({ ...prev, [enrollmentId]: status }));
    setHuboCambios(true);
  }

  function marcarTodosPresentes() {
    setEstados((prev) => {
      const next = { ...prev };
      for (const alumno of lista) {
        if (!next[alumno.enrollmentId]) next[alumno.enrollmentId] = 'PRESENT';
      }
      return next;
    });
    setHuboCambios(true);
    setConfirmarMarcarTodos(false);
  }

  async function guardarAsistencia() {
    setError(null);
    const entries = Object.entries(estados).map(([enrollmentId, status]) => ({ enrollmentId: Number(enrollmentId), status }));
    try {
      await guardar.mutateAsync({ classDate, entries, topic: topic || null, observations: observations || null });
      setHuboCambios(false);
    } catch {
      setError('No se pudo guardar la asistencia. Los cambios siguen acá, probá de nuevo.');
    }
  }

  async function confirmarCancelarClase() {
    try {
      await cancelar.mutateAsync(classDate);
    } finally {
      setConfirmarCancelar(false);
    }
  }

  const procesados = Object.keys(estados).length;
  const presentes = Object.values(estados).filter((s) => s === 'PRESENT').length;
  const ausentes = Object.values(estados).filter((s) => s === 'ABSENT').length;

  if (cargandoRoster) {
    return (
      <div className="flex flex-col gap-2 pt-4">
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 pt-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Input type="date" value={classDate} onChange={(e) => setClassDate(e.target.value)} className="w-fit" />
        <div className="text-[12.5px] font-semibold">
          {huboCambios ? (
            <span className="text-warning">● Cambios sin guardar</span>
          ) : hayAsistenciaGuardada ? (
            <span className="text-success">✓ Asistencia guardada</span>
          ) : (
            <span className="text-ink-faint">Todavía no se tomó asistencia esta fecha</span>
          )}
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatCard label="Inscriptos">
          <div className="font-heading text-xl font-bold text-ink">{lista.length}</div>
        </StatCard>
        <StatCard label="Presentes">
          <div className="font-heading text-xl font-bold text-success">{presentes}</div>
        </StatCard>
        <StatCard label="Ausentes">
          <div className="font-heading text-xl font-bold text-danger">{ausentes}</div>
        </StatCard>
      </div>

      {claseCancelada && (
        <div className="rounded-xl bg-danger-bg px-4 py-2 text-[13px] font-semibold text-danger">
          Esta clase está cancelada — no cuenta como inasistencia para nadie. Podés seguir editando el temario/observaciones, pero la grilla de asistencia queda deshabilitada.
        </div>
      )}

      {error && <div className="rounded-xl bg-danger-bg px-4 py-2 text-[13px] font-semibold text-danger">{error}</div>}

      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Search className="h-4 w-4 text-ink-faint" />
            <Input placeholder="Buscar alumno..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} className="w-[220px]" />
          </div>
          <div className="flex items-center gap-3">
            <span className="text-[12px] text-ink-faint">{procesados} / {lista.length} procesados</span>
            <Button variant="outline" size="sm" onClick={() => setConfirmarMarcarTodos(true)} disabled={lista.length === 0 || claseCancelada}>
              Marcar todos presentes
            </Button>
          </div>
        </div>

        {listaFiltrada.length === 0 ? (
          <EmptyState title="No hay alumnos para mostrar" description="Esta comisión no tiene alumnos matriculados activos, o ninguno coincide con la búsqueda." />
        ) : (
          <div className="flex flex-col gap-2">
            <div className="hidden px-3 text-[11px] font-bold uppercase tracking-wide text-ink-faint sm:flex sm:items-center sm:justify-between">
              <span>Alumno</span>
              <span className="w-24 shrink-0 text-center">Inasistencias</span>
              <span className="shrink-0">Estado</span>
            </div>
            {listaFiltrada.map((alumno) => (
              <FilaAlumno
                key={alumno.enrollmentId}
                alumno={alumno}
                estado={estados[alumno.enrollmentId]}
                onCambiarEstado={cambiarEstado}
                deshabilitada={claseCancelada}
              />
            ))}
          </div>
        )}
      </GlassCard>

      <GlassCard className="flex flex-col gap-3 p-4">
        <div>
          <div className="font-heading text-[14px] font-semibold text-ink">Temario</div>
          <Textarea value={topic} onChange={(e) => setTopic(e.target.value.slice(0, 1000))} placeholder="Escriba los temas desarrollados durante la clase..." />
          <div className="mt-1 text-right text-[11px] text-ink-faint">{topic.length} / 1000 caracteres</div>
        </div>
        <div>
          <div className="font-heading text-[14px] font-semibold text-ink">Observaciones (opcional)</div>
          <Textarea value={observations} onChange={(e) => setObservations(e.target.value.slice(0, 500))} placeholder="Observaciones de la clase..." />
          <div className="mt-1 text-right text-[11px] text-ink-faint">{observations.length} / 500 caracteres</div>
        </div>
      </GlassCard>

      <div className="flex items-center justify-between">
        <Button variant="destructive" size="sm" onClick={() => setConfirmarCancelar(true)} disabled={claseCancelada}>
          Cancelar clase
        </Button>
        <Button variant="gradient" onClick={guardarAsistencia} disabled={!huboCambios || guardar.isPending || claseCancelada}>
          Guardar asistencia
        </Button>
      </div>

      <Dialog open={confirmarMarcarTodos} onOpenChange={setConfirmarMarcarTodos}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>¿Marcar a los {lista.length} alumnos como presentes?</DialogTitle>
            <DialogDescription>Esto reemplaza únicamente a los alumnos que todavía no tienen asistencia cargada. No modifica lo ya marcado.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmarMarcarTodos(false)}>Cancelar</Button>
            <Button variant="gradient" onClick={marcarTodosPresentes}>Marcar presentes</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmarCancelar} onOpenChange={setConfirmarCancelar}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>¿Cancelar la clase del {classDate}?</DialogTitle>
            <DialogDescription>Esta fecha deja de contar como inasistencia para todos los alumnos.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmarCancelar(false)}>Volver</Button>
            <Button variant="destructive" onClick={confirmarCancelarClase}>Cancelar clase</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function ModalSolicitarModificacion({ attendanceId, estadoActual, onClose }) {
  const [nuevoEstado, setNuevoEstado] = useState('PRESENT');
  const [motivo, setMotivo] = useState('');
  const solicitar = useSolicitarModificacion(attendanceId);

  async function enviar() {
    await solicitar.mutateAsync({ requestedStatus: nuevoEstado, reason: motivo });
    onClose();
  }

  return (
    <Dialog open={!!attendanceId} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Solicitar modificación de asistencia</DialogTitle>
          <DialogDescription>Estado actual: {ESTADO_POR_VALOR[estadoActual]?.label ?? estadoActual}. La modificación queda pendiente de aprobación.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <Select value={nuevoEstado} onValueChange={setNuevoEstado}>
            <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              {ESTADOS.map((e) => <SelectItem key={e.value} value={e.value}>{e.label}</SelectItem>)}
            </SelectContent>
          </Select>
          <Textarea placeholder="Motivo de la modificación..." value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button variant="gradient" onClick={enviar} disabled={!motivo || solicitar.isPending}>Enviar solicitud</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
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

function TabHistorial({ type, sectionId }) {
  const { data: historial, isLoading } = useHistorialAsistencia(type, sectionId);
  const [celdaSeleccionada, setCeldaSeleccionada] = useState(null);
  const [solicitandoModificacion, setSolicitandoModificacion] = useState(false);
  const [alumnoHover, setAlumnoHover] = useState(null);
  const [alumnoFijado, setAlumnoFijado] = useState(null);
  const alumnoResaltado = alumnoHover ?? alumnoFijado;
  const [fechaHover, setFechaHover] = useState(null);

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
      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="font-heading text-[14px] font-semibold text-ink">Historial de asistencia</div>
        <div className="overflow-x-auto">
      <table className="border-separate border-spacing-0 text-[11.5px]">
        <thead>
          <tr>
            <th rowSpan={3} className={`${celdaSticky} bg-surface backdrop-blur-[20px] backdrop-saturate-[1.6] whitespace-nowrap border-b border-border px-3 py-1 text-left align-bottom font-semibold text-ink-faint`}>
              Alumno
            </th>
            {gruposPorAnio.map((a, i) => (
              <th
                key={a.anio}
                colSpan={a.meses.reduce((acc, m) => acc + m.dias.length, 0)}
                className={`bg-surface backdrop-blur-[20px] backdrop-saturate-[1.6] border-b border-border px-1 py-1 text-center font-semibold text-ink-faint ${i < gruposPorAnio.length - 1 ? divisorDeAnio : ''}`}
              >
                {a.anio}
              </th>
            ))}
            <th rowSpan={3} className="bg-surface backdrop-blur-[20px] backdrop-saturate-[1.6] border-b border-border px-2 py-1 text-center align-bottom font-semibold text-ink-faint">%</th>
          </tr>
          <tr>
            {gruposPorAnio.flatMap((a) =>
              a.meses.map((m, i) => (
                <th
                  key={`${a.anio}-${m.mes}`}
                  colSpan={m.dias.length}
                  className={`bg-surface backdrop-blur-[20px] backdrop-saturate-[1.6] border-b border-border px-1 py-1 text-center font-semibold capitalize text-ink-faint ${i === a.meses.length - 1 ? divisorDeAnio : divisorDeMes}`}
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
                className={`bg-surface backdrop-blur-[20px] backdrop-saturate-[1.6] w-7 border-b border-border px-0.5 py-1 text-center font-normal text-ink-faint ${divisor(fecha)} ${fecha === fechaHover ? 'shadow-[inset_3px_0_0_var(--accent-2),inset_-3px_0_0_var(--accent-2)]' : ''}`}
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
                <td className={`${celdaSticky} whitespace-nowrap border-y-2 px-3 py-1 font-medium ${resaltada ? 'border-accent-2 bg-gradient-to-r from-brand to-accent-2 text-white' : 'border-transparent bg-surface backdrop-blur-[20px] backdrop-saturate-[1.6] text-ink'}`}>
                  {fila.studentName}
                </td>
                {historial.classDates.map((fecha) => {
                  const celda = fila.cellsByDate[fecha];
                  const config = celda ? ESTADO_POR_VALOR[celda.status] : null;
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
                      className={`w-7 border-y-[3px] px-0.5 py-1 text-center ${celda ? 'cursor-pointer' : ''} ${config ? config.filaClass : ''} ${divisor(fecha)} ${resaltada ? 'border-accent-2' : 'border-transparent'} ${fecha === fechaHover ? 'shadow-[inset_3px_0_0_var(--accent-2),inset_-3px_0_0_var(--accent-2)]' : ''}`}
                      title={config?.label ?? 'Sin cargar'}
                    >
                      {config ? config.label[0] : '·'}
                    </td>
                  );
                })}
                <td className={`border-y-2 px-2 py-1 text-center font-semibold ${resaltada ? 'border-accent-2 bg-gradient-to-r from-brand to-accent-2 text-white' : 'border-transparent text-ink'}`}>
                  {fila.attendancePercentage == null ? '-' : `${Math.round(fila.attendancePercentage * 100)}%`}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
        </div>
      </GlassCard>

      <Dialog open={!!celdaSeleccionada && !solicitandoModificacion} onOpenChange={(open) => !open && setCeldaSeleccionada(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Detalle de asistencia</DialogTitle>
            <DialogDescription>
              {celdaSeleccionada?.studentName} · {celdaSeleccionada?.fecha} · {ESTADO_POR_VALOR[celdaSeleccionada?.status]?.label}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCeldaSeleccionada(null)}>Cerrar</Button>
            <Button variant="gradient" onClick={() => setSolicitandoModificacion(true)}>Solicitar modificación</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
  let acumulado = 0;
  const stops = segmentos.map((s) => {
    const desde = acumulado;
    acumulado += (s.valor / total) * 100;
    return `var(${s.colorVar}) ${desde}% ${acumulado}%`;
  }).join(', ');

  return (
    <div className="flex flex-wrap items-center gap-6">
      <div className="relative h-36 w-36 shrink-0 rounded-full" style={{ background: `conic-gradient(${stops})` }}>
        <div className="absolute inset-[16px] flex flex-col items-center justify-center rounded-full bg-surface">
          <div className="font-heading text-xl font-bold text-ink">{centroValor}</div>
          <div className="text-[10px] text-ink-faint">{centroLabel}</div>
        </div>
      </div>
      <div className="flex flex-col gap-1.5 text-[12.5px] text-ink-soft">
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
    { key: 'ABSENT', label: 'Ausente', colorVar: '--danger', valor: conteos.ABSENT },
    { key: 'JUSTIFIED', label: 'Justificada', colorVar: '--accent', valor: conteos.JUSTIFIED + conteos.JUSTIFICATION_PENDING },
  ].filter((s) => s.valor > 0);

  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="font-heading text-[14px] font-semibold text-ink">Distribución de asistencia</div>
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
  const porcentaje = entrada.attendancePercentage == null ? null : Math.round(entrada.attendancePercentage * 100);
  const inasistencias = entrada.absenceCount;
  // "Presencias" = clases registradas menos las que ya cuentan como inasistencia (Ausente,
  // justificación pendiente o justificada) — mismo criterio que absenceCount en el backend.
  const presencias = totalClases != null ? Math.max(0, totalClases - inasistencias) : null;
  const pctPresencias = totalClases ? (presencias / totalClases) * 100 : 0;
  const pctInasistencias = totalClases ? (inasistencias / totalClases) * 100 : 0;
  const tooltip = totalClases
    ? `${presencias} presencia${presencias === 1 ? '' : 's'} · ${inasistencias} inasistencia${inasistencias === 1 ? '' : 's'}`
    : null;

  return (
    <div className="flex items-center gap-3 px-3 py-1.5">
      <div className="w-32 shrink-0 truncate text-[12.5px] font-medium text-ink">{entrada.studentName}</div>
      <div
        className="relative flex h-2 flex-1 cursor-default overflow-visible"
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
      >
        {tooltip && hover && (
          <div className="absolute -top-8 left-1/2 z-10 -translate-x-1/2 whitespace-nowrap rounded-md bg-ink px-2 py-1 text-[11px] font-semibold text-white shadow-md">
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
              <div className={`h-full rounded-full ${entrada.atRisk ? 'bg-danger' : 'bg-success'}`} style={{ width: `${porcentaje}%` }} />
            )
          )}
        </div>
      </div>
      <div className={`w-12 shrink-0 text-right text-[12px] font-semibold ${entrada.atRisk ? 'text-danger' : 'text-ink-faint'}`}>
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
          <div className="px-2 pb-1 font-heading text-[14px] font-semibold text-ink">% de asistencia por alumno</div>
          <div className="px-2 pb-1 text-[11px] text-ink-faint">Pasá el mouse por la barra para ver presencias e inasistencias</div>
          {[...reporte].sort((a, b) => (b.attendancePercentage ?? 0) - (a.attendancePercentage ?? 0)).map((entrada) => (
            <BarraAsistenciaPorAlumno key={entrada.enrollmentId} entrada={entrada} totalClases={totalClasesPorAlumno.get(entrada.studentId)} />
          ))}
        </GlassCard>
      )}
    </div>
  );
}

function TabExportar({ type, sectionId }) {
  const { data: historial } = useHistorialAsistencia(type, sectionId);
  const [scope, setScope] = useState('class');
  const [format, setFormat] = useState('pdf');
  const [anioFiltro, setAnioFiltro] = useState('');
  const [classDate, setClassDate] = useState('');
  const [exportando, setExportando] = useState(false);

  const anios = useMemo(
    () => [...new Set((historial?.classDates ?? []).map((f) => f.slice(0, 4)))].sort(),
    [historial],
  );
  // Con carreras de varios años elegir el año primero evita mostrar de una las 300+
  // clases de toda la comisión — mismo criterio que el filtro de Reporte de asistencia.
  const fechasDelAnio = useMemo(
    () => (historial?.classDates ?? []).filter((f) => f.startsWith(anioFiltro)),
    [historial, anioFiltro],
  );
  const gruposPorMes = useMemo(() => agruparFechasPorMes(fechasDelAnio), [fechasDelAnio]);

  useEffect(() => {
    if (!anioFiltro && anios.length) setAnioFiltro(anios[anios.length - 1]);
  }, [anios, anioFiltro]);

  useEffect(() => {
    if (fechasDelAnio.length && !fechasDelAnio.includes(classDate)) {
      setClassDate(fechasDelAnio[fechasDelAnio.length - 1]);
    }
  }, [fechasDelAnio, classDate]);

  async function exportar() {
    setExportando(true);
    try {
      await exportarAsistencia(type, sectionId, { format, scope, classDate: scope === 'class' ? classDate : undefined });
    } finally {
      setExportando(false);
    }
  }

  return (
    <div className="flex flex-col gap-4 pt-4">
      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="font-heading text-[14px] font-semibold text-ink">¿Qué desea exportar?</div>
        <Select value={scope} onValueChange={setScope}>
          <SelectTrigger className="w-full sm:w-[280px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="class">Asistencia de una clase</SelectItem>
            <SelectItem value="history">Historial de asistencia</SelectItem>
            <SelectItem value="absence-report">Reporte de asistencia</SelectItem>
          </SelectContent>
        </Select>
        {scope === 'class' && (
          anios.length === 0 ? (
            <p className="text-[12.5px] text-ink-faint">Todavía no diste ninguna clase en esta comisión.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              <Select value={anioFiltro} onValueChange={setAnioFiltro}>
                <SelectTrigger className="w-full sm:w-[140px]"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {anios.map((anio) => <SelectItem key={anio} value={anio}>{anio}</SelectItem>)}
                </SelectContent>
              </Select>
              <Select value={classDate} onValueChange={setClassDate}>
                <SelectTrigger className="w-full sm:w-[280px]"><SelectValue placeholder="Elegí una clase" /></SelectTrigger>
                <SelectContent>
                  {gruposPorMes.map((grupo) => (
                    <SelectGroup key={grupo.mes}>
                      <SelectLabel>{grupo.etiqueta}</SelectLabel>
                      {grupo.fechas.map((fecha) => <SelectItem key={fecha} value={fecha}>{fecha}</SelectItem>)}
                    </SelectGroup>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )
        )}
      </GlassCard>

      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="font-heading text-[14px] font-semibold text-ink">Formato</div>
        <div className="flex gap-2">
          <Button variant={format === 'pdf' ? 'gradient' : 'outline'} onClick={() => setFormat('pdf')}>PDF</Button>
          <Button variant={format === 'xlsx' ? 'gradient' : 'outline'} onClick={() => setFormat('xlsx')}>XLS</Button>
        </div>
      </GlassCard>

      <Button variant="gradient" onClick={exportar} disabled={exportando || (scope === 'class' && !classDate)} className="w-fit">
        {exportando ? 'Generando...' : 'Exportar'}
      </Button>
    </div>
  );
}

export function DocenteTomarAsistencia() {
  const { data: comisiones, isLoading } = useMisComisionesParaAsistencia();
  const [seccionId, setSeccionId] = useState(null);

  const lista = comisiones ?? [];
  const seccion = lista.find((c) => String(c.id) === seccionId);

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  if (lista.length === 0) {
    return (
      <PageContainer>
        <h1 className="font-heading text-2xl font-bold text-ink">Tomar asistencia</h1>
        <GlassCard className="p-4">
          <EmptyState title="Todavía no tenés comisiones asignadas" description="Cuando Dirección te asigne una comisión, aparece acá." />
        </GlassCard>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-heading text-2xl font-bold text-ink">Tomar asistencia</h1>
        <Select value={seccionId ?? undefined} onValueChange={setSeccionId}>
          <SelectTrigger className="w-full sm:w-[280px]"><SelectValue placeholder="Elegí una comisión" /></SelectTrigger>
          <SelectContent>
            {lista.map((c) => (
              <SelectItem key={`${c.type}-${c.id}`} value={String(c.id)}>{c.subjectName} · {c.sectionName}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {!seccion ? (
        <GlassCard className="p-4">
          <EmptyState title="Elegí una comisión" description="Seleccioná arriba la comisión de la que querés tomar asistencia." />
        </GlassCard>
      ) : (
        <Tabs defaultValue="asistencia">
          <TabsList variant="segmented">
            <TabsTrigger value="asistencia">Asistencia</TabsTrigger>
            <TabsTrigger value="historial">Historial</TabsTrigger>
            <TabsTrigger value="reporte">Reporte de asistencia</TabsTrigger>
            <TabsTrigger value="exportar">Exportar</TabsTrigger>
          </TabsList>
          <TabsContent value="asistencia"><TabAsistencia type={seccion.type} sectionId={seccion.id} /></TabsContent>
          <TabsContent value="historial"><TabHistorial type={seccion.type} sectionId={seccion.id} /></TabsContent>
          <TabsContent value="reporte"><TabReporteDeAsistencia type={seccion.type} sectionId={seccion.id} /></TabsContent>
          <TabsContent value="exportar"><TabExportar type={seccion.type} sectionId={seccion.id} /></TabsContent>
        </Tabs>
      )}
    </PageContainer>
  );
}
