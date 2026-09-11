import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { useMisCursos, useMisMatriculaciones } from '@/hooks/useAlumno';
import { useFacturasDeCurso, useFacturasDeMatricula, useBillingStanding } from '@/hooks/useBilling';
import { cn } from '@/lib/utils';

const FEE_TYPE_LABELS = {
  RECURRING_FEE: 'Cuota mensual',
  ENROLLMENT_FEE: 'Matrícula',
  SUBSCRIPTION_FEE: 'Cuota anual',
  EXAM_RIGHT: 'Derecho a examen',
  RETAKEN_SUBJECT_FEE: 'Recurse',
  DEGREE_ISSUANCE: 'Emisión de título',
  ADMINISTRATIVE_FEE: 'Gestión administrativa',
};

const STATUS_LABELS = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  OVERDUE: { label: 'Vencida', tone: 'danger' },
  PAID: { label: 'Pagada', tone: 'success' },
  WAIVED: { label: 'Bonificada', tone: 'neutral' },
  CANCELLED: { label: 'Anulada', tone: 'neutral' },
};

const BILLING_STANDING_LABELS = {
  AL_DIA: { label: 'Al día', tone: 'success' },
  MOROSO: { label: 'Moroso', tone: 'danger' },
  SIN_INFORMACION: { label: 'Sin info', tone: 'neutral' },
};

function formatPeriod(period) {
  if (!period) return '-';
  const [year, month] = period.split('-');
  const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  return `${MESES[parseInt(month, 10) - 1]} ${year}`;
}

function formatMonto(amount) {
  if (amount == null) return '-';
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 0 }).format(amount);
}

function formatFecha(date) {
  if (!date) return '-';
  const d = new Date(date + 'T00:00:00');
  return d.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function EnrollmentTab({ label, isActive, billingStanding, onClick }) {
  const standing = billingStanding ? (BILLING_STANDING_LABELS[billingStanding] ?? { label: billingStanding, tone: 'neutral' }) : null;
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex min-w-[140px] flex-col gap-1 rounded-lg border px-4 py-3 text-left transition-colors',
        isActive
          ? 'border-brand bg-accent-soft text-ink'
          : 'border-border bg-surface text-ink-faint hover:border-brand/50 hover:text-ink',
      )}
    >
      <span className="line-clamp-1 text-[13px] font-semibold">{label}</span>
      {standing && <StatusPill tone={standing.tone} label={standing.label} />}
    </button>
  );
}

function InvoicesTable({ invoices, onPagar }) {
  const navigate = useNavigate();
  if (!invoices?.length) {
    return <p className="py-8 text-center text-sm text-ink-faint">No hay facturas para esta inscripción.</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-[12px] font-semibold uppercase tracking-wide text-ink-faint">
            <th className="py-2.5 pr-4">Concepto</th>
            <th className="py-2.5 pr-4">Período</th>
            <th className="py-2.5 pr-4">Vencimiento</th>
            <th className="py-2.5 pr-4 text-right">Importe</th>
            <th className="py-2.5 pr-4">Estado</th>
            <th className="py-2.5" />
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {invoices.map((inv) => {
            const statusInfo = STATUS_LABELS[inv.status] ?? { label: inv.status, tone: 'neutral' };
            const pagable = inv.status === 'PENDING' || inv.status === 'OVERDUE';
            return (
              <tr key={inv.id} className="hover:bg-neutral-bg/40">
                <td className="py-3 pr-4 font-medium">{FEE_TYPE_LABELS[inv.feeType] ?? inv.feeType}</td>
                <td className="py-3 pr-4 text-ink-faint">{formatPeriod(inv.period)}</td>
                <td className="py-3 pr-4 text-ink-faint">{formatFecha(inv.dueDate)}</td>
                <td className="py-3 pr-4 text-right font-mono font-semibold tabular-nums">
                  {formatMonto(inv.remainingBalance)}
                </td>
                <td className="py-3 pr-4">
                  <StatusPill tone={statusInfo.tone} label={statusInfo.label} />
                </td>
                <td className="py-3">
                  {pagable && (
                    <button
                      type="button"
                      onClick={() => navigate(`/alumno/pagos/${inv.id}/seleccion`)}
                      className="rounded-lg bg-gradient-to-r from-brand to-accent-2 px-3 py-1.5 text-[12px] font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
                    >
                      Pagar
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function EnrollmentInvoices({ enrollment }) {
  const isCourse = enrollment.type === 'course';
  const cursoQuery = useFacturasDeCurso(isCourse ? enrollment.enrollmentId : null);
  const matriculaQuery = useFacturasDeMatricula(!isCourse ? enrollment.enrollmentId : null);
  const { data, isLoading } = isCourse ? cursoQuery : matriculaQuery;

  if (isLoading) return <Skeleton className="h-32" />;
  return <InvoicesTable invoices={data?.invoices} />;
}

export function AlumnoPagos() {
  const { data: cursos, isLoading: cargandoCursos } = useMisCursos();
  const { data: carreras, isLoading: cargandoCarreras } = useMisMatriculaciones();
  const { data: standings } = useBillingStanding();
  const [seleccionado, setSeleccionado] = useState(null);

  const cargando = cargandoCursos || cargandoCarreras;

  function findStanding(type, id) {
    const enrollmentType = type === 'course' ? 'CourseEnrollment' : 'ProgramEnrollment';
    return standings?.find((s) => s.enrollmentType === enrollmentType && s.enrollmentId === id)?.billingStanding ?? null;
  }

  const enrollments = [
    ...(cursos ?? []).map((c) => ({
      id: `course-${c.id}`,
      type: 'course',
      enrollmentId: c.id,
      label: c.courseName ?? `Curso #${c.courseId}`,
      billingStanding: findStanding('course', c.id),
    })),
    ...(carreras ?? []).map((m) => ({
      id: `program-${m.id}`,
      type: 'program',
      enrollmentId: m.id,
      label: m.programName ?? `Carrera #${m.programId}`,
      billingStanding: findStanding('program', m.id),
    })),
  ];

  const seleccionadoObj = enrollments.find((e) => e.id === seleccionado) ?? enrollments[0] ?? null;

  if (!seleccionado && enrollments.length > 0 && seleccionadoObj) {
    setSeleccionado(seleccionadoObj.id);
  }

  return (
    <PageContainer>
      <h1 className="text-xl font-bold">Mis pagos</h1>

      {cargando && (
        <div className="flex gap-3">
          {[1, 2, 3].map((i) => <Skeleton key={i} className="h-16 w-40" />)}
        </div>
      )}

      {!cargando && enrollments.length === 0 && (
        <GlassCard className="p-8 text-center text-sm text-ink-faint">
          No tenés inscripciones activas.
        </GlassCard>
      )}

      {!cargando && enrollments.length > 0 && (
        <>
          <div className="flex flex-wrap gap-3">
            {enrollments.map((e) => (
              <EnrollmentTab
                key={e.id}
                label={e.label}
                isActive={seleccionado === e.id}
                billingStanding={e.billingStanding}
                onClick={() => setSeleccionado(e.id)}
              />
            ))}
          </div>

          {seleccionadoObj && (
            <GlassCard className="p-5">
              <EnrollmentInvoices enrollment={seleccionadoObj} />
            </GlassCard>
          )}
        </>
      )}
    </PageContainer>
  );
}
