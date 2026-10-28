import { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, Search } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { useMisCursos, useMisMatriculaciones } from '@/features/enrollment';
import { AdmissionInvoicePendingCard, CreditBalanceCard } from '@/features/billing';
import { useEnrollmentPaymentStatus } from '@/hooks/useBilling';
import { formatDate } from '@/lib/billing';

const STANDING_CONFIG = {
  UP_TO_DATE: { label: 'Al día', tone: 'success' },
  OVERDUE: { label: 'Moroso', tone: 'danger' },
  SUSPENDED: { label: 'Suspendido', tone: 'warning' },
  WITHDRAWN_FOR_NONPAYMENT: { label: 'Dado de baja', tone: 'neutral' },
};

const ENROLLMENT_STATUS_LABELS = {
  ENROLLED: 'En curso',
  IN_PROGRESS: 'En curso',
  COMPLETED: 'Finalizado',
  WITHDRAWN: 'Dado de baja',
  SUSPENDED: 'Suspendido',
};

export function AlumnoPagos() {
  const navigate = useNavigate();
  const [busqueda, setBusqueda] = useState('');

  const { data: cursos, isLoading: cargandoCursos } = useMisCursos();
  const { data: carreras, isLoading: cargandoCarreras } = useMisMatriculaciones();
  const { data: standings } = useEnrollmentPaymentStatus();

  const cargando = cargandoCursos || cargandoCarreras;

  function findStanding(enrollmentType, id) {
    return standings?.find((s) => s.enrollmentType === enrollmentType && s.enrollmentId === id)?.paymentStatus ?? null;
  }

  const allEnrollments = useMemo(() => [
    ...(cursos ?? []).map((c) => ({
      key: `curso-${c.id}`,
      id: c.id,
      type: 'curso',
      typeLabel: 'Curso',
      name: c.courseName ?? `Curso #${c.courseId}`,
      status: c.status,
      enrollmentDate: c.enrollmentDate,
      standing: findStanding('CourseEnrollment', c.id),
    })),
    ...(carreras ?? []).map((m) => ({
      key: `carrera-${m.id}`,
      id: m.id,
      type: 'carrera',
      typeLabel: 'Carrera',
      name: m.programName ?? `Carrera #${m.programId}`,
      status: m.status,
      enrollmentDate: m.enrollmentDate,
      standing: findStanding('ProgramEnrollment', m.id),
    })),
  ], [cursos, carreras, standings]);

  const enrollments = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    if (!q) return allEnrollments;
    return allEnrollments.filter((e) =>
      e.name.toLowerCase().includes(q) || e.typeLabel.toLowerCase().includes(q),
    );
  }, [allEnrollments, busqueda]);

  return (
    <PageContainer>
      <div className="relative">
        <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-faint" />
        <input
          type="text"
          placeholder="Buscar curso o carrera…"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          className="w-full rounded-xl border border-border bg-surface py-2.5 pl-10 pr-4 text-sm text-ink outline-none placeholder:text-ink-faint focus:border-brand"
        />
      </div>

      <AdmissionInvoicePendingCard />

      <CreditBalanceCard />

      <GlassCard className="overflow-hidden p-0">
        {cargando && (
          <div className="flex flex-col divide-y divide-border">
            {[1, 2, 3].map((i) => (
              <div key={i} className="flex items-center gap-4 px-5 py-4">
                <Skeleton className="h-4 w-48" />
                <Skeleton className="h-4 w-16" />
                <Skeleton className="h-6 w-20 rounded-full" />
              </div>
            ))}
          </div>
        )}

        {!cargando && enrollments.length === 0 && (
          <p className="px-5 py-10 text-center text-sm text-ink-faint">
            {busqueda ? 'No hay resultados para esa búsqueda.' : 'No tenés inscripciones activas.'}
          </p>
        )}

        {!cargando && enrollments.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-5">Nombre</TableHead>
                <TableHead>Tipo</TableHead>
                <TableHead>Estado de inscripción</TableHead>
                <TableHead>Inscripto desde</TableHead>
                <TableHead>Estado de cuenta</TableHead>
                <TableHead className="pr-5" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {enrollments.map((e) => {
                const standingInfo = e.standing
                  ? (STANDING_CONFIG[e.standing] ?? { label: e.standing, tone: 'neutral' })
                  : null;
                return (
                  <TableRow key={e.key} onClick={() => navigate(`/alumno/pagos/${e.type}/${e.id}`)} className="cursor-pointer">
                    <TableCell className="pl-5 font-semibold text-ink">{e.name}</TableCell>
                    <TableCell className="text-ink-faint">{e.typeLabel}</TableCell>
                    <TableCell>
                      <StatusPill tone="neutral">{ENROLLMENT_STATUS_LABELS[e.status] ?? e.status ?? '-'}</StatusPill>
                    </TableCell>
                    <TableCell className="text-ink-faint">{formatDate(e.enrollmentDate) || '-'}</TableCell>
                    <TableCell>
                      {standingInfo ? (
                        <StatusPill tone={standingInfo.tone}>{standingInfo.label}</StatusPill>
                      ) : (
                        <span className="text-ink-faint">—</span>
                      )}
                    </TableCell>
                    <TableCell className="pr-5">
                      <ChevronRight size={16} className="text-ink-faint" />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </GlassCard>
    </PageContainer>
  );
}
