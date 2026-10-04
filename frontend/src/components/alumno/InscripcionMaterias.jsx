import { useMemo, useState } from 'react';
import { AlertTriangle, ArrowLeft, CheckCircle2, ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { useMateriasDelPlan } from '@/hooks/useMateriasDelPlan';
import { useMisInscripciones, useSeccionesDeMateria, useInscribirmeAMateria, useEstadoMatricula } from '@/hooks/useAlumno';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { AvisoMatricula } from './AvisoMatricula';
import { obligacionPendiente, CODIGO_MATRICULA_IMPAGA } from '@/lib/matricula';
import { ESTADOS_EN_CURSO } from '@/lib/notas';
import { horarioMockDeComision, seSuperponen } from '@/lib/horarioMock';

// Inscripción a materias (Épica 2 Fase 2) — flujo de 3 pasos EN LA MISMA
// PANTALLA (pedido explícito del usuario, para poder anotarse a varias
// materias seguidas sin ir y volver): elegir materia → elegir comisión →
// confirmar. Al confirmar, vuelve al paso 1 con la lista de disponibles ya
// actualizada (la materia recién inscripta deja de aparecer ahí).
//
// Horarios: MOCK (ver lib/horarioMock.js) — el campo real de horario todavía
// no tiene día/hora estructurados en el backend, así que la validación de
// superposición usa un horario de ejemplo determinístico por comisión, no el
// dato real. Sirve para demostrar la UX; cuando el backend tenga horario
// estructurado, solo cambia horarioMock.js.
export function InscripcionMaterias({ carreraId, onCerrar }) {
  const [materiaSeleccionada, setMateriaSeleccionada] = useState(null);
  const [comisionSeleccionada, setComisionSeleccionada] = useState(null);
  const [confirmacion, setConfirmacion] = useState(null);
  const [matriculaRechazada, setMatriculaRechazada] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: obligaciones } = useEstadoMatricula(carreraId);
  const pendiente = obligacionPendiente(obligaciones);

  const { filas, cargando: cargandoMaterias } = useMateriasDelPlan(carreraId);
  const disponibles = filas.filter((f) => f.estado === 'disponible' || f.estado === 'desaprobada');

  const { data: inscripciones } = useMisInscripciones();
  // Horarios ya comprometidos por el alumno (cualquier carrera, no solo esta —
  // un choque de horario es un choque real independientemente de en qué
  // carrera esté cada comisión) — mismo mock determinístico por sectionId.
  const horariosComprometidos = useMemo(
    () =>
      (inscripciones ?? [])
        .filter((i) => ESTADOS_EN_CURSO.has(i.status))
        .map((i) => ({ sectionName: i.sectionName, subjectName: i.subjectName, ...horarioMockDeComision(i.sectionId) })),
    [inscripciones],
  );

  const { data: comisiones, isLoading: cargandoComisiones } = useSeccionesDeMateria(materiaSeleccionada?.subjectId);
  const inscribirme = useInscribirmeAMateria();

  function elegirMateria(fila) {
    setMateriaSeleccionada(fila);
    setComisionSeleccionada(null);
    setConfirmacion(null);
  }

  function volverAMaterias() {
    setMateriaSeleccionada(null);
    setComisionSeleccionada(null);
  }

  async function confirmar() {
    try {
      await inscribirme.mutateAsync({ programEnrollmentId: carreraId, sectionId: comisionSeleccionada.id });
    } catch (error) {
      if (error?.code === CODIGO_MATRICULA_IMPAGA) {
        setMatriculaRechazada(true);
        queryClient.invalidateQueries({ queryKey: ['me', 'program-enrollments', carreraId, 'enrollment-fee-status'] });
      }
      return;
    }
    setConfirmacion({ subjectName: materiaSeleccionada.subjectName, sectionName: comisionSeleccionada.name });
    setMateriaSeleccionada(null);
    setComisionSeleccionada(null);
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <button type="button" onClick={onCerrar} className="flex items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink">
          <ArrowLeft className="h-4 w-4" /> Volver a mis materias
        </button>
      </div>

      {confirmacion && (
        <GlassCard className="flex items-center gap-3 bg-success-bg p-4">
          <CheckCircle2 className="h-5 w-5 shrink-0 text-success" />
          <div className="text-[13.5px] font-semibold text-ink">
            Te inscribiste a {confirmacion.subjectName} — {confirmacion.sectionName}. Podés elegir otra materia para seguir anotándote.
          </div>
        </GlassCard>
      )}

      {/* Paso 1: elegir materia */}
      {!materiaSeleccionada && (
        <GlassCard className="flex flex-col gap-3 p-5">
          <div className="font-heading text-[15px] font-semibold text-ink">Elegí una materia</div>
          {cargandoMaterias ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-12" />
              <Skeleton className="h-12" />
            </div>
          ) : disponibles.length === 0 ? (
            <EmptyState title="No tenés materias disponibles" description="Ya estás inscripto o aprobaste todo lo que podías cursar ahora." />
          ) : (
            <div className="flex flex-col gap-1">
              {disponibles.map((fila) => (
                <button
                  key={fila.key}
                  type="button"
                  onClick={() => elegirMateria(fila)}
                  className="flex items-center justify-between rounded-xl border border-border px-4 py-3 text-left transition-colors hover:border-brand hover:bg-neutral-bg"
                >
                  <div>
                    <div className="text-[13.5px] font-semibold text-ink">{fila.subjectName}</div>
                    <div className="text-xs text-ink-faint">{fila.curricularYear ? `${fila.curricularYear}° año` : ''}</div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-ink-faint" />
                </button>
              ))}
            </div>
          )}
        </GlassCard>
      )}

      {/* Paso 2: elegir comisión, paso 3: confirmar (misma card, la comisión elegida se marca) */}
      {materiaSeleccionada && (
        <GlassCard className="flex flex-col gap-4 p-5">
          <div className="flex items-center justify-between">
            <div>
              <div className="font-heading text-[15px] font-semibold text-ink">{materiaSeleccionada.subjectName}</div>
              <div className="text-xs text-ink-faint">Elegí una comisión</div>
            </div>
            <button type="button" onClick={volverAMaterias} className="text-xs font-bold text-primary hover:underline">
              Cambiar materia
            </button>
          </div>

          {cargandoComisiones ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-14" />
              <Skeleton className="h-14" />
            </div>
          ) : !comisiones || comisiones.length === 0 ? (
            <EmptyState title="No hay comisiones abiertas" description="Todavía no se armó ninguna comisión para esta materia." />
          ) : (
            <div className="flex flex-col gap-2">
              {comisiones.map((comision) => {
                const horario = horarioMockDeComision(comision.id);
                const conflicto = horariosComprometidos.find((h) => seSuperponen(h, horario));
                const seleccionada = comisionSeleccionada?.id === comision.id;
                return (
                  <button
                    key={comision.id}
                    type="button"
                    disabled={!!conflicto}
                    onClick={() => setComisionSeleccionada(comision)}
                    className={`flex flex-col gap-1 rounded-xl border px-4 py-3 text-left transition-colors ${
                      conflicto
                        ? 'cursor-not-allowed border-border bg-neutral-bg opacity-60'
                        : seleccionada
                          ? 'border-brand bg-accent-soft'
                          : 'border-border hover:border-brand hover:bg-neutral-bg'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-[13.5px] font-semibold text-ink">{comision.name}</span>
                      <span className="text-xs font-semibold text-ink-faint">{horario.label}</span>
                    </div>
                    {conflicto && (
                      <div className="flex items-center gap-1.5 text-xs font-semibold text-danger">
                        <AlertTriangle className="h-3.5 w-3.5" />
                        Se pisa con {conflicto.subjectName} ({conflicto.sectionName}, {conflicto.label})
                      </div>
                    )}
                  </button>
                );
              })}
            </div>
          )}

          {(pendiente || matriculaRechazada) && (
            <AvisoMatricula obligacion={pendiente} onPagar={(invoiceId) => navigate(`/alumno/pagos/${invoiceId}/seleccion`)} />
          )}

          {inscribirme.isError && !matriculaRechazada && (
            <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
              No pudimos completar la inscripción. Probá de nuevo.
            </div>
          )}

          <Button variant="gradient" disabled={!comisionSeleccionada || inscribirme.isPending} onClick={confirmar} className="self-start">
            {inscribirme.isPending ? 'Confirmando…' : 'Confirmar inscripción'}
          </Button>
        </GlassCard>
      )}
    </div>
  );
}
