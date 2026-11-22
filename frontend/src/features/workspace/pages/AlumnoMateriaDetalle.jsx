import { useState } from 'react';
import { CheckCircle2, Clock, Flag, Star, UserX, XCircle } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell, TableCaption } from '@/components/ui/table';
import { useMiInscripcion, useDarmeDeBajaDeMateria } from '@/features/enrollment';
import { useNotasDeMateria } from '@/features/evaluation';
import { COLUMNAS_NOTAS, TIPO_NOTA_LABEL, mapearNotasAColumnas } from '@/lib/notas';
import { SeccionAsistencia } from '@/features/attendance';

const ESTADOS_QUE_ADMITEN_BAJA = new Set(['ENROLLED', 'IN_PROGRESS']);

const BADGE_ESTADO = {
  REGULAR: { label: 'Cursada regular', tone: 'success', Icon: Flag },
  IN_PROGRESS: { label: 'En curso', tone: 'accent', Icon: Clock },
  WAITLISTED: { label: 'En lista de espera', tone: 'warning', Icon: Clock },
  WAITLIST_EXPIRED: { label: 'Lista de espera vencida', tone: 'neutral', Icon: XCircle },
  ENROLLED: { label: 'Inscripto', tone: 'accent', Icon: CheckCircle2 },
  APPROVED: { label: 'Aprobada', tone: 'success', Icon: CheckCircle2 },
  FAILED: { label: 'Desaprobada', tone: 'danger', Icon: XCircle },
  WITHDRAWN: { label: 'De baja', tone: 'neutral', Icon: UserX },
};

export function AlumnoMateriaDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [modalBajaAbierto, setModalBajaAbierto] = useState(false);

  const {
    data: inscripcion,
    isLoading: cargandoInscripcion,
    isError: inscripcionFallo,
    error: errorInscripcion,
    refetch: recargarInscripcion,
  } = useMiInscripcion(id);
  const { data: notas, isLoading: cargandoNotas, isError: notasFallaron, refetch: recargarNotas } = useNotasDeMateria(id);
  const darseDeBaja = useDarmeDeBajaDeMateria();

  if (cargandoInscripcion) {
    return (
      <PageContainer>
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-28" />
        <Skeleton className="h-32" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  if (inscripcionFallo) {
    return (
      <PageContainer>
        <LoadErrorState
          error={errorInscripcion}
          onRetry={recargarInscripcion}
          resource="la materia" distinguishForbidden
          backTo="/alumno/mis-carreras"
          backLabel="Mis carreras"
        />
      </PageContainer>
    );
  }

  if (!inscripcion) {
    return (
      <PageContainer>
        <GlassCard className="flex flex-1 flex-col p-12">
          <EmptyState
            icon="📚"
            title="Materia no encontrada"
            description="Puede que ya no esté disponible."
            action={
              <Button variant="outline" className="h-11" onClick={() => navigate('/alumno/mis-carreras')}>
                Volver a Mis carreras
              </Button>
            }
          />
        </GlassCard>
      </PageContainer>
    );
  }

  const badge = BADGE_ESTADO[inscripcion.status] ?? { label: 'Sin estado', tone: 'neutral' };
  const puedeDarseDeBaja = ESTADOS_QUE_ADMITEN_BAJA.has(inscripcion.status);
  const { columnas, extras } = mapearNotasAColumnas(notas);

  async function confirmarBaja() {
    try {
      await darseDeBaja.mutateAsync(id);
    } catch {
      return;
    }
    setModalBajaAbierto(false);
    navigate('/alumno/mis-carreras');
  }

  return (
    <PageContainer>
      <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">{inscripcion.subjectName}</h1>
            <StatusPill tone={badge.tone}>
              <badge.Icon className="h-3.5 w-3.5" aria-hidden="true" />
              {badge.label}
            </StatusPill>
          </div>
          <div className="mt-1 text-sm text-ink-faint">
            {inscripcion.subjectCode && `Código ${inscripcion.subjectCode} · `}
            {inscripcion.sectionName}
            {inscripcion.curricularYear ? ` · ${inscripcion.curricularYear}° año` : ''}
          </div>
        </div>
        {puedeDarseDeBaja && (
          <Button variant="outline" className="h-11 px-4 text-danger" onClick={() => setModalBajaAbierto(true)}>
            Darme de baja
          </Button>
        )}
      </div>

      <GlassCard className="grid grid-cols-1 gap-4 p-5 sm:grid-cols-3" aria-label="Datos de la comisión" role="region">
        <div>
          <div className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Profesor</div>
          <div className="mt-1 text-size-13-5 font-semibold text-ink">{inscripcion.instructorTitular ?? 'A confirmar'}</div>
        </div>
        <div>
          <div className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Adjunto</div>
          <div className="mt-1 text-size-13-5 font-semibold text-ink">{inscripcion.instructorAdjunto ?? '—'}</div>
        </div>
        <div>
          <div className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Horarios</div>
          <div className="mt-1 text-size-13-5 font-semibold text-ink">{inscripcion.schedule ?? 'A confirmar'}</div>
        </div>
      </GlassCard>

      <SeccionAsistencia sectionEnrollmentId={id} />

      <GlassCard className="flex flex-col gap-4 p-5">
        <h2 className="flex items-center gap-2 font-heading text-size-15 font-semibold text-ink">
          <Star className="h-4 w-4 text-primary" aria-hidden="true" />
          Notas
        </h2>
        {cargandoNotas ? (
          <Skeleton className="h-16" />
        ) : notasFallaron ? (
          <LoadErrorState onRetry={recargarNotas} resource="las notas" bare />
        ) : (
          <>
            <div className="overflow-x-auto">
            <Table className="min-w-[560px]">
              <TableCaption className="sr-only">Notas de la materia</TableCaption>
              <TableHeader>
                <TableRow>
                  {COLUMNAS_NOTAS.map((columna) => (
                    <TableHead key={columna.key}>{columna.label}</TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  {COLUMNAS_NOTAS.map((columna) => (
                    <TableCell key={columna.key} className="font-heading text-base font-bold tabular-nums text-ink">
                      {columnas[columna.key] ?? <span className="font-normal text-ink-faint">—</span>}
                    </TableCell>
                  ))}
                </TableRow>
              </TableBody>
            </Table>
            </div>
            {extras.length > 0 && (
              <div className="mt-3 text-xs text-ink-faint">
                Notas adicionales: {extras.map((g) => `${TIPO_NOTA_LABEL[g.type] ?? 'Nota'} ${g.sequenceNumber ?? ''}: ${g.score ?? '—'}`).join(' · ')}
              </div>
            )}
          </>
        )}
      </GlassCard>

      <ConfirmActionDialog
        open={modalBajaAbierto}
        onOpenChange={setModalBajaAbierto}
        title="¿Dar de baja esta materia?"
        description={`Vas a perder tu lugar en ${inscripcion.sectionName}. Si querés volver a cursarla vas a tener que inscribirte de nuevo.`}
        confirmLabel="Sí, darme de baja"
        pending={darseDeBaja.isPending}
        onConfirm={confirmarBaja}
      >
        {darseDeBaja.isError && (
          <div role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
            No pudimos procesar la baja. Probá de nuevo.
          </div>
        )}
      </ConfirmActionDialog>
    </PageContainer>
  );
}
