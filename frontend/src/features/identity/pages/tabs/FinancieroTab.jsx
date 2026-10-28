import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { RefundsSection, StudentDiscountsSection } from '@/features/billing';
import {
  useInscripcionesACarrera,
  useInscripcionesACurso,
  useEstadoMatriculaDeInscripcion,
  useHabilitacionesDeMatricula,
  useOtorgarHabilitacion,
  useRevocarHabilitacion,
} from '@/features/identity/hooks/useMatriculaAlumno';

const ESTADO_MATRICULA = {
  NOT_ISSUED: { label: 'Todavía no emitida', tone: 'neutral' },
  PENDING: { label: 'Pendiente de pago', tone: 'warning' },
  IN_REVIEW: { label: 'Comprobante en revisión', tone: 'accent' },
  PAID: { label: 'Paga', tone: 'success' },
  EXEMPTED: { label: 'Habilitado sin pagar', tone: 'accent' },
};

const MIN_MOTIVO = 10;

function formatearFecha(valor) {
  if (!valor) return '—';
  const [anio, mes, dia] = String(valor).slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}

function HabilitarDialog({ obligacion, programEnrollmentId, onClose }) {
  const otorgar = useOtorgarHabilitacion(programEnrollmentId);
  const [motivo, setMotivo] = useState('');
  const motivoValido = motivo.trim().length >= MIN_MOTIVO;

  async function handleSubmit(e) {
    e.preventDefault();
    if (!motivoValido) return;
    try {
      await otorgar.mutateAsync({ templateId: obligacion.templateId, slot: obligacion.slot, reason: motivo.trim() });
      toast.success('Habilitación registrada.');
      onClose();
    } catch {
      toast.error('No se pudo registrar la habilitación.');
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Permitir inscripción sin matrícula paga</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <p className="text-[13px] text-ink-soft">
            El alumno va a poder inscribirse a materias, mesas y pedir certificados sin tener paga la matrícula. La deuda se
            mantiene: la factura sigue pendiente hasta que se pague. La habilitación dura hasta que la revoques.
          </p>
          <div className="flex flex-col gap-1">
            <label htmlFor="habilitacion-motivo" className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Motivo</label>
            <textarea
              id="habilitacion-motivo"
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              maxLength={1000}
              rows={3}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-ink focus:outline-none focus:ring-1 focus:ring-accent"
            />
            <p className="text-[12px] text-ink-faint">
              {motivo.trim().length}/{MIN_MOTIVO} caracteres como mínimo
            </p>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" variant="gradient" loading={otorgar.isPending} disabled={!motivoValido}>Registrar habilitación</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function MatriculaDeInscripcion({ inscripcion }) {
  const { data: obligaciones, isLoading } = useEstadoMatriculaDeInscripcion(inscripcion.id);
  const { data: habilitaciones } = useHabilitacionesDeMatricula(inscripcion.id);
  const revocar = useRevocarHabilitacion(inscripcion.id);
  const [aHabilitar, setAHabilitar] = useState(null);

  async function handleRevocar(habilitacion) {
    try {
      await revocar.mutateAsync({ exemptionId: habilitacion.id, notes: null });
      toast.success('Habilitación revocada.');
    } catch {
      toast.error('No se pudo revocar la habilitación.');
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <h3 className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
        Matrícula — {inscripcion.programName ?? `Carrera ${inscripcion.programId}`}
      </h3>
      {isLoading ? (
        <Skeleton rows={2} />
      ) : !obligaciones?.length ? (
        <EmptyState title="Sin matrícula configurada" description="La carrera no tiene un año lectivo con matrícula para este período." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[480px] text-left text-[13px]">
            <thead>
              <tr className="text-[11px] uppercase tracking-widest text-ink-faint">
                <th className="py-2 pr-3 font-bold">Matrícula</th>
                <th className="py-2 pr-3 font-bold">Estado</th>
                <th className="py-2 pr-3 font-bold">Vence</th>
                <th className="py-2 font-bold" />
              </tr>
            </thead>
            <tbody>
              {obligaciones.map((o) => {
                const estado = ESTADO_MATRICULA[o.status] ?? { label: o.status, tone: 'neutral' };
                return (
                  <tr key={`${o.templateId}-${o.slot}`}>
                    <td className="py-2 pr-3 text-ink">Matrícula {o.slot} del año</td>
                    <td className="py-2 pr-3"><StatusPill tone={estado.tone}>{estado.label}</StatusPill></td>
                    <td className="py-2 pr-3 text-ink-soft">{formatearFecha(o.dueDate)}</td>
                    <td className="py-2 text-right">
                      {!o.satisfied && (
                        <Button variant="outline" size="sm" onClick={() => setAHabilitar(o)} aria-label={`Permitir inscripción sin matrícula ${o.slot}`}>
                          Permitir inscripción sin matrícula
                        </Button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {habilitaciones?.length > 0 && (
        <div className="flex flex-col gap-2">
          <h4 className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Habilitaciones</h4>
          {habilitaciones.map((h) => (
            <div key={h.id} className="flex flex-wrap items-start justify-between gap-2 rounded-2xl px-4 py-3 hover:bg-neutral-bg/60">
              <div className="min-w-0">
                <p className="text-[13px] text-ink">{h.reason}</p>
                <p className="text-[12px] text-ink-faint">
                  Otorgada por {h.grantedByName} el {formatearFecha(h.grantedAt)}
                  {h.revokedAt ? ` · Revocada por ${h.revokedByName} el ${formatearFecha(h.revokedAt)}` : ''}
                </p>
              </div>
              {h.active ? (
                <Button variant="outline" size="sm" loading={revocar.isPending} onClick={() => handleRevocar(h)} aria-label={`Revocar habilitación de matrícula ${h.slot}`}>
                  Revocar
                </Button>
              ) : (
                <StatusPill tone="neutral">Revocada</StatusPill>
              )}
            </div>
          ))}
        </div>
      )}

      {aHabilitar && (
        <HabilitarDialog obligacion={aHabilitar} programEnrollmentId={inscripcion.id} onClose={() => setAHabilitar(null)} />
      )}
    </div>
  );
}

const ACTIVE_PROGRAM_STATUSES = ['ENROLLED', 'PAUSED'];
const ENROLLMENT_STATUS_LABELS = {
  PAUSED: 'pausada',
  WITHDRAWN: 'baja',
  COMPLETED: 'finalizada',
  GRADUATED: 'egresada',
};

function buildEnrollmentOptions(programs, courses) {
  const programOptions = (programs ?? []).map((enrollment) => ({
    key: `program-${enrollment.id}`,
    kind: 'program',
    id: enrollment.id,
    name: enrollment.programName ?? `Carrera ${enrollment.programId}`,
    typeLabel: 'carrera',
    status: enrollment.status,
    active: ACTIVE_PROGRAM_STATUSES.includes(enrollment.status),
    raw: enrollment,
  }));
  const courseOptions = (courses ?? []).map((enrollment) => ({
    key: `course-${enrollment.id}`,
    kind: 'course',
    id: enrollment.id,
    name: enrollment.courseName ?? `Curso ${enrollment.courseId}`,
    typeLabel: 'curso',
    status: enrollment.status,
    active: !['WITHDRAWN', 'COMPLETED'].includes(enrollment.status),
    raw: enrollment,
  }));
  return [...programOptions, ...courseOptions].sort((a, b) => Number(b.active) - Number(a.active));
}

function optionLabel(option) {
  const suffix = option.active ? '' : ENROLLMENT_STATUS_LABELS[option.status] ? ` (${ENROLLMENT_STATUS_LABELS[option.status]})` : '';
  return `${option.name} · ${option.typeLabel}${suffix}`;
}

function EnrollmentSelector({ options, value, onChange }) {
  if (options.length === 1) {
    return (
      <p className="text-[13px] text-ink-soft">
        Inscripción: <span className="font-semibold text-ink">{optionLabel(options[0])}</span>
      </p>
    );
  }
  return (
    <div className="flex flex-wrap items-center gap-3">
      <Label htmlFor="financial-enrollment" className="text-[13px] font-semibold">Inscripción</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger id="financial-enrollment" className="h-10 min-w-64">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.key} value={option.key}>{optionLabel(option)}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

function EnrollmentFinance({ studentId, roleBase }) {
  const programs = useInscripcionesACarrera(studentId);
  const courses = useInscripcionesACurso(studentId);
  const [selectedKey, setSelectedKey] = useState(null);
  const options = useMemo(() => buildEnrollmentOptions(programs.data, courses.data), [programs.data, courses.data]);

  if (programs.isLoading || courses.isLoading) {
    return <Skeleton rows={3} />;
  }
  if (!options.length) {
    return <GlassCard className="p-6"><EmptyState title="Sin inscripciones" description="El alumno todavía no tiene una carrera ni un curso." /></GlassCard>;
  }
  const selected = options.find((option) => option.key === selectedKey) ?? options[0];

  return (
    <>
      <EnrollmentSelector options={options} value={selected.key} onChange={setSelectedKey} />
      {selected.kind === 'program' && selected.active && <MatriculaDeInscripcion inscripcion={selected.raw} />}
      <StudentDiscountsSection key={selected.key} studentId={studentId} enrollment={{ kind: selected.kind, id: selected.id }} roleBase={roleBase} />
    </>
  );
}

export function FinancieroTab({ studentId, puedeHabilitar = false, roleBase }) {
  if (!puedeHabilitar) {
    return (
      <div className="flex min-h-[200px] items-center justify-center text-[13px] text-ink-faint">
        Disponible próximamente
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-6">
      <EnrollmentFinance studentId={studentId} roleBase={roleBase} />
      <RefundsSection studentId={studentId} />
    </div>
  );
}
