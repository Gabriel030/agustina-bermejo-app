import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { useMiInscripcion, useNotasDeMateria, useDarmeDeBajaDeMateria } from '@/hooks/useAlumno';
import { COLUMNAS_NOTAS, mapearNotasAColumnas } from '@/lib/notas';
import { MateriaContenidoMock } from '@/components/alumno/MateriaContenidoMock';
import { SeccionAsistencia } from '@/components/alumno/SeccionAsistencia';

const ESTADOS_QUE_ADMITEN_BAJA = new Set(['ENROLLED', 'IN_PROGRESS']);

const BADGE_ESTADO = {
  REGULAR: { label: 'Regular', tone: 'success' },
  IN_PROGRESS: { label: 'En curso', tone: 'accent' },
  WAITLISTED: { label: 'En lista de espera', tone: 'warning' },
  ENROLLED: { label: 'Inscripto', tone: 'accent' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  FAILED: { label: 'Desaprobada', tone: 'danger' },
  WITHDRAWN: { label: 'De baja', tone: 'neutral' },
};

// Épica 2 Fase 2 — detalle de una materia en curso: grilla materia/profesor/
// adjunto/horarios, notas (columnas fijas, ver lib/notas.js) y la acción de
// darse de baja, que antes no tenía dónde vivir en Mis carreras (decisión de
// producto: sacarla de un botón grande en la pantalla principal y ponerla acá,
// en el contexto de la materia puntual).
export function AlumnoMateriaDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [modalBajaAbierto, setModalBajaAbierto] = useState(false);

  const { data: inscripcion, isLoading: cargandoInscripcion } = useMiInscripcion(id);
  const { data: notas, isLoading: cargandoNotas } = useNotasDeMateria(id);
  const darseDeBaja = useDarmeDeBajaDeMateria();

  if (cargandoInscripcion) {
    return (
      <PageContainer>
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-32" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  if (!inscripcion) {
    return (
      <GlassCard className="flex flex-1 flex-col items-center justify-center gap-2 p-12 text-center">
        <div className="font-heading text-xl font-semibold text-ink">Materia no encontrada</div>
        <button type="button" onClick={() => navigate('/alumno/mis-carreras/materias')} className="text-sm font-semibold text-primary">
          Volver a Materias
        </button>
      </GlassCard>
    );
  }

  const badge = BADGE_ESTADO[inscripcion.status] ?? { label: inscripcion.status, tone: 'neutral' };
  const puedeDarseDeBaja = ESTADOS_QUE_ADMITEN_BAJA.has(inscripcion.status);
  const { columnas, extras } = mapearNotasAColumnas(notas);

  async function confirmarBaja() {
    await darseDeBaja.mutateAsync(id);
    setModalBajaAbierto(false);
    navigate('/alumno/mis-carreras');
  }

  return (
    <PageContainer>
      <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="font-heading text-2xl font-bold tracking-tight text-ink">{inscripcion.subjectName}</div>
            <StatusPill tone={badge.tone}>{badge.label}</StatusPill>
          </div>
          <div className="mt-1 text-sm text-ink-faint">
            {inscripcion.subjectCode && `Código ${inscripcion.subjectCode} · `}
            {inscripcion.sectionName}
            {inscripcion.curricularYear ? ` · ${inscripcion.curricularYear}° año` : ''}
          </div>
        </div>
        {puedeDarseDeBaja && (
          <Button variant="outline" className="rounded-xl text-danger" onClick={() => setModalBajaAbierto(true)}>
            Darme de baja
          </Button>
        )}
      </div>

      <GlassCard className="grid grid-cols-1 gap-4 p-5 sm:grid-cols-3">
        <div>
          <div className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Profesor</div>
          <div className="mt-1 text-[13.5px] font-semibold text-ink">{inscripcion.instructorTitular ?? 'A confirmar'}</div>
        </div>
        <div>
          <div className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Adjunto</div>
          <div className="mt-1 text-[13.5px] font-semibold text-ink">{inscripcion.instructorAdjunto ?? '—'}</div>
        </div>
        <div>
          <div className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Horarios</div>
          <div className="mt-1 text-[13.5px] font-semibold text-ink">{inscripcion.schedule ?? 'A confirmar'}</div>
        </div>
      </GlassCard>

      {/* Objetivos/Temario/Cronograma — contenido mockeado (ver MateriaContenidoMock.jsx),
          cards apiladas en vez de tabs internas (decisión de UX): es contenido liviano de
          lectura secuencial, y agregar una segunda capa de navegación acá iría en contra
          del pedido explícito del usuario de simplificar el flujo, no complicarlo más. */}
      <MateriaContenidoMock subjectName={inscripcion.subjectName} />

      <SeccionAsistencia sectionEnrollmentId={id} />

      <GlassCard className="flex flex-col gap-4 p-5">
        <div className="font-heading text-[15px] font-semibold text-ink">Notas</div>
        {cargandoNotas ? (
          <Skeleton className="h-16" />
        ) : (
          <>
            <div className="overflow-x-auto">
            <Table className="min-w-[480px]">
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
                    <TableCell key={columna.key} className="font-heading text-base font-bold text-ink">
                      {columnas[columna.key] ?? <span className="font-normal text-ink-faint">—</span>}
                    </TableCell>
                  ))}
                </TableRow>
              </TableBody>
            </Table>
            </div>
            {extras.length > 0 && (
              <div className="mt-3 text-xs text-ink-faint">
                Notas adicionales: {extras.map((g) => `${g.type} ${g.sequenceNumber ?? ''}: ${g.score ?? '—'}`).join(' · ')}
              </div>
            )}
          </>
        )}
      </GlassCard>

      <Dialog open={modalBajaAbierto} onOpenChange={setModalBajaAbierto}>
        <DialogContent className="max-w-sm rounded-xl border border-border bg-surface p-6 text-ink backdrop-blur-[28px] backdrop-saturate-[1.6]">
          <DialogHeader>
            <DialogTitle>¿Dar de baja esta materia?</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-ink-faint">
            Vas a perder tu lugar en {inscripcion.sectionName}. Si querés volver a cursarla vas a tener que inscribirte de nuevo.
          </p>
          {darseDeBaja.isError && (
            <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
              No pudimos procesar la baja. Probá de nuevo.
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" className="rounded-xl" onClick={() => setModalBajaAbierto(false)}>
              Cancelar
            </Button>
            <Button variant="gradient" onClick={confirmarBaja} disabled={darseDeBaja.isPending}>
              {darseDeBaja.isPending ? 'Dando de baja…' : 'Sí, darme de baja'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
